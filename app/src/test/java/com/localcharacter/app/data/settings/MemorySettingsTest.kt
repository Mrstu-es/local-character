package com.localcharacter.app.data.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemorySettingsTest {
    @Test fun `character continuity is shared across chats by default and remains optional`() {
        assertTrue(MemorySettings().shareAcrossChats)
        assertFalse(MemorySettings(shareAcrossChats = false).shareAcrossChats)
    }
}
