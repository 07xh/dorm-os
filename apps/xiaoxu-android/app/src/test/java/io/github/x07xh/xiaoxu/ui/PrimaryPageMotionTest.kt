package io.github.x07xh.xiaoxu.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import org.junit.Assert.assertEquals
import org.junit.Test

/** Regression: all native page switches are immediate and must not reintroduce lag. */
class PrimaryPageMotionTest {
    @Test fun mainTabsHaveNoEnterOrExitAnimation() {
        for (forward in listOf(true, false)) {
            val transition = xiaoxuPrimaryPageTransition(forward)
            assertEquals(EnterTransition.None, transition.targetContentEnter)
            assertEquals(ExitTransition.None, transition.initialContentExit)
        }
    }

    @Test fun everySecondaryRouteUsesImmediateTransition() {
        for ((from, to) in listOf(
            "home" to "notifications",
            "notifications" to "home",
            "home" to "timetable-settings",
            "timetable-settings" to "home",
            "home" to "detail",
            "detail" to "home",
            "home" to "home"
        )) {
            val transition = xiaoxuSettingsRouteTransition(from, to)
            assertEquals(EnterTransition.None, transition.targetContentEnter)
            assertEquals(ExitTransition.None, transition.initialContentExit)
        }
    }
}
