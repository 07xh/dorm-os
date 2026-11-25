package io.github.x07xh.xiaoxu.ui

// SPDX-License-Identifier: GPL-3.0-only
// Source: znjhahaha/zhengfang-apk @ bb3b80a6d64e4c2057c0544bb417696ceaa2a9ed, ui/theme/NavigationMotion.kt
// Copyright upstream authors and contributors.
object MotionProfile {
    const val IconMillis = 280
    const val PressMillis = MotionDuration.Fast
    const val DetailStaggerMillis = 35L
    const val HierarchyEnterDp = 22f
    const val HierarchyBehindDp = 6f
    const val SheetExitMillis = 220
    fun hierarchySpring() = androidx.compose.animation.core.spring<Float>(0.76f, 220f)
    fun sheetSpring() = androidx.compose.animation.core.spring<Float>(0.78f, 210f)
    fun pageSpring() = androidx.compose.animation.core.spring<Float>(0.74f, 180f)
    fun iconSpring() = androidx.compose.animation.core.spring<Float>(0.68f, 420f)
    fun pagerSpring() = MotionSpring.snappy<Float>()
}
