package io.github.x07xh.xiaoxu.ui.v2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiV2GlassMotionPolicyTest {
    @Test fun restingAndSlowMaterialIsUnchanged() {
        assertEquals(1f, UiV2GlassMotionPolicy.lensQuality(0f), 0.001f)
        assertEquals(1f, UiV2GlassMotionPolicy.lensQuality(599f), 0.001f)
        assertTrue(UiV2GlassMotionPolicy.allowChromaticFringe(599f))
    }
    @Test fun fastMotionReducesQualitySmoothlyAndSymmetrically() {
        assertEquals(0.75f, UiV2GlassMotionPolicy.lensQuality(900f), 0.001f)
        assertEquals(0.75f, UiV2GlassMotionPolicy.lensQuality(-900f), 0.001f)
        assertEquals(0.5f, UiV2GlassMotionPolicy.lensQuality(1500f), 0.001f)
        assertFalse(UiV2GlassMotionPolicy.allowChromaticFringe(600f))
        assertFalse(UiV2GlassMotionPolicy.allowChromaticFringe(-1500f))
    }
    @Test fun invalidVelocityNeverProducesInvalidShaderParameter() {
        assertEquals(1f, UiV2GlassMotionPolicy.lensQuality(Float.NaN), 0.001f)
        assertEquals(0f, UiV2GlassMotionPolicy.motionFraction(Float.POSITIVE_INFINITY), 0.001f)
        assertFalse(UiV2GlassMotionPolicy.allowChromaticFringe(Float.NaN))
    }
}
