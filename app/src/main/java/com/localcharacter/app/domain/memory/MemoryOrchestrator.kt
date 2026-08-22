package com.localcharacter.app.domain.memory

import android.util.Log
import com.localcharacter.app.AppBuildInfo
import com.localcharacter.app.data.repository.ChatRepository
import com.localcharacter.app.data.repository.MemoryRepository
import com.localcharacter.app.data.repository.PendingEventRepository
import com.localcharacter.app.data.repository.RelationshipRepository
import com.localcharacter.app.data.repository.SummaryRepository
import com.localcharacter.app.data.settings.MemoryLevel
import com.localcharacter.app.data.settings.MemorySettings
import com.localcharacter.app.domain.model.Character
import com.localcharacter.app.domain.model.CharacterRelationship
import com.localcharacter.app.domain.model.ChatMessage
import com.localcharacter.app.domain.model.Conversation
import com.localcharacter.app.domain.model.ConversationSummary
import com.localcharacter.app.domain.model.GenerationSettings
import com.localcharacter.app.domain.model.Memory
import com.localcharacter.app.domain.model.MemoryOrigin
import com.localcharacter.app.domain.model.MemoryType
import com.localcharacter.app.domain.model.PendingEvent
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlin.math.max

data class MemoryProcessingReport(val candidates: Int, val stored: Int, val merged: Int, val replaced: Int)

class MemoryOrchestrator(
    private val memories: MemoryRepository,
    private val summaries: SummaryRepository,
    private val relationships: RelationshipRepository,
    private val pendingEvents: PendingEventRepository,
    private val chats: ChatRepository,
    private val extraction: MemoryExtractionService,
    private val summarizer: ConversationSummarizer,
    private val conflictResolver: MemoryConflictResolver = MemoryConflictResolver(),
    private val pendingEventManager: PendingEventManager = PendingEventManager(),
    private val relationshipManager: RelationshipManager = RelationshipManager(),
    private val deduplicator: MemoryDeduplicator = MemoryDeduplicator(),
    private val explicitDetector: ExplicitMemoryIntentDetector = ExplicitMemoryIntentDetector(),
    private val forgetDetector: MemoryForgetIntentDetector = MemoryForgetIntentDetector(),
    private val opinionDetector: CharacterOpinionDetector = CharacterOpinionDetector(),
) {
    suspend fun handleManualIntents(
        message: ChatMessage,
        character: Character,
        conversation: Conversation,
        settings: MemorySettings,
    ) {
        forgetDetector.detect(message.content)?.let { target ->
            val candidates = memories.candidates(
                character.id, conversation.id, conversation.userPersonaId, settings.shareAcrossChats,
            )
            val targetTokens = MemoryTextNormalizer.tokens(target)
            candidates.map { memory ->
                val memoryTokens = MemoryTextNormalizer.tokens(memory.content)
                val overlap = if (targetTokens.isEmpty()) 0f else {
                    targetTokens.intersect(memoryTokens).size.toFloat() / targetTokens.size
                }
                memory to overlap
            }.filter { it.second >= 0.45f }
                .sortedByDescending { it.second }
                .take(3)
                .forEach { memories.deactivate(it.first.id) }
        }

        explicitDetector.detect(message.content)?.let { content ->
            val now = System.currentTimeMillis()
            val candidate = MemoryCandidate(
                type = MemoryType.FACT,
                content = content,
                importance = 1f,
                confidence = 1f,
                origin = MemoryOrigin.USER_STATED_FACT,
                isPinned = true,
            )
            val existing = memories.candidates(
                character.id, conversation.id, conversation.userPersonaId, settings.shareAcrossChats, now,
            )
            val duplicate = deduplicator.find(candidate, existing)?.memory
            if (duplicate != null) {
                memories.save(duplicate.copy(importance = 1f, confidence = 1f, isPinned = true, expiresAt = null, updatedAt = now))
            } else {
                memories.save(Memory(
                    id = UUID.randomUUID().toString(),
                    characterId = character.id,
                    conversationId = if (settings.shareAcrossChats) null else conversation.id,
                    userPersonaId = conversation.userPersonaId,
                    type = MemoryType.FACT,
                    content = content,
                    normalizedContent = MemoryTextNormalizer.normalize(content),
                    importance = 1f,
                    confidence = 1f,
                    origin = MemoryOrigin.USER_STATED_FACT,
                    createdAt = now,
                    updatedAt = now,
                    lastAccessedAt = now,
                    sourceMessageId = message.id,
                    isPinned = true,
                ))
            }
        }
    }

    /** Stores explicit character opinions immediately, before the slower extraction pass. */
    suspend fun rememberCharacterOpinions(
        characterMessage: ChatMessage,
        character: Character,
        conversation: Conversation,
        settings: MemorySettings,
    ): Int {
        if (!settings.intelligentMemory) return 0
        val candidates = opinionDetector.detect(characterMessage.content, character.name)
        candidates.forEach { candidate ->
            persist(candidate, null, characterMessage, character, conversation, settings)
        }
        if (candidates.isNotEmpty()) debug("Character opinions stored immediately: ${candidates.size}")
        return candidates.size
    }

    suspend fun processAfterTurn(
        userMessage: ChatMessage?,
        characterMessage: ChatMessage,
        character: Character,
        conversation: Conversation,
        memorySettings: MemorySettings,
        generationSettings: GenerationSettings,
    ): MemoryProcessingReport {
        if (!memorySettings.intelligentMemory) return MemoryProcessingReport(0, 0, 0, 0)
        updateRelationship(userMessage, characterMessage, character, conversation)

        var report = MemoryProcessingReport(0, 0, 0, 0)
        runCatching {
            val extracted = extraction.extract(
                userMessage?.content.orEmpty(), characterMessage.content, character.name, generationSettings,
            ).filter { it.importance >= threshold(memorySettings.level) }
            var stored = 0
            var merged = 0
            var replaced = 0
            extracted.forEach { candidate ->
                when (persist(candidate, userMessage, characterMessage, character, conversation, memorySettings)) {
                    ConflictAction.COEXIST -> stored++
                    ConflictAction.MERGE -> merged++
                    ConflictAction.REPLACE -> replaced++
                }
            }
            report = MemoryProcessingReport(extracted.size, stored, merged, replaced)
            debug("MemoryExtraction: ${extracted.size} candidates; Stored: $stored; Merged: $merged; Replaced: $replaced")
        }.onFailure { debug("MemoryExtraction failed safely: ${it.message}") }

        if (memorySettings.conversationSummaries) {
            runCatching { updateSummary(conversation, character, generationSettings) }
                .onFailure { debug("ConversationSummary failed safely: ${it.message}") }
        }
        return report
    }

    suspend fun markFollowUpsUsed(events: List<PendingEvent>, userMessage: String, response: String) {
        if ('?' !in response && '¿' !in response) return
        val responseTokens = MemoryTextNormalizer.tokens(response)
        val userSignalsOutcome = Regex("(?i)\\b(termin[eé]|acab[eé]|salió|fue|resultado|arreglamos|volvió)\\b")
            .containsMatchIn(userMessage)
        events.forEach { event ->
            val relevant = responseTokens.intersect(MemoryTextNormalizer.tokens(event.description)).isNotEmpty()
            if (relevant) {
                val now = System.currentTimeMillis()
                pendingEvents.markAsked(event.id, now, now + 3L * 86_400_000L)
                if (userSignalsOutcome) pendingEvents.resolve(event.id)
            }
        }
    }

    private suspend fun persist(
        candidate: MemoryCandidate,
        userMessage: ChatMessage?,
        characterMessage: ChatMessage,
        character: Character,
        conversation: Conversation,
        settings: MemorySettings,
    ): ConflictAction {
        val now = System.currentTimeMillis()
        val scopedConversation = if (settings.shareAcrossChats) null else conversation.id
        val sourceId = if (
            candidate.origin == MemoryOrigin.CHARACTER_STATED ||
            candidate.origin == MemoryOrigin.CHARACTER_INFERENCE ||
            userMessage == null
        ) {
            characterMessage.id
        } else userMessage.id
        val memory = Memory(
            id = UUID.randomUUID().toString(),
            characterId = character.id,
            conversationId = scopedConversation,
            userPersonaId = conversation.userPersonaId,
            type = candidate.type,
            content = candidate.content,
            normalizedContent = MemoryTextNormalizer.normalize(candidate.content),
            importance = candidate.importance,
            confidence = candidate.confidence,
            origin = candidate.origin,
            createdAt = now,
            updatedAt = now,
            lastAccessedAt = now,
            sourceMessageId = sourceId,
            eventAt = candidate.eventAt,
            expiresAt = if (candidate.isPinned) null else candidate.expiresAt
                ?: candidate.eventAt?.takeIf { candidate.type == MemoryType.EVENT && it > now }?.plus(2L * 86_400_000L),
            isPinned = candidate.isPinned,
        )
        val existing = memories.candidates(
            character.id, conversation.id, conversation.userPersonaId, settings.shareAcrossChats, now,
        )
        val resolution = conflictResolver.resolve(candidate, existing)
        val stored = when (resolution.action) {
            ConflictAction.MERGE -> {
                val old = requireNotNull(resolution.existing)
                old.copy(
                    importance = max(old.importance, memory.importance),
                    confidence = max(old.confidence, memory.confidence),
                    updatedAt = now,
                    isPinned = old.isPinned || memory.isPinned,
                    eventAt = old.eventAt ?: memory.eventAt,
                    expiresAt = if (old.isPinned || memory.isPinned) null else old.expiresAt ?: memory.expiresAt,
                ).also { memories.save(it) }
            }
            ConflictAction.REPLACE -> memory.also { memories.supersede(requireNotNull(resolution.existing).id, it) }
            ConflictAction.COEXIST -> memory.also { memories.save(it) }
        }
        if (resolution.action != ConflictAction.MERGE) {
            pendingEventManager.fromMemory(stored, now)?.let { pendingEvents.save(it) }
        }
        return resolution.action
    }

    private suspend fun updateRelationship(
        userMessage: ChatMessage?,
        characterMessage: ChatMessage,
        character: Character,
        conversation: Conversation,
    ) {
        val now = System.currentTimeMillis()
        val id = UUID.nameUUIDFromBytes(
            "${character.id}|${conversation.userPersonaId.orEmpty()}|${conversation.id}".toByteArray(StandardCharsets.UTF_8),
        ).toString()
        val current = relationships.get(character.id, conversation.id, conversation.userPersonaId)
            ?: relationshipManager.initial(id, character.id, conversation.id, conversation.userPersonaId, now)
        relationships.save(relationshipManager.evolve(current, userMessage?.content.orEmpty(), characterMessage.content, now))
    }

    private suspend fun updateSummary(
        conversation: Conversation,
        character: Character,
        settings: GenerationSettings,
    ) {
        val previous = summaries.latest(conversation.id)
        val unsummarized = chats.after(conversation.id, previous?.toCreatedAt ?: -1L)
        val total = chats.messageCount(conversation.id)
        if (!summarizer.shouldSummarize(total, unsummarized.size)) return
        val window = summarizer.selectWindow(unsummarized)
        if (window.isEmpty()) return
        val text = summarizer.summarize(previous?.summary.orEmpty(), window, character.name, settings)
        if (text.isBlank()) return
        val now = System.currentTimeMillis()
        summaries.save(
            ConversationSummary(
                id = UUID.randomUUID().toString(),
                conversationId = conversation.id,
                summary = text,
                fromMessageId = window.first().id,
                toMessageId = window.last().id,
                fromCreatedAt = window.first().createdAt,
                toCreatedAt = window.last().createdAt,
                createdAt = now,
                updatedAt = now,
            ),
        )
        debug("Summary tokens (estimated): ${text.length / 4}")
    }

    private fun threshold(level: MemoryLevel): Float = when (level) {
        MemoryLevel.MINIMAL -> 0.65f
        MemoryLevel.NORMAL -> 0.45f
        MemoryLevel.DETAILED -> 0.3f
    }

    private fun debug(message: String) {
        if (AppBuildInfo.DEBUG) Log.d("LocalMemory", message)
    }
}
