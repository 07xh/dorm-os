package io.github.x07xh.xiaoxu.ui.v2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UiV2SegmentDragPolicyTest {
    @Test fun dragUsesLogicalDirectionAndClamps() {
        assertEquals(2f, UiV2SegmentDragPolicy.move(1f, 100f, 400f, 4, false), 0.001f)
        assertEquals(0f, UiV2SegmentDragPolicy.move(1f, 100f, 400f, 4, true), 0.001f)
        assertEquals(3f, UiV2SegmentDragPolicy.move(1f, 999f, 400f, 4, false), 0.001f)
        assertEquals(1f, UiV2SegmentDragPolicy.move(1f, 20f, 0f, 4, false), 0.001f)
    }
    @Test fun releaseSnapsToNearestValidIndex() {
        assertEquals(1, UiV2SegmentDragPolicy.nearest(1.49f, 4))
        assertEquals(2, UiV2SegmentDragPolicy.nearest(1.5f, 4))
        assertEquals(3, UiV2SegmentDragPolicy.nearest(99f, 4))
        assertNull(UiV2SegmentDragPolicy.nearest(Float.NaN, 4))
        assertNull(UiV2SegmentDragPolicy.nearest(0f, 0))
    }
}
