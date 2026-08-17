package com.localcharacter.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CharacterAvatarResolverTest {
    @Test fun `keeps local or remote avatar data`() {
        assertEquals("content://cards/avatar.png", CharacterAvatarResolver.resolve("Nuria", " content://cards/avatar.png ").imageData)
    }

    @Test fun `blank avatar resolves to a stable initial fallback`() {
        val result = CharacterAvatarResolver.resolve(" Nuria ", "  ")
        assertNull(result.imageData)
        assertEquals("N", result.fallbackInitial)
    }
}
