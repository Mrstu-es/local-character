package com.localcharacter.app.llm.provider.remote

import com.localcharacter.app.llm.provider.LlmMessage
import com.localcharacter.app.llm.provider.LlmRequest
import com.localcharacter.app.llm.provider.LlmRole
import com.localcharacter.app.llm.provider.LlmStreamEvent
import com.localcharacter.app.llm.provider.PricingType
import com.localcharacter.app.llm.provider.ProviderCapabilities
import com.localcharacter.app.llm.provider.ProviderDefinition
import com.localcharacter.app.llm.provider.ProviderKind
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RemoteProvidersContractTest {
    private lateinit var server: MockWebServer
    private val client = OkHttpClient.Builder().retryOnConnectionFailure(false).build()
    private val json = Json { ignoreUnknownKeys = true }

    @Before fun start() {
        server = MockWebServer()
        server.start()
    }

    @After fun stop() {
        server.shutdown()
    }

    @Test fun groqUsesCurrentOpenAiCompatibleStreamingShape() = runBlocking {
        server.enqueue(sse(openAiChunks()))
        val provider = GroqProvider(definition("groq", PricingType.FREE_TIER), client) { "groq-secret" }
        val events = provider.generate(request()).toList()
        val recorded = server.takeRequest()
        val body = json.parseToJsonElement(recorded.body.readUtf8()).jsonObject
        assertEquals("/v1/chat/completions", recorded.path)
        assertEquals("Bearer groq-secret", recorded.getHeader("Authorization"))
        assertEquals(64, body["max_completion_tokens"]?.jsonPrimitive?.int)
        assertEquals("system", body["messages"]?.jsonArray?.first()?.jsonObject?.get("role")?.jsonPrimitive?.content)
        assertTextAndUsage(events)
    }

    @Test fun openRouterReadsLivePricingCapabilitiesAndStreams() = runBlocking {
        server.enqueue(jsonResponse("""{
          "data":[{
            "id":"vendor/free-model:free","name":"Free model","context_length":32768,
            "pricing":{"prompt":"0","completion":"0"},
            "supported_parameters":["reasoning","tools"],
            "architecture":{"input_modalities":["text","image"]}
          },{
            "id":"vendor/paid","pricing":{"prompt":"0.000002","completion":"0.000006"}
          }]
        }"""))
        val provider = OpenRouterProvider(definition("openrouter", PricingType.UNKNOWN), client) { "router-secret" }
        val models = provider.getModels()
        val recorded = server.takeRequest()
        assertEquals("/v1/models", recorded.path)
        assertEquals(PricingType.FREE, models[0].pricingType)
        assertTrue(models[0].supportsVision)
        assertTrue(models[0].supportsReasoning)
        assertTrue(models[0].supportsTools)
        assertEquals(2.0, models[1].inputPrice!!, 0.000001)
        assertEquals(6.0, models[1].outputPrice!!, 0.000001)

        server.enqueue(sse(openAiChunks()))
        assertTextAndUsage(provider.generate(request()).toList())
        val streamRequest = server.takeRequest()
        assertEquals("Local Character Android", streamRequest.getHeader("X-OpenRouter-Title"))
    }

    @Test fun mistralUsesChatCompletionsAndMaxTokens() = runBlocking {
        server.enqueue(sse(openAiChunks()))
        val provider = MistralProvider(definition("mistral", PricingType.UNKNOWN), client) { "mistral-secret" }
        assertTextAndUsage(provider.generate(request()).toList())
        val body = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals(64, body["max_tokens"]?.jsonPrimitive?.int)
        assertFalse("max_completion_tokens" in body)
    }

    @Test fun customOpenAiCompatibleCanRunWithoutKey() = runBlocking {
        server.enqueue(sse(openAiChunks()))
        val provider = CustomOpenAiCompatibleProvider(
            definition("custom_test", PricingType.UNKNOWN, requiresKey = false), client, { null }, "manual-model",
        )
        assertTextAndUsage(provider.generate(request(model = "manual-model")).toList())
        val recorded = server.takeRequest()
        assertEquals("/v1/chat/completions", recorded.path)
        assertEquals(null, recorded.getHeader("Authorization"))
    }

    @Test fun geminiMapsSystemContentsConfigStreamingAndUsage() = runBlocking {
        server.enqueue(sse(
            "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Hola\"}]}}]," +
                "\"usageMetadata\":{\"promptTokenCount\":5,\"candidatesTokenCount\":2}}\n\n",
        ))
        val provider = GeminiProvider(definition("gemini", PricingType.FREE_TIER), client) { "gemini-secret" }
        val events = provider.generate(request(model = "gemini-test")).toList()
        val recorded = server.takeRequest()
        val body = json.parseToJsonElement(recorded.body.readUtf8()).jsonObject
        assertEquals("/v1/models/gemini-test:streamGenerateContent?alt=sse", recorded.path)
        assertEquals("gemini-secret", recorded.getHeader("x-goog-api-key"))
        assertTrue("systemInstruction" in body)
        assertEquals("user", body["contents"]?.jsonArray?.first()?.jsonObject?.get("role")?.jsonPrimitive?.content)
        assertEquals(64, body["generationConfig"]?.jsonObject?.get("maxOutputTokens")?.jsonPrimitive?.int)
        assertTextAndUsage(events)
    }

    @Test fun openAiUsesResponsesApiAndParsesResponseEvents() = runBlocking {
        server.enqueue(sse(
            "event: response.output_text.delta\n" +
                "data: {\"type\":\"response.output_text.delta\",\"delta\":\"Hola\"}\n\n" +
                "event: response.completed\n" +
                "data: {\"type\":\"response.completed\",\"response\":{\"usage\":{\"input_tokens\":5,\"output_tokens\":2}}}\n\n",
        ))
        val provider = OpenAiProvider(definition("openai", PricingType.PAID), client) { "openai-secret" }
        val events = provider.generate(request(model = "response-model")).toList()
        val recorded = server.takeRequest()
        val body = json.parseToJsonElement(recorded.body.readUtf8()).jsonObject
        assertEquals("/v1/responses", recorded.path)
        assertEquals("persona", body["instructions"]?.jsonPrimitive?.content)
        assertEquals(false, body["store"]?.jsonPrimitive?.boolean)
        assertEquals(64, body["max_output_tokens"]?.jsonPrimitive?.int)
        assertTextAndUsage(events)
    }

    @Test fun anthropicUsesMessagesHeadersAndNativeEvents() = runBlocking {
        server.enqueue(sse(
            "event: message_start\n" +
                "data: {\"type\":\"message_start\",\"message\":{\"usage\":{\"input_tokens\":5,\"output_tokens\":0}}}\n\n" +
                "event: content_block_delta\n" +
                "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"Hola\"}}\n\n" +
                "event: message_delta\n" +
                "data: {\"type\":\"message_delta\",\"usage\":{\"output_tokens\":2}}\n\n" +
                "event: message_stop\n" +
                "data: {\"type\":\"message_stop\"}\n\n",
        ))
        val provider = AnthropicProvider(definition("anthropic", PricingType.PAID), client) { "anthropic-secret" }
        val events = provider.generate(request(model = "claude-test")).toList()
        val recorded = server.takeRequest()
        val body = json.parseToJsonElement(recorded.body.readUtf8()).jsonObject
        assertEquals("/v1/messages", recorded.path)
        assertEquals("anthropic-secret", recorded.getHeader("x-api-key"))
        assertEquals("2023-06-01", recorded.getHeader("anthropic-version"))
        assertEquals("persona", body["system"]?.jsonPrimitive?.content)
        assertEquals(64, body["max_tokens"]?.jsonPrimitive?.int)
        assertTextAndUsage(events)
    }

    @Test fun modelListingMappersUseProviderSpecificEndpoints() = runBlocking {
        server.enqueue(jsonResponse("""{"models":[{"name":"models/gemini-x","displayName":"Gemini X","supportedGenerationMethods":["generateContent"],"inputTokenLimit":8192}]}"""))
        val gemini = GeminiProvider(definition("gemini", PricingType.FREE_TIER), client) { "k" }
        assertEquals("gemini-x", gemini.getModels().single().modelId)
        assertEquals("/v1/models", server.takeRequest().path)

        server.enqueue(jsonResponse("""{"data":[{"id":"claude-x","display_name":"Claude X"}]}"""))
        val anthropic = AnthropicProvider(definition("anthropic", PricingType.PAID), client) { "k" }
        assertEquals("Claude X", anthropic.getModels().single().displayName)
        assertEquals("/v1/models", server.takeRequest().path)

        server.enqueue(jsonResponse("""{"data":[{"id":"openai-x"}]}"""))
        val openai = OpenAiProvider(definition("openai", PricingType.PAID), client) { "k" }
        assertEquals("openai-x", openai.getModels().single().modelId)
        assertEquals("/v1/models", server.takeRequest().path)
    }

    private fun request(model: String = "test-model") = LlmRequest(
        modelId = model,
        systemPrompt = "persona",
        messages = listOf(LlmMessage(LlmRole.USER, "hola"), LlmMessage(LlmRole.ASSISTANT, "saludo")),
        temperature = 0.7f,
        topP = 0.9f,
        topK = 20,
        maxTokens = 64,
        stopSequences = listOf("STOP"),
    )

    private fun definition(id: String, price: PricingType, requiresKey: Boolean = true) = ProviderDefinition(
        providerId = id,
        displayName = id,
        kind = if (id.startsWith("custom")) ProviderKind.CUSTOM else ProviderKind.ONLINE,
        pricingType = price,
        baseUrl = server.url("/v1").toString().trimEnd('/'),
        capabilities = ProviderCapabilities(supportsTopK = id == "gemini"),
        requiresApiKey = requiresKey,
        documentationUrl = "https://example.invalid",
        pricingNote = "test",
    )

    private fun openAiChunks() =
        "data: {\"choices\":[{\"delta\":{\"content\":\"Hola\"}}]}\n\n" +
            "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":5,\"completion_tokens\":2}}\n\n" +
            "data: [DONE]\n\n"

    private fun sse(body: String) = MockResponse()
        .setHeader("Content-Type", "text/event-stream")
        .setBody(body)

    private fun jsonResponse(body: String) = MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private fun assertTextAndUsage(events: List<LlmStreamEvent>) {
        assertTrue(events.any { it is LlmStreamEvent.TextDelta && it.text == "Hola" })
        assertTrue(events.any { it is LlmStreamEvent.Usage && it.usage.inputTokens == 5L })
        assertTrue(events.any { it is LlmStreamEvent.Usage && it.usage.outputTokens == 2L })
        assertTrue(events.any { it is LlmStreamEvent.Completed })
    }
}
