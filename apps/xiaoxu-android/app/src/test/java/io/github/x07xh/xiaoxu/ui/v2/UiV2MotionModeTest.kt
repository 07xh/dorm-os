package io.github.x07xh.xiaoxu.ui.v2

import org.junit.Assert.assertEquals
import org.junit.Test

class UiV2MotionModeTest {
    @Test fun preferencesHaveSafeFallback() {
        assertEquals(UiV2MotionMode.Standard, UiV2MotionMode.parse(null))
        assertEquals(UiV2MotionMode.Standard, UiV2MotionMode.parse("unknown"))
        assertEquals(UiV2MotionMode.Fluid, UiV2MotionMode.parse("Fluid"))
    }
    @Test fun systemReduceMotionOverridesFluid() {
        assertEquals(UiV2MotionMode.Reduced, uiV2EffectiveMotion(UiV2MotionMode.Fluid, 0f))
        assertEquals(UiV2MotionMode.Fluid, uiV2EffectiveMotion(UiV2MotionMode.Fluid, 1f))
        assertEquals(UiV2MotionMode.Reduced, uiV2EffectiveMotion(UiV2MotionMode.Reduced, 1f))
    }
}
