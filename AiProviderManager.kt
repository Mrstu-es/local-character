package com.localcharacter.app.llm.provider

import com.localcharacter.app.data.settings.AiProviderSettings
import com.localcharacter.app.data.settings.CustomAiProviderSettings
import com.localcharacter.app.data.settings.SettingsRepository
import com.localcharacter.app.security.ApiCredentialStore
import com.localcharacter.app.llm.provider.remote.AnthropicProvider
import com.localcharacter.app.llm.provider.remote.CustomOpenAiCompatibleProvider
import com.localcharacter.app.llm.provider.remote.GeminiProvider
import com.localcharacter.app.llm.provider.remote.GroqProvider
import com.localcharacter.app.llm.provider.remote.MistralProvider
import com.localcharacter.app.llm.provider.remote.OpenAiProvider
import com.localcharacter.app.llm.provider.remote.OpenRouterProvider
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient

class AiProviderManager(
    private val settings: SettingsRepository,
    private val credentialStore: ApiCredentialStore,
    private val localProvider: LocalLlamaProvider,
    private val client: OkHttpClient,
) {
    private val activeProvider = AtomicReference<LlmProvider?>(null)
    private val mutableConnectionResults = MutableStateFlow<Map<String, ProviderConnectionResult>>(emptyMap())
    val connectionResults = mutableConnectionResults.asStateFlow()

    suspend fun definitions(): List<ProviderDefinition> {
        val state = settings.aiProviderSettings.first()
        return ProviderCatalog.builtIns + state.customProviders.filter { it.enabled }.map(::customDefinition)
    }

    suspend fun summaries(): List<ProviderSummary> {
        val state = settings.aiProviderSettings.first()
        val results = mutableConnectionResults.value
        return definitions().map { definition ->
            val hasKey = !definition.requiresApiKey || credentialStore.contains(definition.providerId)
            val result = results[definition.providerId]
            val status = when {
                definition.kind == ProviderKind.LOCAL -> ProviderStatus.LOCAL
                !hasKey -> ProviderStatus.NOT_CONFIGURED
                result is ProviderConnectionResult.Success -> ProviderStatus.CONNECTED
                result is ProviderConnectionResult.Failure && result.error.kind == ProviderErrorKind.RATE_LIMIT -> ProviderStatus.RATE_LIMITED
                result is ProviderConnectionResult.Failure && result.error.kind == ProviderErrorKind.NETWORK -> ProviderStatus.OFFLINE
                result is ProviderConnectionResult.Failure -> ProviderStatus.ERROR
                else -> ProviderStatus.NOT_CONFIGURED
            }
            ProviderSummary(
                definition, status, hasKey,
                maskedKey = credentialStore.masked(definition.providerId),
                selectedModelId = when (definition.providerId) {
                    state.globalSelection.providerId -> state.globalSelection.modelId
                    else -> state.customProviders.firstOrNull { it.id == definition.providerId }?.modelId
                },
                statusMessage = (result as? ProviderConnectionResult.Failure)?.error?.friendlyMessage,
            )
        }
    }

    suspend fun testConnection(providerId: String): ProviderConnectionResult {
        val result = provider(providerId).testConnection()
        mutableConnectionResults.value = mutableConnectionResults.value + (providerId to result)
        return result
    }

    suspend fun models(providerId: String, forceRefresh: Boolean = false): List<LlmModelInfo> {
        if (providerId == LOCAL_PROVIDER_ID) return localProvider.getModels()
        val state = settings.aiProviderSettings.first()
        val cached = state.cachedModels[providerId].orEmpty()
        val updated = state.modelCacheUpdatedAt[providerId] ?: 0L
        if (!forceRefresh && cached.isNotEmpty() && System.currentTimeMillis() - updated < MODEL_CACHE_MILLIS) return cached
        val remote = provider(providerId).getModels()
        settings.updateAiProviderSettings { current ->
            current.copy(
                cachedModels = current.cachedModels + (providerId to remote),
                modelCacheUpdatedAt = current.modelCacheUpdatedAt + (providerId to System.currentTimeMillis()),
            )
        }
        return remote
    }

    suspend fun allCachedModels(includeLocal: Boolean = true): List<LlmModelInfo> {
        val state = settings.aiProviderSettings.first()
        val remote = state.cachedModels.values.flatten()
        return if (includeLocal) localProvider.getModels() + remote else remote
    }

    suspend fun resolveSelection(characterId: String, conversationId: String): ProviderModelSelection {
        val state = settings.aiProviderSettings.first()
        return ModelSelectionResolver.resolve(
            state.globalSelection,
            state.characterSelections[characterId],
            state.conversationSelections[conversationId],
            characterId in state.localOnlyCharacterIds,
        )
    }

    suspend fun modelInfo(selection: ProviderModelSelection): LlmModelInfo? {
        if (selection.providerId == LOCAL_PROVIDER_ID) return localProvider.getModels().firstOrNull()
        val state = settings.aiProviderSettings.first()
        return state.cachedModels[selection.providerId]?.firstOrNull { it.modelId == selection.modelId }
            ?: runCatching { models(selection.providerId).firstOrNull { it.modelId == selection.modelId } }.getOrNull()
    }

    suspend fun generate(selection: ProviderModelSelection, request: LlmRequest): Flow<LlmStreamEvent> {
        val selected = provider(selection.providerId)
        activeProvider.set(selected)
        return selected.generate(request)
    }

    suspend fun cancelGeneration() {
        activeProvider.getAndSet(null)?.cancelGeneration()
    }

    suspend fun fallbackCandidates(primary: ProviderModelSelection): List<ProviderModelSelection> {
        val state = settings.aiProviderSettings.first()
        if (!state.automaticFallback) return emptyList()
        return state.fallbackChain.filter { it != primary }.distinct()
    }

    suspend fun saveCredential(providerId: String, key: String) {
        credentialStore.save(providerId, key)
        mutableConnectionResults.value = mutableConnectionResults.value - providerId
    }

    suspend fun deleteCredential(providerId: String) {
        credentialStore.delete(providerId)
        mutableConnectionResults.value = mutableConnectionResults.value - providerId
    }

    suspend fun setGlobalSelection(selection: ProviderModelSelection) = settings.updateAiProviderSettings {
        it.copy(globalSelection = selection)
    }

    suspend fun setCharacterSelection(characterId: String, selection: ProviderModelSelection?) = settings.updateAiProviderSettings {
        it.copy(characterSelections = if (selection == null) it.characterSelections - characterId else it.characterSelections + (characterId to selection))
    }

    suspend fun setConversationSelection(conversationId: String, selection: ProviderModelSelection?) = settings.updateAiProviderSettings {
        it.copy(conversationSelections = if (selection == null) it.conversationSelections - conversationId else it.conversationSelections + (conversationId to selection))
    }

    suspend fun setCharacterLocalOnly(characterId: String, enabled: Boolean) = settings.updateAiProviderSettings {
        it.copy(localOnlyCharacterIds = if (enabled) it.localOnlyCharacterIds + characterId else it.localOnlyCharacterIds - characterId)
    }

    suspend fun toggleFavorite(model: LlmModelInfo) = settings.updateAiProviderSettings {
        val key = "${model.providerId}/${model.modelId}"
        it.copy(favoriteModels = if (key in it.favoriteModels) it.favoriteModels - key else it.favoriteModels + key)
    }

    suspend fun saveCustomProvider(config: CustomAiProviderSettings, apiKey: String?) {
        val validated = ProviderBaseUrlValidator.validate(config.baseUrl)
        val normalized = config.copy(baseUrl = validated.normalizedUrl)
        settings.updateAiProviderSettings { state ->
            val items = state.customProviders.toMutableList()
            val index = items.indexOfFirst { it.id == normalized.id }
            if (index >= 0) items[index] = normalized else items += normalized
            state.copy(customProviders = items.sortedBy { it.name.lowercase() })
        }
        apiKey?.takeIf(String::isNotBlank)?.let { credentialStore.save(config.id, it) }
    }

    suspend fun deleteCustomProvider(providerId: String) {
        settings.updateAiProviderSettings { state ->
            state.copy(
                customProviders = state.customProviders.filterNot { it.id == providerId },
                cachedModels = state.cachedModels - providerId,
                modelCacheUpdatedAt = state.modelCacheUpdatedAt - providerId,
                favoriteModels = state.favoriteModels.filterNot { it.startsWith("$providerId/") }.toSet(),
            )
        }
        credentialStore.delete(providerId)
    }

    private suspend fun provider(providerId: String): LlmProvider {
        if (providerId == LOCAL_PROVIDER_ID) return localProvider
        val state = settings.aiProviderSettings.first()
        val custom = state.customProviders.firstOrNull { it.id == providerId }
        val definition = ProviderCatalog.builtIn(providerId) ?: custom?.let(::customDefinition)
            ?: error("Proveedor no configurado: $providerId")
        val keyProvider: suspend () -> String? = { credentialStore.get(providerId) }
        return when (providerId) {
            "groq" -> GroqProvider(definition, client, keyProvider)
            "openrouter" -> OpenRouterProvider(definition, client, keyProvider)
            "gemini" -> GeminiProvider(definition, client, keyProvider)
            "openai" -> OpenAiProvider(definition, client, keyProvider)
            "anthropic" -> AnthropicProvider(definition, client, keyProvider)
            "mistral" -> MistralProvider(definition, client, keyProvider)
            else -> CustomOpenAiCompatibleProvider(definition, client, keyProvider, custom?.modelId.orEmpty())
        }
    }

    private fun customDefinition(config: CustomAiProviderSettings) = ProviderDefinition(
        providerId = config.id,
        displayName = config.name,
        kind = ProviderKind.CUSTOM,
        pricingType = config.pricingType,
        baseUrl = config.baseUrl,
        capabilities = ProviderCapabilities(),
        requiresApiKey = config.requiresApiKey,
        documentationUrl = config.baseUrl,
        pricingNote = "Precio no disponible. Información sujeta a cambios.",
    )

    companion object {
        private val MODEL_CACHE_MILLIS = TimeUnit.HOURS.toMillis(24)
    }
}
