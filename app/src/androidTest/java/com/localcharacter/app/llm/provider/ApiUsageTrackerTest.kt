package com.localcharacter.app.llm.provider

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.localcharacter.app.data.database.AppDatabase
import com.localcharacter.app.data.repository.AiUsageRepository
import com.localcharacter.app.data.settings.SettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ApiUsageTrackerTest {
    private lateinit var database: AppDatabase
    private lateinit var tracker: ApiUsageTracker

    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        tracker = ApiUsageTracker(AiUsageRepository(database.aiUsageDao()), SettingsRepository(context))
    }

    @After fun close() {
        database.close()
    }

    @Test fun recordsRealTokensCostLatencyAndConversationLocally() = runBlocking {
        val selection = ProviderModelSelection("openrouter", "paid-model")
        tracker.record(
            selection = selection,
            model = LlmModelInfo("openrouter", "paid-model", inputPrice = 2.0, outputPrice = 6.0),
            usage = TokenUsage(inputTokens = 1_000, outputTokens = 2_000),
            conversationId = "conversation",
            characterId = "character",
            timeToFirstTokenMillis = 120,
            generationDurationMillis = 800,
        )

        val row = database.aiUsageDao().since(0).single()
        assertEquals("openrouter", row.providerId)
        assertEquals(1_000L, row.inputTokens)
        assertEquals(2_000L, row.outputTokens)
        assertEquals(0.014, row.estimatedCostUsd!!, 0.0000001)
        assertEquals("conversation", row.conversationId)
        assertEquals(120L, row.timeToFirstTokenMillis)
    }
}
