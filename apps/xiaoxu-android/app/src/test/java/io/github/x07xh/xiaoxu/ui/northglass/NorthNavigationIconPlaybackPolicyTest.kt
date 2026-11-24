package io.github.x07xh.xiaoxu.ui.northglass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NorthNavigationIconPlaybackPolicyTest {
    @Test fun firstSelectionKeepsOnlyEnteringIcon() {
        assertEquals(listOf(1,2,3,4), retiredNavigationIcons(5,-1,0))
    }

    @Test fun rapidSwitchKeepsOnlyCurrentAndImmediateOutgoingIcon() {
        val sequence = listOf(0,1,2,4,3,0,2,1)
        for ((previous,next) in sequence.zipWithNext()) {
            val remaining = (0 until 5).toSet() -
                retiredNavigationIcons(5,previous,next).toSet()
            assertEquals(setOf(previous,next),remaining)
            assertTrue(remaining.size <= 2)
        }
    }

    @Test fun sameSelectionDoesNotRetainUnrelatedIcons() {
        assertEquals(listOf(0,1,3,4),retiredNavigationIcons(5,2,2))
        assertTrue(retiredNavigationIcons(1,0,0).isEmpty())
    }
}
