package xyz.dormos.nfc

import org.junit.Assert.assertEquals
import org.junit.Test

class TabNavigationMotionTest {
    @Test
    fun visualOrderMatchesBottomNavigation() {
        assertEquals(0, tabNavigationOrder(0)) // Home
        assertEquals(1, tabNavigationOrder(2)) // Scenes
        assertEquals(2, tabNavigationOrder(1)) // Tools
    }

    @Test
    fun movingToTheRightTabEntersFromTheRight() {
        assertEquals(1, tabTransitionDirection(0, 2)) // Home -> Scenes
        assertEquals(1, tabTransitionDirection(2, 1)) // Scenes -> Tools
        assertEquals(1, tabTransitionDirection(0, 1)) // Home -> Tools
    }

    @Test
    fun movingToTheLeftTabEntersFromTheLeft() {
        assertEquals(-1, tabTransitionDirection(2, 0)) // Scenes -> Home
        assertEquals(-1, tabTransitionDirection(1, 2)) // Tools -> Scenes
        assertEquals(-1, tabTransitionDirection(1, 0)) // Tools -> Home
    }

    @Test
    fun selectingCurrentTabDoesNotCreateDirection() {
        assertEquals(0, tabTransitionDirection(0, 0))
        assertEquals(0, tabTransitionDirection(2, 2))
        assertEquals(0, tabTransitionDirection(1, 1))
    }

    @Test
    fun referenceMotionUsesShortBoundedDurations() {
        assertEquals(210, TAPFLOW_PAGE_TRANSITION_MS)
        assertEquals(185, TAPFLOW_BUTTON_TRANSITION_MS)
        assertEquals(64, TAPFLOW_BOTTOM_NAV_HEIGHT_DP)
    }
}
