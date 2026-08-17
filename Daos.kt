package com.localcharacter.app.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CharacterDao {
    @Query("SELECT * FROM characters ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<CharacterEntity>>

    @Query("SELECT * FROM characters WHERE id = :id")
    suspend fun get(id: String): CharacterEntity?

    @Query("SELECT * FROM characters WHERE name LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' OR tagsJson LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    suspend fun search(query: String): List<CharacterEntity>

    @Query("SELECT COUNT(*) FROM characters")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(character: CharacterEntity)

    @Delete
    suspend fun delete(character: CharacterEntity)
}

@Dao
interface CharacterSourceDao {
    @Query("SELECT * FROM character_sources")
    suspend fun all(): List<CharacterSourceEntity>

    @Query("SELECT * FROM character_sources WHERE providerId = :providerId AND remoteId = :remoteId LIMIT 1")
    suspend fun find(providerId: String, remoteId: String): CharacterSourceEntity?

    @Query("SELECT * FROM character_sources WHERE characterId = :characterId LIMIT 1")
    suspend fun forCharacter(characterId: String): CharacterSourceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(source: CharacterSourceEntity)
}

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY isPinned DESC, updatedAt DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun get(id: String): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE characterId = :characterId ORDER BY updatedAt DESC LIMIT 1")
    suspend fun latestForCharacter(characterId: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(conversation: ConversationEntity)

    @Query("UPDATE conversations SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touch(id: String, updatedAt: Long)

    @Query("UPDATE conversations SET title = :title WHERE id = :id")
    suspend fun rename(id: String, title: String)

    @Query("UPDATE conversations SET isPinned = NOT isPinned WHERE id = :id")
    suspend fun togglePinned(id: String)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface GroupConversationDao {
    @Query("SELECT * FROM group_conversations ORDER BY isPinned DESC, updatedAt DESC")
    fun observeAll(): Flow<List<GroupConversationEntity>>

    @Query("SELECT * FROM group_conversations WHERE id = :id")
    suspend fun get(id: String): GroupConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(group: GroupConversationEntity)

    @Query("UPDATE group_conversations SET updatedAt = :updatedAt, lastMessageAt = :updatedAt WHERE id = :id")
    suspend fun touch(id: String, updatedAt: Long)

    @Query("UPDATE group_conversations SET name = :name WHERE id = :id")
    suspend fun rename(id: String, name: String)

    @Query("UPDATE group_conversations SET turnMode = :turnMode, maxAutoResponses = :maxAutoResponses, maxBotChain = :maxBotChain, sharedMemoryEnabled = :sharedMemoryEnabled WHERE id = :id")
    suspend fun updateSettings(id: String, turnMode: String, maxAutoResponses: Int, maxBotChain: Int, sharedMemoryEnabled: Boolean)

    @Query("UPDATE group_conversations SET isPinned = NOT isPinned WHERE id = :id")
    suspend fun togglePinned(id: String)

    @Query("DELETE FROM group_conversations WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface GroupParticipantDao {
    @Query("SELECT * FROM group_participants WHERE groupId = :groupId ORDER BY position ASC")
    suspend fun forGroup(groupId: String): List<GroupParticipantEntity>

    @Query("SELECT * FROM group_participants WHERE groupId = :groupId ORDER BY position ASC")
    fun observeForGroup(groupId: String): Flow<List<GroupParticipantEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(participant: GroupParticipantEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(participants: List<GroupParticipantEntity>)

    @Query("UPDATE group_participants SET enabled = :enabled WHERE groupId = :groupId AND characterId = :characterId")
    suspend fun setEnabled(groupId: String, characterId: String, enabled: Boolean)

    @Query("UPDATE group_participants SET lastSpokeAt = :lastSpokeAt, messageCount = messageCount + 1 WHERE groupId = :groupId AND characterId = :characterId")
    suspend fun recordMessage(groupId: String, characterId: String, lastSpokeAt: Long)

    @Query("DELETE FROM group_participants WHERE groupId = :groupId AND characterId = :characterId")
    suspend fun delete(groupId: String, characterId: String)
}

@Dao
interface GroupMessageDao {
    @Query(
        """SELECT * FROM (
            SELECT * FROM group_messages WHERE groupId = :groupId
            ORDER BY createdAt DESC LIMIT :limit
        ) ORDER BY createdAt ASC""",
    )
    fun observeRecent(groupId: String, limit: Int): Flow<List<GroupMessageEntity>>

    @Query("SELECT * FROM group_messages WHERE groupId = :groupId ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recent(groupId: String, limit: Int): List<GroupMessageEntity>

    @Query("SELECT COUNT(*) FROM group_messages WHERE groupId = :groupId")
    suspend fun count(groupId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: GroupMessageEntity)

    @Query("UPDATE group_messages SET content = :content, isComplete = :isComplete WHERE id = :id")
    suspend fun updateContent(id: String, content: String, isComplete: Boolean)

    @Query("DELETE FROM group_messages WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM group_messages WHERE groupId = :groupId AND createdAt >= :fromCreatedAt")
    suspend fun deleteFrom(groupId: String, fromCreatedAt: Long)

    @Query("SELECT * FROM group_messages WHERE groupId = :groupId AND createdAt <= :toCreatedAt ORDER BY createdAt ASC")
    suspend fun upTo(groupId: String, toCreatedAt: Long): List<GroupMessageEntity>
}

@Dao
interface GroupMemoryDao {
    @Query("SELECT * FROM group_memories WHERE groupId = :groupId AND isActive = 1 ORDER BY importance DESC, updatedAt DESC")
    suspend fun activeForGroup(groupId: String): List<GroupMemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(memory: GroupMemoryEntity)

    @Query("DELETE FROM group_memories WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface GroupContextDao {
    @Query("SELECT * FROM group_contexts WHERE groupId = :groupId LIMIT 1")
    suspend fun get(groupId: String): GroupContextEntity?

    @Query("SELECT * FROM group_contexts WHERE groupId = :groupId LIMIT 1")
    fun observe(groupId: String): Flow<GroupContextEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(context: GroupContextEntity)
}

@Dao
interface GroupParticipantContextDao {
    @Query("SELECT * FROM group_participant_contexts WHERE groupId = :groupId")
    suspend fun forGroup(groupId: String): List<GroupParticipantContextEntity>

    @Query("SELECT * FROM group_participant_contexts WHERE groupId = :groupId AND characterId = :characterId LIMIT 1")
    suspend fun get(groupId: String, characterId: String): GroupParticipantContextEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(context: GroupParticipantContextEntity)
}

@Dao
interface MessageDao {
    @Query(
        """SELECT * FROM (
            SELECT * FROM messages
            WHERE conversationId = :conversationId
            ORDER BY createdAt DESC
            LIMIT :limit
        ) ORDER BY createdAt ASC""",
    )
    fun observeRecentForConversation(conversationId: String, limit: Int): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recent(conversationId: String, limit: Int): List<MessageEntity>

    @Query("SELECT content FROM messages WHERE conversationId = :conversationId ORDER BY createdAt DESC LIMIT 1")
    suspend fun lastContent(conversationId: String): String?

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND createdAt > :afterCreatedAt ORDER BY createdAt ASC")
    suspend fun after(conversationId: String, afterCreatedAt: Long): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :conversationId")
    suspend fun count(conversationId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: MessageEntity)

    @Query("UPDATE messages SET content = :content, isComplete = :isComplete WHERE id = :id")
    suspend fun updateContent(id: String, content: String, isComplete: Boolean)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId AND createdAt >= :fromCreatedAt")
    suspend fun deleteFrom(conversationId: String, fromCreatedAt: Long)

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND createdAt <= :toCreatedAt ORDER BY createdAt ASC")
    suspend fun upTo(conversationId: String, toCreatedAt: Long): List<MessageEntity>
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories WHERE conversationId = :conversationId AND isActive = 1 ORDER BY isPinned DESC, importance DESC, updatedAt DESC")
    fun observeForConversation(conversationId: String): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE id = :id")
    suspend fun get(id: String): MemoryEntity?

    @Query(
        """SELECT * FROM memories
        WHERE characterId = :characterId
          AND isActive = 1
          AND (expiresAt IS NULL OR expiresAt > :now)
          AND (conversationId = :conversationId OR (:includeGlobal = 1 AND conversationId IS NULL))
          AND ((:userPersonaId IS NULL AND userPersonaId IS NULL) OR userPersonaId = :userPersonaId)
        ORDER BY isPinned DESC, importance DESC, lastAccessedAt DESC""",
    )
    suspend fun activeCandidates(
        characterId: String,
        conversationId: String,
        userPersonaId: String?,
        includeGlobal: Boolean,
        now: Long,
    ): List<MemoryEntity>

    @Query(
        """SELECT * FROM memories
        WHERE characterId = :characterId AND isActive = 1
          AND ((:userPersonaId IS NULL AND userPersonaId IS NULL) OR userPersonaId = :userPersonaId)
        ORDER BY updatedAt DESC""",
    )
    suspend fun activeForCharacter(characterId: String, userPersonaId: String?): List<MemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(memory: MemoryEntity)

    @Query("UPDATE memories SET content = :content, normalizedContent = :normalizedContent, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateContent(id: String, content: String, normalizedContent: String, updatedAt: Long)

    @Query("UPDATE memories SET isPinned = :isPinned, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setPinned(id: String, isPinned: Boolean, updatedAt: Long)

    @Query("UPDATE memories SET isActive = :isActive, supersededById = :supersededById, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setActive(id: String, isActive: Boolean, supersededById: String?, updatedAt: Long)

    @Query("UPDATE memories SET lastAccessedAt = :accessedAt, accessCount = accessCount + 1 WHERE id IN (:ids)")
    suspend fun touch(ids: List<String>, accessedAt: Long)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun delete(id: String)

    @Transaction
    suspend fun supersede(oldId: String, replacement: MemoryEntity) {
        upsert(replacement)
        setActive(oldId, false, replacement.id, replacement.updatedAt)
    }
}

@Dao
interface ConversationSummaryDao {
    @Query("SELECT * FROM conversation_summaries WHERE conversationId = :conversationId ORDER BY toCreatedAt DESC LIMIT 1")
    suspend fun latest(conversationId: String): ConversationSummaryEntity?

    @Query("SELECT * FROM conversation_summaries WHERE conversationId = :conversationId ORDER BY toCreatedAt DESC")
    fun observe(conversationId: String): Flow<List<ConversationSummaryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(summary: ConversationSummaryEntity)
}

@Dao
interface CharacterRelationshipDao {
    @Query(
        """SELECT * FROM character_relationships
        WHERE characterId = :characterId
          AND ((conversationId = :conversationId) OR (conversationId IS NULL AND :conversationId IS NULL))
          AND ((userPersonaId = :userPersonaId) OR (userPersonaId IS NULL AND :userPersonaId IS NULL))
        LIMIT 1""",
    )
    suspend fun get(characterId: String, conversationId: String?, userPersonaId: String?): CharacterRelationshipEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(relationship: CharacterRelationshipEntity)
}

@Dao
interface PendingEventDao {
    @Query(
        """SELECT * FROM pending_events
        WHERE characterId = :characterId AND isActive = 1
          AND (sourceMemoryId IS NULL OR EXISTS (SELECT 1 FROM memories m WHERE m.id = sourceMemoryId AND m.isActive = 1))
          AND (conversationId = :conversationId OR (:includeGlobal = 1 AND conversationId IS NULL))
          AND ((:userPersonaId IS NULL AND userPersonaId IS NULL) OR userPersonaId = :userPersonaId)
          AND status IN ('PENDING', 'FOLLOW_UP_AVAILABLE', 'ASKED')
        ORDER BY eventAt ASC""",
    )
    suspend fun activeForScope(
        characterId: String,
        conversationId: String,
        userPersonaId: String?,
        includeGlobal: Boolean,
    ): List<PendingEventEntity>

    @Query(
        """SELECT * FROM pending_events
        WHERE characterId = :characterId AND isActive = 1 AND eventAt <= :now
          AND (sourceMemoryId IS NULL OR EXISTS (SELECT 1 FROM memories m WHERE m.id = sourceMemoryId AND m.isActive = 1))
          AND (cooldownUntil IS NULL OR cooldownUntil <= :now)
          AND (conversationId = :conversationId OR (:includeGlobal = 1 AND conversationId IS NULL))
          AND ((:userPersonaId IS NULL AND userPersonaId IS NULL) OR userPersonaId = :userPersonaId)
          AND status IN ('PENDING', 'FOLLOW_UP_AVAILABLE', 'ASKED')
        ORDER BY eventAt ASC LIMIT :limit""",
    )
    suspend fun due(
        characterId: String,
        conversationId: String,
        userPersonaId: String?,
        includeGlobal: Boolean,
        now: Long,
        limit: Int,
    ): List<PendingEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(event: PendingEventEntity)

    @Query("UPDATE pending_events SET status = :status, followUpAskedAt = :askedAt, cooldownUntil = :cooldownUntil, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateFollowUp(id: String, status: String, askedAt: Long?, cooldownUntil: Long?, updatedAt: Long)

    @Query("UPDATE pending_events SET status = 'RESOLVED', isActive = 0, updatedAt = :updatedAt WHERE id = :id")
    suspend fun resolve(id: String, updatedAt: Long)
}

@Dao
interface LoreDao {
    @Query("SELECT * FROM lore_entries WHERE characterId = :characterId ORDER BY priority DESC")
    suspend fun forCharacter(characterId: String): List<LoreEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: LoreEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<LoreEntryEntity>)

    @Query("DELETE FROM lore_entries WHERE characterId = :characterId")
    suspend fun deleteForCharacter(characterId: String)

    @Query("DELETE FROM lore_entries WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface ModelDao {
    @Query("SELECT * FROM models ORDER BY isActive DESC, addedAt DESC")
    fun observeAll(): Flow<List<ModelEntity>>

    @Query("SELECT * FROM models WHERE isActive = 1 LIMIT 1")
    suspend fun active(): ModelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(model: ModelEntity)

    @Query("UPDATE models SET isActive = CASE WHEN id = :id THEN 1 ELSE 0 END")
    suspend fun activateOnly(id: String)

    @Query("UPDATE models SET isActive = 0")
    suspend fun clearActive()

    @Query("DELETE FROM models WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface UserPersonaDao {
    @Query("SELECT * FROM user_personas ORDER BY isDefault DESC, updatedAt DESC")
    fun observeAll(): Flow<List<UserPersonaEntity>>

    @Query("SELECT * FROM user_personas WHERE id = :id LIMIT 1")
    suspend fun get(id: String): UserPersonaEntity?

    @Query("SELECT * FROM user_personas WHERE isDefault = 1 ORDER BY updatedAt DESC LIMIT 1")
    suspend fun defaultPersona(): UserPersonaEntity?

    @Query("SELECT COUNT(*) FROM user_personas")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(persona: UserPersonaEntity)

    @Query("UPDATE user_personas SET isDefault = CASE WHEN id = :id THEN 1 ELSE 0 END, updatedAt = CASE WHEN id = :id THEN :updatedAt ELSE updatedAt END")
    suspend fun setDefault(id: String, updatedAt: Long)

    @Query("DELETE FROM user_personas WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface VoiceRepositoryDao {
    @Query("SELECT * FROM voice_repositories ORDER BY name")
    fun observeAll(): Flow<List<VoiceRepositoryEntity>>

    @Query("SELECT * FROM voice_repositories WHERE enabled = 1 ORDER BY name")
    suspend fun enabled(): List<VoiceRepositoryEntity>

    @Query("SELECT * FROM voice_repositories WHERE id = :id LIMIT 1")
    suspend fun get(id: String): VoiceRepositoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(repository: VoiceRepositoryEntity)

    @Query("DELETE FROM voice_repositories WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface VoiceDao {
    @Query("SELECT * FROM voices ORDER BY language, name")
    fun observeAll(): Flow<List<VoiceEntity>>

    @Query("SELECT * FROM voices WHERE id = :id LIMIT 1")
    suspend fun get(id: String): VoiceEntity?

    @Query("SELECT * FROM voices WHERE repositoryId = :repositoryId AND remoteId = :remoteId LIMIT 1")
    suspend fun find(repositoryId: String, remoteId: String): VoiceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(voice: VoiceEntity)

    @Query("DELETE FROM voices WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface CharacterPreferencesDao {
    @Query("SELECT * FROM character_preferences WHERE characterId = :characterId LIMIT 1")
    fun observe(characterId: String): Flow<CharacterPreferencesEntity?>

    @Query("SELECT * FROM character_preferences WHERE characterId = :characterId LIMIT 1")
    suspend fun get(characterId: String): CharacterPreferencesEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(preferences: CharacterPreferencesEntity)
}

@Dao
interface AiUsageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(usage: AiUsageEntity)

    @Query("SELECT * FROM ai_usage WHERE timestamp >= :since ORDER BY timestamp DESC")
    fun observeSince(since: Long): Flow<List<AiUsageEntity>>

    @Query("SELECT * FROM ai_usage WHERE timestamp >= :since ORDER BY timestamp DESC")
    suspend fun since(since: Long): List<AiUsageEntity>

    @Query("SELECT COALESCE(SUM(estimatedCostUsd), 0.0) FROM ai_usage WHERE timestamp >= :since")
    suspend fun estimatedCostSince(since: Long): Double
}
