package com.localcharacter.app.data.settings

import com.localcharacter.app.llm.provider.LlmModelInfo
import com.localcharacter.app.llm.provider.PricingType
import com.localcharacter.app.llm.provider.ProviderModelSelection
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class CustomAiProviderSettings(
    val id: String,
    val name: String,
    val baseUrl: String,
    val modelId: String,
    val requiresApiKey: Boolean = true,
    val pricingType: PricingType = PricingType.UNKNOWN,
    val enabled: Boolean = true,
)

@Serializable
data class AiBudgetSettings(
    val monthlyBudgetUsd: Double? = null,
    val warningPercent: Int = 80,
    val warnAtLimit: Boolean = false,
    val blockAtLimit: Boolean = false,
)

@Serializable
data class AiProviderSettings(
    val globalSelection: ProviderModelSelection = ProviderModelSelection(),
    val characterSelections: Map<String, ProviderModelSelection> = emptyMap(),
    val conversationSelections: Map<String, ProviderModelSelection> = emptyMap(),
    val localOnlyCharacterIds: Set<String> = emptySet(),
    val customProviders: List<CustomAiProviderSettings> = emptyList(),
    val cachedModels: Map<String, List<LlmModelInfo>> = emptyMap(),
    val modelCacheUpdatedAt: Map<String, Long> = emptyMap(),
    /** Stored as providerId/modelId to avoid collisions between catalogs. */
    val favoriteModels: Set<String> = emptySet(),
    val preferFreeModels: Boolean = true,
    val automaticFallback: Boolean = false,
    val fallbackChain: List<ProviderModelSelection> = emptyList(),
    val budget: AiBudgetSettings = AiBudgetSettings(),
)

object AiProviderSettingsCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    fun decode(value: String?): AiProviderSettings = value
        ?.takeIf(String::isNotBlank)
        ?.let { runCatching { json.decodeFromString<AiProviderSettings>(it) }.getOrDefault(AiProviderSettings()) }
        ?: AiProviderSettings()
    fun encode(value: AiProviderSettings): String = json.encodeToString(value)
}

fun LlmModelInfo.favoriteKey(): String = "$providerId/$modelId"
