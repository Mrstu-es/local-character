package com.localcharacter.app.data.character

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.URI
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Stores only the image explicitly selected by the user in app-private storage. */
class CharacterAvatarStore(context: Context) {
    private val applicationContext = context.applicationContext
    private val root = File(applicationContext.filesDir, "character_avatars")

    suspend fun import(characterId: String, source: Uri): String = withContext(Dispatchers.IO) {
        val bytes = applicationContext.contentResolver.openInputStream(source)?.use {
            it.readAtMost(MAX_IMAGE_BYTES + 1)
        } ?: error("Android no pudo abrir la foto seleccionada.")
        require(bytes.size <= MAX_IMAGE_BYTES) { "La foto supera el límite de 20 MB." }

        val format = AvatarImageHeader.detect(bytes)
            ?: error("El archivo seleccionado no es una imagen compatible.")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) {
            "Android no pudo leer la foto seleccionada."
        }
        require(bounds.outWidth <= MAX_DIMENSION && bounds.outHeight <= MAX_DIMENSION) {
            "La resolución de la foto es demasiado grande."
        }

        val directory = File(root, characterId.sha256()).also { directory ->
            check(directory.exists() || directory.mkdirs()) { "No se pudo preparar el avatar privado." }
        }
        val finalFile = File(directory, "avatar-${System.currentTimeMillis()}-${UUID.randomUUID()}.${format.extension}")
        val temporary = File(directory, ".${finalFile.name}.tmp")
        try {
            temporary.outputStream().use { it.write(bytes) }
            check(temporary.renameTo(finalFile)) { "No se pudo guardar la foto del personaje." }
        } finally {
            if (temporary.exists()) temporary.delete()
        }
        finalFile.toURI().toString()
    }

    suspend fun deleteManaged(avatarUri: String?) = withContext(Dispatchers.IO) {
        val file = avatarUri?.toManagedFileOrNull() ?: return@withContext
        if (file.isFile) file.delete()
        file.parentFile?.takeIf { parent -> parent != root && parent.listFiles()?.isEmpty() == true }?.delete()
    }

    private fun String.toManagedFileOrNull(): File? = runCatching {
        val parsed = URI(this)
        if (parsed.scheme != "file") return@runCatching null
        val candidate = File(parsed).canonicalFile
        val canonicalRoot = root.canonicalFile
        candidate.takeIf { it.path.startsWith(canonicalRoot.path + File.separator) }
    }.getOrNull()

    private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray())
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val MAX_IMAGE_BYTES = 20 * 1024 * 1024
        const val MAX_DIMENSION = 16_384
    }
}

internal enum class AvatarImageFormat(val extension: String) { JPEG("jpg"), PNG("png"), WEBP("webp"), GIF("gif"), HEIF("heic"), AVIF("avif") }

internal object AvatarImageHeader {
    fun detect(bytes: ByteArray): AvatarImageFormat? = when {
        bytes.startsWith(0xFF, 0xD8, 0xFF) -> AvatarImageFormat.JPEG
        bytes.startsWith(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> AvatarImageFormat.PNG
        bytes.ascii(0, 4) == "RIFF" && bytes.ascii(8, 4) == "WEBP" -> AvatarImageFormat.WEBP
        bytes.ascii(0, 6) in setOf("GIF87a", "GIF89a") -> AvatarImageFormat.GIF
        bytes.ascii(4, 4) == "ftyp" && bytes.ascii(8, 4) in HEIF_BRANDS -> {
            if (bytes.ascii(8, 4) in AVIF_BRANDS) AvatarImageFormat.AVIF else AvatarImageFormat.HEIF
        }
        else -> null
    }

    private fun ByteArray.startsWith(vararg expected: Int): Boolean =
        size >= expected.size && expected.indices.all { this[it].toInt() and 0xFF == expected[it] }

    private fun ByteArray.ascii(offset: Int, length: Int): String? =
        takeIf { size >= offset + length }?.let { String(it, offset, length, Charsets.US_ASCII) }

    private val AVIF_BRANDS = setOf("avif", "avis")
    private val HEIF_BRANDS = setOf("heic", "heix", "hevc", "hevx", "mif1", "msf1") + AVIF_BRANDS
}

private fun InputStream.readAtMost(limit: Int): ByteArray {
    val output = ByteArrayOutputStream(minOf(limit, 64 * 1024))
    val buffer = ByteArray(16 * 1024)
    var total = 0
    while (total < limit) {
        val read = read(buffer, 0, minOf(buffer.size, limit - total))
        if (read < 0) break
        output.write(buffer, 0, read)
        total += read
    }
    return output.toByteArray()
}
