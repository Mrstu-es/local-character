package com.localcharacter.app.data.catalog

import com.localcharacter.app.domain.character.RemoteAsset
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

class CatalogNetworkException(message: String) : IOException(message)

enum class AssetKind { JSON, CARD, IMAGE }

object RemoteUrlPolicy {
    fun validate(rawUrl: String, allowedHosts: Set<String>): HttpUrl {
        val url = rawUrl.toHttpUrlOrNull() ?: throw CatalogNetworkException("URL remota no válida.")
        if (url.scheme != "https") throw CatalogNetworkException("Solo se permiten conexiones HTTPS.")
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) throw CatalogNetworkException("La URL no puede contener credenciales.")
        if (url.port != 443) throw CatalogNetworkException("El puerto remoto no está permitido.")
        val normalized = allowedHosts.map { it.lowercase() }.toSet()
        if (url.host.lowercase() !in normalized) throw CatalogNetworkException("El host remoto no está autorizado para este proveedor.")
        return url
    }
}

object DownloadedAssetValidator {
    fun validate(asset: RemoteAsset, kind: AssetKind) {
        if (asset.bytes.isEmpty()) throw CatalogNetworkException("El servidor devolvió un archivo vacío.")
        val type = asset.contentType.orEmpty().substringBefore(';').trim().lowercase()
        when (kind) {
            AssetKind.JSON -> if (type.isNotBlank() && type != "application/json" && !type.endsWith("+json")) {
                throw CatalogNetworkException("El servidor no devolvió JSON.")
            }
            AssetKind.IMAGE -> if (!asset.bytes.isRecognizedImage() || (type.isNotBlank() && !type.startsWith("image/"))) {
                throw CatalogNetworkException("El avatar descargado no es una imagen válida.")
            }
            AssetKind.CARD -> {
                val json = asset.bytes.firstOrNull()?.toInt()?.toChar() in listOf('{', '[')
                if (!asset.bytes.isPng() && !json) throw CatalogNetworkException("La tarjeta descargada no es PNG ni JSON.")
                if (type.isNotBlank() && type !in setOf("image/png", "application/json", "application/octet-stream")) {
                    throw CatalogNetworkException("Tipo de tarjeta no permitido: $type")
                }
            }
        }
    }

    fun extension(asset: RemoteAsset): String = when {
        asset.bytes.isPng() -> "png"
        asset.bytes.isJpeg() -> "jpg"
        asset.bytes.isWebp() -> "webp"
        else -> "json"
    }

    private fun ByteArray.isRecognizedImage() = isPng() || isJpeg() || isWebp()
    private fun ByteArray.isPng() = size >= 8 && copyOfRange(0, 8).contentEquals(byteArrayOf(0x89.toByte(), 80, 78, 71, 13, 10, 26, 10))
    private fun ByteArray.isJpeg() = size >= 3 && this[0] == 0xff.toByte() && this[1] == 0xd8.toByte() && this[2] == 0xff.toByte()
    private fun ByteArray.isWebp() = size >= 12 && copyOfRange(0, 4).decodeToString() == "RIFF" && copyOfRange(8, 12).decodeToString() == "WEBP"
}

class SecureCatalogHttpClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build(),
) {
    suspend fun get(
        rawUrl: String,
        allowedHosts: Set<String>,
        maxBytes: Int,
        kind: AssetKind,
        fileName: String,
    ): RemoteAsset = withContext(Dispatchers.IO) {
        var url = RemoteUrlPolicy.validate(rawUrl, allowedHosts)
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            val request = Request.Builder()
                .url(url)
                .header("Accept", when (kind) {
                    AssetKind.JSON -> "application/json"
                    AssetKind.IMAGE -> "image/png,image/jpeg,image/webp"
                    AssetKind.CARD -> "image/png,application/json,application/octet-stream"
                })
                .header("User-Agent", "Nuria-Android/0.3")
                .get()
                .build()
            val response = client.newCall(request).execute()
            response.use {
                if (it.code in 300..399) {
                    if (redirectCount >= MAX_REDIRECTS) throw CatalogNetworkException("Demasiadas redirecciones.")
                    val location = it.header("Location") ?: throw CatalogNetworkException("Redirección sin destino.")
                    url = RemoteUrlPolicy.validate(url.resolve(location)?.toString().orEmpty(), allowedHosts)
                    return@repeat
                }
                if (!it.isSuccessful) throw CatalogNetworkException("El proveedor respondió HTTP ${it.code}.")
                val body = it.body ?: throw CatalogNetworkException("Respuesta remota vacía.")
                val declared = body.contentLength()
                if (declared > maxBytes) throw CatalogNetworkException("La descarga supera el límite de tamaño.")
                val bytes = body.byteStream().use { input -> input.readAtMost(maxBytes) }
                val asset = RemoteAsset(bytes, body.contentType()?.toString(), fileName)
                DownloadedAssetValidator.validate(asset, kind)
                return@withContext asset
            }
        }
        throw CatalogNetworkException("No se pudo completar la descarga.")
    }

    private fun java.io.InputStream.readAtMost(limit: Int): ByteArray {
        val output = ByteArrayOutputStream(minOf(limit, 64 * 1024))
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) throw CatalogNetworkException("La descarga supera el límite de tamaño.")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    companion object {
        const val MAX_JSON_BYTES = 2 * 1024 * 1024
        const val MAX_CARD_BYTES = 20 * 1024 * 1024
        const val MAX_IMAGE_BYTES = 12 * 1024 * 1024
        private const val MAX_REDIRECTS = 3
    }
}
