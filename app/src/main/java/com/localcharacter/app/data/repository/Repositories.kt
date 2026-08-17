package com.localcharacter.app.data.repository

import com.localcharacter.app.data.database.CharacterDao
import com.localcharacter.app.data.database.AiUsageDao
import com.localcharacter.app.data.database.CharacterSourceDao
import com.localcharacter.app.data.database.CharacterRelationshipDao
import com.localcharacter.app.data.database.ConversationDao
import com.localcharacter.app.data.database.ConversationSummaryDao
import com.localcharacter.app.data.database.LoreDao
import com.localcharacter.app.data.database.MemoryDao
import com.localcharacter.app.data.database.MessageDao
import com.localcharacter.app.data.database.ModelDao
import com.localcharacter.app.data.database.PendingEventDao
import com.localcharacter.app.data.database.CharacterPreferencesDao
import com.localcharacter.app.data.database.UserPersonaDao
import com.localcharacter.app.data.database.VoiceDao
import com.localcharacter.app.data.database.VoiceRepositoryDao
import com.localcharacter.app.data.database.GroupConversationDao
import com.localcharacter.app.data.database.GroupParticipantDao
import com.localcharacter.app.data.database.GroupMessageDao
import com.localcharacter.app.data.database.GroupMemoryDao
import com.localcharacter.app.data.database.GroupContextDao
import com.localcharacter.app.data.database.GroupParticipantContextDao
import com.localcharacter.app.data.database.toDomain
import com.localcharacter.app.data.database.toEntity
import com.localcharacter.app.domain.character.LocalCharacterSource
import com.localcharacter.app.domain.model.Character
import com.localcharacter.app.domain.model.CharacterSource
import com.localcharacter.app.domain.model.CharacterRelationship
import com.localcharacter.app.domain.model.ChatMessage
import com.localcharacter.app.domain.model.Conversation
import com.localcharacter.app.domain.model.ConversationSummary
import com.localcharacter.app.domain.model.LoreEntry
import com.localcharacter.app.domain.model.Memory
import com.localcharacter.app.domain.model.ModelDescriptor
import com.localcharacter.app.domain.model.PendingEvent
import com.localcharacter.app.domain.model.PendingEventStatus
import com.localcharacter.app.domain.model.CharacterPreferences
import com.localcharacter.app.domain.model.UserPersona
import com.localcharacter.app.domain.model.VoiceModel
import com.localcharacter.app.domain.model.VoiceRepository
import com.localcharacter.app.domain.model.GroupConversation
import com.localcharacter.app.domain.model.GroupParticipant
import com.localcharacter.app.domain.model.GroupMessage
import com.localcharacter.app.domain.model.GroupMemory
import com.localcharacter.app.domain.model.GroupContext
import com.localcharacter.app.domain.model.GroupParticipantContext
import com.localcharacter.app.llm.provider.AiUsageRecord
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CharacterRepository(private val dao: CharacterDao) : LocalCharacterSource {
    val characters: Flow<List<Character>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun search(query: String): List<Character> =
        dao.search(query.trim()).map { it.toDomain() }

    override suspend fun getCharacter(id: String): Character? = dao.get(id)?.toDomain()
    suspend fun save(character: Character) = dao.upsert(character.toEntity())
    suspend fun delete(character: Character) = dao.delete(character.toEntity())
    suspend fun isEmpty(): Boolean = dao.count() == 0
}

class CharacterSourceRepository(private val dao: CharacterSourceDao) {
    suspend fun all(): List<CharacterSource> = dao.all().map { it.toDomain() }
    suspend fun find(providerId: String, remoteId: String): CharacterSource? = dao.find(providerId, remoteId)?.toDomain()
    suspend fun forCharacter(characterId: String): CharacterSource? = dao.forCharacter(characterId)?.toDomain()
    suspend fun save(source: CharacterSource) = dao.upsert(source.toEntity())
}

class ChatRepository(
    private val conversations: ConversationDao,
    private val messages: MessageDao,
) {
    val allConversations: Flow<List<Conversation>> = conversations.observeAll().map { rows -> rows.map { it.toDomain() } }

    fun messages(conversationId: String, limit: Int = 100): Flow<List<ChatMessage>> =
        messages.observeRecentForConversation(conversationId, limit).map { rows -> rows.map { it.toDomain() } }

    suspend fun getConversation(id: String): Conversation? = conversations.get(id)?.toDomain()
    suspend fun latestForCharacter(characterId: String): Conversation? = conversations.latestForCharacter(characterId)?.toDomain()
    suspend fun saveConversation(conversation: Conversation) = conversations.upsert(conversation.toEntity())
    suspend fun saveMessage(message: ChatMessage) {
        messages.upsert(message.toEntity())
        conversations.touch(message.conversationId, System.currentTimeMillis())
    }
    suspend fun updateMessage(id: String, content: String, isComplete: Boolean) = messages.updateContent(id, content, isComplete)
    suspend fun deleteMessage(id: String) = messages.delete(id)
    suspend fun rewindFrom(conversationId: String, createdAt: Long) = messages.deleteFrom(conversationId, createdAt)
    suspend fun messagesUpTo(conversationId: String, createdAt: Long): List<ChatMessage> = messages.upTo(conversationId, createdAt).map { it.toDomain() }
    suspend fun recent(conversationId: String, limit: Int = 30): List<ChatMessage> = messages.recent(conversationId, limit).reversed().map { it.toDomain() }
    suspend fun after(conversationId: String, createdAt: Long): List<ChatMessage> = messages.after(conversationId, createdAt).map { it.toDomain() }
    suspend fun messageCount(conversationId: String): Int = messages.count(conversationId)
    suspend fun lastContent(conversationId: String): String = messages.lastContent(conversationId).orEmpty()
    suspend fun rename(id: String, title: String) = conversations.rename(id, title.trim())
    suspend fun togglePinned(id: String) = conversations.togglePinned(id)
    suspend fun deleteConversation(id: String) = conversations.delete(id)

    suspend fun create(character: Character, userPersonaId: String? = null): Conversation {
        val now = System.currentTimeMillis()
        val conversation = Conversation(
            UUID.randomUUID().toString(), character.id, character.name, userPersonaId = userPersonaId,
            createdAt = now, updatedAt = now,
        )
        saveConversation(conversation)
        if (character.firstMessage.isNotBlank()) {
            saveMessage(ChatMessage(UUID.randomUUID().toString(), conversation.id, com.localcharacter.app.domain.model.MessageRole.CHARACTER, character.firstMessage, true, now))
        }
        return conversation
    }
}

class GroupRepository(
    private val groups: GroupConversationDao,
    private val participants: GroupParticipantDao,
    private val messages: GroupMessageDao,
    private val memories: GroupMemoryDao,
    private val contexts: GroupContextDao,
    private val participantContexts: GroupParticipantContextDao,
) {
    val allGroups: Flow<List<GroupConversation>> = groups.observeAll().map { rows -> rows.map { it.toDomain() } }
    fun messages(groupId: String, limit: Int = 100): Flow<List<GroupMessage>> =
        messages.observeRecent(groupId, limit).map { rows -> rows.map { it.toDomain() } }
    fun participants(groupId: String): Flow<List<GroupParticipant>> =
        participants.observeForGroup(groupId).map { rows -> rows.map { it.toDomain() } }
    suspend fun get(id: String): GroupConversation? = groups.get(id)?.toDomain()
    suspend fun getParticipants(id: String): List<GroupParticipant> = participants.forGroup(id).map { it.toDomain() }
    suspend fun save(group: GroupConversation) = groups.upsert(group.toEntity())
    suspend fun saveParticipants(items: List<GroupParticipant>) = participants.upsertAll(items.map { it.toEntity() })
    suspend fun addParticipant(item: GroupParticipant) = participants.upsert(item.toEntity())
    suspend fun removeParticipant(groupId: String, characterId: String) = participants.delete(groupId, characterId)
    suspend fun setParticipantEnabled(groupId: String, characterId: String, enabled: Boolean) = participants.setEnabled(groupId, characterId, enabled)
    suspend fun recordParticipantMessage(groupId: String, characterId: String, at: Long) = participants.recordMessage(groupId, characterId, at)
    suspend fun saveMessage(message: GroupMessage) {
        messages.upsert(message.toEntity())
        groups.touch(message.groupId, message.createdAt)
        message.senderCharacterId?.let { participants.recordMessage(message.groupId, it, message.createdAt) }
    }
    suspend fun updateMessage(id: String, content: String, complete: Boolean) = messages.updateContent(id, content, complete)
    suspend fun deleteMessage(id: String) = messages.delete(id)
    suspend fun rewindFrom(groupId: String, createdAt: Long) = messages.deleteFrom(groupId, createdAt)
    suspend fun messagesUpTo(groupId: String, createdAt: Long): List<GroupMessage> = messages.upTo(groupId, createdAt).map { it.toDomain() }
    suspend fun recent(groupId: String, limit: Int = 40): List<GroupMessage> = messages.recent(groupId, limit).reversed().map { it.toDomain() }
    suspend fun messageCount(groupId: String): Int = messages.count(groupId)
    suspend fun rename(id: String, name: String) = groups.rename(id, name.trim())
    suspend fun updateSettings(group: GroupConversation) = groups.updateSettings(group.id, group.turnMode.name, group.maxAutoResponses, group.maxBotChain, group.sharedMemoryEnabled)
    suspend fun togglePinned(id: String) = groups.togglePinned(id)
    suspend fun delete(id: String) = groups.delete(id)
    suspend fun activeMemories(groupId: String): List<GroupMemory> = memories.activeForGroup(groupId).map { it.toDomain() }
    suspend fun saveMemory(memory: GroupMemory) = memories.upsert(memory.toEntity())
    suspend fun deleteMemory(id: String) = memories.delete(id)
    suspend fun context(groupId: String): GroupContext = contexts.get(groupId)?.toDomain() ?: GroupContext(groupId)
    fun observeContext(groupId: String): Flow<GroupContext?> = contexts.observe(groupId).map { it?.toDomain() }
    suspend fun saveContext(context: GroupContext) = contexts.upsert(context.toEntity())
    suspend fun participantContext(groupId: String, characterId: String): GroupParticipantContext? =
        participantContexts.get(groupId, characterId)?.toDomain()
    suspend fun participantContexts(groupId: String): List<GroupParticipantContext> =
        participantContexts.forGroup(groupId).map { it.toDomain() }
    suspend fun saveParticipantContext(context: GroupParticipantContext) = participantContexts.upsert(context.toEntity())

    suspend fun create(
        name: String,
        characterIds: List<String>,
        userPersonaId: String?,
        avatarPath: String? = null,
        turnMode: com.localcharacter.app.domain.model.GroupTurnMode = com.localcharacter.app.domain.model.GroupTurnMode.SMART,
    ): GroupConversation {
        require(characterIds.distinct().size >= 2) { "Selecciona al menos 2 personajes." }
        val now = System.currentTimeMillis()
        val group = GroupConversation(UUID.randomUUID().toString(), name.trim().ifBlank { "Nuevo grupo" }, avatarPath = avatarPath, userPersonaId = userPersonaId, createdAt = now, updatedAt = now, lastMessageAt = now, turnMode = turnMode)
        save(group)
        saveParticipants(characterIds.distinct().mapIndexed { index, id -> GroupParticipant(group.id, id, index) })
        saveContext(GroupContext(groupId = group.id, title = group.name))
        return group
    }
}

class UserPersonaRepository(private val dao: UserPersonaDao) {
    val personas: Flow<List<UserPersona>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }
    suspend fun get(id: String): UserPersona? = dao.get(id)?.toDomain()
    suspend fun default(): UserPersona? = dao.defaultPersona()?.toDomain()
    suspend fun save(persona: UserPersona) {
        dao.upsert(persona.toEntity())
        if (persona.isDefault) dao.setDefault(persona.id, persona.updatedAt)
    }
    suspend fun ensureDefault(name: String = "Usuario"): UserPersona = default() ?: run {
        val now = System.currentTimeMillis()
        val persona = UserPersona(
            id = UUID.randomUUID().toString(), name = name, createdAt = now, updatedAt = now, isDefault = true,
        )
        save(persona)
        persona
    }
    suspend fun setDefault(id: String) = dao.setDefault(id, System.currentTimeMillis())
    suspend fun delete(id: String) = dao.delete(id)
}

class VoiceRepositoryRepository(private val dao: VoiceRepositoryDao) {
    val repositories: Flow<List<VoiceRepository>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }
    suspend fun enabled(): List<VoiceRepository> = dao.enabled().map { it.toDomain() }
    suspend fun get(id: String): VoiceRepository? = dao.get(id)?.toDomain()
    suspend fun save(repository: VoiceRepository) = dao.upsert(repository.toEntity())
    suspend fun delete(id: String) = dao.delete(id)
}

class VoiceModelsRepository(private val dao: VoiceDao) {
    val voices: Flow<List<VoiceModel>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }
    suspend fun get(id: String): VoiceModel? = dao.get(id)?.toDomain()
    suspend fun find(repositoryId: String, remoteId: String): VoiceModel? = dao.find(repositoryId, remoteId)?.toDomain()
    suspend fun save(voice: VoiceModel) = dao.upsert(voice.toEntity())
    suspend fun delete(id: String) = dao.delete(id)
}

class CharacterPreferencesRepository(private val dao: CharacterPreferencesDao) {
    fun observe(characterId: String): Flow<CharacterPreferences> = dao.observe(characterId).map {
        it?.toDomain() ?: CharacterPreferences(characterId)
    }
    suspend fun get(characterId: String): CharacterPreferences =
        dao.get(characterId)?.toDomain() ?: CharacterPreferences(characterId)
    suspend fun save(preferences: CharacterPreferences) = dao.upsert(preferences.toEntity())
}

class ContextRepository(private val lore: LoreDao) {
    suspend fun lore(characterId: String): List<LoreEntry> = lore.forCharacter(characterId).map { it.toDomain() }
    suspend fun saveLore(entry: LoreEntry) = lore.upsert(entry.toEntity())
    suspend fun deleteLore(id: String) = lore.delete(id)
}

class MemoryRepository(private val dao: MemoryDao) {
    fun observe(conversationId: String): Flow<List<Memory>> =
        dao.observeForConversation(conversationId).map { rows -> rows.map { it.toDomain() } }

    suspend fun get(id: String): Memory? = dao.get(id)?.toDomain()

    suspend fun candidates(
        characterId: String,
        conversationId: String,
        userPersonaId: String?,
        includeGlobal: Boolean,
        now: Long = System.currentTimeMillis(),
    ): List<Memory> = dao.activeCandidates(characterId, conversationId, userPersonaId, includeGlobal, now).map { it.toDomain() }

    suspend fun activeForCharacter(characterId: String, userPersonaId: String?): List<Memory> =
        dao.activeForCharacter(characterId, userPersonaId).map { it.toDomain() }

    suspend fun save(memory: Memory) = dao.upsert(memory.toEntity())
    suspend fun supersede(oldId: String, replacement: Memory) = dao.supersede(oldId, replacement.toEntity())
    suspend fun edit(id: String, content: String, normalizedContent: String) =
        dao.updateContent(id, content.trim(), normalizedContent, System.currentTimeMillis())
    suspend fun setPinned(id: String, pinned: Boolean) = dao.setPinned(id, pinned, System.currentTimeMillis())
    suspend fun deactivate(id: String) = dao.setActive(id, false, null, System.currentTimeMillis())
    suspend fun touch(ids: List<String>) {
        if (ids.isNotEmpty()) dao.touch(ids, System.currentTimeMillis())
    }
    suspend fun delete(id: String) = dao.delete(id)
}

class SummaryRepository(private val dao: ConversationSummaryDao) {
    suspend fun latest(conversationId: String): ConversationSummary? = dao.latest(conversationId)?.toDomain()
    fun observe(conversationId: String): Flow<List<ConversationSummary>> =
        dao.observe(conversationId).map { rows -> rows.map { it.toDomain() } }
    suspend fun save(summary: ConversationSummary) = dao.upsert(summary.toEntity())
}

class RelationshipRepository(private val dao: CharacterRelationshipDao) {
    suspend fun get(characterId: String, conversationId: String?, userPersonaId: String?): CharacterRelationship? =
        dao.get(characterId, conversationId, userPersonaId)?.toDomain()
    suspend fun save(relationship: CharacterRelationship) = dao.upsert(relationship.toEntity())
}

class PendingEventRepository(private val dao: PendingEventDao) {
    suspend fun active(
        characterId: String,
        conversationId: String,
        userPersonaId: String?,
        includeGlobal: Boolean,
    ): List<PendingEvent> = dao.activeForScope(characterId, conversationId, userPersonaId, includeGlobal).map { it.toDomain() }

    suspend fun due(
        characterId: String,
        conversationId: String,
        userPersonaId: String?,
        includeGlobal: Boolean,
        now: Long = System.currentTimeMillis(),
        limit: Int = 2,
    ): List<PendingEvent> = dao.due(characterId, conversationId, userPersonaId, includeGlobal, now, limit).map { it.toDomain() }

    suspend fun save(event: PendingEvent) = dao.upsert(event.toEntity())
    suspend fun markAsked(id: String, now: Long, cooldownUntil: Long) =
        dao.updateFollowUp(id, PendingEventStatus.ASKED.name, now, cooldownUntil, now)
    suspend fun resolve(id: String) = dao.resolve(id, System.currentTimeMillis())
}

class ModelRepository(private val dao: ModelDao) {
    val models: Flow<List<ModelDescriptor>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }
    suspend fun active(): ModelDescriptor? = dao.active()?.toDomain()
    suspend fun save(model: ModelDescriptor) = dao.upsert(model.toEntity())
    suspend fun activate(id: String) = dao.activateOnly(id)
    suspend fun clearActive() = dao.clearActive()
    suspend fun delete(id: String) = dao.delete(id)
}

class AiUsageRepository(private val dao: AiUsageDao) {
    fun observeSince(since: Long): Flow<List<AiUsageRecord>> = dao.observeSince(since).map { rows -> rows.map { it.toDomain() } }
    suspend fun since(since: Long): List<AiUsageRecord> = dao.since(since).map { it.toDomain() }
    suspend fun estimatedCostSince(since: Long): Double = dao.estimatedCostSince(since)
    suspend fun save(usage: AiUsageRecord) = dao.upsert(usage.toEntity())
}
