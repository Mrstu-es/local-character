package com.localcharacter.app.performance

import android.os.SystemClock
import android.util.Log
import com.localcharacter.app.AppBuildInfo

/** Debug-only navigation trace. It measures request -> composition, not model inference. */
object NavigationPerformance {
    @Volatile private var lastStart = 0L

    fun requested(route: String) {
        if (!AppBuildInfo.DEBUG) return
        lastStart = SystemClock.elapsedRealtime()
        Log.d("LocalNavigation", "tap route=$route")
    }

    fun composed(route: String) {
        if (!AppBuildInfo.DEBUG) return
        val elapsed = lastStart.takeIf { it > 0 }?.let { SystemClock.elapsedRealtime() - it }
        Log.d("LocalNavigation", "firstComposition route=$route elapsedMs=${elapsed ?: "unknown"}")
    }

    fun dataReady(route: String, itemCount: Int) {
        if (!AppBuildInfo.DEBUG) return
        Log.d("LocalNavigation", "dataReady route=$route items=$itemCount")
    }
}
