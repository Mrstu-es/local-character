package com.localcharacter.app.data.catalog

import com.localcharacter.app.domain.character.CatalogPage
import com.localcharacter.app.domain.character.CatalogLanguageMatcher
import com.localcharacter.app.domain.character.CatalogRequest
import com.localcharacter.app.domain.character.CatalogSort
import com.localcharacter.app.domain.character.CharacterCatalogProvider
import com.localcharacter.app.domain.character.ProviderAvailability
import com.localcharacter.app.domain.character.ProviderCapabilities
import com.localcharacter.app.domain.character.ProviderDescriptor
import com.localcharacter.app.domain.character.ProviderHealth
import com.localcharacter.app.domain.character.RemoteAsset
import com.localcharacter.app.domain.character.RemoteCharacterDetail
import com.localcharacter.app.domain.character.RemoteCharacterSummary
import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl

class AiCharacterCardsProvider(
    private val http: SecureCatalogHttpClient,
    private val apiBaseUrl: String = API_BASE,
    private val assetOrigin: String = ASSET_ORIGIN,
    private val siteOrigin: String = SITE_ORIGIN,
) : CharacterCatalogProvider {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val apiHost = apiBaseUrl.toHttpUrl().host
    private val assetHost = assetOrigin.toHttpUrl().host

    override val descriptor = ProviderDescriptor(
        id = ID,
        displayName = "AI Character Cards",
        availability = ProviderAvailability.AVAILABLE,
        statusMessage = "Catálogo público de solo lectura, sin cuenta.",
        capabilities = ProviderCapabilities(search = true, pagination = true, detail = true, cardDownload = true, avatarDownload = true),
    )

    override suspend fun health(): ProviderHealth = runCatching {
        http.get(
            "$apiBaseUrl/cards/metadata/languages",
            setOf(apiHost),
            SecureCatalogHttpClient.MAX_JSON_BYTES,
            AssetKind.JSON,
            "languages.json",
        )
        ProviderHealth(ProviderAvailability.AVAILABLE, "Conexión pública disponible.")
    }.getOrElse { ProviderHealth(ProviderAvailability.DEGRADED, it.message ?: "Proveedor no disponible.") }

    override suspend fun search(request: CatalogRequest): CatalogPage<RemoteCharacterSummary> {
        val skip = request.cursor?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val limit = request.pageSize.coerceIn(1, 40)
        val builder = "$apiBaseUrl/cards".toHttpUrl().newBuilder()
            .addQueryParameter("skip", skip.toString())
            .addQueryParameter("limit", limit.toString())
        if (request.safeOnly) builder.addQueryParameter("isNsfw", "false")
        request.query.trim().takeIf(String::isNotBlank)?.let { builder.addQueryParameter("search", it.take(160)) }
        request.language?.takeIf(String::isNotBlank)?.let { builder.addQueryParameter("language", it.take(12)) }
        request.tags.take(12).takeIf(Collection<String>::isNotEmpty)?.let { builder.addQueryParameter("tags", it.joinToString(",")) }
        when (request.sort) {
            CatalogSort.RECENT -> builder.addQueryParameter("orderBy", "createdAt")
            CatalogSort.MOST_DOWNLOADED -> builder.addQueryParameter("orderBy", "downloadCount")
            CatalogSort.TRENDING -> return trending(request, skip, limit)
        }
        val asset = http.get(builder.build().toString(), setOf(apiHost), SecureCatalogHttpClient.MAX_JSON_BYTES, AssetKind.JSON, "cards.json")
        val response = json.decodeFromString<AiccListResponse>(asset.bytes.decodeToString())
        return response.toPage(skip, limit, request.language).also { CatalogDebugLog.event(ID, "Search results: ${it.items.size}") }
    }

    private suspend fun trending(request: CatalogRequest, skip: Int, limit: Int): CatalogPage<RemoteCharacterSummary> {
        val builder = "$apiBaseUrl/cards/trending".toHttpUrl().newBuilder()
            .addQueryParameter("period", "7d")
            .addQueryParameter("skip", skip.toString())
            .addQueryParameter("limit", limit.toString())
            .addQueryParameter("nsfw", if (request.safeOnly) "sfw" else "all")
        request.language?.takeIf(String::isNotBlank)?.let { builder.addQueryParameter("language", it.take(12)) }
        request.tags.take(12).takeIf(Collection<String>::isNotEmpty)?.let { builder.addQueryParameter("tags", it.joinToString(",")) }
        val asset = http.get(builder.build().toString(), setOf(apiHost), SecureCatalogHttpClient.MAX_JSON_BYTES, AssetKind.JSON, "trending.json")
        val response = json.decodeFromString<AiccTrendingResponse>(asset.bytes.decodeToString())
        val items = response.data.asSequence()
            .filter { !request.safeOnly || !it.isNsfw }
            .map(::mapSummary)
            .filter { CatalogLanguageMatcher.matches(request.language, it.language) }
            .toList()
        return CatalogPage(items, (skip + items.size).takeIf { items.size == limit }?.toString(), response.total)
            .also { CatalogDebugLog.event(ID, "Trending results: ${it.items.size}") }
    }

    override suspend fun getDetail(remoteId: String): RemoteCharacterDetail {
        require(remoteId.all(Char::isDigit) && remoteId.length <= 12) { "Identificador remoto no válido." }
        val asset = http.get("$apiBaseUrl/cards/$remoteId", setOf(apiHost), SecureCatalogHttpClient.MAX_JSON_BYTES, AssetKind.JSON, "card-$remoteId.json")
        val dto = json.decodeFromString<AiccCardDto>(asset.bytes.decodeToString())
        val current = dto.versions.firstOrNull { it.isCurrent } ?: dto.versions.maxByOrNull { it.version }
        val cardUrl = current?.fileUrl?.let(::resolveAssetUrl)
        val fields = current?.let { version ->
            runCatching {
                val fieldsAsset = http.get(
                    "$apiBaseUrl/cards/$remoteId/versions/${version.id}/fields",
                    setOf(apiHost),
                    SecureCatalogHttpClient.MAX_JSON_BYTES,
                    AssetKind.JSON,
                    "fields-$remoteId.json",
                )
                json.decodeFromString<AiccFieldsResponse>(fieldsAsset.bytes.decodeToString()).fields
            }.getOrNull()
        }
        return RemoteCharacterDetail(
            summary = mapSummary(dto),
            cardUrl = cardUrl,
            version = current?.version?.toString(),
            firstMessagePreview = fields?.firstMes?.take(4_000),
            personality = fields?.personality?.take(20_000),
            scenario = fields?.scenario?.take(20_000),
            exampleMessages = fields?.exampleMessages?.take(40_000),
            systemPrompt = fields?.systemPrompt?.take(20_000),
            alternateGreetings = fields?.alternateGreetings.orEmpty().take(32).map { it.take(20_000) },
        ).also { CatalogDebugLog.event(ID, "Detail mapped; image URL resolved: ${it.summary.avatarUrl != null}") }
    }

    override suspend fun downloadCard(detail: RemoteCharacterDetail): RemoteAsset {
        val url = detail.cardUrl ?: throw CatalogNetworkException("El proveedor no publicó una tarjeta descargable.")
        return http.get(url, setOf(assetHost), SecureCatalogHttpClient.MAX_CARD_BYTES, AssetKind.CARD, "${detail.summary.remoteId}.png")
            .also { CatalogDebugLog.event(ID, "Card downloaded: yes") }
    }

    override suspend fun downloadAvatar(detail: RemoteCharacterDetail): RemoteAsset? {
        val url = detail.summary.avatarUrl ?: return null
        return http.get(url, setOf(assetHost), SecureCatalogHttpClient.MAX_IMAGE_BYTES, AssetKind.IMAGE, "${detail.summary.remoteId}-avatar")
            .also { CatalogDebugLog.event(ID, "Avatar downloaded: yes") }
    }

    private fun AiccListResponse.toPage(skip: Int, limit: Int, language: String?): CatalogPage<RemoteCharacterSummary> {
        val mapped = data.map(::mapSummary).filter { CatalogLanguageMatcher.matches(language, it.language) }
        val totalCount = pagination?.total
        val consumed = skip + mapped.size
        val next = consumed.takeIf { mapped.size == limit && (totalCount == null || it < totalCount) }?.toString()
        return CatalogPage(mapped, next, totalCount)
    }

    internal fun mapSummary(dto: AiccCardDto) = RemoteCharacterSummary(
        providerId = ID,
        remoteId = dto.id.toString(),
        name = dto.title.take(120),
        description = HtmlPlainText.convert(dto.excerpt.ifBlank { dto.description }).take(2_000),
        avatarUrl = dto.imageUrl.takeIf(String::isNotBlank)?.let { runCatching { resolveAssetUrl(it) }.getOrNull() },
        author = dto.author.takeIf(String::isNotBlank)?.take(120),
        tags = dto.tags.map { it.name }.filter(String::isNotBlank).take(32),
        language = dto.language.takeIf(String::isNotBlank),
        isNsfw = dto.isNsfw,
        downloadCount = dto.downloadCount,
        updatedAt = parseTimestamp(dto.updatedAt ?: dto.createdAt),
        sourceUrl = "$siteOrigin/cards/${dto.id}",
    )

    private fun resolveAssetUrl(path: String): String {
        val resolved = if (path.startsWith("https://")) path else {
            assetOrigin.toHttpUrl().resolve(path)?.toString() ?: throw CatalogNetworkException("Ruta de imagen remota no válida.")
        }
        return RemoteUrlPolicy.validate(resolved, setOf(assetHost)).toString()
    }

    private fun parseTimestamp(raw: String?): Long? = raw?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

    companion object {
        const val ID = "ai_character_cards"
        const val API_BASE = "https://api.aicharactercards.com/api"
        const val ASSET_ORIGIN = "https://api.aicharactercards.com"
        const val SITE_ORIGIN = "https://aicharactercards.com"
    }
}

internal object HtmlPlainText {
    fun convert(value: String): String = value
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("</p\\s*>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace(Regex("[ \\t]+"), " ")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
}

@Serializable
internal data class AiccListResponse(val data: List<AiccCardDto> = emptyList(), val pagination: AiccPagination? = null)

@Serializable
internal data class AiccTrendingResponse(val data: List<AiccCardDto> = emptyList(), val total: Int? = null)

@Serializable
internal data class AiccPagination(val skip: Int = 0, val limit: Int = 0, val total: Int? = null)

@Serializable
internal data class AiccTagDto(val id: Int = 0, val name: String = "")

@Serializable
internal data class AiccVersionDto(
    val id: Int = 0,
    val version: Int = 0,
    val isCurrent: Boolean = false,
    val fileUrl: String = "",
    val fileName: String = "",
)

@Serializable
internal data class AiccFieldsResponse(val fields: AiccFieldsDto = AiccFieldsDto())

@Serializable
internal data class AiccFieldsDto(
    @kotlinx.serialization.SerialName("first_mes") val firstMes: String = "",
    val personality: String = "",
    val scenario: String = "",
    @kotlinx.serialization.SerialName("mes_example") val exampleMessages: String = "",
    @kotlinx.serialization.SerialName("system_prompt") val systemPrompt: String = "",
    @kotlinx.serialization.SerialName("alternate_greetings") val alternateGreetings: List<String> = emptyList(),
)

@Serializable
internal data class AiccCardDto(
    val id: Int,
    val title: String = "",
    val description: String = "",
    val excerpt: String = "",
    val imageUrl: String = "",
    val language: String = "",
    val author: String = "",
    val isNsfw: Boolean = false,
    val downloadCount: Int? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val tags: List<AiccTagDto> = emptyList(),
    val versions: List<AiccVersionDto> = emptyList(),
)
