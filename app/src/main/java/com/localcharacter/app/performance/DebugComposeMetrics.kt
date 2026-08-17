package com.localcharacter.app.performance

import android.util.Log
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import com.localcharacter.app.AppBuildInfo
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun DebugListMetrics(screen: String, itemCount: Int, listState: LazyListState) {
    if (!AppBuildInfo.DEBUG) return
    val recompositions = remember(screen) { AtomicInteger() }
    val currentItemCount by rememberUpdatedState(itemCount)
    SideEffect {
        val count = recompositions.incrementAndGet()
        if (count == 1 || count % 25 == 0) {
            Log.d("LocalPerformance", "screen=$screen recompositions=$count items=$currentItemCount")
        }
    }
    LaunchedEffect(screen, listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.size }
            .distinctUntilChanged()
            .collect { visible -> Log.d("LocalPerformance", "screen=$screen visibleItems=$visible totalItems=$currentItemCount") }
    }
}
