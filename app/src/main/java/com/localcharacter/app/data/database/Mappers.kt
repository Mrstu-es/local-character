package com.localcharacter.app.data.database

import com.localcharacter.app.domain.model.Character
import com.localcharacter.app.domain.model.CharacterSource
import com.localcharacter.app.domain.model.AvatarState
import com.localcharacter.app.domain.model.ChatMessage
import com.localcharacter.app.domain.model.Conversation
import com.localcharacter.app.domain.model.GroupConversation
import com.localcharacter.app.domain.model.GroupMessage
import com.localcharacter.app.domain.model.GroupMessageRole
import com.localcharacter.app.domain.model.GroupParticipant
import com.localcharacter.app.domain.model.GroupMemory
import com.localcharacter.app.domain.model.GroupContext
import com.localcharacter.app.domain.model.GroupLorePolicy
import com.localcharacter.app.domain.model.GroupParticipantContext
import com.localcharacter.app.domain.model.GroupTurnMode
import com.localcharacter.app.domain.model.ConversationSummary
import com.localcharacter.app.domain.model.CharacterRelationship
import com.localcharacter.app.domain.model.LoreEntry
import com.localcharacter.app.domain.model.Memory
import com.localcharacter.app.domain.model.MemoryOrigin
import com.localcharacter.app.domain.model.MemoryType
import com.localcharacter.app.domain.model.MessageRole
import com.localcharacter.app.domain.model.ModelDescriptor
import com.localcharacter.app.llm.provider.AiUsageRecord
import com.localcharacter.app.domain.model.PendingEvent
import com.localcharacter.app.domain.model.PendingEventStatus
import com.localcharacter.app.domain.model.CharacterContentOverride
import com.localcharacter.app.domain.model.CharacterPreferences
import com.localcharacter.app.domain.model.TtsReadMode
import com.localcharacter.app.domain.model.UserPersona
import com.localcharacter.app.domain.model.VoiceAutoplayOverride
import com.localcharacter.app.domain.model.VoiceEngineType
import com.localcharacter.app.domain.model.VoiceModel
import com.localcharacter.app.domain.model.VoiceRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

private val mapperJson = Json { ignoreUnknownKeys = true }

private fun List<String>.toJsonString(): String = JsonArray(map(::JsonPrimitive)).toString()
private fun String.toStringList(): List<String> = runCatching {
    mapperJson.parseToJsonElement(this).let { it as JsonArray }.map { it.jsonPrimitive.content }
}.getOrDefault(emptyList())

fun CharacterEntity.toDomain() = Character(
    id, name, avatarUri, description, personality, scenario, firstMessage,
    exampleMessages, systemPrompt, creatorNotes, tagsJson.toStringList(),
    alternateGreetingsJson.toStringList(), createdAt, updatedAt, recommendedVoiceId,
)

fun Character.toEntity() = CharacterEntity(
    id, name, avatarUri, description, personality, scenario, firstMessage,
    exampleMessages, systemPrompt, creatorNotes, tags.toJsonString(),
    alternateGreetings.toJsonString(), createdAt, updatedAt, recommendedVoiceId,
)

fun CharacterSourceEntity.toDomain() = CharacterSource(
    characterId, providerId, remoteId, sourceUrl, author, version, sourceUpdatedAt,
    contentHash, avatarHash, downloadedAt, originalCardPath, localAvatarPath,
    runCatching { AvatarState.valueOf(avatarState) }.getOrDefault(AvatarState.MISSING),
)

fun CharacterSource.toEntity() = CharacterSourceEntity(
    characterId, providerId, remoteId, sourceUrl, author, version, sourceUpdatedAt,
    contentHash, avatarHash, downloadedAt, originalCardPath, localAvatarPath, avatarState.name,
)

fun ConversationEntity.toDomain() = Conversation(id, characterId, title, userPersonaId, summary, isPinned, createdAt, updatedAt)
fun Conversation.toEntity() = ConversationEntity(id, characterId, title, userPersonaId, summary, isPinned, createdAt, updatedAt)
fun GroupConversationEntity.toDomain() = GroupConversation(
    id, name, avatarPath, userPersonaId, createdAt, updatedAt, lastMessageAt,
    runCatching { GroupTurnMode.valueOf(turnMode) }.getOrDefault(GroupTurnMode.SMART),
    maxAutoResponses, maxBotChain, isPinned, sharedMemoryEnabled, forcedProviderId, forcedModelId,
)
fun GroupConversation.toEntity() = GroupConversationEntity(
    id, name, avatarPath, userPersonaId, createdAt, updatedAt, lastMessageAt,
    turnMode.name, maxAutoResponses, maxBotChain, isPinned, sharedMemoryEnabled, forcedProviderId, forcedModelId,
)
fun GroupParticipantEntity.toDomain() = GroupParticipant(groupId, characterId, position, enabled, joinedAt, lastSpokeAt, messageCount)
fun GroupParticipant.toEntity() = GroupParticipantEntity(groupId, characterId, position, enabled, joinedAt, lastSpokeAt, messageCount)
fun GroupMessageEntity.toDomain() = GroupMessage(
    id, groupId, runCatching { GroupMessageRole.valueOf(role) }.getOrDefault(GroupMessageRole.USER),
    content, isComplete, senderCharacterId, userPersonaId, createdAt,
)
fun GroupMessage.toEntity() = GroupMessageEntity(id, groupId, role.name, content, isComplete, senderCharacterId, userPersonaId, createdAt)
fun GroupMemoryEntity.toDomain() = GroupMemory(id, groupId, type, content, importance, createdAt, updatedAt, isActive)
fun GroupMemory.toEntity() = GroupMemoryEntity(id, groupId, type, content, importance, createdAt, updatedAt, isActive)
fun GroupContextEntity.toDomain() = GroupContext(
    groupId, title, description, scenario, userRole, worldRules, initialSituation,
    openingMessage, notes, runCatching { GroupLorePolicy.valueOf(lorePolicy) }.getOrDefault(GroupLorePolicy.ADAPTIVE),
    currentLocation, currentSituation, stateSummary, version, createdAt, updatedAt,
)
fun GroupContext.toEntity() = GroupContextEntity(
    groupId, title, description, scenario, userRole, worldRules, initialSituation,
    openingMessage, notes, lorePolicy.name, currentLocation, currentSituation, stateSummary,
    version, createdAt, updatedAt,
)
fun GroupParticipantContextEntity.toDomain() = GroupParticipantContext(
    groupId, characterId, role, scenarioOverride, relationshipToUser, relationshipToGroup, notes, updatedAt,
)
fun GroupParticipantContext.toEntity() = GroupParticipantContextEntity(
    groupId, characterId, role, scenarioOverride, relationshipToUser, relationshipToGroup, notes, updatedAt,
)
fun MessageEntity.toDomain() = ChatMessage(id, conversationId, MessageRole.valueOf(role), content, isComplete, createdAt)
fun ChatMessage.toEntity() = MessageEntity(id, conversationId, role.name, content, isComplete, createdAt)
fun MemoryEntity.toDomain() = Memory(
    id = id,
    characterId = characterId,
    conversationId = conversationId,
    userPersonaId = userPersonaId,
    type = runCatching { MemoryType.valueOf(type) }.getOrDefault(MemoryType.FACT),
    content = content,
    normalizedContent = normalizedContent,
    importance = importance,
    confidence = confidence,
    origin = runCatching { MemoryOrigin.valueOf(origin) }.getOrDefault(MemoryOrigin.SYSTEM_INFERENCE),
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastAccessedAt = lastAccessedAt,
    accessCount = accessCount,
    sourceMessageId = sourceMessageId,
    eventAt = eventAt,
    expiresAt = expiresAt,
    isPinned = isPinned,
    isActive = isActive,
    supersededById = supersededById,
)
fun Memory.toEntity() = MemoryEntity(
    id, characterId, conversationId, userPersonaId, type.name, content, normalizedContent,
    importance, confidence, origin.name, createdAt, updatedAt, lastAccessedAt, accessCount,
    sourceMessageId, eventAt, expiresAt, isPinned, isActive, supersededById,
)
fun ConversationSummaryEntity.toDomain() = ConversationSummary(
    id, conversationId, summary, fromMessageId, toMessageId, fromCreatedAt, toCreatedAt, createdAt, updatedAt,
)
fun ConversationSummary.toEntity() = ConversationSummaryEntity(
    id, conversationId, summary, fromMessageId, toMessageId, fromCreatedAt, toCreatedAt, createdAt, updatedAt,
)
fun CharacterRelationshipEntity.toDomain() = CharacterRelationship(
    id, characterId, conversationId, userPersonaId, trust, affection, familiarity, tension,
    relationshipSummary, lastInteractionAt, interactionCount,
)
fun CharacterRelationship.toEntity() = CharacterRelationshipEntity(
    id, characterId, conversationId, userPersonaId, trust, affection, familiarity, tension,
    relationshipSummary, lastInteractionAt, interactionCount,
)
fun PendingEventEntity.toDomain() = PendingEvent(
    id, characterId, conversationId, userPersonaId, description, eventAt,
    runCatching { PendingEventStatus.valueOf(status) }.getOrDefault(PendingEventStatus.PENDING),
    sourceMessageId, sourceMemoryId, followUpAskedAt, cooldownUntil, createdAt, updatedAt, isActive,
)
fun PendingEvent.toEntity() = PendingEventEntity(
    id, characterId, conversationId, userPersonaId, description, eventAt, status.name,
    sourceMessageId, sourceMemoryId, followUpAskedAt, cooldownUntil, createdAt, updatedAt, isActive,
)
fun LoreEntryEntity.toDomain() = LoreEntry(id, characterId, keywordsJson.toStringList(), content, priority, enabled, caseSensitive)
fun LoreEntry.toEntity() = LoreEntryEntity(id, characterId, keywords.toJsonString(), content, priority, enabled, caseSensitive)
fun ModelEntity.toDomain() = ModelDescriptor(
    id, displayName, uri, sizeBytes, architecture, quantization, contextSize,
    tensorCount, parameterCount, tokenizer, embeddedChatTemplate, chatTemplateMode, customChatTemplate,
    isActive, addedAt,
)
fun ModelDescriptor.toEntity() = ModelEntity(
    id, displayName, uri, sizeBytes, architecture, quantization, contextSize,
    tensorCount, parameterCount, tokenizer, embeddedChatTemplate, chatTemplateMode, customChatTemplate,
    isActive, addedAt,
)

fun UserPersonaEntity.toDomain() = UserPersona(id, name, avatarUri, description, createdAt, updatedAt, isDefault)
fun UserPersona.toEntity() = UserPersonaEntity(id, name, avatarUri, description, createdAt, updatedAt, isDefault)

fun VoiceRepositoryEntity.toDomain() = VoiceRepository(
    id, name, manifestUrl, enabled, lastSyncAt, lastSuccessfulSyncAt, etag, schemaVersion,
)
fun VoiceRepository.toEntity() = VoiceRepositoryEntity(
    id, name, manifestUrl, enabled, lastSyncAt, lastSuccessfulSyncAt, etag, schemaVersion,
)

fun VoiceEntity.toDomain() = VoiceModel(
    id, name, runCatching { VoiceEngineType.valueOf(engine) }.getOrDefault(VoiceEngineType.OTHER), language,
    localModelPath, localConfigPath, filesJson, sampleUrl, repositoryId, remoteId, version, license,
    author, source, consentMetadata, contentHash, sizeBytes, installedAt, updatedAt,
)
fun VoiceModel.toEntity() = VoiceEntity(
    id, name, engine.name, language, localModelPath, localConfigPath, filesJson, sampleUrl,
    repositoryId, remoteId, version, license, author, source, consentMetadata, contentHash,
    sizeBytes, installedAt, updatedAt,
)

fun CharacterPreferencesEntity.toDomain() = CharacterPreferences(
    characterId = characterId,
    contentOverride = runCatching { CharacterContentOverride.valueOf(contentOverride) }
        .getOrDefault(CharacterContentOverride.USE_GLOBAL),
    voiceId = voiceId,
    autoplayOverride = runCatching { VoiceAutoplayOverride.valueOf(autoplayOverride) }
        .getOrDefault(VoiceAutoplayOverride.USE_GLOBAL),
    speed = speed,
    pitch = pitch,
    volume = volume,
    readMode = runCatching { TtsReadMode.valueOf(readMode) }.getOrDefault(TtsReadMode.DIALOGUE_ONLY),
    updatedAt = updatedAt,
)
fun CharacterPreferences.toEntity() = CharacterPreferencesEntity(
    characterId, contentOverride.name, voiceId, autoplayOverride.name, speed, pitch, volume, readMode.name, updatedAt,
)

fun AiUsageEntity.toDomain() = AiUsageRecord(
    id, providerId, modelId, inputTokens, outputTokens, estimatedCostUsd, timestamp,
    conversationId, characterId, timeToFirstTokenMillis, generationDurationMillis,
)

fun AiUsageRecord.toEntity() = AiUsageEntity(
    id, providerId, modelId, inputTokens, outputTokens, estimatedCostUsd, timestamp,
    conversationId, characterId, timeToFirstTokenMillis, generationDurationMillis,
)
