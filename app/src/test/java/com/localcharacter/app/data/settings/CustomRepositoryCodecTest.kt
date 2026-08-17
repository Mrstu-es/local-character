package com.localcharacter.app.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomRepositoryCodecTest {
    @Test
    fun `round trip preserves repository state`() {
        val expected = listOf(
            CustomRepositorySettings("one", "Comunidad", "https://example.org/repository.json", false, "Disponible", 42L, 500),
        )
        assertEquals(expected, CustomRepositoryCodec.decode(CustomRepositoryCodec.encode(expected)))
    }

    @Test
    fun `corrupt settings recover as an empty list`() {
        assertTrue(CustomRepositoryCodec.decode("not json").isEmpty())
    }

    @Test fun `repository can be disabled and enabled without losing its configuration`() {
        val repository = CustomRepositorySettings("one", "Comunidad", "https://example.org/repository.json")
        val disabled = CustomRepositoryState.setEnabled(listOf(repository), "one", false).single()
        val enabled = CustomRepositoryState.setEnabled(listOf(disabled), "one", true).single()
        assertEquals(false, disabled.enabled)
        assertEquals(repository, enabled)
    }
}
