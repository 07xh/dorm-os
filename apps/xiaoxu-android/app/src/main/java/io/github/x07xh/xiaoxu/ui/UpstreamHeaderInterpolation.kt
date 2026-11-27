package io.github.x07xh.xiaoxu.ui

// SPDX-License-Identifier: GPL-3.0-only
// Source: znjhahaha/zhengfang-apk @ bb3b80a6d64e4c2057c0544bb417696ceaa2a9ed, ui/system/CollapsingGlassHeader.kt
// Copyright original authors and contributors; adapted for Xiaoxu.
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp

internal fun lerpDp(start: Dp, stop: Dp, fraction: Float): Dp =
    start + (stop - start) * fraction.coerceIn(0f, 1f)

internal fun lerpSp(startSp: Float, stopSp: Float, fraction: Float) =
    (startSp + (stopSp - startSp) * fraction.coerceIn(0f, 1f)).sp
