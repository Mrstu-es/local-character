package com.localcharacter.app.llm

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class LlmTaskPriority(val rank: Int) { SUMMARY(0), MEMORY(1), CHAT_GENERATION(2) }

/** Serializes access to the single llama.cpp context and lets chat preempt background work. */
class LlmTaskQueue(private val engine: LlmEngine) {
    private data class ActiveTask(val id: Long, val priority: LlmTaskPriority, val job: Job)

    private val execution = Mutex()
    private val state = Mutex()
    private val sequence = AtomicLong()
    private var active: ActiveTask? = null

    suspend fun <T> run(priority: LlmTaskPriority, block: suspend () -> T): T {
        preemptLowerPriority(priority)
        return execution.withLock {
            val task = ActiveTask(sequence.incrementAndGet(), priority, currentCoroutineContext()[Job]!!)
            state.withLock { active = task }
            try {
                block()
            } finally {
                state.withLock { if (active?.id == task.id) active = null }
            }
        }
    }

    private suspend fun preemptLowerPriority(incoming: LlmTaskPriority) {
        val task = state.withLock { active?.takeIf { it.priority.rank < incoming.rank } } ?: return
        engine.stopGeneration()
        task.job.cancel(CancellationException("Preempted by ${incoming.name}"))
    }
}
