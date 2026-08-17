package com.localcharacter.app.data.voice

import androidx.room.withTransaction
import com.localcharacter.app.data.catalog.CatalogNetworkException
import com.localcharacter.app.data.catalog.RemoteUrlPolicy
import com.localcharacter.app.data.database.AppDatabase
import com.localcharacter.app.data.database.toDomain
import com.localcharacter.app.data.database.toEntity
import com.localcharacter.app.domain.model.VoiceModel
import java.io.File
import java.io.FileOutputStream
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

interface VoiceFileDownloadClient {
    suspend fun download(file: RemoteVoiceFile, allowedHosts: Set<String>, destination: File)
}

class SecureVoiceFileDownloadClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.MINUTES)
        .followRedirects(false)
        .followSslRedirects(false)
        .build(),
) : VoiceFileDownloadClient {
    override suspend fun download(file: RemoteVoiceFile, allowedHosts: Set<String>, destination: File) =
        withContext(Dispatchers.IO) {
            val url = RemoteUrlPolicy.validate(file.url, allowedHosts)
            val response = client.newCall(
                Request.Builder().url(url).header("User-Agent", "Nuria-Android/0.6").get().build(),
            ).execute()
            response.use {
                if (it.code in 300..399) throw CatalogNetworkException("Las descargas de voz no aceptan redirecciones.")
                if (!it.isSuccessful) throw CatalogNetworkException("La descarga de voz respondió HTTP ${it.code}.")
                val body = it.body ?: throw CatalogNetworkException("El servidor devolvió un archivo vacío.")
                val declared = body.contentLength()
                if (declared >= 0 && declared != file.sizeBytes) {
                    throw CatalogNetworkException("El tamaño remoto de ${file.relativePath} no coincide con el manifiesto.")
                }
                check(destination.parentFile?.mkdirs() != false) { "No se pudo preparar la ruta de la voz." }
                val digest = MessageDigest.getInstance("SHA-256")
                var total = 0L
                body.byteStream().use { input ->
                    DigestOutputStream(FileOutputStream(destination), digest).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > file.sizeBytes) throw CatalogNetworkException("La descarga supera el tamaño declarado.")
                            output.write(buffer, 0, read)
                        }
                    }
                }
                if (total != file.sizeBytes) throw CatalogNetworkException("La descarga quedó incompleta.")
                val actualHash = digest.digest().toHex()
                if (actualHash != file.sha256) throw CatalogNetworkException("SHA-256 incorrecto para ${file.relativePath}.")
            }
        }
}

interface VoiceInstallStore {
    suspend fun find(repositoryId: String, remoteId: String): VoiceModel?
    suspend fun install(voice: VoiceModel, finalizeFiles: () -> Unit)
}

class RoomVoiceInstallStore(private val database: AppDatabase) : VoiceInstallStore {
    override suspend fun find(repositoryId: String, remoteId: String): VoiceModel? =
        database.voiceDao().find(repositoryId, remoteId)?.toDomain()

    override suspend fun install(voice: VoiceModel, finalizeFiles: () -> Unit) = database.withTransaction {
        database.voiceDao().upsert(voice.toEntity())
        finalizeFiles()
    }
}

enum class VoiceInstallOutcome { INSTALLED, ALREADY_INSTALLED }
data class VoiceInstallResult(val outcome: VoiceInstallOutcome, val voiceId: String)

class VoiceInstaller(
    private val voicesRoot: File,
    private val store: VoiceInstallStore,
    private val downloads: VoiceFileDownloadClient = SecureVoiceFileDownloadClient(),
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun install(remote: RemoteVoice, allowedHosts: Set<String>): VoiceInstallResult {
        store.find(remote.repositoryId, remote.remoteId)?.let {
            return VoiceInstallResult(VoiceInstallOutcome.ALREADY_INSTALLED, it.id)
        }
        voicesRoot.mkdirs()
        check(voicesRoot.isDirectory) { "No se pudo preparar el almacenamiento de voces." }
        val tempDir = File(voicesRoot, ".install-${UUID.randomUUID()}")
        check(tempDir.mkdir()) { "No se pudo crear la instalación temporal de voz." }
        var finalDir: File? = null
        try {
            for (file in remote.files) {
                check(VoiceRepositoryParser.isSafeRelativePath(file.relativePath)) { "Ruta de archivo no segura." }
                downloads.download(file, allowedHosts, File(tempDir, file.relativePath))
            }
            val combinedHash = MessageDigest.getInstance("SHA-256")
                .digest(remote.files.sortedBy { it.relativePath }.joinToString("|") { it.sha256 }.encodeToByteArray())
                .toHex()
            val localId = UUID.nameUUIDFromBytes(
                "voice|${remote.repositoryId}|${remote.remoteId}".encodeToByteArray(),
            ).toString()
            val destination = File(
                voicesRoot,
                "${localId.take(12)}-${remote.version.sanitizePathPart()}-${combinedHash.take(12)}",
            )
            finalDir = destination
            val installedFiles = remote.files.map { file ->
                InstalledVoiceFile(
                    role = file.role,
                    relativePath = file.relativePath,
                    localPath = File(destination, file.relativePath).absolutePath,
                    sizeBytes = file.sizeBytes,
                    sha256 = file.sha256,
                )
            }
            val timestamp = now()
            val modelPath = installedFiles.firstOrNull { it.role == "model" }?.localPath
            val configPath = installedFiles.firstOrNull { it.role == "config" }?.localPath
            val voice = VoiceModel(
                id = localId,
                name = remote.name,
                engine = remote.engine,
                language = remote.language,
                localModelPath = modelPath,
                localConfigPath = configPath,
                filesJson = VoiceFilesCodec.encode(installedFiles),
                sampleUrl = remote.sampleUrl,
                repositoryId = remote.repositoryId,
                remoteId = remote.remoteId,
                version = remote.version,
                license = remote.license,
                author = remote.author,
                source = remote.source,
                consentMetadata = remote.consent?.let { Json.encodeToString(it) },
                contentHash = combinedHash,
                sizeBytes = remote.sizeBytes,
                installedAt = timestamp,
                updatedAt = timestamp,
            )
            store.install(voice) {
                check(tempDir.renameTo(destination)) { "No se pudo finalizar la instalación de voz." }
            }
            return VoiceInstallResult(VoiceInstallOutcome.INSTALLED, voice.id)
        } catch (error: Throwable) {
            finalDir?.takeIf(File::exists)?.deleteRecursively()
            throw error
        } finally {
            tempDir.takeIf(File::exists)?.deleteRecursively()
        }
    }

    fun removeLocalFiles(voice: VoiceModel) {
        val root = voicesRoot.canonicalFile
        val directory = voice.localModelPath?.let(::File)?.canonicalFile?.parentFile ?: return
        val installation = generateSequence(directory) { it.parentFile }.firstOrNull { it.parentFile == root } ?: return
        if (installation.isDirectory) installation.deleteRecursively()
    }

    private fun String.sanitizePathPart(): String = replace(Regex("[^A-Za-z0-9._-]"), "_").take(40).ifBlank { "v1" }
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
