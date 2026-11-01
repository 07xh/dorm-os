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
    fun directionIsConsistentForEveryAdjacentTabPair() {
        assert(tabNavigationOrder(2) > tabNavigationOrder(0)) // Home → Scenes
        assert(tabNavigationOrder(1) > tabNavigationOrder(2)) // Scenes → Tools
        assert(tabNavigationOrder(0) < tabNavigationOrder(2)) // Scenes → Home
        assert(tabNavigationOrder(2) < tabNavigationOrder(1)) // Tools → Scenes
    }
}
