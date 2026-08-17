package com.localcharacter.app.domain.memory

import com.localcharacter.app.domain.model.MemoryOrigin
import com.localcharacter.app.domain.model.MemoryType
import org.junit.Assert.assertEquals
import org.junit.Test

class MemoryConflictResolverTest {
    private val resolver = MemoryConflictResolver()

    @Test fun `new residence replaces current residence`() {
        val candidate = MemoryCandidate(MemoryType.FACT, "El usuario ahora vive en La Paz.", .8f, .95f, MemoryOrigin.USER_STATED_FACT)
        val result = resolver.resolve(candidate, listOf(memory("El usuario vive en Santa Cruz.")))
        assertEquals(ConflictAction.REPLACE, result.action)
    }

    @Test fun `coffee preference change replaces old preference`() {
        val candidate = MemoryCandidate(MemoryType.PREFERENCE, "El usuario ya no toma café.", .7f, .9f, MemoryOrigin.USER_STATED_FACT)
        val result = resolver.resolve(candidate, listOf(memory("Al usuario le gusta el café.", MemoryType.PREFERENCE)))
        assertEquals(ConflictAction.REPLACE, result.action)
    }

    @Test fun `separate historical events coexist`() {
        val candidate = MemoryCandidate(MemoryType.EVENT, "Ana consiguió trabajo.", .7f, .9f, MemoryOrigin.USER_STATED_FACT)
        val result = resolver.resolve(candidate, listOf(memory("Ana buscaba trabajo.", MemoryType.EVENT)))
        assertEquals(ConflictAction.COEXIST, result.action)
    }
}
