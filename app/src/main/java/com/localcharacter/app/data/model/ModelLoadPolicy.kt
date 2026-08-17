package com.localcharacter.app.data.model

object ModelLoadPolicy {
    private const val MOBILE_CONTEXT_LIMIT = 2048

    /** Model metadata is a maximum capability, not a request to allocate the whole context on a phone. */
    fun effectiveContextSize(configured: Int, declaredMaximum: Int?): Int {
        val maximum = declaredMaximum?.coerceAtLeast(512) ?: configured.coerceAtLeast(512)
        // 4K used 448 MiB of KV for Qwen3 0.6B on the reference phone. A 2K cap keeps
        // enough conversation history while preventing model + KV from being swapped.
        return configured.coerceAtMost(MOBILE_CONTEXT_LIMIT).coerceIn(512, maximum)
    }

    /** Native inference must leave CPU capacity for Compose, input and Android system work. */
    fun effectiveThreads(configured: Int, availableProcessors: Int): Int {
        val uiReserved = if (availableProcessors >= 6) 2 else 1
        val safeMaximum = (availableProcessors - uiReserved).coerceAtLeast(1).coerceAtMost(4)
        return configured.coerceIn(1, safeMaximum)
    }

    /** One mobile prefill batch avoids evaluating the complete model repeatedly for a short prompt. */
    fun effectiveBatchSize(configured: Int): Int = configured.coerceIn(32, 512)
}
