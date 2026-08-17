package com.localcharacter.app.domain.group

import com.localcharacter.app.domain.model.Character
import com.localcharacter.app.domain.model.GroupMessage
import com.localcharacter.app.domain.model.GroupMessageRole
import com.localcharacter.app.domain.model.GroupParticipant
import com.localcharacter.app.domain.model.GroupTurnMode

data class SpeakerCandidate(val characterId: String, val score: Float, val reason: String)
data class SpeakerDecision(
    val characterId: String?,
    val confidence: Float,
    val reason: String,
    val candidates: List<SpeakerCandidate> = emptyList(),
    val allowChain: Boolean = false,
)

/** Fast, deterministic V1 selector. It resolves obvious context locally and never uses random as the primary rule. */
class GroupSpeakerSelector {
    fun decide(
        input: String?,
        recentMessages: List<GroupMessage>,
        participants: List<GroupParticipant>,
        characters: List<Character>,
        mode: GroupTurnMode,
        forcedCharacterId: String? = null,
    ): SpeakerDecision {
        val active = participants.filter { it.enabled }.sortedBy { it.position }
            .filter { item -> characters.any { it.id == item.characterId } }
        if (active.isEmpty()) return SpeakerDecision(null, 0f, "no active participants")
        forcedCharacterId?.takeIf { id -> active.any { it.characterId == id } }?.let {
            return SpeakerDecision(it, 1f, "manual selection", listOf(SpeakerCandidate(it, 1f, "manual")))
        }
        if (mode == GroupTurnMode.MANUAL) return SpeakerDecision(null, 0f, "manual selection required")
        val normalized = input.orEmpty().lowercase()
        val scored = active.map { participant ->
            val character = characters.first { it.id == participant.characterId }
            val name = character.name.lowercase()
            var score = 0f
            var reason = "context"
            if (normalized.contains("@$name") || Regex("\\b${Regex.escape(name)}\\b").containsMatchIn(normalized)) {
                score += 1f
                reason = "direct mention"
            }
            if (normalized.contains("qué opinan") || normalized.contains("que opinan") ||
                normalized.contains("qué piensan") || normalized.contains("que piensan") ||
                normalized.contains("ustedes") || normalized.contains("todos")) {
                score += 0.12f
                reason = "group request"
            }
            val lastByCharacter = recentMessages.indexOfLast { it.senderCharacterId == participant.characterId }
            if (lastByCharacter >= 0) {
                score += (lastByCharacter.toFloat() / recentMessages.size.coerceAtLeast(1)) * 0.35f
                if (reason == "context") reason = "recent context"
            }
            if (recentMessages.lastOrNull()?.senderCharacterId == participant.characterId) score -= 0.16f
            score -= participant.messageCount.coerceAtMost(20) * 0.005f
            SpeakerCandidate(participant.characterId, score, reason)
        }.sortedByDescending { it.score }
        val best = scored.first()
        val confidence = if (best.score >= 0.75f) 0.95f else if (best.score >= 0.2f) 0.72f else 0.55f
        return SpeakerDecision(best.characterId, confidence, best.reason, scored, allowChain = asksForEveryone(normalized))
    }

    fun asksForEveryone(text: String): Boolean {
        val normalized = text.lowercase()
        return listOf("qué opinan", "que opinan", "qué piensan todos", "que piensan todos", "ustedes qué harían", "ustedes que harian", "quiero escuchar a todos").any(normalized::contains)
    }

    fun nextRoundRobin(recentMessages: List<GroupMessage>, participants: List<GroupParticipant>): String? {
        val active = participants.filter { it.enabled }.sortedBy { it.position }
        if (active.isEmpty()) return null
        val last = recentMessages.lastOrNull { it.role == GroupMessageRole.CHARACTER }?.senderCharacterId
        val index = active.indexOfFirst { it.characterId == last }
        return active[if (index < 0) 0 else (index + 1) % active.size].characterId
    }
}
