package com.localcharacter.app.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatPerformancePolicyTest {
    @Test
    fun `five hundred messages remain paged instead of entering the first composition`() {
        val messages = List(500) { "message-$it" }
        val firstPage = messages.takeLast(ChatListPolicy.INITIAL_LIMIT)
        assertEquals(100, firstPage.size)
        assertEquals(200, ChatListPolicy.nextLimit(firstPage.size))
    }

    @Test
    fun `one thousand fast tokens emit bounded UI snapshots and preserve final text`() {
        val buffer = StreamingTextBuffer(32)
        var emissions = 0
        repeat(1_000) { index -> if (buffer.append("x", index.toLong() + 1) != null) emissions++ }
        assertTrue(emissions <= 33)
        assertEquals(1_000, buffer.completed().length)
    }

    @Test fun `autoscroll follows bottom but respects a user reading older messages`() {
        assertTrue(ChatListPolicy.isNearBottom(99, 100))
        assertTrue(!ChatListPolicy.isNearBottom(40, 100))
        assertEquals(99, ChatListPolicy.autoScrollTargetIndex(100))
        assertEquals(null, ChatListPolicy.autoScrollTargetIndex(0))
    }

    @Test fun `completed Room message suppresses matching transient streaming item`() {
        assertTrue(ChatListPolicy.shouldRenderStreaming(listOf("saved"), "streaming"))
        assertTrue(!ChatListPolicy.shouldRenderStreaming(listOf("saved", "streaming"), "streaming"))
        assertTrue(!ChatListPolicy.shouldRenderStreaming(listOf("saved"), null))
    }
}
