package com.localcharacter.app.ui.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppTopBarLayoutPolicyTest {
    @Test
    fun mainScreenTopBarsShareACompactHeight() {
        assertEquals(56f, AppDimensions.MainTopBarMinHeight.value, 0f)
        assertTrue(AppDimensions.MainTopBarMinHeight < 72.dp)
    }

    @Test
    fun homeAndDetailBarsUseSemanticAccessibleHeights() {
        assertEquals(64f, AppDimensions.HomeTopBarMinHeight.value, 0f)
        assertEquals(56f, AppDimensions.DetailTopBarMinHeight.value, 0f)
        assertEquals(48f, AppDimensions.TopBarTouchTarget.value, 0f)
    }
}
