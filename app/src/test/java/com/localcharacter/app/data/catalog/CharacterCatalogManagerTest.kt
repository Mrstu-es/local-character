package com.localcharacter.app.data.catalog

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
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterCatalogManagerTest {
    @Test fun `provider failure is isolated from successful progressive result`() = runBlocking {
        val manager = CharacterCatalogManager(listOf(FakeProvider("good"), FakeProvider("bad", fail = true)))
        val emissions = manager.searchProgressively(setOf("good", "bad"), CatalogRequest()).toList()
        assertEquals(4, emissions.size)
        assertEquals(1, emissions.first { it.provider.id == "good" && !it.loading }.page?.items?.size)
        assertNotNull(emissions.first { it.provider.id == "bad" && !it.loading }.error)
    }

    @Test fun `custom providers can be replaced without rebuilding built in providers`() {
        val manager = CharacterCatalogManager(listOf(FakeProvider("local")))
        manager.replaceCustomProviders(listOf(FakeProvider("repository_one")))
        assertTrue(manager.descriptors.any { it.id == "local" })
        assertTrue(manager.descriptors.any { it.id == "repository_one" })
        manager.replaceCustomProviders(emptyList())
        assertTrue(manager.descriptors.any { it.id == "local" })
        assertFalse(manager.descriptors.any { it.id == "repository_one" })
    }
}

private class FakeProvider(private val id: String, private val fail: Boolean = false) : CharacterCatalogProvider {
    override val descriptor = ProviderDescriptor(
        id, id, ProviderAvailability.AVAILABLE, "ok",
        ProviderCapabilities(true, true, true, true, true),
    )
    override suspend fun health() = ProviderHealth(ProviderAvailability.AVAILABLE, "ok")
    override suspend fun search(request: CatalogRequest): CatalogPage<RemoteCharacterSummary> {
        if (fail) error("isolated")
        return CatalogPage(listOf(RemoteCharacterSummary(id, "1", "One", "", null, null, emptyList(), null, false, null, null, "https://example.com")), null)
    }
    override suspend fun getDetail(remoteId: String): RemoteCharacterDetail = error("unused")
    override suspend fun downloadCard(detail: RemoteCharacterDetail): RemoteAsset = error("unused")
    override suspend fun downloadAvatar(detail: RemoteCharacterDetail): RemoteAsset? = null
}
