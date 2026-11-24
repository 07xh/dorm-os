package io.github.x07xh.xiaoxu.ui.v2

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiV2SheetPolicyTest {
    @Test fun dismissalGuardWinsOverDistanceAndVelocity() {
        assertFalse(UiV2SheetPolicy.shouldDismiss(false, 800f, 1000f, 3000f, 1400f))
    }
    @Test fun dragOrFlingClosesOnlyWithPositiveOffset() {
        assertTrue(UiV2SheetPolicy.shouldDismiss(true, 501f, 1000f, 0f, 1400f))
        assertTrue(UiV2SheetPolicy.shouldDismiss(true, 100f, 1000f, 1401f, 1400f))
        assertFalse(UiV2SheetPolicy.shouldDismiss(true, 500f, 1000f, 1400f, 1400f))
        assertFalse(UiV2SheetPolicy.shouldDismiss(true, 0f, 1000f, 2000f, 1400f))
        assertFalse(UiV2SheetPolicy.shouldDismiss(true, 100f, 0f, 2000f, 1400f))
    }
}
