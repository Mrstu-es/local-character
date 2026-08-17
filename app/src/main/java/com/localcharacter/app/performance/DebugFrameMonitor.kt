package com.localcharacter.app.performance

import android.util.Log
import android.view.Choreographer
import com.localcharacter.app.AppBuildInfo

/** Lightweight debug-only frame probe; it never schedules callbacks in release builds. */
class DebugFrameMonitor : Choreographer.FrameCallback {
    private var running = false
    private var previousFrameNanos = 0L
    private var slowFrameCount = 0

    fun start() {
        if (!AppBuildInfo.DEBUG || running) return
        running = true
        previousFrameNanos = 0L
        Choreographer.getInstance().postFrameCallback(this)
    }

    fun stop() {
        if (!running) return
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        if (previousFrameNanos != 0L) {
            val durationMs = (frameTimeNanos - previousFrameNanos) / 1_000_000.0
            if (durationMs >= 32.0) {
                slowFrameCount++
                Log.d("LocalPerformance", "Frame lento #$slowFrameCount: %.1f ms".format(durationMs))
            }
        }
        previousFrameNanos = frameTimeNanos
        Choreographer.getInstance().postFrameCallback(this)
    }
}
