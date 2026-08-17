package com.localcharacter.app.domain.memory

import com.localcharacter.app.domain.model.Memory
import com.localcharacter.app.domain.model.MemoryOrigin
import com.localcharacter.app.domain.model.MemoryType
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MemoryDeduplicatorTest {
    @Test fun `recognizes equivalent Toby relationship`() {
        val existing = memory("El usuario tiene un perro llamado Toby.", MemoryType.RELATIONSHIP)
        val candidate = MemoryCandidate(MemoryType.RELATIONSHIP, "Toby es el perro del usuario.", .8f, .95f, MemoryOrigin.USER_STATED_FACT)
        assertNotNull(MemoryDeduplicator().find(candidate, listOf(existing)))
    }

    @Test fun `does not merge unrelated preferences`() {
        val existing = memory("Al usuario le gusta Dragon Ball.", MemoryType.PREFERENCE)
        val candidate = MemoryCandidate(MemoryType.PREFERENCE, "Al usuario le gusta el café.", .7f, .9f, MemoryOrigin.USER_STATED_FACT)
        assertNull(MemoryDeduplicator().find(candidate, listOf(existing)))
    }
}

internal fun memory(content: String, type: MemoryType = MemoryType.FACT, id: String = content) = Memory(
    id = id,
    characterId = "char",
    conversationId = "chat",
    type = type,
    content = content,
    normalizedContent = MemoryTextNormalizer.normalize(content),
)
