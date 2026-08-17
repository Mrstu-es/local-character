package com.localcharacter.app.data.voice

import com.localcharacter.app.data.catalog.RemoteUrlPolicy
import com.localcharacter.app.domain.model.VoiceEngineType
import com.localcharacter.app.domain.model.VoiceRepository
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Serializable
data class VoiceRepositoryManifest(
    val schema: String,
    val version: Int,
    val name: String,
    val description: String = "",
    val voicesIndex: String,
)

@Serializable
data class VoiceIndexDocument(val version: Int, val voices: List<RemoteVoiceDocument>)

@Serializable
data class RemoteVoiceDocument(
    val id: String,
    val name: String,
    val language: String,
    val engine: String,
    val files: List<RemoteVoiceFileDocument>,
    val sampleUrl: String? = null,
    val sizeBytes: Long,
    val license: String,
    val author: String,
    val creator: String = author,
    val source: String,
    val version: String,
    val consent: VoiceConsentDocument? = null,
)

@Serializable
data class RemoteVoiceFileDocument(
    val role: String,
    val url: String,
    val relativePath: String,
    val sizeBytes: Long,
    val sha256: String,
)

@Serializable
data class VoiceConsentDocument(
    val realPersonVoice: Boolean = false,
    val confirmed: Boolean = false,
    val evidence: String? = null,
)

data class RemoteVoiceFile(
    val role: String,
    val url: String,
    val relativePath: String,
    val sizeBytes: Long,
    val sha256: String,
)

data class RemoteVoice(
    val repositoryId: String,
    val remoteId: String,
    val name: String,
    val language: String,
    val engine: VoiceEngineType,
    val files: List<RemoteVoiceFile>,
    val sampleUrl: String?,
    val sizeBytes: Long,
    val license: String,
    val author: String,
    val creator: String,
    val source: String,
    val version: String,
    val consent: VoiceConsentDocument?,
)

data class ParsedVoiceManifest(
    val repository: VoiceRepository,
    val description: String,
    val indexUrl: String,
    val allowedHosts: Set<String>,
)

object VoiceRepositoryParser {
    private val json = Json { ignoreUnknownKeys = false }
    private val hashPattern = Regex("^[0-9a-fA-F]{64}$")
    private val idPattern = Regex("^[A-Za-z0-9._-]{1,120}$")
    private val languagePattern = Regex("^[A-Za-z]{2,3}(?:[-_][A-Za-z0-9]{2,8})*$")
    private val allowedRoles = setOf("model", "tokens", "voices", "config", "lexicon", "data", "rule_fst", "rule_far")
    private val forbiddenExtensions = setOf("apk", "dex", "jar", "class", "so", "dll", "exe", "bat", "cmd", "ps1", "sh", "js")

    fun parseManifest(bytes: ByteArray, manifestUrl: String, existingId: String? = null): ParsedVoiceManifest {
        require(bytes.size <= MAX_INDEX_BYTES) { "voice-repository.json supera el límite permitido." }
        val manifest = json.decodeFromString<VoiceRepositoryManifest>(bytes.decodeToString())
        require(manifest.schema == SCHEMA) { "Esquema de repositorio de voces no compatible." }
        require(manifest.version == 1) { "Versión de repositorio de voces no compatible: ${manifest.version}." }
        require(manifest.name.trim().length in 1..120) { "El repositorio necesita un nombre válido." }
        val base = manifestUrl.toHttpUrl()
        require(base.isHttps && base.username.isEmpty() && base.password.isEmpty() && base.port == 443) {
            "El repositorio de voces debe usar HTTPS sin credenciales ni puertos personalizados."
        }
        val allowed = setOf(base.host)
        val resolvedIndex = base.resolve(manifest.voicesIndex)
            ?: error("voicesIndex no es una ruta válida.")
        RemoteUrlPolicy.validate(resolvedIndex.toString(), allowed)
        return ParsedVoiceManifest(
            repository = VoiceRepository(
                id = existingId ?: UUID.randomUUID().toString(),
                name = manifest.name.trim(),
                manifestUrl = base.toString(),
                schemaVersion = manifest.version,
            ),
            description = manifest.description.trim().take(1_000),
            indexUrl = resolvedIndex.toString(),
            allowedHosts = allowed,
        )
    }

    fun parseIndex(bytes: ByteArray, repositoryId: String, indexUrl: String, allowedHosts: Set<String>): List<RemoteVoice> {
        require(bytes.size <= MAX_INDEX_BYTES) { "voices.json supera el límite permitido." }
        val document = json.decodeFromString<VoiceIndexDocument>(bytes.decodeToString())
        require(document.version == 1) { "Versión de voices.json no compatible: ${document.version}." }
        require(document.voices.size <= MAX_VOICES) { "El índice contiene demasiadas voces." }
        val base = RemoteUrlPolicy.validate(indexUrl, allowedHosts)
        val seen = mutableSetOf<String>()
        return document.voices.map { item ->
            require(idPattern.matches(item.id) && seen.add(item.id)) { "ID de voz inválido o duplicado: ${item.id}." }
            require(item.name.trim().length in 1..120) { "La voz ${item.id} necesita un nombre válido." }
            require(languagePattern.matches(item.language)) { "Idioma no válido para ${item.id}." }
            val engine = parseEngine(item.engine)
            require(item.files.isNotEmpty() && item.files.size <= MAX_FILES_PER_VOICE) { "Lista de archivos no válida para ${item.id}." }
            require(item.license.isNotBlank() && item.author.isNotBlank() && item.source.isNotBlank() && item.version.isNotBlank()) {
                "La voz ${item.id} debe declarar licencia, autor, fuente y versión."
            }
            item.consent?.takeIf { it.realPersonVoice }?.let {
                require(it.confirmed && !it.evidence.isNullOrBlank()) {
                    "La voz ${item.id} imita a una persona real sin consentimiento verificable."
                }
            }
            val files = item.files.map { file -> validateFile(file, base, allowedHosts) }
            validateRequiredFiles(item.id, engine, files)
            val calculatedSize = files.sumOf { it.sizeBytes }
            require(calculatedSize in 1..MAX_TOTAL_VOICE_BYTES && item.sizeBytes == calculatedSize) {
                "El tamaño declarado de ${item.id} no coincide con sus archivos."
            }
            val sample = item.sampleUrl?.takeIf(String::isNotBlank)?.let { raw ->
                val resolved = base.resolve(raw) ?: error("sampleUrl no válida para ${item.id}.")
                RemoteUrlPolicy.validate(resolved.toString(), allowedHosts).toString()
            }
            RemoteVoice(
                repositoryId, item.id, item.name.trim(), item.language.replace('_', '-'), engine, files,
                sample, item.sizeBytes, item.license.trim(), item.author.trim(), item.creator.trim(),
                item.source.trim(), item.version.trim(), item.consent,
            )
        }
    }

    private fun validateFile(file: RemoteVoiceFileDocument, base: HttpUrl, allowedHosts: Set<String>): RemoteVoiceFile {
        val role = file.role.lowercase()
        require(role in allowedRoles) { "Rol de archivo de voz no permitido: ${file.role}." }
        require(isSafeRelativePath(file.relativePath)) { "Ruta de voz no segura: ${file.relativePath}." }
        val extension = file.relativePath.substringAfterLast('.', "").lowercase()
        require(extension !in forbiddenExtensions) { "Formato de archivo no permitido: .$extension." }
        when (role) {
            "model" -> require(extension == "onnx") { "El modelo de voz debe ser ONNX." }
            "tokens", "lexicon" -> require(extension == "txt") { "$role debe usar .txt." }
            "voices" -> require(extension == "bin") { "voices debe usar .bin." }
            "config" -> require(extension == "json") { "config debe usar .json." }
        }
        require(file.sizeBytes in 1..MAX_FILE_BYTES) { "Tamaño de archivo fuera del límite." }
        require(hashPattern.matches(file.sha256)) { "Cada archivo debe declarar SHA-256 hexadecimal." }
        val resolved = base.resolve(file.url) ?: error("URL de archivo no válida.")
        val validated = RemoteUrlPolicy.validate(resolved.toString(), allowedHosts)
        return RemoteVoiceFile(role, validated.toString(), file.relativePath, file.sizeBytes, file.sha256.lowercase())
    }

    private fun validateRequiredFiles(id: String, engine: VoiceEngineType, files: List<RemoteVoiceFile>) {
        val roles = files.map { it.role }.toSet()
        require("model" in roles && "tokens" in roles) { "La voz $id necesita model y tokens." }
        if (engine == VoiceEngineType.KOKORO) require("voices" in roles) { "La voz Kokoro $id necesita voices.bin." }
    }

    private fun parseEngine(value: String): VoiceEngineType = when (value.trim().lowercase()) {
        "kokoro" -> VoiceEngineType.KOKORO
        "piper" -> VoiceEngineType.PIPER
        "vits" -> VoiceEngineType.VITS
        else -> error("Motor TTS no compatible: $value.")
    }

    internal fun isSafeRelativePath(path: String): Boolean {
        if (path.isBlank() || path.length > 240 || '\\' in path || ':' in path || path.startsWith('/')) return false
        val parts = path.split('/')
        return parts.all { it.isNotBlank() && it != "." && it != ".." }
    }

    const val SCHEMA = "localcharacter.voice.repository"
    const val MAX_INDEX_BYTES = 4 * 1024 * 1024
    const val MAX_VOICES = 2_000
    const val MAX_FILES_PER_VOICE = 2_000
    const val MAX_FILE_BYTES = 1_500_000_000L
    const val MAX_TOTAL_VOICE_BYTES = 2_000_000_000L
}
