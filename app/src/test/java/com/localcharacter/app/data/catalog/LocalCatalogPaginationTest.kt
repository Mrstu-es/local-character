package com.localcharacter.app.data.catalog

import com.localcharacter.app.domain.character.CatalogRequest
import com.localcharacter.app.domain.character.LocalCharacterSource
import com.localcharacter.app.domain.model.Character
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalCatalogPaginationTest {
    @Test
    fun `five hundred local characters are exposed in stable pages`() = runBlocking {
        val characters = List(500) { Character(id = "character-$it", name = "Character $it") }
        val provider = LocalCharacterCatalogProvider(object : LocalCharacterSource {
            override suspend fun search(query: String) = characters
            override suspend fun getCharacter(id: String) = characters.firstOrNull { it.id == id }
        })

        val first = provider.search(CatalogRequest(pageSize = 20))
        val second = provider.search(CatalogRequest(cursor = first.nextCursor, pageSize = 20))

        assertEquals(20, first.items.size)
        assertEquals("20", first.nextCursor)
        assertEquals("character-20", second.items.first().remoteId)
        assertEquals(500, second.total)
    }
}
