package com.localcharacter.app.data.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiCharacterCardsMapperTest {
    @Test fun `provider dto is mapped and html is removed at boundary`() {
        val provider = AiCharacterCardsProvider(SecureCatalogHttpClient())
        val mapped = provider.mapSummary(
            AiccCardDto(
                id = 1944,
                title = "Roxanne",
                excerpt = "<p>A <strong>careful</strong> character.</p>",
                imageUrl = "/uploads/avatar.webp",
                author = "Creator",
                language = "en",
                tags = listOf(AiccTagDto(1, "Roleplay")),
            ),
        )
        assertEquals("1944", mapped.remoteId)
        assertEquals("A careful character.", mapped.description)
        assertEquals("https://api.aicharactercards.com/uploads/avatar.webp", mapped.avatarUrl)
        assertEquals("https://aicharactercards.com/cards/1944", mapped.sourceUrl)
        assertFalse(mapped.isNsfw)
        assertTrue("Roleplay" in mapped.tags)
    }
}
