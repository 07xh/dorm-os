package io.github.x07xh.xiaoxu.ui.v2

import kotlin.math.roundToInt

/** Logical indices are independent of screen direction; no business selection during drag. */
internal object UiV2SegmentDragPolicy {
    fun move(position: Float, deltaPx: Float, widthPx: Float, count: Int, rtl: Boolean): Float {
        if (count < 2 || widthPx <= 0f || !widthPx.isFinite() || !deltaPx.isFinite()) return position
        val delta = deltaPx / (widthPx / count) * if (rtl) -1f else 1f
        return (position + delta).coerceIn(0f, (count - 1).toFloat())
    }

    fun nearest(position: Float, count: Int): Int? =
        if (count <= 0 || !position.isFinite()) null
        else position.coerceIn(0f, (count - 1).toFloat()).roundToInt()
}
