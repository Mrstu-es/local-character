package com.localcharacter.app.domain.memory

import com.localcharacter.app.domain.model.MemoryType
import com.localcharacter.app.domain.model.PendingEventStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingEventManagerTest {
    @Test fun `future memory becomes a due follow up after its date`() {
        val now = 1_000_000L
        val manager = PendingEventManager(followUpCooldownMillis = 500L)
        val event = manager.fromMemory(memory("El usuario tiene un examen de programación.", MemoryType.EVENT).copy(eventAt = now + 100), now)!!
        assertEquals(PendingEventStatus.PENDING, event.status)
        val due = manager.refresh(event, now + 101)
        assertTrue(manager.canAsk(due, now + 101))
        val asked = manager.markAsked(due, now + 101)
        assertFalse(manager.canAsk(asked, now + 200))
        assertTrue(manager.canAsk(asked, now + 700))
    }
}
