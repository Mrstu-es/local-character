package com.localcharacter.app.data.settings

import com.localcharacter.app.domain.model.GenerationSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class GenerationSettingsCompatibilityTest {
    @Test fun `new defaults favor coherence on small roleplay models`() {
        val defaults = GenerationSettings()
        assertEquals(0.45f, defaults.temperature)
        assertEquals(0.90f, defaults.topP)
        assertEquals(40, defaults.topK)
        assertEquals(0.05f, defaults.minP)
        assertEquals(1.08f, defaults.repeatPenalty)
        assertEquals(0.60f, GenerationSettings.Roleplay.temperature)
    }

    @Test fun `legacy balanced tuple migrates while operational tuning survives`() {
        val old = GenerationSettings(
            temperature = 0.85f,
            topP = 0.92f,
            topK = 40,
            minP = 0.05f,
            repeatPenalty = 1.08f,
            contextSize = 8_192,
            maxTokens = 444,
            threads = 3,
            batchSize = 256,
        )
        val migrated = GenerationSettingsCompatibility.migrateLegacyDefaults(old)
        assertEquals(0.45f, migrated.temperature)
        assertEquals(0.90f, migrated.topP)
        assertEquals(8_192, migrated.contextSize)
        assertEquals(444, migrated.maxTokens)
        assertEquals(3, migrated.threads)
        assertEquals(256, migrated.batchSize)
    }

    @Test fun `legacy roleplay tuple is capped at stable temperature`() {
        val old = GenerationSettings(
            temperature = 0.95f, topP = 0.93f, topK = 50,
            minP = 0.05f, repeatPenalty = 1.12f,
        )
        val migrated = GenerationSettingsCompatibility.migrateLegacyDefaults(old)
        assertEquals(0.60f, migrated.temperature)
        assertEquals(0.90f, migrated.topP)
        assertEquals(40, migrated.topK)
        assertEquals(1.08f, migrated.repeatPenalty)
    }

    @Test fun `custom sampling values are never overwritten`() {
        val custom = GenerationSettings(
            temperature = 0.72f, topP = 0.87f, topK = 33,
            minP = 0.08f, repeatPenalty = 1.14f,
        )
        assertEquals(custom, GenerationSettingsCompatibility.migrateLegacyDefaults(custom))
    }
}
