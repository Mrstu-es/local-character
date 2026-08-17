package com.localcharacter.app.ui.catalog

import com.localcharacter.app.domain.character.CatalogSort
import com.localcharacter.app.domain.character.ProviderDescriptor
import com.localcharacter.app.domain.character.ProviderHealth
import com.localcharacter.app.domain.character.RemoteCharacterDetail
import com.localcharacter.app.domain.character.RemoteCharacterSummary
import com.localcharacter.app.data.settings.CatalogLanguage

data class ExploreCatalogUiState(
    val providers: List<ProviderDescriptor> = emptyList(),
    val health: Map<String, ProviderHealth> = emptyMap(),
    val selectedProviderId: String = "ai_character_cards",
    val query: String = "",
    val safeOnly: Boolean = true,
    val sort: CatalogSort = CatalogSort.RECENT,
    val selectedTags: Set<String> = emptySet(),
    val language: CatalogLanguage = CatalogLanguage.SPANISH,
    val items: List<RemoteCharacterSummary> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val nextCursor: String? = null,
    val total: Int? = null,
    val error: String? = null,
)

sealed interface RemoteDetailUiState {
    data object Idle : RemoteDetailUiState
    data object Loading : RemoteDetailUiState
    data class Ready(
        val detail: RemoteCharacterDetail,
        val installedCharacterId: String? = null,
        val installing: Boolean = false,
        val installStep: String? = null,
    ) : RemoteDetailUiState
    data class Error(val message: String) : RemoteDetailUiState
}
