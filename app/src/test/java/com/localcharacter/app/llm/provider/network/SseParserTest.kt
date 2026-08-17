package com.localcharacter.app.llm.provider.network

import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Test

class SseParserTest {
    @Test fun parsesCommentsMultilineDataIdsAndFinalEventWithoutBlankLine() {
        val source = Buffer().writeUtf8(
            ": keepalive\n" +
                "id: 7\n" +
                "event: delta\n" +
                "retry: 1500\n" +
                "data: {\"part\":1}\n" +
                "data: {\"part\":2}\n\n" +
                "data: [DONE]",
        )
        val events = SseParser.events(source).toList()
        assertEquals(2, events.size)
        assertEquals("delta", events[0].event)
        assertEquals("{\"part\":1}\n{\"part\":2}", events[0].data)
        assertEquals("7", events[0].id)
        assertEquals(1500L, events[0].retryMillis)
        assertEquals("[DONE]", events[1].data)
        assertEquals("7", events[1].id)
    }
}
