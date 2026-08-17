package com.localcharacter.app.ui.chat

object ChatListPolicy {
    const val INITIAL_LIMIT = 100
    const val PAGE_SIZE = 100
    const val MAX_VISIBLE = 2_000
    fun nextLimit(current: Int): Int = (current + PAGE_SIZE).coerceAtMost(MAX_VISIBLE)
    fun isNearBottom(lastVisibleIndex: Int?, totalItems: Int, threshold: Int = 2): Boolean =
        totalItems == 0 || (lastVisibleIndex ?: 0) >= totalItems - threshold

    fun autoScrollTargetIndex(totalItems: Int): Int? =
        (totalItems - 1).takeIf { it >= 0 }

    /**
     * Room can publish the completed answer just before the transient streaming state is cleared.
     * Never compose both copies because LazyColumn requires globally unique item keys.
     */
    fun shouldRenderStreaming(persistedMessageIds: Iterable<String>, streamingMessageId: String?): Boolean =
        streamingMessageId != null && persistedMessageIds.none { it == streamingMessageId }
}

/** Coalesces native tokens into bounded UI snapshots without touching persistence. */
class StreamingTextBuffer(private val uiIntervalMillis: Long = 32L) {
    private val content = StringBuilder()
    private var lastEmissionMillis = 0L

    fun append(token: String, nowMillis: Long): String? {
        content.append(token)
        if (lastEmissionMillis == 0L || nowMillis - lastEmissionMillis >= uiIntervalMillis) {
            lastEmissionMillis = nowMillis
            return content.toString()
        }
        return null
    }

    fun completed(): String = content.toString().trimEnd()
}
