package io.github.x07xh.xiaoxu.ui

// SPDX-License-Identifier: GPL-3.0-only
// Source: znjhahaha/zhengfang-apk @ bb3b80a6d64e4c2057c0544bb417696ceaa2a9ed, ui/screen/ScheduleFocus.kt
// Copyright original authors and contributors. Adapted package and local data range.

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.focus.FocusRequester

class ScheduleFocusRegistry {
    private val requesters = mutableMapOf<String, FocusRequester>()
    private val positions = mutableMapOf<String, androidx.compose.ui.geometry.Rect>()
    fun place(id: String, bounds: androidx.compose.ui.geometry.Rect) { positions[id] = bounds }
    fun bounds(id: String) = positions[id]
    fun register(id: String, requester: FocusRequester) { requesters[id] = requester }
    fun remove(id: String, requester: FocusRequester) {
        if (requesters[id] === requester) { requesters.remove(id); positions.remove(id) }
    }
    fun restore(id: String) { runCatching { (requesters[id] ?: requesters["header"])?.requestFocus() } }
}
val LocalScheduleFocus = staticCompositionLocalOf<ScheduleFocusRegistry?> { null }
