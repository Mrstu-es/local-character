package com.localcharacter.app.llm.provider

object ProviderCatalog {
    val builtIns = listOf(
        ProviderDefinition(
            LOCAL_PROVIDER_ID, "Local GGUF", ProviderKind.LOCAL, PricingType.LOCAL, "local://llama.cpp",
            ProviderCapabilities(supportsTopK = true, supportsModelListing = false, supportsTokenUsage = false),
            requiresApiKey = false,
            documentationUrl = "https://github.com/ggml-org/llama.cpp",
            pricingNote = "Sin coste de API; usa CPU y memoria del dispositivo.",
        ),
        ProviderDefinition(
            "groq", "Groq", ProviderKind.ONLINE, PricingType.FREE_TIER, "https://api.groq.com/openai/v1",
            ProviderCapabilities(supportsTools = true, supportsStructuredOutput = true),
            documentationUrl = "https://console.groq.com/docs/api-reference",
            pricingNote = "Free tier o pago según la cuenta. Información sujeta a cambios.",
        ),
        ProviderDefinition(
            "openrouter", "OpenRouter", ProviderKind.ONLINE, PricingType.UNKNOWN, "https://openrouter.ai/api/v1",
            ProviderCapabilities(
                supportsReasoning = true, supportsVision = true, supportsTools = true,
                supportsStructuredOutput = true, supportsImageInput = true,
            ),
            documentationUrl = "https://openrouter.ai/docs/api/reference/overview",
            pricingNote = "Modelos gratuitos y de pago; se usa la metadata actual del catálogo.",
        ),
        ProviderDefinition(
            "gemini", "Google Gemini", ProviderKind.ONLINE, PricingType.FREE_TIER,
            "https://generativelanguage.googleapis.com/v1beta",
            ProviderCapabilities(
                supportsTopK = true, supportsReasoning = true, supportsVision = true,
                supportsTools = true, supportsStructuredOutput = true, supportsImageInput = true,
            ),
            documentationUrl = "https://ai.google.dev/api",
            pricingNote = "Free tier y pago según modelo, proyecto y región. Información sujeta a cambios.",
        ),
        ProviderDefinition(
            "openai", "OpenAI", ProviderKind.ONLINE, PricingType.PAID, "https://api.openai.com/v1",
            // Responses is deliberately conservative: reasoning models do not all accept sampling fields.
            ProviderCapabilities(
                supportsTemperature = false, supportsTopP = false, supportsReasoning = true,
                supportsVision = true, supportsTools = true, supportsInputCaching = true,
                supportsStructuredOutput = true, supportsImageInput = true,
            ),
            documentationUrl = "https://platform.openai.com/docs/api-reference/responses",
            pricingNote = "Uso de API facturado según el modelo. Precio exacto no inferido del ID.",
        ),
        ProviderDefinition(
            "anthropic", "Anthropic", ProviderKind.ONLINE, PricingType.PAID, "https://api.anthropic.com/v1",
            ProviderCapabilities(
                supportsReasoning = true, supportsVision = true, supportsTools = true,
                supportsInputCaching = true, supportsImageInput = true,
            ),
            documentationUrl = "https://platform.claude.com/docs/en/api/overview",
            pricingNote = "API de pago/prepago. Precio exacto no inferido del ID.",
        ),
        ProviderDefinition(
            "mistral", "Mistral", ProviderKind.ONLINE, PricingType.UNKNOWN, "https://api.mistral.ai/v1",
            ProviderCapabilities(
                supportsReasoning = true, supportsVision = true, supportsTools = true,
                supportsInputCaching = true, supportsStructuredOutput = true, supportsImageInput = true,
            ),
            documentationUrl = "https://docs.mistral.ai/api",
            pricingNote = "Disponibilidad y facturación dependen de la cuenta. Información sujeta a cambios.",
        ),
    )

    fun builtIn(providerId: String): ProviderDefinition? = builtIns.firstOrNull { it.providerId == providerId }
}

