package com.localcharacter.app.domain.memory

import com.localcharacter.app.domain.model.ChatMessage
import com.localcharacter.app.domain.model.Memory
import com.localcharacter.app.domain.model.MemoryType
import kotlin.math.exp
import kotlin.math.ln

data class ScoredMemory(val memory: Memory, val score: Float)

interface MemorySearchEngine {
    fun score(query: String, recentConversation: String, memory: Memory, now: Long): Float
}

class KeywordMemorySearchEngine : MemorySearchEngine {
    override fun score(query: String, recentConversation: String, memory: Memory, now: Long): Float {
        val queryTokens = MemoryTextNormalizer.tokens(query)
        val recentTokens = MemoryTextNormalizer.tokens(recentConversation)
        val memoryTokens = MemoryTextNormalizer.tokens(memory.normalizedContent)
        val direct = overlap(queryTokens, memoryTokens)
        val contextual = overlap(recentTokens, memoryTokens)
        val properNameBoost = namedTokens(query).intersect(namedTokens(memory.content)).let { if (it.isEmpty()) 0f else 0.28f }
        val ageDays = ((now - memory.updatedAt).coerceAtLeast(0L) / 86_400_000.0)
        val recency = exp(-ageDays / 45.0).toFloat()
        val access = (ln((memory.accessCount + 1).toDouble()) / 10.0).toFloat().coerceAtMost(0.12f)
        val relationshipBoost = if (memory.type == MemoryType.RELATIONSHIP && direct > 0f) 0.12f else 0f
        return (
            direct * 0.48f + contextual * 0.12f + memory.importance * 0.2f + recency * 0.08f +
                properNameBoost + relationshipBoost + access + if (memory.isPinned) 0.18f else 0f
            ).coerceIn(0f, 1.5f)
    }

    private fun overlap(left: Set<String>, right: Set<String>): Float {
        if (left.isEmpty() || right.isEmpty()) return 0f
        return left.intersect(right).size.toFloat() / left.size.coerceAtMost(right.size).coerceAtLeast(1)
    }

    private fun namedTokens(text: String): Set<String> = Regex("(?<![.!?]\\s)(?<!^)[A-ZÁÉÍÓÚÜÑ][a-záéíóúüñ]{2,}")
        .findAll(text).map { MemoryTextNormalizer.ascii(it.value) }.toSet()
}

class MemoryRetriever(
    private val searchEngine: MemorySearchEngine = KeywordMemorySearchEngine(),
) {
    fun retrieve(
        currentMessage: String,
        recentMessages: List<ChatMessage>,
        candidates: List<Memory>,
        limit: Int = 8,
        now: Long = System.currentTimeMillis(),
    ): List<ScoredMemory> {
        val recent = recentMessages.takeLast(8).joinToString(" ") { it.content }
        return candidates.asSequence()
            .filter { it.isActive && (it.expiresAt == null || it.expiresAt > now) }
            .map { ScoredMemory(it, searchEngine.score(currentMessage, recent, it, now)) }
            .filter { it.memory.isPinned || it.score >= 0.28f }
            .sortedWith(compareByDescending<ScoredMemory> { it.memory.isPinned }.thenByDescending { it.score })
            .take(limit.coerceIn(1, 20))
            .toList()
    }
}
