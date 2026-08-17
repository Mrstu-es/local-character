package com.localcharacter.app.domain.prompt

import com.localcharacter.app.domain.lore.LoreMatcher
import com.localcharacter.app.domain.model.Character
import com.localcharacter.app.domain.model.ChatMessage
import com.localcharacter.app.domain.model.LoreEntry
import com.localcharacter.app.domain.model.Memory
import com.localcharacter.app.domain.model.MessageRole
import com.localcharacter.app.llm.provider.LlmMessage
import com.localcharacter.app.llm.provider.LlmRole
import com.localcharacter.app.domain.conversation.GenerationMode
import com.localcharacter.app.domain.conversation.GenerationModeResolver
import com.localcharacter.app.domain.conversation.TemplateVariableResolver
import com.localcharacter.app.domain.model.ContentMode

class TokenBudgetManager {
    fun estimateTokens(text: String): Int = if (text.isBlank()) 0 else (text.length / 3.6).toInt().coerceAtLeast(1)

    fun takeNewestWithinBudget(
        messages: List<ChatMessage>,
        budget: Int,
        protectedLastMessageId: String?,
    ): List<ChatMessage> {
        if (messages.isEmpty()) return emptyList()
        val selected = mutableListOf<ChatMessage>()
        var used = 0
        for (message in messages.asReversed()) {
            val cost = estimateTokens(message.content) + 6
            if (message.id == protectedLastMessageId || used + cost <= budget) {
                selected += message
                used += cost
            }
        }
        return selected.asReversed()
    }

    fun trimTextToBudget(text: String, tokenBudget: Int): String {
        if (estimateTokens(text) <= tokenBudget) return text
        if (tokenBudget <= 0) return ""
        return text.take((tokenBudget * 3.6).toInt().coerceAtLeast(1)).trimEnd() + "…"
    }
}

data class PromptRequest(
    val character: Character,
    val userName: String = "Usuario",
    val userPersona: String = "",
    val messages: List<ChatMessage>,
    val loreEntries: List<LoreEntry> = emptyList(),
    val memories: List<Memory> = emptyList(),
    val conversationSummary: String = "",
    val relationshipState: String = "",
    val pendingFollowUps: List<String> = emptyList(),
    val contextWindow: Int = 4096,
    val responseReserve: Int = 512,
    val responseLanguage: String? = null,
    val generationMode: GenerationMode = GenerationMode.NORMAL_REPLY,
    val contentMode: ContentMode = ContentMode.STANDARD,
)

data class PromptResult(
    val text: String,
    val systemPrompt: String,
    val messages: List<LlmMessage>,
    val estimatedTokens: Int,
    val droppedMessages: Int,
    val includedLoreIds: List<String>,
    val includedMemoryIds: List<String>,
)

class PromptBuilder(
    private val budget: TokenBudgetManager = TokenBudgetManager(),
    private val loreMatcher: LoreMatcher = LoreMatcher(),
) {
    fun build(request: PromptRequest): PromptResult {
        val character = request.character
        val substitutions: (String) -> String = { text ->
            TemplateVariableResolver.resolve(text, character.name, request.userName)
        }
        val currentMessage = if (request.generationMode == GenerationMode.NORMAL_REPLY) {
            request.messages.lastOrNull { it.role == MessageRole.USER }
        } else null
        val earlierMessages = if (currentMessage == null) request.messages else request.messages.filterNot { it.id == currentMessage.id }
        val recentText = request.messages.takeLast(8).joinToString("\n") { it.content }
        val matchedLore = loreMatcher.match(request.loreEntries, recentText)

        val rawEssential = buildList {
            add("SYSTEM\nYou are roleplaying as ${character.name}. Stay consistent, remain fully in character, and never decide the user's actions.")
            request.responseLanguage?.takeIf(String::isNotBlank)?.let {
                add("RESPONSE LANGUAGE\nAlways answer in $it, while preserving the character's natural voice.")
            }
            character.systemPrompt.takeIf { it.isNotBlank() }?.let { add(substitutions(it)) }
            add("CHARACTER\nName: ${character.name}\nDescription: ${substitutions(character.description)}")
            add(buildString {
                append("USER PERSONA\nName: ${request.userName}")
                request.userPersona.trim().take(600).takeIf(String::isNotBlank)?.let { append("\nDescription: ${substitutions(it)}") }
                append("\nKnow this identity, but use the user's name only when it feels natural.")
            })
            add(
                when (request.contentMode) {
                    ContentMode.STANDARD -> "CONTENT MODE\nStandard conversation mode is active. Avoid explicit adult sexual content."
                    ContentMode.ADULT_ENABLED -> "CONTENT MODE\nThe adult preference is enabled inside this app. It does not override provider policies, consent, safety rules, or applicable law."
                },
            )
            character.personality.takeIf { it.isNotBlank() }?.let { add("PERSONALITY\n${substitutions(it)}") }
            character.scenario.takeIf { it.isNotBlank() }?.let { add("SCENARIO\n${substitutions(it)}") }
            add(
                """MEMORY USE
                    You have access only to the relevant memories supplied below. Use them naturally when relevant.
                    Never mention a memory database or say "according to my memory". Do not force memories into unrelated replies.
                    Do not claim to remember anything absent from this context. Treat uncertain memories cautiously.
                    You may ask a natural follow-up about an unresolved event, but only when it fits. Stay consistent with the character.""".trimIndent(),
            )
        }.joinToString("\n\n")

        val continuationInstruction = if (currentMessage == null) {
            GenerationModeResolver.temporaryInstruction(
                request.generationMode, character.name, request.userName,
            )
        } else null
        val currentSection = currentMessage?.let {
            "CURRENT USER MESSAGE\n${request.userName}: ${substitutions(it.content)}"
        } ?: continuationInstruction
            ?.let { "GENERATION MODE (temporary; never store as a user message)\n$it" }
            .orEmpty()
        // Character cards can contain tens of thousands of tokens. Bound their fixed fields
        // before JNI tokenization so a large card cannot monopolize prefill or exceed n_ctx.
        val essentialBudget = (
            request.contextWindow - request.responseReserve - budget.estimateTokens(currentSection) - 96
        ).coerceAtLeast(128)
        val essential = budget.trimTextToBudget(rawEssential, essentialBudget)
        val available = (request.contextWindow - request.responseReserve - budget.estimateTokens(essential) -
            budget.estimateTokens(currentSection) - 48).coerceAtLeast(0)

        val recentBudget = (available * 0.52f).toInt()
        val memoryBudget = (available * 0.22f).toInt()
        val loreBudget = (available * 0.12f).toInt()
        val summaryBudget = (available - recentBudget - memoryBudget - loreBudget).coerceAtLeast(0)

        val history = budget.takeNewestWithinBudget(earlierMessages, recentBudget, null)
        val transcript = history.joinToString("\n") { message ->
            val speaker = if (message.role == MessageRole.USER) request.userName else character.name
            "$speaker: ${substitutions(message.content)}"
        }

        val relationship = budget.trimTextToBudget(request.relationshipState, (memoryBudget * 0.2f).toInt())
        var memoryUsed = budget.estimateTokens(relationship)
        val includedMemories = mutableListOf<Memory>()
        request.memories.forEach { memory ->
            val line = "- ${if (memory.confidence < 0.65f) "Uncertain: " else ""}${memory.content}"
            val cost = budget.estimateTokens(line)
            if (memoryUsed + cost <= memoryBudget) {
                includedMemories += memory
                memoryUsed += cost
            }
        }
        val followUps = mutableListOf<String>()
        request.pendingFollowUps.forEach { item ->
            val line = "- ${substitutions(item)}"
            val cost = budget.estimateTokens(line)
            if (memoryUsed + cost <= memoryBudget) {
                followUps += line
                memoryUsed += cost
            }
        }
        var loreUsed = 0
        val includedLore = matchedLore.filter { entry ->
            val cost = budget.estimateTokens(entry.content) + 3
            (loreUsed + cost <= loreBudget).also { if (it) loreUsed += cost }
        }
        val exampleBudget = when {
            character.exampleMessages.isBlank() -> 0
            request.conversationSummary.isBlank() -> summaryBudget.coerceAtMost(160)
            else -> (summaryBudget / 4).coerceAtMost(80)
        }
        val summary = budget.trimTextToBudget(request.conversationSummary, (summaryBudget - exampleBudget).coerceAtLeast(0))
        val examples = budget.trimTextToBudget(substitutions(character.exampleMessages), exampleBudget)

        val systemSections = buildList {
            add(essential)
            if (relationship.isNotBlank()) add("RELATIONSHIP STATE\n$relationship")
            if (includedMemories.isNotEmpty()) add(
                "RELEVANT LONG-TERM MEMORIES\n" + includedMemories.joinToString("\n") {
                    "- ${if (it.confidence < 0.65f) "Uncertain: " else ""}${it.content}"
                },
            )
            if (followUps.isNotEmpty()) add("UNRESOLVED EVENTS (optional follow-up only if natural)\n" + followUps.joinToString("\n"))
            if (includedLore.isNotEmpty()) add("RELEVANT LORE\n" + includedLore.joinToString("\n") { it.content })
            if (summary.isNotBlank()) add("EARLIER CONVERSATION SUMMARY\n$summary")
            if (examples.isNotBlank()) add("EXAMPLE DIALOGUE\n$examples")
        }
        val sections = buildList {
            addAll(systemSections)
            if (transcript.isNotBlank()) add("RECENT CONVERSATION\n$transcript")
            if (currentSection.isNotBlank()) add(currentSection)
        }
        val text = sections.joinToString("\n\n") + "\n${character.name}:"
        val providerMessages = buildList {
            addAll((history + listOfNotNull(currentMessage)).mapNotNull { message ->
                when (message.role) {
                    MessageRole.USER -> LlmMessage(LlmRole.USER, substitutions(message.content))
                    MessageRole.CHARACTER -> LlmMessage(LlmRole.ASSISTANT, substitutions(message.content))
                    MessageRole.SYSTEM -> null
                }
            })
            // Online chat APIs need a final request after an assistant message. This is
            // ephemeral provider input only: it is never represented by ChatMessage or
            // written to Room, and explicitly says that the user did not speak.
            continuationInstruction?.let { instruction ->
                add(LlmMessage(LlmRole.USER, "[Internal character-continuation instruction; not user speech]\n$instruction"))
            }
        }
        return PromptResult(
            text = text,
            systemPrompt = systemSections.joinToString("\n\n"),
            messages = providerMessages,
            estimatedTokens = budget.estimateTokens(text),
            droppedMessages = earlierMessages.size - history.size,
            includedLoreIds = includedLore.map { it.id },
            includedMemoryIds = includedMemories.map { it.id },
        )
    }
}
