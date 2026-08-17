package com.localcharacter.app.performance

import android.util.Log
import com.localcharacter.app.AppBuildInfo
import java.util.concurrent.atomic.AtomicInteger

object DebugImageMetrics {
    private val starts = AtomicInteger()
    fun onStart(data: Any?) {
        if (!AppBuildInfo.DEBUG) return
        val count = starts.incrementAndGet()
        if (count == 1 || count % 25 == 0) Log.d("LocalPerformance", "imageLoads=$count data=$data")
    }
    fun onError(data: Any?, error: Throwable) {
        if (AppBuildInfo.DEBUG) Log.d("LocalPerformance", "imageError data=$data detail=${error.message}")
    }
}
