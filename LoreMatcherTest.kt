package com.localcharacter.app.domain.lore

import com.localcharacter.app.domain.model.LoreEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoreMatcherTest {
    @Test
    fun `matches keywords and orders by priority`() {
        val entries = listOf(
            LoreEntry("low", "c", listOf("Himmel"), "low", priority = 1),
            LoreEntry("high", "c", listOf("hero party"), "high", priority = 10),
            LoreEntry("off", "c", listOf("Himmel"), "off", enabled = false),
        )
        val result = LoreMatcher().match(entries, "HIMMEL belonged to the Hero Party")
        assertEquals(listOf("high", "low"), result.map { it.id })
        assertTrue(result.none { it.id == "off" })
    }

    @Test
    fun `honors case sensitivity`() {
        val entry = LoreEntry("id", "c", listOf("Astra"), "content", caseSensitive = true)
        assertTrue(LoreMatcher().match(listOf(entry), "astra").isEmpty())
    }
}
