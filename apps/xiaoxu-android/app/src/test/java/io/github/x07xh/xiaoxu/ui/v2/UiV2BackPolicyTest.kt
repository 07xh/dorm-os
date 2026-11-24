package io.github.x07xh.xiaoxu.ui.v2

import org.junit.Assert.assertEquals
import org.junit.Test

class UiV2BackPolicyTest {
    @Test fun cancelledOrInvalidProgressHasIdentityTransform() {
        assertEquals(1f, UiV2BackPolicy.scale(0f), 0.001f)
        assertEquals(1f, UiV2BackPolicy.scale(Float.NaN), 0.001f)
        assertEquals(0f, UiV2BackPolicy.translation(-1f, 1000f, false), 0.001f)
    }

    @Test fun boundedProgressAndPhysicalEdge() {
        assertEquals(0.94f, UiV2BackPolicy.scale(2f), 0.001f)
        assertEquals(40f, UiV2BackPolicy.translation(0.5f, 1000f, false), 0.001f)
        assertEquals(-40f, UiV2BackPolicy.translation(0.5f, 1000f, true), 0.001f)
        assertEquals(0f, UiV2BackPolicy.translation(1f, Float.NaN, true), 0.001f)
    }
}
