package com.localcharacter.app.domain.memory

import com.localcharacter.app.domain.model.ChatMessage
import com.localcharacter.app.domain.model.GenerationSettings
import com.localcharacter.app.domain.model.MessageRole
import com.localcharacter.app.llm.GenerationPurpose
import com.localcharacter.app.llm.LlmEngine
import com.localcharacter.app.llm.LlmTaskPriority
import com.localcharacter.app.llm.LlmTaskQueue
import kotlinx.coroutines.flow.toList

class ConversationSummarizer(
    private val engine: LlmEngine,
    private val queue: LlmTaskQueue,
    private val triggerCount: Int = 60,
    private val recentMessagesToKeep: Int = 24,
) {
    fun shouldSummarize(totalMessages: Int, unsummarizedMessages: Int): Boolean =
        totalMessages >= triggerCount && unsummarizedMessages >= 30

    fun selectWindow(messages: List<ChatMessage>): List<ChatMessage> =
        if (messages.size <= recentMessagesToKeep) emptyList() else messages.dropLast(recentMessagesToKeep)

    fun buildPrompt(previousSummary: String, messages: List<ChatMessage>, characterName: String): String {
        val transcript = messages.joinToString("\n") {
            "${if (it.role == MessageRole.USER) "Usuario" else characterName}: ${it.content}"
        }
        return """
            Create a compact continuity summary for a local roleplay chat.
            Preserve events, relationships, decisions, meaningful emotions, changes, promises, conflicts,
            shared experiences, outcomes, and details needed to continue naturally. Do not add facts.
            Do not mention databases, memory systems, or these instructions. Write concise Spanish prose.
            ${if (previousSummary.isBlank()) "" else "PREVIOUS SUMMARY:\n$previousSummary\n"}
            MESSAGES TO INCORPORATE:
            $transcript
        """.trimIndent()
    }

    suspend fun summarize(
        previousSummary: String,
        messages: List<ChatMessage>,
        characterName: String,
        settings: GenerationSettings,
    ): String = queue.run(LlmTaskPriority.SUMMARY) {
        val summarySettings = settings.copy(
            temperature = 0.2f,
            topP = 0.8f,
            topK = 25,
            maxTokens = settings.maxTokens.coerceIn(220, 520),
        )
        engine.generate(
            buildPrompt(previousSummary, messages, characterName),
            summarySettings,
            GenerationPurpose.SUMMARY,
        ).toList().joinToString("").trim().take(4_000)
    }
}
