package com.localcharacter.app.llm.provider

import com.localcharacter.app.llm.provider.network.ProviderErrorMapper
import java.net.SocketTimeoutException
import okhttp3.Headers.Companion.headersOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderPoliciesTest {
    @Test fun selectionPriorityIsConversationThenCharacterThenGlobal() {
        val global = ProviderModelSelection("groq", "g")
        val character = ProviderModelSelection("openrouter", "c")
        val conversation = ProviderModelSelection("openai", "v")
        assertEquals(conversation, ModelSelectionResolver.resolve(global, character, conversation, false))
        assertEquals(character, ModelSelectionResolver.resolve(global, character, null, false))
        assertEquals(global, ModelSelectionResolver.resolve(global, null, null, false))
    }

    @Test fun localOnlyAlwaysWins() {
        val result = ModelSelectionResolver.resolve(
            ProviderModelSelection("openai", "global"),
            ProviderModelSelection("groq", "character"),
            ProviderModelSelection("anthropic", "conversation"),
            true,
        )
        assertEquals(LOCAL_PROVIDER_ID, result.providerId)
    }

    @Test fun fallbackIsConservative() {
        assertTrue(FallbackPolicy.canFallback(ProviderError(ProviderErrorKind.RATE_LIMIT, "429"), false))
        assertTrue(FallbackPolicy.canFallback(ProviderError(ProviderErrorKind.NETWORK, "offline"), false))
        assertFalse(FallbackPolicy.canFallback(ProviderError(ProviderErrorKind.AUTHENTICATION, "401"), false))
        assertFalse(FallbackPolicy.canFallback(ProviderError(ProviderErrorKind.PROVIDER, "500"), true))
    }

    @Test fun openRouterPricingUsesMetadataBeforeSuffix() {
        assertEquals(PricingType.FREE, PricingClassifier.fromOpenRouter("vendor/model", 0.0, 0.0))
        assertEquals(PricingType.PAID, PricingClassifier.fromOpenRouter("vendor/model:free", 0.000001, 0.000002))
        assertEquals(PricingType.FREE, PricingClassifier.fromOpenRouter("vendor/model:free", null, null))
        assertEquals(PricingType.UNKNOWN, PricingClassifier.fromOpenRouter("vendor/model", null, null))
    }

    @Test fun costCalculatorRequiresRealUsageAndBothPrices() {
        val model = LlmModelInfo("p", "m", inputPrice = 2.0, outputPrice = 6.0)
        assertEquals(0.014, CostCalculator.estimateUsd(TokenUsage(1_000, 2_000), model)!!, 0.0000001)
        assertNull(CostCalculator.estimateUsd(TokenUsage(null, 2_000), model))
        assertNull(CostCalculator.estimateUsd(TokenUsage(1_000, 2_000), model.copy(outputPrice = null)))
    }

    @Test fun baseUrlOnlyAllowsHttpsOrPrivateLanHttp() {
        assertEquals("https://example.com/v1", ProviderBaseUrlValidator.validate("https://example.com/v1/").normalizedUrl)
        assertTrue(ProviderBaseUrlValidator.validate("http://192.168.1.50:1234/v1").cleartextWarning)
        assertTrue(ProviderBaseUrlValidator.validate("http://127.0.0.1:8080/v1").cleartextWarning)
        listOf("file:///tmp/model", "javascript:alert(1)", "http://example.com/v1").forEach { invalid ->
            assertTrue(runCatching { ProviderBaseUrlValidator.validate(invalid) }.isFailure)
        }
    }

    @Test fun errorMapperHandlesStatusRetryAfterAndTimeout() {
        val rate = ProviderErrorMapper.http(429, """{"error":{"message":"slow","code":"rate_limit"}}""", headersOf("Retry-After", "12"))
        assertEquals(ProviderErrorKind.RATE_LIMIT, rate.kind)
        assertEquals(12L, rate.retryAfterSeconds)
        assertEquals(ProviderErrorKind.AUTHENTICATION, ProviderErrorMapper.http(401, null).kind)
        assertEquals(ProviderErrorKind.BILLING, ProviderErrorMapper.http(402, null).kind)
        assertEquals(ProviderErrorKind.TIMEOUT, ProviderErrorMapper.throwable(SocketTimeoutException()).kind)
    }
}
