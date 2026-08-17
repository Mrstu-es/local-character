package com.localcharacter.app.llm.provider.remote

import com.localcharacter.app.llm.provider.LlmModelInfo
import com.localcharacter.app.llm.provider.LlmProvider
import com.localcharacter.app.llm.provider.LlmRequest
import com.localcharacter.app.llm.provider.LlmRole
import com.localcharacter.app.llm.provider.LlmStreamEvent
import com.localcharacter.app.llm.provider.PricingType
import com.localcharacter.app.llm.provider.ProviderCapabilities
import com.localcharacter.app.llm.provider.ProviderConnectionResult
import com.localcharacter.app.llm.provider.ProviderDefinition
import com.localcharacter.app.llm.provider.TokenUsage
import com.localcharacter.app.llm.provider.network.BaseHttpProvider
import com.localcharacter.app.llm.provider.network.ProviderErrorMapper
import com.localcharacter.app.llm.provider.network.SseEvent
import com.localcharacter.app.llm.provider.network.asObject
import com.localcharacter.app.llm.provider.network.int
import com.localcharacter.app.llm.provider.network.obj
import com.localcharacter.app.llm.provider.network.string
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
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

class GeminiProvider(
    private val definition: ProviderDefinition,
    client: OkHttpClient,
    apiKeyProvider: suspend () -> String?,
) : BaseHttpProvider(client, apiKeyProvider), LlmProvider {
    override val providerId = definition.providerId
    override val displayName = definition.displayName
    override val capabilities: ProviderCapabilities = definition.capabilities
    override val pricingType: PricingType = definition.pricingType

    override suspend fun testConnection(): ProviderConnectionResult = connectionTest(::getModels)

    override suspend fun getModels(): List<LlmModelInfo> {
        val root = executeJson { key -> apiRequest("${definition.baseUrl}/models", key).get().build() }
        val rows = root["models"] as? JsonArray ?: return emptyList()
        return rows.mapNotNull { row ->
            val model = row.asObject() ?: return@mapNotNull null
            val methods = (model["supportedGenerationMethods"] as? JsonArray)
                ?.map { it.jsonPrimitive.content }.orEmpty()
            if (methods.isNotEmpty() && "generateContent" !in methods) return@mapNotNull null
            val fullName = model.string("name") ?: return@mapNotNull null
            val id = fullName.removePrefix("models/")
            LlmModelInfo(
                providerId, id, model.string("displayName") ?: id, PricingType.FREE_TIER,
                contextLength = model.int("inputTokenLimit"),
                maxOutputTokens = model.int("outputTokenLimit"),
                supportsVision = true,
                supportsReasoning = true,
                description = model.string("description"),
            )
        }
    }

    override fun generate(request: LlmRequest): Flow<LlmStreamEvent> = streamSse(
        buildRequest = { key ->
            val model = request.modelId.removePrefix("models/")
            val payload = buildJsonObject {
                if (request.systemPrompt.isNotBlank()) put("systemInstruction", content(null, request.systemPrompt))
                put("contents", buildJsonArray {
                    request.messages.forEach { message ->
                        add(content(if (message.role == LlmRole.ASSISTANT) "model" else "user", message.content))
                    }
                })
                put("generationConfig", buildJsonObject {
                    request.temperature?.let { put("temperature", it) }
                    request.topP?.let { put("topP", it) }
                    request.topK?.let { put("topK", it) }
                    put("maxOutputTokens", request.maxTokens)
                    if (request.stopSequences.isNotEmpty()) {
                        put("stopSequences", buildJsonArray {
                            request.stopSequences.forEach { add(JsonPrimitive(it)) }
                        })
                    }
                })
            }
            apiRequest("${definition.baseUrl}/models/$model:streamGenerateContent?alt=sse", key)
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE)).build()
        },
        parseEvent = ::parseEvent,
    )

    private fun parseEvent(event: SseEvent): List<LlmStreamEvent> {
        val root = runCatching { json.parseToJsonElement(event.data).jsonObject }.getOrNull() ?: return emptyList()
        if (root["error"] != null) return listOf(LlmStreamEvent.Error(ProviderErrorMapper.http(500, root.toString())))
        val output = mutableListOf<LlmStreamEvent>()
        val candidates = root["candidates"] as? JsonArray
        val parts = candidates?.firstOrNull()?.asObject()?.obj("content")?.get("parts") as? JsonArray
        parts?.forEach { part -> part.asObject()?.string("text")?.takeIf(String::isNotEmpty)?.let { output += LlmStreamEvent.TextDelta(it) } }
        root.obj("usageMetadata")?.let { usage ->
            output += LlmStreamEvent.Usage(TokenUsage(
                inputTokens = usage.int("promptTokenCount")?.toLong(),
                outputTokens = usage.int("candidatesTokenCount")?.toLong(),
                cachedInputTokens = usage.int("cachedContentTokenCount")?.toLong(),
            ))
        }
        return output
    }

    override suspend fun cancelGeneration() {
        cancelHttpGeneration()
    }

    private fun apiRequest(url: String, key: String) = Request.Builder().url(url)
        .header("x-goog-api-key", key)
        .header("Content-Type", "application/json")
        .header("Accept", "application/json")

    private fun content(role: String?, text: String) = buildJsonObject {
        role?.let { put("role", it) }
        put("parts", buildJsonArray { add(buildJsonObject { put("text", text) }) })
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
