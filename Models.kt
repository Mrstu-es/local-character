package com.localcharacter.app.domain.model

data class Character(
    val id: String,
    val name: String,
    val avatarUri: String? = null,
    val description: String = "",
    val personality: String = "",
    val scenario: String = "",
    val firstMessage: String = "",
    val exampleMessages: String = "",
    val systemPrompt: String = "",
    val creatorNotes: String = "",
    val tags: List<String> = emptyList(),
    val alternateGreetings: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /** LocalCharacter extension; never required by Character Card V2. */
    val recommendedVoiceId: String? = null,
)

enum class AvatarState { LOCAL, CARD_FALLBACK, MISSING }

data class CharacterSource(
    val characterId: String,
    val providerId: String,
    val remoteId: String,
    val sourceUrl: String,
    val author: String? = null,
    val version: String? = null,
    val sourceUpdatedAt: Long? = null,
    val contentHash: String,
    val avatarHash: String? = null,
    val downloadedAt: Long,
    val originalCardPath: String,
    val localAvatarPath: String? = null,
    val avatarState: AvatarState = AvatarState.MISSING,
)

data class Conversation(
    val id: String,
    val characterId: String,
    val title: String,
    val userPersonaId: String? = null,
    val summary: String = "",
    val isPinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

enum class GroupTurnMode { SMART, ROUND_ROBIN, MANUAL }

/** Controls how character lore is reconciled with a group's shared world. */
enum class GroupLorePolicy { ADAPTIVE, ORIGINAL, DISABLED }

data class GroupContext(
    val groupId: String,
    val title: String = "",
    val description: String = "",
    val scenario: String = "",
    val userRole: String = "",
    val worldRules: String = "",
    val initialSituation: String = "",
    val openingMessage: String = "",
    val notes: String = "",
    val lorePolicy: GroupLorePolicy = GroupLorePolicy.ADAPTIVE,
    val currentLocation: String = "",
    val currentSituation: String = "",
    val stateSummary: String = "",
    val version: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
)

data class GroupParticipantContext(
    val groupId: String,
    val characterId: String,
    val role: String = "",
    val scenarioOverride: String = "",
    val relationshipToUser: String = "",
    val relationshipToGroup: String = "",
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)

data class GroupConversation(
    val id: String,
    val name: String,
    val avatarPath: String? = null,
    val userPersonaId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val lastMessageAt: Long = updatedAt,
    val turnMode: GroupTurnMode = GroupTurnMode.SMART,
    val maxAutoResponses: Int = 1,
    val maxBotChain: Int = 2,
    val isPinned: Boolean = false,
    val sharedMemoryEnabled: Boolean = true,
    val forcedProviderId: String? = null,
    val forcedModelId: String? = null,
)

data class GroupParticipant(
    val groupId: String,
    val characterId: String,
    val position: Int,
    val enabled: Boolean = true,
    val joinedAt: Long = System.currentTimeMillis(),
    val lastSpokeAt: Long? = null,
    val messageCount: Int = 0,
)

enum class GroupMessageRole { USER, CHARACTER }

data class GroupMessage(
    val id: String,
    val groupId: String,
    val role: GroupMessageRole,
    val content: String,
    val isComplete: Boolean = true,
    val senderCharacterId: String? = null,
    val userPersonaId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

data class GroupMemory(
    val id: String,
    val groupId: String,
    val type: String,
    val content: String,
    val importance: Float = 0.5f,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val isActive: Boolean = true,
)

enum class MessageRole { SYSTEM, USER, CHARACTER }

data class ChatMessage(
    val id: String,
    val conversationId: String,
    val role: MessageRole,
    val content: String,
    val isComplete: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)

enum class MemoryType {
    FACT, EVENT, PREFERENCE, RELATIONSHIP, EMOTIONAL, GOAL, PROMISE,
    CHARACTER_RELATIONSHIP, SHARED_EVENT,
}

enum class MemoryOrigin { USER_STATED_FACT, CHARACTER_STATED, CHARACTER_INFERENCE, SYSTEM_INFERENCE }

data class Memory(
    val id: String,
    val characterId: String,
    val conversationId: String? = null,
    val userPersonaId: String? = null,
    val type: MemoryType = MemoryType.FACT,
    val content: String,
    val normalizedContent: String = content.lowercase(),
    val importance: Float = 0.5f,
    val confidence: Float = 0.8f,
    val origin: MemoryOrigin = MemoryOrigin.USER_STATED_FACT,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val lastAccessedAt: Long = createdAt,
    val accessCount: Int = 0,
    val sourceMessageId: String? = null,
    val eventAt: Long? = null,
    val expiresAt: Long? = null,
    val isPinned: Boolean = false,
    val isActive: Boolean = true,
    val supersededById: String? = null,
)

data class ConversationSummary(
    val id: String,
    val conversationId: String,
    val summary: String,
    val fromMessageId: String,
    val toMessageId: String,
    val fromCreatedAt: Long,
    val toCreatedAt: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
)

data class CharacterRelationship(
    val id: String,
    val characterId: String,
    val conversationId: String?,
    val userPersonaId: String?,
    val trust: Float = 0.2f,
    val affection: Float = 0.2f,
    val familiarity: Float = 0.05f,
    val tension: Float = 0f,
    val relationshipSummary: String = "",
    val lastInteractionAt: Long = System.currentTimeMillis(),
    val interactionCount: Int = 0,
)

enum class PendingEventStatus { PENDING, FOLLOW_UP_AVAILABLE, ASKED, RESOLVED, EXPIRED }

data class PendingEvent(
    val id: String,
    val characterId: String,
    val conversationId: String?,
    val userPersonaId: String?,
    val description: String,
    val eventAt: Long,
    val status: PendingEventStatus = PendingEventStatus.PENDING,
    val sourceMessageId: String?,
    val sourceMemoryId: String? = null,
    val followUpAskedAt: Long? = null,
    val cooldownUntil: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val isActive: Boolean = true,
)

data class LoreEntry(
    val id: String,
    val characterId: String,
    val keywords: List<String>,
    val content: String,
    val priority: Int = 0,
    val enabled: Boolean = true,
    val caseSensitive: Boolean = false,
)

data class UserPersona(
    val id: String,
    val name: String,
    val avatarUri: String? = null,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val isDefault: Boolean = false,
)

enum class ContentMode { STANDARD, ADULT_ENABLED }

enum class CharacterContentOverride { USE_GLOBAL, STANDARD, ADULT_ENABLED }

enum class VoiceAutoplayOverride { USE_GLOBAL, ALWAYS, NEVER }

enum class TtsReadMode { DIALOGUE_ONLY, DIALOGUE_AND_ACTIONS }

enum class VoiceEngineType { ANDROID_SYSTEM, KOKORO, PIPER, VITS, OTHER }

data class VoiceModel(
    val id: String,
    val name: String,
    val engine: VoiceEngineType,
    val language: String,
    val localModelPath: String? = null,
    val localConfigPath: String? = null,
    val filesJson: String = "[]",
    val sampleUrl: String? = null,
    val repositoryId: String? = null,
    val remoteId: String? = null,
    val version: String = "1.0",
    val license: String,
    val author: String,
    val source: String,
    val consentMetadata: String? = null,
    val contentHash: String,
    val sizeBytes: Long = 0,
    val installedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = installedAt,
)

data class VoiceRepository(
    val id: String,
    val name: String,
    val manifestUrl: String,
    val enabled: Boolean = true,
    val lastSyncAt: Long? = null,
    val lastSuccessfulSyncAt: Long? = null,
    val etag: String? = null,
    val schemaVersion: Int = 1,
)

data class CharacterPreferences(
    val characterId: String,
    val contentOverride: CharacterContentOverride = CharacterContentOverride.USE_GLOBAL,
    val voiceId: String? = null,
    val autoplayOverride: VoiceAutoplayOverride = VoiceAutoplayOverride.USE_GLOBAL,
    val speed: Float = 1f,
    val pitch: Float = 1f,
    val volume: Float = 1f,
    val readMode: TtsReadMode = TtsReadMode.DIALOGUE_ONLY,
    val updatedAt: Long = System.currentTimeMillis(),
)

data class ModelDescriptor(
    val id: String,
    val displayName: String,
    val uri: String,
    val sizeBytes: Long,
    val architecture: String? = null,
    val quantization: String? = null,
    val contextSize: Int? = null,
    val tensorCount: Long = 0,
    val parameterCount: Long? = null,
    val tokenizer: String? = null,
    val embeddedChatTemplate: String? = null,
    val chatTemplateMode: String = "AUTO",
    val customChatTemplate: String? = null,
    val isActive: Boolean = false,
    val addedAt: Long = System.currentTimeMillis(),
)

data class GenerationSettings(
    val temperature: Float = 0.85f,
    val topP: Float = 0.92f,
    val topK: Int = 40,
    val minP: Float = 0.05f,
    val repeatPenalty: Float = 1.08f,
    val contextSize: Int = 4096,
    val maxTokens: Int = 320,
    val threads: Int = (Runtime.getRuntime().availableProcessors() - 2).coerceIn(2, 4),
    val batchSize: Int = 512,
) {
    companion object {
        val Creative = GenerationSettings(temperature = 1.05f, topP = 0.95f, topK = 60)
        val Balanced = GenerationSettings()
        val Precise = GenerationSettings(temperature = 0.45f, topP = 0.8f, topK = 30)
        val Roleplay = GenerationSettings(temperature = 0.95f, topP = 0.93f, topK = 50, repeatPenalty = 1.12f)
    }
}
