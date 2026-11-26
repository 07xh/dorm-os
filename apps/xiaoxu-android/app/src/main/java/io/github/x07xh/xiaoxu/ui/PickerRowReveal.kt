package io.github.x07xh.xiaoxu.ui

// SPDX-License-Identifier: GPL-3.0-only
// Upstream: znjhahaha/zhengfang-apk@bb3b80a6d64e4c2057c0544bb417696ceaa2a9ed, app/src/main/java/com/tyust/course/ui/system/PickerRowReveal.kt.
// Original authors and contributors. See upstream license and attribution.

/** Only the initial viewport staggers. Scroll position and list length never delay later rows. */
internal fun pickerRowReveal(index: Int, first: Int, count: Int, expanded: Boolean,
    settled: Boolean, reducedMotion: Boolean, seconds: Float, travel: Float, extent: Float): Float {
    fun smooth(value: Float): Float { val t = value.coerceIn(0f, 1f); return t * t * (3f - 2f * t) }
    if (reducedMotion) return if (expanded) 1f else 0f
    if (!expanded) return smooth((extent - 0.015f) / 0.11f)
    if (settled || index !in first until first + count) return 1f
    val delay = ((index - first) * 0.018f).coerceAtMost(0.09f)
    return minOf(smooth((seconds - 0.22f - delay) / 0.20f), smooth((travel - 0.50f) / 0.34f))
}
