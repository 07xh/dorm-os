package io.github.x07xh.xiaoxu.ui.v2

/** Presentation-only window policy. No route state or business data. */
internal enum class UiV2Window { Compact, Medium, Expanded }

internal fun uiV2Window(widthDp: Float, heightDp: Float): UiV2Window = when {
    heightDp < 480f || widthDp < 600f -> UiV2Window.Compact
    widthDp < 840f -> UiV2Window.Medium
    else -> UiV2Window.Expanded
}
