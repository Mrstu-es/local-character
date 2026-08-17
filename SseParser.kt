package com.localcharacter.app.llm.provider.network

import okio.BufferedSource

data class SseEvent(
    val event: String? = null,
    val data: String,
    val id: String? = null,
    val retryMillis: Long? = null,
)

/** Implements the SSE field and multi-line data rules; Okio handles arbitrary network chunk boundaries. */
object SseParser {
    fun events(source: BufferedSource): Sequence<SseEvent> = sequence {
        var event: String? = null
        var id: String? = null
        var retry: Long? = null
        val data = mutableListOf<String>()

        suspend fun SequenceScope<SseEvent>.flush() {
            if (data.isNotEmpty()) yield(SseEvent(event, data.joinToString("\n"), id, retry))
            event = null
            retry = null
            data.clear()
        }

        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: break
            if (line.isEmpty()) {
                flush()
                continue
            }
            if (line.startsWith(':')) continue
            val separator = line.indexOf(':')
            val field = if (separator < 0) line else line.substring(0, separator)
            val rawValue = if (separator < 0) "" else line.substring(separator + 1)
            val value = rawValue.removePrefix(" ")
            when (field) {
                "event" -> event = value
                "data" -> data += value
                "id" -> if ('\u0000' !in value) id = value
                "retry" -> retry = value.toLongOrNull()
            }
        }
        flush()
    }
}

