package com.localcharacter.app.domain.memory

import com.localcharacter.app.domain.model.CharacterRelationship
import com.localcharacter.app.domain.model.Memory
import com.localcharacter.app.domain.model.MemoryType
import com.localcharacter.app.domain.model.PendingEvent
import com.localcharacter.app.domain.model.PendingEventStatus
import java.time.Duration
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

data class DuplicateMatch(val memory: Memory, val similarity: Float)

class MemoryDeduplicator {
    fun find(candidate: MemoryCandidate, existing: List<Memory>): DuplicateMatch? = existing.asSequence()
        .filter { it.isActive }
        .map { memory -> DuplicateMatch(memory, similarity(candidate, memory)) }
        .filter { it.similarity >= 0.72f }
        .maxByOrNull { it.similarity }

    fun similarity(candidate: MemoryCandidate, memory: Memory): Float {
        val left = MemoryTextNormalizer.tokens(candidate.content)
        val right = MemoryTextNormalizer.tokens(memory.content)
        if (left.isEmpty() || right.isEmpty()) return 0f
        val jaccard = left.intersect(right).size.toFloat() / left.union(right).size
        val containment = left.intersect(right).size.toFloat() / min(left.size, right.size)
        val sameType = if (candidate.type == memory.type) 0.12f else 0f
        return (jaccard * 0.45f + containment * 0.43f + sameType).coerceAtMost(1f)
    }
}

enum class ConflictAction { COEXIST, MERGE, REPLACE }
data class ConflictResolution(val action: ConflictAction, val existing: Memory? = null)

class MemoryConflictResolver(private val deduplicator: MemoryDeduplicator = MemoryDeduplicator()) {
    fun resolve(candidate: MemoryCandidate, existing: List<Memory>): ConflictResolution {
        deduplicator.find(candidate, existing)?.let { return ConflictResolution(ConflictAction.MERGE, it.memory) }
        if (candidate.type in setOf(MemoryType.EVENT, MemoryType.EMOTIONAL, MemoryType.SHARED_EVENT, MemoryType.PROMISE)) {
            return ConflictResolution(ConflictAction.COEXIST)
        }
        val newSlot = semanticSlot(candidate.content, candidate.type) ?: return ConflictResolution(ConflictAction.COEXIST)
        val conflict = existing.asSequence()
            .filter { it.isActive && it.type == candidate.type }
            .filter { semanticSlot(it.content, it.type) == newSlot }
            .maxByOrNull { it.updatedAt }
        return if (conflict != null && indicatesChange(candidate.content, conflict.content)) {
            ConflictResolution(ConflictAction.REPLACE, conflict)
        } else {
            ConflictResolution(ConflictAction.COEXIST)
        }
    }

    private fun semanticSlot(text: String, type: MemoryType): String? {
        val normalized = MemoryTextNormalizer.ascii(text)
        return when {
            Regex("\\b(vivo|vive|mude|mudo|residencia)\\b").containsMatchIn(normalized) -> "residence"
            Regex("\\b(trabajo|trabaja|empleo)\\b").containsMatchIn(normalized) -> "employment"
            Regex("\\b(estudio|estudia|carrera)\\b").containsMatchIn(normalized) -> "studies"
            type == MemoryType.PREFERENCE -> {
                val subject = MemoryTextNormalizer.tokens(text)
                    .filterNot {
                        it in setOf("preferencia", "quiero", "encanta", "odio", "gusta", "no", "nunca", "deje") ||
                            it.startsWith("tom")
                    }
                    .sorted().joinToString("_")
                subject.takeIf { it.isNotBlank() }?.let { "preference:$it" }
            }
            else -> null
        }
    }

    private fun indicatesChange(newText: String, oldText: String): Boolean {
        val next = MemoryTextNormalizer.ascii(newText)
        val previous = MemoryTextNormalizer.ascii(oldText)
        val markers = Regex("\\b(ahora|actualmente|mude|cambie|ya no|deje|antes)\\b")
        val oppositePolarity = Regex("\\b(no|nunca|deje|ya no)\\b").containsMatchIn(next) !=
            Regex("\\b(no|nunca|deje|ya no)\\b").containsMatchIn(previous)
        return markers.containsMatchIn(next) || oppositePolarity
    }
}

class PendingEventManager(
    private val followUpCooldownMillis: Long = Duration.ofDays(3).toMillis(),
) {
    fun fromMemory(memory: Memory, now: Long = System.currentTimeMillis()): PendingEvent? {
        val eventAt = memory.eventAt ?: return null
        if (memory.type !in setOf(MemoryType.EVENT, MemoryType.PROMISE, MemoryType.GOAL)) return null
        return PendingEvent(
            id = UUID.randomUUID().toString(),
            characterId = memory.characterId,
            conversationId = memory.conversationId,
            userPersonaId = memory.userPersonaId,
            description = memory.content,
            eventAt = eventAt,
            status = if (eventAt <= now) PendingEventStatus.FOLLOW_UP_AVAILABLE else PendingEventStatus.PENDING,
            sourceMessageId = memory.sourceMessageId,
            sourceMemoryId = memory.id,
            createdAt = now,
            updatedAt = now,
        )
    }

    fun refresh(event: PendingEvent, now: Long): PendingEvent = when {
        !event.isActive -> event
        event.status == PendingEventStatus.PENDING && event.eventAt <= now ->
            event.copy(status = PendingEventStatus.FOLLOW_UP_AVAILABLE, updatedAt = now)
        else -> event
    }

    fun canAsk(event: PendingEvent, now: Long): Boolean = event.isActive && event.eventAt <= now &&
        event.status in setOf(PendingEventStatus.PENDING, PendingEventStatus.FOLLOW_UP_AVAILABLE, PendingEventStatus.ASKED) &&
        (event.cooldownUntil == null || event.cooldownUntil <= now)

    fun markAsked(event: PendingEvent, now: Long): PendingEvent = event.copy(
        status = PendingEventStatus.ASKED,
        followUpAskedAt = now,
        cooldownUntil = now + followUpCooldownMillis,
        updatedAt = now,
    )
}

class RelationshipManager {
    fun evolve(
        current: CharacterRelationship,
        userMessage: String,
        characterMessage: String,
        now: Long = System.currentTimeMillis(),
    ): CharacterRelationship {
        val text = MemoryTextNormalizer.ascii("$userMessage $characterMessage")
        val warm = Regex("\\b(gracias|confio|amigo|carino|quiero|ayudaste|secreto)\\b").findAll(text).count()
        val conflict = Regex("\\b(enojado|odio|molesto|discusion|peleamos|callate|idiota)\\b").findAll(text).count()
        val reconcile = Regex("\\b(perdon|disculpa|arreglamos|reconcili)\\b").containsMatchIn(text)
        val trustDelta = (warm * 0.012f) + if (reconcile) 0.015f else 0f
        val affectionDelta = (warm * 0.01f) - (conflict * 0.006f)
        val tensionDelta = (conflict * 0.02f) - if (reconcile) 0.035f else 0f
        return current.copy(
            trust = (current.trust + trustDelta).coerceIn(0f, 1f),
            affection = (current.affection + affectionDelta).coerceIn(0f, 1f),
            familiarity = min(1f, current.familiarity + 0.006f),
            tension = (current.tension + tensionDelta).coerceIn(0f, 1f),
            relationshipSummary = describe(
                (current.trust + trustDelta).coerceIn(0f, 1f),
                min(1f, current.familiarity + 0.006f),
                (current.tension + tensionDelta).coerceIn(0f, 1f),
            ),
            lastInteractionAt = now,
            interactionCount = current.interactionCount + 1,
        )
    }

    fun describe(trust: Float, familiarity: Float, tension: Float): String = buildList {
        add(if (familiarity < 0.2f) "La relación aún es reciente." else "Existe familiaridad entre ambos.")
        if (trust > 0.55f) add("El usuario ha mostrado confianza.")
        if (tension > 0.25f) add("Hay cierta tensión pendiente; evita fingir que no ocurrió.")
    }.joinToString(" ")

    fun initial(id: String, characterId: String, conversationId: String?, userPersonaId: String?, now: Long) =
        CharacterRelationship(id, characterId, conversationId, userPersonaId, lastInteractionAt = now)
}
