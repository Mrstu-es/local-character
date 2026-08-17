package com.localcharacter.app.llm.provider

import java.net.InetAddress
import java.net.URI

object PricingClassifier {
    fun fromOpenRouter(modelId: String, inputPerToken: Double?, outputPerToken: Double?): PricingType {
        if (inputPerToken == 0.0 && outputPerToken == 0.0) return PricingType.FREE
        if (inputPerToken != null || outputPerToken != null) return PricingType.PAID
        return if (modelId.endsWith(":free")) PricingType.FREE else PricingType.UNKNOWN
    }
}

object CostCalculator {
    fun estimateUsd(usage: TokenUsage, model: LlmModelInfo): Double? {
        val input = usage.inputTokens ?: return null
        val output = usage.outputTokens ?: return null
        val inputPrice = model.inputPrice ?: return null
        val outputPrice = model.outputPrice ?: return null
        return input * inputPrice / 1_000_000.0 + output * outputPrice / 1_000_000.0
    }
}

object ProviderBaseUrlValidator {
    data class Result(val normalizedUrl: String, val cleartextWarning: Boolean)

    fun validate(raw: String): Result {
        val value = raw.trim().trimEnd('/')
        val uri = runCatching { URI(value) }.getOrElse { throw IllegalArgumentException("Base URL no válida.") }
        require(uri.scheme == "https" || uri.scheme == "http") { "Solo se permite https:// o http:// para red local." }
        require(!uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null) { "Base URL no válida." }
        if (uri.scheme == "http") require(isPrivateOrLoopback(uri.host)) {
            "HTTP sin cifrar solo se permite para localhost o direcciones LAN privadas."
        }
        return Result(value, uri.scheme == "http")
    }

    private fun isPrivateOrLoopback(host: String): Boolean = runCatching {
        val address = InetAddress.getByName(host)
        address.isLoopbackAddress || address.isSiteLocalAddress || address.isLinkLocalAddress
    }.getOrDefault(host.equals("localhost", ignoreCase = true))
}

object ModelSelectionResolver {
    fun resolve(
        global: ProviderModelSelection,
        character: ProviderModelSelection?,
        conversation: ProviderModelSelection?,
        localOnly: Boolean,
    ): ProviderModelSelection = when {
        localOnly -> ProviderModelSelection()
        conversation != null -> conversation
        character != null -> character
        else -> global
    }
}

object FallbackPolicy {
    fun canFallback(error: ProviderError, emittedText: Boolean): Boolean = !emittedText && error.kind in setOf(
        ProviderErrorKind.NETWORK,
        ProviderErrorKind.TIMEOUT,
        ProviderErrorKind.RATE_LIMIT,
        ProviderErrorKind.MODEL_NOT_FOUND,
        ProviderErrorKind.PROVIDER,
    )
}

