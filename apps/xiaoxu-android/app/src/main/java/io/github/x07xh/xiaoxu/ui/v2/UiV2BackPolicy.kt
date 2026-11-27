package io.github.x07xh.xiaoxu.ui.v2

/** Only the visible page moves. No parent page is composed for the preview. */
internal object UiV2BackPolicy {
    fun progress(raw: Float): Float = if (raw.isFinite()) raw.coerceIn(0f, 1f) else 0f
    fun scale(raw: Float): Float = 1f - 0.06f * progress(raw)
    fun translation(raw: Float, width: Float, fromRight: Boolean): Float =
        if (!width.isFinite() || width <= 0f) 0f
        else width * 0.08f * progress(raw) * if (fromRight) -1f else 1f
}
