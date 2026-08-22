package com.localcharacter.app.domain.memory

import com.localcharacter.app.domain.model.GenerationSettings
import com.localcharacter.app.llm.GenerationPurpose
import com.localcharacter.app.llm.LlmEngine
import com.localcharacter.app.llm.LlmTaskPriority
import com.localcharacter.app.llm.LlmTaskQueue
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.toList

class MemoryExtractionService(
    private val engine: LlmEngine,
    private val queue: LlmTaskQueue,
    private val parser: MemoryParser = MemoryParser(),
) {
    suspend fun extract(
        userMessage: String,
        characterMessage: String,
        characterName: String,
        settings: GenerationSettings,
        now: Long = System.currentTimeMillis(),
    ): List<MemoryCandidate> = queue.run(LlmTaskPriority.MEMORY) {
        val today = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
        val prompt = """
            You extract durable memories from one roleplay turn. Work only from the supplied text.
            Today is $today. Convert relative dates such as "tomorrow" or "mañana" to ISO-8601 absolute dates.
            Keep useful facts, relationships, preferences, events, emotions, goals, promises, and shared events.
            Ignore greetings, filler, ordinary questions, and short-lived trivia. Do not invent.
            Direct user statements use origin USER_STATED_FACT. Statements or promises made by $characterName use CHARACTER_STATED.
            Return JSON only, with no markdown or explanation:
            {"memories":[{"type":"FACT|EVENT|PREFERENCE|OPINION|RELATIONSHIP|EMOTIONAL|GOAL|PROMISE|CHARACTER_RELATIONSHIP|SHARED_EVENT","content":"self-contained memory","importance":0.0,"confidence":0.0,"origin":"USER_STATED_FACT|CHARACTER_STATED|CHARACTER_INFERENCE|SYSTEM_INFERENCE","eventDate":"YYYY-MM-DD or null","expiresAt":"ISO-8601 or null"}]}

            USER: $userMessage
            $characterName: $characterMessage
        """.trimIndent()
        val extractionSettings = settings.copy(
            temperature = 0.15f,
            topP = 0.75f,
            topK = 20,
            // Keep autonomous work short so it cannot monopolize a mobile CPU after every turn.
            maxTokens = settings.maxTokens.coerceIn(96, 160),
        )
        val raw = engine.generate(prompt, extractionSettings, GenerationPurpose.MEMORY).toList().joinToString("")
        parser.parse(raw)
    }
}
