package io.github.x07xh.xiaoxu.ui

// SPDX-License-Identifier: GPL-3.0-only
// Source: znjhahaha/zhengfang-apk @ bb3b80a6d64e4c2057c0544bb417696ceaa2a9ed, ui/screen/ScheduleHeader.kt
// Original authors and contributors. Package and imports adapted.
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer

@Composable
internal fun ScheduleWeekLabel(week: Int, next: Boolean, style: TextStyle, color: Color) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val width = remember(style, density, measurer) {
        with(density) { (1..ScheduleMaxWeeks).maxOf { measurer.measure(it.toString(), style).size.width }.toDp() }
    }
    Row(Modifier.testTag("schedule-header-week"), verticalAlignment = Alignment.CenterVertically) {
        Text((if (next) "下学期 · " else "") + "第 ", Modifier.testTag("schedule-week-prefix"), style = style, color = color)
        ScheduleDateText(week.toString(), week.toLong(), "schedule-week-number", style, color,
            modifier = Modifier.width(width), fillColumn = true)
        Text(" 周", Modifier.testTag("schedule-week-suffix"), style = style, color = color)
    }
}
