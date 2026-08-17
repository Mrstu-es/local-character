package com.localcharacter.app.ui.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterChatTopBarLayoutTest {
    @Test
    fun narrowWidthMovesActionsToMenuAndKeepsTitleSpace() {
        val metrics = chatTopBarMetrics(320.dp)

        assertFalse(metrics.showInlineActions)
        assertFalse(metrics.showSubtitle)
        assertTrue(metrics.titleMaxWidth >= 72.dp)
    }

    @Test
    fun normalPhoneShowsInlineActionsAndSubtitle() {
        val metrics = chatTopBarMetrics(393.dp)

        assertTrue(metrics.showInlineActions)
        assertTrue(metrics.showSubtitle)
        assertTrue(metrics.titleMaxWidth > chatTopBarMetrics(320.dp).titleMaxWidth)
    }

    @Test
    fun expandedWidthUsesLargerAvatarAndMoreTitleSpace() {
        val phone = chatTopBarMetrics(393.dp)
        val tablet = chatTopBarMetrics(840.dp)

        assertTrue(tablet.avatarSize > phone.avatarSize)
        assertTrue(tablet.titleMaxWidth > phone.titleMaxWidth)
        assertTrue(tablet.minHeight >= phone.minHeight)
    }
}
