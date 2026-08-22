package com.localcharacter.app.domain.prompt

import com.localcharacter.app.domain.model.Character
import com.localcharacter.app.domain.model.ChatMessage
import com.localcharacter.app.domain.model.MessageRole
import com.localcharacter.app.domain.model.Memory
import com.localcharacter.app.domain.model.MemoryOrigin
import com.localcharacter.app.domain.model.MemoryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.localcharacter.app.domain.conversation.GenerationMode
import com.localcharacter.app.llm.provider.LlmRole

class PromptBuilderTest {
    private val character = Character(
        id = "astra", name = "Astra", personality = "Observadora", scenario = "Una biblioteca",
        systemPrompt = "Habla como {{char}} con {{user}}.",
    )

    @Test
    fun `build includes identity and replaces placeholders`() {
        val message = ChatMessage("current", "c", MessageRole.USER, "Hola Astra")
        val result = PromptBuilder().build(PromptRequest(character, userName = "Nuria", messages = listOf(message)))
        assertTrue(result.text.contains("Astra"))
        assertTrue(result.text.contains("Nuria"))
        assertFalse(result.text.contains("{{char}}"))
        assertTrue(result.text.contains("Hola Astra"))
        assertTrue(result.systemPrompt.contains("Astra"))
        assertEquals("Hola Astra", result.messages.single().content)
    }

    @Test
    fun `current user message is never truncated`() {
        val old = (1..20).map { ChatMessage("old$it", "c", MessageRole.CHARACTER, "x".repeat(300)) }
        val current = ChatMessage("current", "c", MessageRole.USER, "mensaje actual que debe conservarse")
        val result = PromptBuilder().build(PromptRequest(character, messages = old + current, contextWindow = 300, responseReserve = 100))
        assertTrue(result.text.contains(current.content))
        assertTrue(result.droppedMessages > 0)
    }

    @Test
    fun `memory context is natural and ordered before current message`() {
        val memory = Memory(id = "milo", characterId = "astra", content = "El gato del usuario se llama Milo.")
        val current = ChatMessage("current", "c", MessageRole.USER, "¿Cómo se llama mi gato?")
        val result = PromptBuilder().build(
            PromptRequest(
                character = character,
                messages = listOf(current),
                memories = listOf(memory),
                relationshipState = "Existe familiaridad entre ambos.",
                pendingFollowUps = listOf("El usuario tuvo un examen de programación hoy."),
            ),
        )
        assertTrue(result.text.contains("El gato del usuario se llama Milo"))
        assertTrue(result.text.indexOf("RELEVANT LONG-TERM MEMORIES") < result.text.indexOf("CURRENT USER MESSAGE"))
        assertTrue(result.text.contains("Never mention a memory database"))
        assertEquals(listOf("milo"), result.includedMemoryIds)
    }

    @Test fun `selected response language is part of the prompt`() {
        val message = ChatMessage("current", "c", MessageRole.USER, "Hola")
        val result = PromptBuilder().build(
            PromptRequest(character = character, messages = listOf(message), responseLanguage = "Spanish"),
        )
        assertTrue(result.text.contains("Always answer in Spanish"))
    }

    @Test fun `current question is anchored to the immediately preceding scene`() {
        val greeting = ChatMessage("a1", "c", MessageRole.CHARACTER, "*Astra busca su cuaderno.* No recuerdo dónde lo dejé.")
        val question = ChatMessage("u1", "c", MessageRole.USER, "¿Dónde crees que lo dejaste?")
        val result = PromptBuilder().build(
            PromptRequest(character = character, userName = "Tadeo", messages = listOf(greeting, question)),
        )
        assertTrue(result.text.contains("CONVERSATION CONTINUITY"))
        assertTrue(result.text.contains("IMMEDIATE SCENE ANCHOR"))
        assertTrue(result.text.contains("No recuerdo dónde lo dejé"))
        assertTrue(result.text.contains("¿Dónde crees que lo dejaste?"))
        assertTrue(result.text.indexOf("Turn 1") < result.text.indexOf("CURRENT USER MESSAGE"))
    }

    @Test fun `character opinions have explicit ownership in prompt`() {
        val opinion = Memory(
            id = "op1", characterId = character.id, type = MemoryType.OPINION,
            content = "Astra expressed this opinion: honesty matters.",
            origin = MemoryOrigin.CHARACTER_STATED, importance = 0.9f,
        )
        val current = ChatMessage("u1", "c", MessageRole.USER, "¿Qué opinas de mentir?")
        val result = PromptBuilder().build(PromptRequest(character = character, messages = listOf(current), memories = listOf(opinion)))
        assertTrue(result.text.contains("CHARACTER'S PERSISTENT OPINIONS"))
        assertTrue(result.text.contains("honesty matters"))
    }

    @Test fun `oversized character card is bounded to the context`() {
        val huge = character.copy(description = "detalle ".repeat(20_000), personality = "rasgo ".repeat(20_000))
        val message = ChatMessage("current", "c", MessageRole.USER, "Mensaje imprescindible")
        val result = PromptBuilder().build(
            PromptRequest(character = huge, messages = listOf(message), contextWindow = 2048, responseReserve = 320),
        )
        assertTrue(result.estimatedTokens <= 2048)
        assertTrue(result.text.contains(message.content))
    }

    @Test fun `character continue keeps history and uses an internal instruction`() {
        val user = ChatMessage("u", "c", MessageRole.USER, "Me quedaré aquí")
        val assistant = ChatMessage("a", "c", MessageRole.CHARACTER, "Astra mira por la ventana")
        val result = PromptBuilder().build(
            PromptRequest(
                character = character,
                userName = "Tadeo",
                messages = listOf(user, assistant),
                generationMode = GenerationMode.CHARACTER_CONTINUE,
            ),
        )
        assertTrue(result.text.contains("GENERATION MODE"))
        assertTrue(result.text.contains("Tadeo has not spoken again"))
        assertFalse(result.text.contains("CURRENT USER MESSAGE"))
        assertEquals(3, result.messages.size)
        assertEquals(LlmRole.USER, result.messages.last().role)
        assertTrue(result.messages.last().content.contains("not user speech"))
        assertTrue(result.messages.last().content.contains("Tadeo has not spoken again"))
    }
}

class TokenBudgetManagerTest {
    @Test
    fun `returns newest messages in chronological order`() {
        val items = (1..5).map { ChatMessage("$it", "c", MessageRole.USER, "mensaje $it ".repeat(10)) }
        val selected = TokenBudgetManager().takeNewestWithinBudget(items, 70, "5")
        assertEquals(selected.sortedBy { it.createdAt }, selected)
        assertTrue(selected.last().id == "5")
    }
}
