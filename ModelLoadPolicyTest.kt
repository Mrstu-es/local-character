package com.localcharacter.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelLoadPolicyTest {
    @Test fun `does not allocate the full 32k context declared by Qwen`() {
        assertEquals(2048, ModelLoadPolicy.effectiveContextSize(4096, 32768))
    }

    @Test fun `respects a smaller model maximum`() {
        assertEquals(2048, ModelLoadPolicy.effectiveContextSize(4096, 2048))
    }

    @Test fun `reserves two cores for UI on an eight core phone`() {
        assertEquals(4, ModelLoadPolicy.effectiveThreads(8, 8))
        assertEquals(3, ModelLoadPolicy.effectiveThreads(4, 4))
    }

    @Test fun `accepts a full mobile prefill batch`() {
        assertEquals(512, ModelLoadPolicy.effectiveBatchSize(1024))
        assertEquals(256, ModelLoadPolicy.effectiveBatchSize(256))
        assertEquals(64, ModelLoadPolicy.effectiveBatchSize(64))
    }
}
