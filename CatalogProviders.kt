package com.localcharacter.app.data.catalog

import com.localcharacter.app.domain.character.CatalogPage
import com.localcharacter.app.domain.character.CatalogRequest
import com.localcharacter.app.domain.character.CatalogLanguageMatcher
import com.localcharacter.app.domain.character.CharacterCatalogProvider
import com.localcharacter.app.domain.character.LocalCharacterSource
import com.localcharacter.app.domain.character.ProviderAvailability
import com.localcharacter.app.domain.character.ProviderCapabilities
import com.localcharacter.app.domain.character.ProviderDescriptor
import com.localcharacter.app.domain.character.ProviderHealth
import com.localcharacter.app.domain.character.RemoteAsset
import com.localcharacter.app.domain.character.RemoteCharacterDetail
import com.localcharacter.app.domain.character.RemoteCharacterSummary
import com.localcharacter.app.domain.character.ContentRating
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl

class LocalCharacterCatalogProvider(private val source: LocalCharacterSource) : CharacterCatalogProvider {
    override val descriptor = ProviderDescriptor(
        id = ID,
        displayName = "Locales",
        availability = ProviderAvailability.AVAILABLE,
        statusMessage = "Personajes guardados en este dispositivo.",
        capabilities = ProviderCapabilities(search = true, pagination = false, detail = true, cardDownload = false, avatarDownload = false),
    )

    override suspend fun health() = ProviderHealth(ProviderAvailability.AVAILABLE, descriptor.statusMessage)

    override suspend fun search(request: CatalogRequest): CatalogPage<RemoteCharacterSummary> {
        val characters = source.search(request.query).filter { character ->
            request.tags.isEmpty() || character.tags.any { tag -> request.tags.any { it.equals(tag, true) } }
        }
        val offset = request.cursor?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val limit = request.pageSize.coerceIn(1, 40)
        val visible = characters.drop(offset).take(limit)
        return CatalogPage(visible.map { character ->
            RemoteCharacterSummary(
                providerId = ID,
                remoteId = character.id,
                name = character.name,
                description = character.description,
                avatarUrl = character.avatarUri,
                author = null,
                tags = character.tags,
                language = null,
                isNsfw = false,
                downloadCount = null,
                updatedAt = character.updatedAt,
                sourceUrl = "local://${character.id}",
                installedCharacterId = character.id,
            )
        }, (offset + visible.size).takeIf { visible.size == limit && it < characters.size }?.toString(), characters.size)
    }

    override suspend fun getDetail(remoteId: String): RemoteCharacterDetail {
        val character = source.getCharacter(remoteId) ?: error("Personaje local no encontrado.")
        return RemoteCharacterDetail(
            RemoteCharacterSummary(
                ID, character.id, character.name, character.description, character.avatarUri, null,
                character.tags, null, false, null, character.updatedAt, "local://${character.id}", character.id,
            ),
            cardUrl = null,
            version = null,
            firstMessagePreview = character.firstMessage,
        )
    }

    override suspend fun downloadCard(detail: RemoteCharacterDetail): RemoteAsset = error("Un personaje local no necesita descarga.")
    override suspend fun downloadAvatar(detail: RemoteCharacterDetail): RemoteAsset? = null

    companion object { const val ID = "local" }
}

/**
 * Optional provider for community repositories that explicitly publish a repository.json.
 * It is dormant until the caller supplies a trusted HTTPS index URL and allowed host set.
 */
class GenericRepositoryProvider(
    private val http: SecureCatalogHttpClient,
    private val indexUrl: String,
    private val allowedHosts: Set<String>,
    override val descriptor: ProviderDescriptor,
) : CharacterCatalogProvider {
    init { RemoteUrlPolicy.validate(indexUrl, allowedHosts) }

    override suspend fun health(): ProviderHealth = runCatching {
        loadIndex()
        ProviderHealth(ProviderAvailability.AVAILABLE, "Repositorio JSON disponible.")
    }.getOrElse { ProviderHealth(ProviderAvailability.DEGRADED, it.message ?: "Repositorio no disponible.") }

    override suspend fun search(request: CatalogRequest): CatalogPage<RemoteCharacterSummary> {
        val index = loadIndex()
        val offset = request.cursor?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val limit = request.pageSize.coerceIn(1, 40)
        val query = request.query.trim()
        val filtered = index.characters.asSequence()
            .filter { !request.safeOnly || (!it.isNsfw && !it.contentRating.equals("MATURE", true)) }
            .filter { CatalogLanguageMatcher.matches(request.language, it.language) }
            .filter { query.isBlank() || it.name.contains(query, true) || it.description.contains(query, true) || it.tags.any { tag -> tag.contains(query, true) } }
            .filter { request.tags.isEmpty() || it.tags.any(request.tags::contains) }
            .toList()
        val items = filtered.drop(offset).take(limit).map(::mapSummary)
        return CatalogPage(items, (offset + items.size).takeIf { items.size == limit && it < filtered.size }?.toString(), filtered.size)
    }

    override suspend fun getDetail(remoteId: String): RemoteCharacterDetail {
        val item = loadIndex().characters.firstOrNull { it.id == remoteId } ?: error("Personaje remoto no encontrado.")
        return RemoteCharacterDetail(
            mapSummary(item), item.cardUrl, item.version, item.firstMessagePreview,
            item.personality, item.scenario, item.exampleMessages, item.systemPrompt,
            item.alternateGreetings, item.galleryImages,
        )
    }

    override suspend fun downloadCard(detail: RemoteCharacterDetail): RemoteAsset {
        val url = detail.cardUrl ?: error("El repositorio no publicó la tarjeta.")
        return http.get(url, allowedHosts, SecureCatalogHttpClient.MAX_CARD_BYTES, AssetKind.CARD, "${detail.summary.remoteId}.card")
    }

    override suspend fun downloadAvatar(detail: RemoteCharacterDetail): RemoteAsset? = detail.summary.avatarUrl?.let {
        http.get(it, allowedHosts, SecureCatalogHttpClient.MAX_IMAGE_BYTES, AssetKind.IMAGE, "${detail.summary.remoteId}-avatar")
    }

    private suspend fun loadIndex(): RepositoryIndexDto {
        val file = indexUrl.toHttpUrl().pathSegments.lastOrNull().orEmpty().ifBlank { "repository.json" }
        val asset = http.get(indexUrl, allowedHosts, SecureCatalogHttpClient.MAX_JSON_BYTES, AssetKind.JSON, file)
        return RepositoryIndexParser.parse(asset.bytes.decodeToString())
    }

    private fun mapSummary(item: RepositoryCharacterDto) = RemoteCharacterSummary(
        descriptor.id, item.id, item.name.take(120), item.description.take(2_000),
        item.avatarUrl?.let { runCatching { RemoteUrlPolicy.validate(it, allowedHosts).toString() }.getOrNull() },
        item.author?.take(120), item.tags.take(32), item.language,
        item.isNsfw || item.contentRating.equals("MATURE", true), item.downloadCount,
        item.updatedAt, RemoteUrlPolicy.validate(item.sourceUrl, allowedHosts).toString(), null,
        contentRating = runCatching { ContentRating.valueOf(item.contentRating.uppercase()) }
            .getOrDefault(if (item.isNsfw) ContentRating.MATURE else ContentRating.UNKNOWN),
    )
}

object RepositoryIndexParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    fun parse(value: String): RepositoryIndexDto {
        val index = json.decodeFromString<RepositoryIndexDto>(value)
        require(index.schemaVersion == 1) { "Versión de repository.json no compatible." }
        require(index.characters.size <= 100_000) { "El repositorio declara demasiados personajes." }
        return index
    }
}

@Serializable
data class RepositoryIndexDto(
    val schemaVersion: Int,
    val characters: List<RepositoryCharacterDto> = emptyList(),
)

@Serializable
data class RepositoryCharacterDto(
    val id: String,
    val name: String,
    val description: String = "",
    val avatarUrl: String? = null,
    val cardUrl: String,
    val sourceUrl: String,
    val author: String? = null,
    val version: String? = null,
    val firstMessagePreview: String? = null,
    val personality: String? = null,
    val scenario: String? = null,
    val exampleMessages: String? = null,
    val systemPrompt: String? = null,
    val alternateGreetings: List<String> = emptyList(),
    val galleryImages: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val language: String? = null,
    val isNsfw: Boolean = false,
    val contentRating: String = if (isNsfw) "MATURE" else "UNKNOWN",
    val downloadCount: Int? = null,
    val updatedAt: Long? = null,
)
