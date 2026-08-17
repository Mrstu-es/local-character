package com.localcharacter.app.data.voice

import com.localcharacter.app.data.catalog.AssetKind
import com.localcharacter.app.data.catalog.SecureCatalogHttpClient
import com.localcharacter.app.domain.model.VoiceRepository
import okhttp3.HttpUrl.Companion.toHttpUrl

data class SyncedVoiceRepository(
    val repository: VoiceRepository,
    val voices: List<RemoteVoice>,
    val description: String,
)

class VoiceCatalogClient(private val http: SecureCatalogHttpClient = SecureCatalogHttpClient()) {
    suspend fun fetch(manifestUrl: String, existing: VoiceRepository? = null): SyncedVoiceRepository {
        val host = manifestUrl.toHttpUrl().host
        val manifestAsset = http.get(
            manifestUrl, setOf(host), VoiceRepositoryParser.MAX_INDEX_BYTES, AssetKind.JSON, "voice-repository.json",
        )
        val parsed = VoiceRepositoryParser.parseManifest(manifestAsset.bytes, manifestUrl, existing?.id)
        val indexAsset = http.get(
            parsed.indexUrl, parsed.allowedHosts, VoiceRepositoryParser.MAX_INDEX_BYTES, AssetKind.JSON, "voices.json",
        )
        val voices = VoiceRepositoryParser.parseIndex(
            indexAsset.bytes, parsed.repository.id, parsed.indexUrl, parsed.allowedHosts,
        )
        val now = System.currentTimeMillis()
        return SyncedVoiceRepository(
            parsed.repository.copy(
                enabled = existing?.enabled ?: true,
                lastSyncAt = now,
                lastSuccessfulSyncAt = now,
                etag = existing?.etag,
            ),
            voices,
            parsed.description,
        )
    }
}
