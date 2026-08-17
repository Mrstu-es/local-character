package com.localcharacter.app.domain.group

import com.localcharacter.app.domain.model.Character
import com.localcharacter.app.domain.model.GroupConversation
import com.localcharacter.app.domain.model.GroupMessage
import com.localcharacter.app.domain.model.GroupMessageRole
import com.localcharacter.app.domain.model.GroupParticipant
import com.localcharacter.app.domain.model.GroupTurnMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupSpeakerSelectorTest {
    private val alya = Character("a", "Alya", description = "calm")
    private val luna = Character("l", "Luna", description = "sarcastic")
    private val sophie = Character("s", "Sophie", description = "shy")
    private val participants = listOf(GroupParticipant("g", "a", 0), GroupParticipant("g", "l", 1), GroupParticipant("g", "s", 2))
    private val selector = GroupSpeakerSelector()

    @Test fun directMentionWins() {
        val result = selector.decide("Alya, ¿cómo estás?", emptyList(), participants, listOf(alya, luna, sophie), GroupTurnMode.SMART)
        assertEquals("a", result.characterId)
        assertEquals("direct mention", result.reason)
    }

    @Test fun groupRequestAllowsChain() {
        assertTrue(selector.asksForEveryone("¿Qué opinan todos?") )
        val plan = GroupTurnOrchestrator(selector).speakersForTurn(
            GroupConversation("g", "Grupo", maxAutoResponses = 3, maxBotChain = 2),
            "¿Qué opinan todos?", emptyList(), participants, listOf(alya, luna, sophie),
        )
        assertEquals(2, plan.size)
    }

    @Test fun botMentionSuggestsNextSpeaker() {
        val recent = listOf(GroupMessage("1", "g", GroupMessageRole.CHARACTER, "Luna, estás equivocada.", senderCharacterId = "a"))
        assertEquals("l", selector.decide(null, recent, participants, listOf(alya, luna, sophie), GroupTurnMode.SMART).characterId)
    }

    @Test fun roundRobinIsExplicitOnly() {
        val recent = listOf(GroupMessage("1", "g", GroupMessageRole.CHARACTER, "hola", senderCharacterId = "a"))
        assertEquals("l", selector.nextRoundRobin(recent, participants))
    }
}
