package com.localcharacter.app.llm.provider

import com.localcharacter.app.domain.model.GenerationSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

enum class PricingType { LOCAL, FREE, FREE_TIER, PAID, UNKNOWN }
enum class ProviderKind { LOCAL, ONLINE, CUSTOM }
enum class ProviderStatus { LOCAL, CONNECTED, NOT_CONFIGURED, RATE_LIMITED, OFFLINE, ERROR }

data class ProviderCapabilities(
    val supportsStreaming: Boolean = true,
    val supportsSystemPrompt: Boolean = true,
    val supportsTemperature: Boolean = true,
    val supportsTopP: Boolean = true,
    val supportsTopK: Boolean = false,
    val supportsReasoning: Boolean = false,
    val supportsVision: Boolean = false,
    val supportsTools: Boolean = false,
    val supportsModelListing: Boolean = true,
    val supportsTokenUsage: Boolean = true,
    val supportsInputCaching: Boolean = false,
    val supportsStructuredOutput: Boolean = false,
    val supportsImageInput: Boolean = false,
)

@Serializable
data class ProviderModelSelection(
    val providerId: String = LOCAL_PROVIDER_ID,
    val modelId: String = ACTIVE_LOCAL_MODEL_ID,
)

@Serializable
data class LlmModelInfo(
    val providerId: String,
    val modelId: String,
    val displayName: String = modelId,
    val pricingType: PricingType = PricingType.UNKNOWN,
    /** Current USD price per one million tokens when the provider supplies it. */
    val inputPrice: Double? = null,
    val outputPrice: Double? = null,
    val currency: String = "USD",
    val contextLength: Int? = null,
    val maxOutputTokens: Int? = null,
    val supportsStreaming: Boolean = true,
    val supportsVision: Boolean = false,
    val supportsReasoning: Boolean = false,
    val supportsTools: Boolean = false,
    val description: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)

enum class LlmRole { USER, ASSISTANT }

data class LlmMessage(val role: LlmRole, val content: String)

data class LlmRequest(
    val modelId: String,
    val systemPrompt: String,
    val messages: List<LlmMessage>,
    val temperature: Float? = null,
    val topP: Float? = null,
    val topK: Int? = null,
    val maxTokens: Int,
    val stopSequences: List<String> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),
    /** The legacy formatted prompt used only by llama.cpp. */
    val localFormattedPrompt: String? = null,
    val localGenerationSettings: GenerationSettings? = null,
)

data class TokenUsage(
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
    val cachedInputTokens: Long? = null,
)

data class AiUsageRecord(
    val id: String,
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

sealed interface LlmStreamEvent {
    data class TextDelta(val text: String) : LlmStreamEvent
    data class Usage(val usage: TokenUsage) : LlmStreamEvent
    data class Error(val error: ProviderError) : LlmStreamEvent
    data object Completed : LlmStreamEvent
}

enum class ProviderErrorKind {
    AUTHENTICATION, ACCESS_DENIED, MODEL_NOT_FOUND, TIMEOUT, RATE_LIMIT,
    BILLING, PROVIDER, NETWORK, INVALID_REQUEST, CANCELLED, UNKNOWN,
}

data class ProviderError(
    val kind: ProviderErrorKind,
    val friendlyMessage: String,
    val technicalMessage: String? = null,
    val httpStatus: Int? = null,
    val retryAfterSeconds: Long? = null,
    val providerCode: String? = null,
)

sealed interface ProviderConnectionResult {
    data class Success(val modelCount: Int?) : ProviderConnectionResult
    data class Failure(val error: ProviderError) : ProviderConnectionResult
}

interface LlmProvider {
    val providerId: String
    val displayName: String
    val capabilities: ProviderCapabilities
    val pricingType: PricingType

    suspend fun testConnection(): ProviderConnectionResult
    suspend fun getModels(): List<LlmModelInfo>
    fun generate(request: LlmRequest): Flow<LlmStreamEvent>
    suspend fun cancelGeneration()
}

data class ProviderDefinition(
    val providerId: String,
    val displayName: String,
    val kind: ProviderKind,
    val pricingType: PricingType,
    val baseUrl: String,
    val capabilities: ProviderCapabilities,
    val requiresApiKey: Boolean = true,
    val verifiedAt: String = "2026-08-15",
    val documentationUrl: String,
    val pricingNote: String,
)

data class ProviderSummary(
    val definition: ProviderDefinition,
    val status: ProviderStatus,
    val keyConfigured: Boolean,
    val maskedKey: String? = null,
    val selectedModelId: String? = null,
    val statusMessage: String? = null,
)

const val LOCAL_PROVIDER_ID = "local"
const val ACTIVE_LOCAL_MODEL_ID = "active"
