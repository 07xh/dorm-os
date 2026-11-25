package io.github.x07xh.xiaoxu.ui

// SPDX-License-Identifier: GPL-3.0-only
// Source: znjhahaha/zhengfang-apk @ bb3b80a6d64e4c2057c0544bb417696ceaa2a9ed, schedule/ScheduleDetails.kt
// Copyright original authors and contributors. Adapted package and local data range.
data class ScheduleDetail(val id: String, val label: String, val value: String, val weeks: List<Int>? = null)
