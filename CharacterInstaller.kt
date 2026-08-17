package com.localcharacter.app.data.catalog

import androidx.room.withTransaction
import com.localcharacter.app.data.charactercard.CharacterCardParser
import com.localcharacter.app.data.database.AppDatabase
import com.localcharacter.app.data.database.toDomain
import com.localcharacter.app.data.database.toEntity
import com.localcharacter.app.domain.character.CatalogIdentity
import com.localcharacter.app.domain.character.CharacterCatalogProvider
import com.localcharacter.app.domain.character.RemoteCharacterDetail
import com.localcharacter.app.domain.model.AvatarState
import com.localcharacter.app.domain.model.Character
import com.localcharacter.app.domain.model.CharacterSource
import com.localcharacter.app.domain.model.LoreEntry
import java.io.File
import java.security.MessageDigest
import java.util.UUID

enum class InstallOutcome { INSTALLED, ALREADY_INSTALLED }

data class CharacterInstallResult(
    val outcome: InstallOutcome,
    val characterId: String,
    val avatarWarning: String? = null,
)

interface CharacterInstallStore {
    suspend fun find(providerId: String, remoteId: String): CharacterSource?
    suspend fun install(
        character: Character,
        lore: List<LoreEntry>,
        source: CharacterSource,
        finalizeFiles: () -> Unit,
    )
}

class RoomCharacterInstallStore(private val database: AppDatabase) : CharacterInstallStore {
    override suspend fun find(providerId: String, remoteId: String): CharacterSource? =
        database.characterSourceDao().find(providerId, remoteId)?.toDomain()

    override suspend fun install(
        character: Character,
        lore: List<LoreEntry>,
        source: CharacterSource,
        finalizeFiles: () -> Unit,
    ) = database.withTransaction {
        database.characterDao().upsert(character.toEntity())
        database.loreDao().deleteForCharacter(character.id)
        database.loreDao().upsertAll(lore.map { it.toEntity() })
        database.characterSourceDao().upsert(source.toEntity())
        // The directory rename is inside the DB transaction. A failed rename rolls Room back.
        finalizeFiles()
    }
}

class CharacterInstaller(
    private val charactersRoot: File,
    private val parser: CharacterCardParser,
    private val store: CharacterInstallStore,
    private val now: () -> Long = System::currentTimeMillis,
) {
    fun removeLocalFiles(source: CharacterSource) {
        val root = charactersRoot.canonicalFile
        val directory = File(source.originalCardPath).canonicalFile.parentFile ?: return
        if (directory.parentFile == root && directory.isDirectory) directory.deleteRecursively()
    }

    suspend fun install(provider: CharacterCatalogProvider, detail: RemoteCharacterDetail): CharacterInstallResult {
        val providerId = provider.descriptor.id
        val remoteId = detail.summary.remoteId
        store.find(providerId, remoteId)?.let {
            return CharacterInstallResult(InstallOutcome.ALREADY_INSTALLED, it.characterId)
        }

        val localId = CatalogIdentity.localCharacterId(providerId, remoteId)
        val card = provider.downloadCard(detail)
        DownloadedAssetValidator.validate(card, AssetKind.CARD)
        val parsed = parser.parse(card.bytes, card.fileName)
        CatalogDebugLog.event(providerId, "Card parsed: yes; lore entries: ${parsed.lore.size}")
        val cardHash = card.bytes.sha256()
        val avatarAttempt = runCatching { provider.downloadAvatar(detail) }
        val avatar = avatarAttempt.getOrNull()
        avatar?.let { DownloadedAssetValidator.validate(it, AssetKind.IMAGE) }

        charactersRoot.mkdirs()
        check(charactersRoot.isDirectory) { "No se pudo preparar el almacenamiento local de personajes." }
        val tempDir = File(charactersRoot, ".install-${UUID.randomUUID()}")
        check(tempDir.mkdir()) { "No se pudo crear el directorio temporal de instalación." }
        var finalDir: File? = null
        try {
            val cardExtension = DownloadedAssetValidator.extension(card)
            val tempCard = File(tempDir, "original_card.$cardExtension")
            tempCard.writeBytes(card.bytes)
            val avatarExtension = avatar?.let(DownloadedAssetValidator::extension)
            val tempAvatar = avatarExtension?.let { File(tempDir, "avatar.$it").also { file -> file.writeBytes(requireNotNull(avatar).bytes) } }

            val destination = File(charactersRoot, "$localId-${cardHash.take(12)}-${UUID.randomUUID().toString().take(8)}")
            finalDir = destination
            val finalCard = File(destination, tempCard.name)
            val finalAvatar = tempAvatar?.let { File(destination, it.name) }
            val avatarFile = finalAvatar ?: finalCard.takeIf { cardExtension == "png" }
            val timestamp = now()
            val character = parsed.character.copy(
                id = localId,
                avatarUri = avatarFile?.toURI()?.toString(),
                createdAt = timestamp,
                updatedAt = timestamp,
            )
            val lore = parsed.lore.mapIndexed { index, item ->
                item.copy(
                    id = CatalogIdentity.localCharacterId("$providerId-lore-$remoteId", index.toString()),
                    characterId = localId,
                )
            }
            val source = CharacterSource(
                characterId = localId,
                providerId = providerId,
                remoteId = remoteId,
                sourceUrl = detail.summary.sourceUrl,
                author = detail.summary.author,
                version = detail.version,
                sourceUpdatedAt = detail.summary.updatedAt,
                contentHash = cardHash,
                avatarHash = avatar?.bytes?.sha256(),
                downloadedAt = timestamp,
                originalCardPath = finalCard.absolutePath,
                localAvatarPath = avatarFile?.absolutePath,
                avatarState = when {
                    finalAvatar != null -> AvatarState.LOCAL
                    avatarFile != null -> AvatarState.CARD_FALLBACK
                    else -> AvatarState.MISSING
                },
            )
            store.install(character, lore, source) {
                check(tempDir.renameTo(destination)) { "No se pudo finalizar la copia local del personaje." }
            }
            CatalogDebugLog.event(providerId, "Character installed: yes")
            return CharacterInstallResult(
                InstallOutcome.INSTALLED,
                localId,
                avatarWarning = avatarAttempt.exceptionOrNull()?.message,
            )
        } catch (error: Throwable) {
            finalDir?.takeIf(File::exists)?.deleteRecursively()
            throw error
        } finally {
            tempDir.takeIf(File::exists)?.deleteRecursively()
        }
    }
}

private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(this)
    .joinToString("") { "%02x".format(it) }
