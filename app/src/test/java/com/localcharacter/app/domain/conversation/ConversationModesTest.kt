package com.localcharacter.app.domain.conversation

import com.localcharacter.app.domain.model.CharacterContentOverride
import com.localcharacter.app.domain.model.ContentMode
import com.localcharacter.app.domain.model.TtsReadMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationModesTest {
    @Test fun `template resolver changes only supported complete variables`() {
        assertEquals(
            "Alya conoce a Tadeo; {{unknown}} y {{username}} quedan intactos.",
            TemplateVariableResolver.resolve(
                "{{char}} conoce a {{ user }}; {{unknown}} y {{username}} quedan intactos.", "Alya", "Tadeo",
            ),
        )
    }

    @Test fun `action formatter wraps once`() {
        assertEquals("**abre la puerta**", ActionModeFormatter.format("abre la puerta", ComposerMode.ACTION))
        assertEquals("**abre la puerta**", ActionModeFormatter.format(" **abre la puerta** ", ComposerMode.ACTION))
        assertEquals("abre la puerta", ActionModeFormatter.format(" abre la puerta ", ComposerMode.NORMAL))
    }

    @Test fun `generation mode creates only an internal continuation instruction`() {
        assertEquals(null, GenerationModeResolver.temporaryInstruction(GenerationMode.NORMAL_REPLY, "Alya", "Tadeo"))
        val instruction = GenerationModeResolver.temporaryInstruction(
            GenerationMode.CHARACTER_CONTINUE, "Alya", "Tadeo",
        ).orEmpty()
        assertTrue(instruction.contains("Alya"))
        assertTrue(instruction.contains("Tadeo has not spoken again"))
        assertFalse(instruction.contains("User: next"))
    }

    @Test fun `character content override has priority over global`() {
        assertEquals(ContentMode.STANDARD, ContentPolicyResolver.resolve(ContentMode.STANDARD, CharacterContentOverride.USE_GLOBAL))
        assertEquals(ContentMode.ADULT_ENABLED, ContentPolicyResolver.resolve(ContentMode.ADULT_ENABLED, CharacterContentOverride.USE_GLOBAL))
        assertEquals(ContentMode.ADULT_ENABLED, ContentPolicyResolver.resolve(ContentMode.STANDARD, CharacterContentOverride.ADULT_ENABLED))
        assertEquals(ContentMode.STANDARD, ContentPolicyResolver.resolve(ContentMode.ADULT_ENABLED, CharacterContentOverride.STANDARD))
    }

    @Test fun `roleplay parser and sanitizer can read dialogue only`() {
        val value = "**se acerca a la ventana** \"Hola, Tadeo.\" *espera una respuesta*"
        val segments = RoleplayTextParser.parse(value)
        assertEquals(3, segments.size)
        assertTrue(segments[0] is RoleplaySegment.Action)
        assertTrue(segments[1] is RoleplaySegment.Dialogue)
        assertTrue(segments[2] is RoleplaySegment.Narration)
        assertEquals("Hola, Tadeo.", TtsTextSanitizer.sanitize(value, TtsReadMode.DIALOGUE_ONLY))
        assertEquals(
            "se acerca a la ventana Hola, Tadeo. espera una respuesta",
            TtsTextSanitizer.sanitize(value, TtsReadMode.DIALOGUE_AND_ACTIONS),
        )
    }
}
