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
import com.localcharacter.app.llm.provider.network.long
import com.localcharacter.app.llm.provider.network.obj
import com.localcharacter.app.llm.provider.network.string
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class OpenAiProvider(
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
        val root = executeJson { key -> request("${definition.baseUrl}/models", key).get().build() }
        return (root["data"] as? JsonArray).orEmpty().mapNotNull { row ->
            val model = row.asObject() ?: return@mapNotNull null
            val id = model.string("id") ?: return@mapNotNull null
            LlmModelInfo(providerId, id, id, PricingType.PAID)
        }
    }

    override fun generate(request: LlmRequest): Flow<LlmStreamEvent> = streamSse(
        buildRequest = { key ->
            val payload = buildJsonObject {
                put("model", request.modelId)
                put("stream", true)
                put("store", false)
                if (request.systemPrompt.isNotBlank()) put("instructions", request.systemPrompt)
                put("input", buildJsonArray {
                    request.messages.forEach { message ->
                        add(buildJsonObject {
                            put("role", if (message.role == LlmRole.ASSISTANT) "assistant" else "user")
                            put("content", message.content)
                        })
                    }
                })
                put("max_output_tokens", request.maxTokens)
            }
            request("${definition.baseUrl}/responses", key)
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE)).build()
        },
        parseEvent = ::parseEvent,
    )

    private fun parseEvent(event: SseEvent): List<LlmStreamEvent> {
        val root = runCatching { json.parseToJsonElement(event.data).jsonObject }.getOrNull() ?: return emptyList()
        return when (root.string("type") ?: event.event) {
            "response.output_text.delta" -> root.string("delta")?.takeIf(String::isNotEmpty)
                ?.let { listOf(LlmStreamEvent.TextDelta(it)) }.orEmpty()
            "response.completed" -> {
                val usage = root.obj("response")?.obj("usage")
                buildList {
                    usage?.let {
                        add(LlmStreamEvent.Usage(TokenUsage(
                            inputTokens = it.long("input_tokens"),
                            outputTokens = it.long("output_tokens"),
                            cachedInputTokens = it.obj("input_tokens_details")?.long("cached_tokens"),
                        )))
                    }
                    add(LlmStreamEvent.Completed)
                }
            }
            "error", "response.failed" -> listOf(
                LlmStreamEvent.Error(ProviderErrorMapper.http(500, root.toString())),
            )
            else -> emptyList()
        }
    }

    override suspend fun cancelGeneration() {
        cancelHttpGeneration()
    }

    private fun request(url: String, key: String) = Request.Builder().url(url)
        .header("Authorization", "Bearer $key")
        .header("Content-Type", "application/json")
        .header("Accept", "application/json")

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())
