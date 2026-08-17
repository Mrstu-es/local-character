package com.localcharacter.app.domain.character

import com.localcharacter.app.domain.model.Character

enum class ProviderAvailability { AVAILABLE, DEGRADED, UNAVAILABLE, AUTH_REQUIRED, UNSUPPORTED }
enum class ProviderAuthState { NOT_REQUIRED, SIGNED_OUT, SIGNED_IN, UNAVAILABLE }
enum class CatalogSort { RECENT, TRENDING, MOST_DOWNLOADED }
enum class ContentRating { EVERYONE, TEEN, MATURE, UNKNOWN }

data class ProviderCapabilities(
    val search: Boolean,
    val pagination: Boolean,
    val detail: Boolean,
    val cardDownload: Boolean,
    val avatarDownload: Boolean,
)

data class ProviderDescriptor(
    val id: String,
    val displayName: String,
    val availability: ProviderAvailability,
    val statusMessage: String,
    val capabilities: ProviderCapabilities,
)

data class ProviderHealth(
    val availability: ProviderAvailability,
    val message: String,
    val checkedAt: Long = System.currentTimeMillis(),
)

data class CatalogRequest(
    val query: String = "",
    val cursor: String? = null,
    val pageSize: Int = 20,
    val tags: Set<String> = emptySet(),
    val language: String? = null,
    val safeOnly: Boolean = true,
    val sort: CatalogSort = CatalogSort.RECENT,
)

object CatalogLanguageMatcher {
    fun matches(requested: String?, actual: String?): Boolean {
        val wanted = normalize(requested) ?: return true
        val item = normalize(actual) ?: return false
        return item == wanted
    }

    private fun normalize(value: String?): String? {
        val normalized = value?.trim()?.lowercase()?.replace('_', '-')?.takeIf(String::isNotBlank) ?: return null
        val base = normalized.substringBefore('-')
        return when (base) {
            "es", "spa", "spanish", "español", "espanol" -> "es"
            "en", "eng", "english", "inglés", "ingles" -> "en"
            "pt", "por", "portuguese", "português", "portugues" -> "pt"
            "fr", "fra", "fre", "french", "français", "francais" -> "fr"
            "de", "deu", "ger", "german", "deutsch" -> "de"
            "it", "ita", "italian", "italiano" -> "it"
            "ru", "rus", "russian", "русский" -> "ru"
            "ja", "jpn", "japanese", "日本語" -> "ja"
            "ko", "kor", "korean", "한국어" -> "ko"
            "zh", "zho", "chi", "chinese", "中文" -> "zh"
            "pl", "pol", "polish", "polski" -> "pl"
            "tr", "tur", "turkish", "türkçe", "turkce" -> "tr"
            "ar", "ara", "arabic", "العربية" -> "ar"
            "nl", "nld", "dut", "dutch", "nederlands" -> "nl"
            "sv", "swe", "swedish", "svenska" -> "sv"
            "da", "dan", "danish", "dansk" -> "da"
            "no", "nor", "norwegian", "norsk" -> "no"
            "fi", "fin", "finnish", "suomi" -> "fi"
            "hu", "hun", "hungarian", "magyar" -> "hu"
            "th", "tha", "thai", "ไทย" -> "th"
            "vi", "vie", "vietnamese", "tiếng việt", "tieng viet" -> "vi"
            "id", "ind", "indonesian", "bahasa indonesia" -> "id"
            else -> base
        }
    }
}

data class CatalogPage<T>(
    val items: List<T>,
    val nextCursor: String?,
    val total: Int? = null,
)

data class RemoteCharacterSummary(
    val providerId: String,
    val remoteId: String,
    val name: String,
    val description: String,
    val avatarUrl: String?,
    val author: String?,
    val tags: List<String>,
    val language: String?,
    val isNsfw: Boolean,
    val downloadCount: Int?,
    val updatedAt: Long?,
    val sourceUrl: String,
    val installedCharacterId: String? = null,
    val thumbnailUrl: String? = null,
    val coverUrl: String? = null,
    /** Provider-supplied classification; never inferred from names, avatars, or ambiguous tags. */
    val contentRating: ContentRating = if (isNsfw) ContentRating.MATURE else ContentRating.UNKNOWN,
)

data class RemoteCharacterDetail(
    val summary: RemoteCharacterSummary,
    val cardUrl: String?,
    val version: String?,
    val firstMessagePreview: String? = null,
    val personality: String? = null,
    val scenario: String? = null,
    val exampleMessages: String? = null,
    val systemPrompt: String? = null,
    val alternateGreetings: List<String> = emptyList(),
    val galleryImages: List<String> = emptyList(),
)

data class RemoteAsset(
    val bytes: ByteArray,
    val contentType: String?,
    val fileName: String,
)

/** Provider-neutral contract. Network DTOs must be mapped before crossing this boundary. */
interface CharacterCatalogProvider {
    val descriptor: ProviderDescriptor
    suspend fun health(): ProviderHealth
    suspend fun search(request: CatalogRequest): CatalogPage<RemoteCharacterSummary>
    suspend fun getDetail(remoteId: String): RemoteCharacterDetail
    suspend fun downloadCard(detail: RemoteCharacterDetail): RemoteAsset
    suspend fun downloadAvatar(detail: RemoteCharacterDetail): RemoteAsset?
}

/** Direct access to already-installed characters, exposed through the same catalog contract. */
interface LocalCharacterSource {
    suspend fun search(query: String): List<Character>
    suspend fun getCharacter(id: String): Character?
}

class UnsupportedCharacterProvider(
    override val descriptor: ProviderDescriptor,
) : CharacterCatalogProvider {
    override suspend fun health() = ProviderHealth(descriptor.availability, descriptor.statusMessage)
    override suspend fun search(request: CatalogRequest): CatalogPage<RemoteCharacterSummary> = CatalogPage(emptyList(), null, 0)
    override suspend fun getDetail(remoteId: String): RemoteCharacterDetail = throw UnsupportedOperationException(descriptor.statusMessage)
    override suspend fun downloadCard(detail: RemoteCharacterDetail): RemoteAsset = throw UnsupportedOperationException(descriptor.statusMessage)
    override suspend fun downloadAvatar(detail: RemoteCharacterDetail): RemoteAsset? = throw UnsupportedOperationException(descriptor.statusMessage)
}
