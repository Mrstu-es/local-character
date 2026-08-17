package com.localcharacter.app.data.catalog

import com.localcharacter.app.data.charactercard.CharacterCardParser
import com.localcharacter.app.domain.character.CatalogPage
import com.localcharacter.app.domain.character.CatalogRequest
import com.localcharacter.app.domain.character.CharacterCatalogProvider
import com.localcharacter.app.domain.character.ProviderAvailability
import com.localcharacter.app.domain.character.ProviderCapabilities
import com.localcharacter.app.domain.character.ProviderDescriptor
import com.localcharacter.app.domain.character.ProviderHealth
import com.localcharacter.app.domain.character.RemoteAsset
import com.localcharacter.app.domain.character.RemoteCharacterDetail
import com.localcharacter.app.domain.character.RemoteCharacterSummary
import com.localcharacter.app.domain.model.Character
import com.localcharacter.app.domain.model.CharacterSource
import com.localcharacter.app.domain.model.LoreEntry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CharacterInstallerTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `install is complete and duplicate remote identity is reused`() = runBlocking {
        val store = FakeInstallStore()
        val installer = CharacterInstaller(temporary.newFolder("characters"), CharacterCardParser(), store, now = { 100L })
        val first = installer.install(TestDownloadProvider(), detail())
        val second = installer.install(TestDownloadProvider(), detail())

        assertEquals(InstallOutcome.INSTALLED, first.outcome)
        assertEquals(InstallOutcome.ALREADY_INSTALLED, second.outcome)
        assertEquals(first.characterId, second.characterId)
        assertEquals(1, store.commitCount)
        assertEquals("Nova", store.character?.name)
        assertEquals(1, store.lore.size)
        assertNotNull(store.source)
        assert(File(requireNotNull(store.source).originalCardPath).isFile)
    }

    @Test fun `failed transaction compensates finalized files`() = runBlocking {
        val root = temporary.newFolder("rollback")
        val installer = CharacterInstaller(root, CharacterCardParser(), FakeInstallStore(failAfterFinalize = true))
        runCatching { installer.install(TestDownloadProvider(), detail()) }
        assertFalse(root.listFiles().orEmpty().any { it.isDirectory })
    }

    private fun detail() = RemoteCharacterDetail(
        RemoteCharacterSummary("test", "7", "Nova", "Desc", null, "Author", emptyList(), "es", false, 1, 2L, "https://example.com/cards/7"),
        "https://example.com/card.png",
        "1",
    )
}

private class FakeInstallStore(private val failAfterFinalize: Boolean = false) : CharacterInstallStore {
    var source: CharacterSource? = null
    var character: Character? = null
    var lore: List<LoreEntry> = emptyList()
    var commitCount = 0
    override suspend fun find(providerId: String, remoteId: String): CharacterSource? = source?.takeIf { it.providerId == providerId && it.remoteId == remoteId }
    override suspend fun install(character: Character, lore: List<LoreEntry>, source: CharacterSource, finalizeFiles: () -> Unit) {
        finalizeFiles()
        if (failAfterFinalize) error("transaction failed")
        this.character = character
        this.lore = lore
        this.source = source
        commitCount++
    }
}

private class TestDownloadProvider : CharacterCatalogProvider {
    override val descriptor = ProviderDescriptor(
        "test", "Test", ProviderAvailability.AVAILABLE, "ok",
        ProviderCapabilities(true, true, true, true, false),
    )
    override suspend fun health() = ProviderHealth(ProviderAvailability.AVAILABLE, "ok")
    override suspend fun search(request: CatalogRequest): CatalogPage<RemoteCharacterSummary> = error("unused")
    override suspend fun getDetail(remoteId: String): RemoteCharacterDetail = error("unused")
    override suspend fun downloadCard(detail: RemoteCharacterDetail) = RemoteAsset(
        """{"spec":"chara_card_v2","spec_version":"2.0","data":{"name":"Nova","description":"Desc","first_mes":"Hola","character_book":{"entries":[{"keys":["moon"],"content":"Moon lore"}]}}}""".encodeToByteArray(),
        "application/json",
        "nova.json",
    )
    override suspend fun downloadAvatar(detail: RemoteCharacterDetail): RemoteAsset? = null
}
