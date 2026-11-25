package io.github.x07xh.xiaoxu.ui

// SPDX-License-Identifier: GPL-3.0-only
// Source: znjhahaha/zhengfang-apk @ bb3b80a6d64e4c2057c0544bb417696ceaa2a9ed, ui/system/NavScrollIntent.kt
// Original authors and contributors; package and existing shell insets adapted.

import kotlin.math.abs
import kotlin.math.sign

internal class NavScrollIntent {
    private var travel = 0f
    fun reset() { travel = 0f }

    fun scroll(deltaDp: Float): Boolean? {
        if (!deltaDp.isFinite() || deltaDp == 0f) return null
        if (travel.sign != deltaDp.sign) travel = 0f
        travel += deltaDp
        val threshold = if (travel < 0f) 40f else 20f
        if (abs(travel) < threshold) return null
        val minimized = travel < 0f
        travel = 0f
        return minimized
    }
}
