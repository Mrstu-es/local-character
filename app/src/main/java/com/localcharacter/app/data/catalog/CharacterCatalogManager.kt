package com.localcharacter.app.data.catalog

import com.localcharacter.app.domain.character.CatalogPage
import com.localcharacter.app.domain.character.CatalogRequest
import com.localcharacter.app.domain.character.CharacterCatalogProvider
import com.localcharacter.app.domain.character.ProviderDescriptor
import com.localcharacter.app.domain.character.ProviderHealth
import com.localcharacter.app.domain.character.RemoteCharacterDetail
import com.localcharacter.app.domain.character.RemoteCharacterSummary
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch

data class ProviderCatalogResult(
    val provider: ProviderDescriptor,
    val loading: Boolean,
    val page: CatalogPage<RemoteCharacterSummary>? = null,
    val error: String? = null,
)

class CharacterCatalogManager(providers: List<CharacterCatalogProvider>) {
    private val builtInProviders = providers.associateBy { it.descriptor.id }
    @Volatile private var customProviders = emptyMap<String, CharacterCatalogProvider>()
    private val providersById: Map<String, CharacterCatalogProvider>
        get() = builtInProviders + customProviders
    val descriptors: List<ProviderDescriptor>
        get() = providersById.values.map { it.descriptor }

    fun replaceCustomProviders(providers: List<CharacterCatalogProvider>) {
        customProviders = providers.associateBy { it.descriptor.id }
    }

    fun provider(id: String): CharacterCatalogProvider = providersById[id] ?: error("Proveedor desconocido: $id")

    suspend fun health(providerIds: Set<String> = providersById.keys): Map<String, ProviderHealth> = coroutineScope {
        providersById.filterKeys { it in providerIds }.map { (id, provider) -> async { id to provider.health() } }.awaitAll().toMap()
    }

    /** Each provider emits independently; one failure never discards another provider's result. */
    fun searchProgressively(providerIds: Set<String>, request: CatalogRequest): Flow<ProviderCatalogResult> = channelFlow {
        providerIds.mapNotNull(providersById::get).forEach { provider ->
            launch {
                send(ProviderCatalogResult(provider.descriptor, loading = true))
                runCatching { provider.search(request) }
                    .onSuccess { send(ProviderCatalogResult(provider.descriptor, loading = false, page = it)) }
                    .onFailure { send(ProviderCatalogResult(provider.descriptor, loading = false, error = it.message ?: "Error del proveedor.")) }
            }
        }
    }

    suspend fun page(providerId: String, request: CatalogRequest) = provider(providerId).search(request)
    suspend fun detail(providerId: String, remoteId: String): RemoteCharacterDetail = provider(providerId).getDetail(remoteId)
}
