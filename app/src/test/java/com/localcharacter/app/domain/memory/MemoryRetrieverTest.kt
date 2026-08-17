package com.localcharacter.app.domain.memory

import com.localcharacter.app.domain.model.MemoryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MemoryRetrieverTest {
    @Test fun `Toby query retrieves relationship and illness but not anime preference`() {
        val memories = listOf(
            memory("Toby es el perro del usuario.", MemoryType.RELATIONSHIP, "relation"),
            memory("Toby estuvo enfermo y fue al veterinario.", MemoryType.EVENT, "illness"),
            memory("Al usuario le gusta Dragon Ball.", MemoryType.PREFERENCE, "anime"),
        )
        val result = MemoryRetriever().retrieve("Toby volvió del veterinario.", emptyList(), memories, now = memories.first().updatedAt)
        assertEquals("relation", result.first().memory.id)
        assertFalse(result.any { it.memory.id == "anime" })
    }
}
