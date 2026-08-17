package com.localcharacter.app.domain.group

import com.localcharacter.app.domain.model.Character
import com.localcharacter.app.domain.model.GroupConversation
import com.localcharacter.app.domain.model.GroupMessage
import com.localcharacter.app.domain.model.GroupParticipant

/** Keeps a group turn finite: one generation at a time and a bounded optional chain. */
class GroupTurnOrchestrator(private val selector: GroupSpeakerSelector = GroupSpeakerSelector()) {
    fun speakersForTurn(
        group: GroupConversation,
        input: String?,
        recent: List<GroupMessage>,
        participants: List<GroupParticipant>,
        characters: List<Character>,
        forcedCharacterId: String? = null,
    ): List<String> {
        val first = when (group.turnMode) {
            com.localcharacter.app.domain.model.GroupTurnMode.ROUND_ROBIN -> selector.nextRoundRobin(recent, participants)
            else -> selector.decide(input, recent, participants, characters, group.turnMode, forcedCharacterId).characterId
        } ?: return emptyList()
        val shouldChain = input?.let(selector::asksForEveryone) == true
        if (!shouldChain || group.maxAutoResponses <= 1) return listOf(first)
        val active = participants.filter { it.enabled }.sortedBy { it.position }.map { it.characterId }
        val ordered = buildList {
            add(first)
            active.filter { it != first }.forEach { if (size < group.maxAutoResponses && size < group.maxBotChain) add(it) }
        }
        return ordered
    }
}
