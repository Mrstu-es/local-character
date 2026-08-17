package com.localcharacter.app.llm

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LlamaBridgeTest {
    @Test
    fun nativeLibraryReportsPinnedVersion() {
        assertEquals("llama.cpp b10434", LlamaBridge.getVersion())
    }
}
