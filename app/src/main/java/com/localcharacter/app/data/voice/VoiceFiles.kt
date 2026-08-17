package com.localcharacter.app.data.voice

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class InstalledVoiceFile(
    val role: String,
    val relativePath: String,
    val localPath: String,
    val sizeBytes: Long,
    val sha256: String,
)

object VoiceFilesCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    fun decode(value: String): List<InstalledVoiceFile> =
        runCatching { json.decodeFromString<List<InstalledVoiceFile>>(value) }.getOrDefault(emptyList())
    fun encode(files: List<InstalledVoiceFile>): String = json.encodeToString(files)
}
