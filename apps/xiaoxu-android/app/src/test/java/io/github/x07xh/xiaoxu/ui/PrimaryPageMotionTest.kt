package io.github.x07xh.xiaoxu.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * JVM contract for the native page motion.
 *
 * The visual flicker in 12024.mp4 occurred when the outgoing page was faded
 * completely out before the next page began drawing. Keep an outgoing screen
 * in the composition until its full-size incoming screen has covered it.
 */
class PrimaryPageMotionTest {
    @Test
    fun primaryTabsKeepOldPageUntilNewPageIsVisible() {
        for (forward in listOf(true, false)) {
            val transition = xiaoxuPrimaryPageTransition(forward)
            assertEquals(ExitTransition.None, transition.initialContentExit)
            assertEquals(1f, transition.targetContentZIndex, 0f)
            assertNotEquals(EnterTransition.None, transition.targetContentEnter)
        }
    }

    @Test
    fun settingAndAcademicChildRoutesNeverFadeOutTheOldScreen() {
        for ((from, to) in listOf(
            "home" to "notifications",
            "notifications" to "home",
            "home" to "timetable-settings",
            "timetable-settings" to "home",
            "home" to "detail",
            "detail" to "home"
        )) {
            val transition = xiaoxuSettingsRouteTransition(from, to)
            assertEquals(ExitTransition.None, transition.initialContentExit)
            assertEquals(1f, transition.targetContentZIndex, 0f)
            assertNotEquals(EnterTransition.None, transition.targetContentEnter)
        }
    }

    @Test
    fun unchangedRouteDoesNotReplayThePageTransition() {
        val transition = xiaoxuSettingsRouteTransition("home", "home")
        assertEquals(EnterTransition.None, transition.targetContentEnter)
        assertEquals(ExitTransition.None, transition.initialContentExit)
    }
}
