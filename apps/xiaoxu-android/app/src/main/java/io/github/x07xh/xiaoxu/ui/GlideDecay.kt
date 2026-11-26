package io.github.x07xh.xiaoxu.ui

// SPDX-License-Identifier: GPL-3.0-only
// Source: znjhahaha/zhengfang-apk @ bb3b80a6d64e4c2057c0544bb417696ceaa2a9ed, ui/system/glass/GlideDecay.kt.
// Original authors and contributors.

import kotlin.math.abs
import kotlin.math.pow

internal fun decayGlideVelocity(velocity: Float, elapsedSeconds: Float): Float {
    if (!velocity.isFinite()) return 0f
    val decayed = velocity * 0.78f.pow(elapsedSeconds.coerceAtLeast(0f) * 60f)
    return if (abs(decayed) < 0.006f) 0f else decayed
}
