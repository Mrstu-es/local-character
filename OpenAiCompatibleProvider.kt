package com.localcharacter.app.llm.provider.remote

import com.localcharacter.app.llm.provider.LlmMessage
import com.localcharacter.app.llm.provider.LlmModelInfo
import com.localcharacter.app.llm.provider.LlmProvider
import com.localcharacter.app.llm.provider.LlmRequest
import com.localcharacter.app.llm.provider.LlmRole
import com.localcharacter.app.llm.provider.LlmStreamEvent
import com.localcharacter.app.llm.provider.PricingClassifier
import com.localcharacter.app.llm.provider.PricingType
import com.localcharacter.app.llm.provider.ProviderCapabilities
import com.localcharacter.app.llm.provider.ProviderConnectionResult
import com.localcharacter.app.llm.provider.ProviderDefinition
import com.localcharacter.app.llm.provider.TokenUsage
import com.localcharacter.app.llm.provider.network.BaseHttpProvider
import com.localcharacter.app.llm.provider.network.SseEvent
import com.localcharacter.app.llm.provider.network.asObject
import com.localcharacter.app.llm.provider.network.double
import com.localcharacter.app.llm.provider.network.int
import com.localcharacter.app.llm.provider.network.long
import com.localcharacter.app.llm.provider.network.obj
import com.localcharacter.app.llm.provider.network.string
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

open class OpenAiCompatibleProvider(
    private val definition: ProviderDefinition,
    client: OkHttpClient,
    apiKeyProvider: suspend () -> String?,
    private val manualModelId: String? = null,
    private val extraHeaders: Map<String, String> = emptyMap(),
    private val maxTokensField: String = "max_tokens",
) : BaseHttpProvider(client, apiKeyProvider, definition.requiresApiKey), LlmProvider {
    override val providerId: String = definition.providerId
    override val displayName: String = definition.displayName
    override val capabilities: ProviderCapabilities = definition.capabilities
    override val pricingType: PricingType = definition.pricingType

    override suspend fun testConnection(): ProviderConnectionResult = connectionTest(::getModels)

    override suspend fun getModels(): List<LlmModelInfo> {
        val root = executeJson { key -> authorizedRequest("${definition.baseUrl}/models", key).get().build() }
        return parseModels(root)
    }

    protected open fun parseModels(root: JsonObject): List<LlmModelInfo> {
        val rows = root["data"] as? JsonArray ?: JsonArray(emptyList())
        return rows.mapNotNull { element ->
            val model = element.asObject() ?: return@mapNotNull null
            val id = model.string("id") ?: return@mapNotNull null
            LlmModelInfo(
                providerId = providerId,
                modelId = id,
                displayName = model.string("name") ?: id,
                pricingType = pricingType,
                contextLength = model.int("context_window") ?: model.int("context_length")
                    ?: model.int("max_context_length"),
                maxOutputTokens = model.int("max_completion_tokens"),
            )
        }.ifEmpty {
            manualModelId?.takeIf(String::isNotBlank)?.let {
                listOf(LlmModelInfo(providerId, it, it, pricingType))
            }.orEmpty()
        }
    }

    override fun generate(request: LlmRequest): Flow<LlmStreamEvent> = streamSse(
        buildRequest = { key ->
            val payload = buildChatPayload(request)
            authorizedRequest("${definition.baseUrl}/chat/completions", key)
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
        },
        parseEvent = ::parseChatEvent,
    )

    protected open fun buildChatPayload(request: LlmRequest): JsonObject = buildJsonObject {
        put("model", request.modelId)
        put("stream", true)
        put("messages", buildJsonArray {
            if (capabilities.supportsSystemPrompt && request.systemPrompt.isNotBlank()) {
                add(message("system", request.systemPrompt))
            }
            request.messages.forEach { add(message(it.role.apiName(), it.content)) }
        })
        put(maxTokensField, request.maxTokens)
        if (capabilities.supportsTemperature) request.temperature?.let { put("temperature", it) }
        if (capabilities.supportsTopP) request.topP?.let { put("top_p", it) }
        if (capabilities.supportsTopK) request.topK?.let { put("top_k", it) }
        if (request.stopSequences.isNotEmpty()) put("stop", buildJsonArray {
            request.stopSequences.forEach { add(JsonPrimitive(it)) }
        })
    }

    protected open fun parseChatEvent(event: SseEvent): List<LlmStreamEvent> {
        val root = runCatching { json.parseToJsonElement(event.data).jsonObject }.getOrNull() ?: return emptyList()
        root["error"]?.let {
            return listOf(LlmStreamEvent.Error(
                com.localcharacter.app.llm.provider.network.ProviderErrorMapper.http(500, root.toString()),
            ))
        }
        val output = mutableListOf<LlmStreamEvent>()
        val choices = root["choices"] as? JsonArray
        choices?.firstOrNull()?.asObject()?.obj("delta")?.get("content")?.let { content ->
            val text = when (content) {
                is kotlinx.serialization.json.JsonPrimitive -> content.content
                is JsonArray -> content.mapNotNull { it.asObject()?.string("text") }.joinToString("")
                else -> ""
            }
            if (text.isNotEmpty()) output += LlmStreamEvent.TextDelta(text)
        }
        (root.obj("usage") ?: root.obj("x_groq")?.obj("usage"))?.let { usage ->
            output += LlmStreamEvent.Usage(
                TokenUsage(
                    inputTokens = usage.long("prompt_tokens") ?: usage.long("input_tokens"),
                    outputTokens = usage.long("completion_tokens") ?: usage.long("output_tokens"),
                    cachedInputTokens = usage.obj("prompt_tokens_details")?.long("cached_tokens"),
                ),
            )
        }
        return output
    }

    override suspend fun cancelGeneration() {
        cancelHttpGeneration()
    }

    protected fun authorizedRequest(url: String, key: String): Request.Builder = Request.Builder()
        .url(url)
        .header("Accept", "application/json")
        .header("Content-Type", "application/json")
        .apply {
            if (key.isNotBlank()) header("Authorization", "Bearer $key")
            extraHeaders.forEach { (name, value) -> header(name, value) }
        }

    private fun LlmRole.apiName() = if (this == LlmRole.ASSISTANT) "assistant" else "user"
    private fun message(role: String, content: String) = buildJsonObject {
        put("role", role)
        put("content", content)
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

class GroqProvider(
    definition: ProviderDefinition,
    client: OkHttpClient,
    apiKeyProvider: suspend () -> String?,
) : OpenAiCompatibleProvider(definition, client, apiKeyProvider, maxTokensField = "max_completion_tokens")

class MistralProvider(
    definition: ProviderDefinition,
    client: OkHttpClient,
    apiKeyProvider: suspend () -> String?,
) : OpenAiCompatibleProvider(definition, client, apiKeyProvider)

class CustomOpenAiCompatibleProvider(
    definition: ProviderDefinition,
    client: OkHttpClient,
    apiKeyProvider: suspend () -> String?,
    manualModelId: String,
) : OpenAiCompatibleProvider(definition, client, apiKeyProvider, manualModelId)

class OpenRouterProvider(
    definition: ProviderDefinition,
    client: OkHttpClient,
    apiKeyProvider: suspend () -> String?,
) : OpenAiCompatibleProvider(
    definition, client, apiKeyProvider,
    extraHeaders = mapOf("X-OpenRouter-Title" to "Local Character Android"),
) {
    override fun parseModels(root: JsonObject): List<LlmModelInfo> {
        val rows = root["data"] as? JsonArray ?: return emptyList()
        return rows.mapNotNull { element ->
            val model = element.asObject() ?: return@mapNotNull null
            val id = model.string("id") ?: return@mapNotNull null
            val pricing = model.obj("pricing")
            val inputPerToken = pricing?.string("prompt")?.toDoubleOrNull()
            val outputPerToken = pricing?.string("completion")?.toDoubleOrNull()
            val supported = (model["supported_parameters"] as? JsonArray)
                ?.mapNotNull { runCatching { it.jsonPrimitive.content }.getOrNull() }.orEmpty()
            val modalities = model.obj("architecture")?.get("input_modalities") as? JsonArray
            val modalityNames = modalities?.mapNotNull { runCatching { it.jsonPrimitive.content }.getOrNull() }.orEmpty()
            LlmModelInfo(
                providerId = providerId,
                modelId = id,
                displayName = model.string("name") ?: id,
                pricingType = PricingClassifier.fromOpenRouter(id, inputPerToken, outputPerToken),
                inputPrice = inputPerToken?.times(1_000_000.0),
                outputPrice = outputPerToken?.times(1_000_000.0),
                contextLength = model.int("context_length"),
                supportsStreaming = true,
                supportsVision = modalityNames.any { it == "image" || it == "file" },
                supportsReasoning = "reasoning" in supported,
                supportsTools = "tools" in supported,
                description = model.string("description"),
            )
        }
    }
}
