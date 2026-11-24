package io.github.x07xh.xiaoxu.ui.v2

/** Presentation rules only. All dismissal paths honor the caller's guard. */
internal object UiV2SheetPolicy {
    const val HeightFraction = 0.88f
    const val CloseFraction = 0.5f
    const val FlingDpPerSecond = 700f
    const val GutterDp = 20
    const val CornerDp = 28
    const val HandleWidthDp = 36
    const val HandleHeightDp = 4

    fun shouldDismiss(
        enabled: Boolean, offsetPx: Float, heightPx: Float,
        velocityPx: Float, flingPx: Float
    ): Boolean = enabled && heightPx > 0f && offsetPx > 0f &&
        (offsetPx > heightPx * CloseFraction || velocityPx > flingPx)
}
