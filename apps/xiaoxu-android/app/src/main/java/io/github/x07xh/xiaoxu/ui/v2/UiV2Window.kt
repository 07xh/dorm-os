package io.github.x07xh.xiaoxu.ui.v2

/** Presentation-only window policy. No route state or business data. */
internal enum class UiV2Window { Compact, Medium, Expanded }

internal fun uiV2Window(widthDp: Float, heightDp: Float): UiV2Window = when {
    heightDp < 480f || widthDp < 600f -> UiV2Window.Compact
    widthDp < 840f -> UiV2Window.Medium
    else -> UiV2Window.Expanded
}

/**
 * Resolve a split from the actual available content width, not device identity.
 * Primary content keeps its minimum; the detail shrinks first, then folds away.
 */
internal fun uiV2DetailWidth(
    widthDp: Float, heightDp: Float, mainMin: Float, detailMin: Float,
    detailMax: Float, gap: Float, horizontalPadding: Float = 0f
): Float? {
    if (!widthDp.isFinite() || !heightDp.isFinite() ||
        uiV2Window(widthDp, heightDp) != UiV2Window.Expanded) return null
    val room = widthDp - horizontalPadding - gap - mainMin
    if (room < detailMin) return null
    return (widthDp * 0.34f).coerceIn(detailMin, detailMax).coerceAtMost(room)
}
