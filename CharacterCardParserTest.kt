package com.localcharacter.app.data.charactercard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterCardParserTest {
    @Test
    fun `parses v2 card and lorebook separately`() {
        val raw = """
            {"spec":"chara_card_v2","spec_version":"2.0","data":{
              "name":"Luna","description":"Cartógrafa","first_mes":"Hola {{user}}",
              "tags":["Original","Aventura"],
              "character_book":{"entries":[{"keys":["brújula"],"content":"La brújula es antigua.","priority":7}]}
            }}
        """.trimIndent()
        val result = CharacterCardParser().parseJson(raw)
        assertEquals("Luna", result.character.name)
        assertEquals(listOf("Original", "Aventura"), result.character.tags)
        assertEquals(1, result.lore.size)
        assertEquals(result.character.id, result.lore.single().characterId)
    }

    @Test(expected = CharacterCardException::class)
    fun `rejects malformed card`() {
        CharacterCardParser().parseJson("{broken")
    }

    @Test
    fun `exports v2 without losing internal fields`() {
        val parsed = CharacterCardParser().parseJson("""{"name":"Astra","personality":"Precisa","alternate_greetings":["Uno"]}""")
        val exported = CharacterCardParser().exportJson(parsed.character, emptyList())
        assertTrue(exported.contains("chara_card_v2"))
        assertTrue(exported.contains("Precisa"))
    }

    @Test fun `keeps recommended voice in namespaced optional extension`() {
        val raw = """{"spec":"chara_card_v2","data":{"name":"Alya","extensions":{"localcharacter":{"recommendedVoiceId":"voice-es-01"}}}}"""
        val imported = CharacterCardParser().parseJson(raw)
        assertEquals("voice-es-01", imported.character.recommendedVoiceId)
        val exported = CharacterCardParser().exportJson(imported.character, emptyList())
        assertTrue(exported.contains("localcharacter"))
        assertTrue(exported.contains("voice-es-01"))
    }
}
