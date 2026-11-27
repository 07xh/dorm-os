package io.github.x07xh.xiaoxu.ui

// SPDX-License-Identifier: GPL-3.0-only
// Source: znjhahaha/zhengfang-apk @ bb3b80a6d64e4c2057c0544bb417696ceaa2a9ed, ui/system/AppSymbol.kt
// Copyright upstream authors and contributors. Package adapted for Xiaoxu.

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class AppSymbolSpec(val outline: ImageVector) {
    Courses(Icons.AutoMirrored.Outlined.FormatListBulleted),
    Schedule(Icons.Outlined.DateRange),
    Grab(Icons.Outlined.MyLocation),
    Grades(Icons.Outlined.BarChart),
    Settings(Icons.Outlined.Settings)
}
