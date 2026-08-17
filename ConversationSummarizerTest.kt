package com.localcharacter.app.domain.memory

import com.localcharacter.app.domain.model.ChatMessage
import com.localcharacter.app.domain.model.GenerationSettings
import com.localcharacter.app.domain.model.MessageRole
import com.localcharacter.app.llm.LlmEngine
import com.localcharacter.app.llm.LlmState
import com.localcharacter.app.llm.LlmTaskQueue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationSummarizerTest {
    private val summarizer = ConversationSummarizer(FakeEngine(), LlmTaskQueue(FakeEngine()))

    @Test fun `summarizes only after enough history accumulates`() {
        assertFalse(summarizer.shouldSummarize(59, 59))
        assertFalse(summarizer.shouldSummarize(70, 20))
        assertTrue(summarizer.shouldSummarize(70, 35))
    }

    @Test fun `keeps recent messages outside summary window`() {
        val messages = (1..70).map { ChatMessage("$it", "c", MessageRole.USER, "mensaje $it", createdAt = it.toLong()) }
        val window = summarizer.selectWindow(messages)
        assertEquals(46, window.size)
        assertEquals("46", window.last().id)
        val prompt = summarizer.buildPrompt("Antes habló de Milo.", window, "Astra")
        assertTrue(prompt.contains("promises", ignoreCase = true))
        assertTrue(prompt.contains("Milo"))
    }
}

private class FakeEngine : LlmEngine {
    override val state: StateFlow<LlmState> = MutableStateFlow(LlmState.NoModelLoaded)
    override suspend fun loadModel(path: String, displayName: String, settings: GenerationSettings) = Result.success("")
    override suspend fun unloadModel() = Unit
    override fun generate(prompt: String, settings: GenerationSettings, purpose: com.localcharacter.app.llm.GenerationPurpose): Flow<String> = emptyFlow()
    override suspend fun stopGeneration() = Unit
    override fun nativeVersion() = "test"
}
