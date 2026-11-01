package xyz.dormos.nfc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TabNavigationMotionTest {
    @Test
    fun pagerKeepsExactlyThreeTabsInVisualOrder() {
        assertEquals(3, TAPFLOW_TAB_COUNT)
        assertEquals(listOf(0, 2, 1), (0 until TAPFLOW_TAB_COUNT).map(::tabIdForPage))
    }

    @Test
    fun visualOrderMatchesBottomNavigation() {
        assertEquals(0, tabNavigationOrder(0)) // Home
        assertEquals(1, tabNavigationOrder(2)) // Scenes
        assertEquals(2, tabNavigationOrder(1)) // Tools
    }

    @Test
    fun pagerMapsBackToOriginalTabId() {
        for (tab in listOf(0, 2, 1)) {
            assertEquals(tab, tabIdForPage(tabNavigationOrder(tab)))
        }
    }

    @Test
    fun directionIsConsistentForEveryAdjacentTabPair() {
        assertTrue(tabNavigationOrder(2) > tabNavigationOrder(0)) // Home → Scenes
        assertTrue(tabNavigationOrder(1) > tabNavigationOrder(2)) // Scenes → Tools
        assertTrue(tabNavigationOrder(0) < tabNavigationOrder(2)) // Scenes → Home
        assertTrue(tabNavigationOrder(2) < tabNavigationOrder(1)) // Tools → Scenes
    }
}
