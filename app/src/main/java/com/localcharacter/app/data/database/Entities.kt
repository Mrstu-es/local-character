package com.localcharacter.app.data.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "characters")
data class CharacterEntity(
    @PrimaryKey val id: String,
    val name: String,
    val avatarUri: String?,
    val description: String,
    val personality: String,
    val scenario: String,
    val firstMessage: String,
    val exampleMessages: String,
    val systemPrompt: String,
    val creatorNotes: String,
    val tagsJson: String,
    val alternateGreetingsJson: String,
    val createdAt: Long,
    val updatedAt: Long,
    val recommendedVoiceId: String?,
)

@Entity(
    tableName = "character_sources",
    foreignKeys = [ForeignKey(
        entity = CharacterEntity::class,
        parentColumns = ["id"],
        childColumns = ["characterId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("characterId"), Index(value = ["providerId", "remoteId"], unique = true)],
)
data class CharacterSourceEntity(
    @PrimaryKey val characterId: String,
    val providerId: String,
    val remoteId: String,
    val sourceUrl: String,
    val author: String?,
    val version: String?,
    val sourceUpdatedAt: Long?,
    val contentHash: String,
    val avatarHash: String?,
    val downloadedAt: Long,
    val originalCardPath: String,
    val localAvatarPath: String?,
    val avatarState: String,
)

@Entity(
    tableName = "conversations",
    foreignKeys = [ForeignKey(
        entity = CharacterEntity::class,
        parentColumns = ["id"],
        childColumns = ["characterId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("characterId"), Index("updatedAt")],
)
data class ConversationEntity(
    @PrimaryKey val id: String,
    val characterId: String,
    val title: String,
    val userPersonaId: String?,
    val summary: String,
    val isPinned: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "group_conversations", indices = [Index("updatedAt"), Index("lastMessageAt")])
data class GroupConversationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val avatarPath: String?,
    val userPersonaId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val lastMessageAt: Long,
    val turnMode: String,
    val maxAutoResponses: Int,
    val maxBotChain: Int,
    val isPinned: Boolean,
    val sharedMemoryEnabled: Boolean,
    val forcedProviderId: String?,
    val forcedModelId: String?,
)

@Entity(
    tableName = "group_participants",
    primaryKeys = ["groupId", "characterId"],
    foreignKeys = [
        ForeignKey(entity = GroupConversationEntity::class, parentColumns = ["id"], childColumns = ["groupId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CharacterEntity::class, parentColumns = ["id"], childColumns = ["characterId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("groupId"), Index("characterId")],
)
data class GroupParticipantEntity(
    val groupId: String,
    val characterId: String,
    val position: Int,
    val enabled: Boolean,
    val joinedAt: Long,
    val lastSpokeAt: Long?,
    val messageCount: Int,
)

@Entity(
    tableName = "group_messages",
    foreignKeys = [
        ForeignKey(entity = GroupConversationEntity::class, parentColumns = ["id"], childColumns = ["groupId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CharacterEntity::class, parentColumns = ["id"], childColumns = ["senderCharacterId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = UserPersonaEntity::class, parentColumns = ["id"], childColumns = ["userPersonaId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("groupId"), Index(value = ["groupId", "createdAt"]), Index("senderCharacterId")],
)
data class GroupMessageEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val role: String,
    val content: String,
    val isComplete: Boolean,
    val senderCharacterId: String?,
    val userPersonaId: String?,
    val createdAt: Long,
)

@Entity(
    tableName = "group_memories",
    foreignKeys = [ForeignKey(entity = GroupConversationEntity::class, parentColumns = ["id"], childColumns = ["groupId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("groupId"), Index("isActive")],
)
data class GroupMemoryEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val type: String,
    val content: String,
    val importance: Float,
    val createdAt: Long,
    val updatedAt: Long,
    val isActive: Boolean,
)

@Entity(
    tableName = "group_contexts",
    foreignKeys = [ForeignKey(entity = GroupConversationEntity::class, parentColumns = ["id"], childColumns = ["groupId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("groupId")],
)
data class GroupContextEntity(
    @PrimaryKey val groupId: String,
    val title: String,
    val description: String,
    val scenario: String,
    val userRole: String,
    val worldRules: String,
    val initialSituation: String,
    val openingMessage: String,
    val notes: String,
    val lorePolicy: String,
    val currentLocation: String,
    val currentSituation: String,
    val stateSummary: String,
    val version: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "group_participant_contexts",
    primaryKeys = ["groupId", "characterId"],
    foreignKeys = [
        ForeignKey(entity = GroupConversationEntity::class, parentColumns = ["id"], childColumns = ["groupId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CharacterEntity::class, parentColumns = ["id"], childColumns = ["characterId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("groupId"), Index("characterId")],
)
data class GroupParticipantContextEntity(
    val groupId: String,
    val characterId: String,
    val role: String,
    val scenarioOverride: String,
    val relationshipToUser: String,
    val relationshipToGroup: String,
    val notes: String,
    val updatedAt: Long,
)

@Entity(
    tableName = "messages",
    foreignKeys = [ForeignKey(
        entity = ConversationEntity::class,
        parentColumns = ["id"],
        childColumns = ["conversationId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("conversationId"), Index(value = ["conversationId", "createdAt"])],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String,
    val content: String,
    val isComplete: Boolean,
    val createdAt: Long,
)

@Entity(
    tableName = "memories",
    foreignKeys = [
        ForeignKey(
            entity = CharacterEntity::class,
            parentColumns = ["id"],
            childColumns = ["characterId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("conversationId"),
        Index("characterId"),
        Index("userPersonaId"),
        Index("type"),
        Index("isActive"),
        Index(value = ["characterId", "conversationId", "userPersonaId", "isActive"]),
    ],
)
data class MemoryEntity(
    @PrimaryKey val id: String,
    val characterId: String,
    val conversationId: String?,
    val userPersonaId: String?,
    val type: String,
    val content: String,
    val normalizedContent: String,
    val importance: Float,
    val confidence: Float,
    val origin: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastAccessedAt: Long,
    val accessCount: Int,
    val sourceMessageId: String?,
    val eventAt: Long?,
    val expiresAt: Long?,
    val isPinned: Boolean,
    val isActive: Boolean,
    val supersededById: String?,
)

@Entity(
    tableName = "conversation_summaries",
    foreignKeys = [ForeignKey(
        entity = ConversationEntity::class,
        parentColumns = ["id"],
        childColumns = ["conversationId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("conversationId"), Index(value = ["conversationId", "toCreatedAt"])],
)
data class ConversationSummaryEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val summary: String,
    val fromMessageId: String,
    val toMessageId: String,
    val fromCreatedAt: Long,
    val toCreatedAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "character_relationships",
    foreignKeys = [
        ForeignKey(entity = CharacterEntity::class, parentColumns = ["id"], childColumns = ["characterId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ConversationEntity::class, parentColumns = ["id"], childColumns = ["conversationId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("characterId"), Index("conversationId"), Index("userPersonaId")],
)
data class CharacterRelationshipEntity(
    @PrimaryKey val id: String,
    val characterId: String,
    val conversationId: String?,
    val userPersonaId: String?,
    val trust: Float,
    val affection: Float,
    val familiarity: Float,
    val tension: Float,
    val relationshipSummary: String,
    val lastInteractionAt: Long,
    val interactionCount: Int,
)

@Entity(
    tableName = "pending_events",
    foreignKeys = [
        ForeignKey(entity = CharacterEntity::class, parentColumns = ["id"], childColumns = ["characterId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ConversationEntity::class, parentColumns = ["id"], childColumns = ["conversationId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = MemoryEntity::class, parentColumns = ["id"], childColumns = ["sourceMemoryId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("characterId"), Index("conversationId"), Index("userPersonaId"), Index("eventAt"), Index("status"), Index("sourceMemoryId")],
)
data class PendingEventEntity(
    @PrimaryKey val id: String,
    val characterId: String,
    val conversationId: String?,
    val userPersonaId: String?,
    val description: String,
    val eventAt: Long,
    val status: String,
    val sourceMessageId: String?,
    val sourceMemoryId: String?,
    val followUpAskedAt: Long?,
    val cooldownUntil: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val isActive: Boolean,
)

@Entity(
    tableName = "lore_entries",
    foreignKeys = [ForeignKey(
        entity = CharacterEntity::class,
        parentColumns = ["id"],
        childColumns = ["characterId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("characterId")],
)
data class LoreEntryEntity(
    @PrimaryKey val id: String,
    val characterId: String,
    val keywordsJson: String,
    val content: String,
    val priority: Int,
    val enabled: Boolean,
    val caseSensitive: Boolean,
)

@Entity(tableName = "models")
data class ModelEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val uri: String,
    val sizeBytes: Long,
    val architecture: String?,
    val quantization: String?,
    val contextSize: Int?,
    val tensorCount: Long,
    val parameterCount: Long?,
    val tokenizer: String?,
    val embeddedChatTemplate: String?,
    val chatTemplateMode: String,
    val customChatTemplate: String?,
    val isActive: Boolean,
    val addedAt: Long,
)

@Entity(tableName = "user_personas")
data class UserPersonaEntity(
    @PrimaryKey val id: String,
    val name: String,
    val avatarUri: String?,
    val description: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isDefault: Boolean,
)

@Entity(
    tableName = "voice_repositories",
    indices = [Index(value = ["manifestUrl"], unique = true)],
)
data class VoiceRepositoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val manifestUrl: String,
    val enabled: Boolean,
    val lastSyncAt: Long?,
    val lastSuccessfulSyncAt: Long?,
    val etag: String?,
    val schemaVersion: Int,
)

@Entity(
    tableName = "voices",
    foreignKeys = [ForeignKey(
        entity = VoiceRepositoryEntity::class,
        parentColumns = ["id"],
        childColumns = ["repositoryId"],
        onDelete = ForeignKey.SET_NULL,
    )],
    indices = [Index("repositoryId"), Index("language"), Index("engine"), Index(value = ["repositoryId", "remoteId"])],
)
data class VoiceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val engine: String,
    val language: String,
    val localModelPath: String?,
    val localConfigPath: String?,
    val filesJson: String,
    val sampleUrl: String?,
    val repositoryId: String?,
    val remoteId: String?,
    val version: String,
    val license: String,
    val author: String,
    val source: String,
    val consentMetadata: String?,
    val contentHash: String,
    val sizeBytes: Long,
    val installedAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "character_preferences",
    foreignKeys = [
        ForeignKey(
            entity = CharacterEntity::class,
            parentColumns = ["id"],
            childColumns = ["characterId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = VoiceEntity::class,
            parentColumns = ["id"],
            childColumns = ["voiceId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("voiceId")],
)
data class CharacterPreferencesEntity(
    @PrimaryKey val characterId: String,
    val contentOverride: String,
    val voiceId: String?,
    val autoplayOverride: String,
    val speed: Float,
    val pitch: Float,
    val volume: Float,
    val readMode: String,
    val updatedAt: Long,
)

@Entity(
    tableName = "ai_usage",
    indices = [
        Index("providerId"), Index("modelId"), Index("timestamp"),
        Index("conversationId"), Index("characterId"),
    ],
)
data class AiUsageEntity(
    @PrimaryKey val id: String,
    val providerId: String,
    val modelId: String,
    val inputTokens: Long?,
    val outputTokens: Long?,
    val estimatedCostUsd: Double?,
    val timestamp: Long,
    val conversationId: String?,
    val characterId: String?,
    val timeToFirstTokenMillis: Long?,
    val generationDurationMillis: Long?,
)
