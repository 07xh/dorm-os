package io.github.x07xh.xiaoxu.ui

// SPDX-License-Identifier: GPL-3.0-only
// Source: znjhahaha/zhengfang-apk @ bb3b80a6d64e4c2057c0544bb417696ceaa2a9ed, ui/screen/ScheduleGlass.kt
// Copyright upstream authors and contributors. Theme binding only.
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

/** Readable, translucent course surfaces; scrolling cards never capture a backdrop. */
@Composable
internal fun scheduleCardColor(accent: Color, amount: Float): Color {
    val surface = MaterialTheme.colorScheme.surface
    return lerp(surface, accent.copy(alpha = surface.alpha), amount)
}

@Composable
internal fun scheduleCardBorder(accent: Color, emphasized: Boolean = false): BorderStroke {
    val light = !isSystemInDarkTheme()
    return BorderStroke(0.8.dp, Brush.linearGradient(listOf(
        Color.White.copy(alpha = if (light) 0.86f else 0.28f),
        accent.copy(alpha = if (emphasized) 0.46f else 0.15f),
        Color.White.copy(alpha = if (light) 0.48f else 0.12f)
    )))
}
