package io.github.x07xh.xiaoxu.ui

// SPDX-License-Identifier: GPL-3.0-only
// Source: znjhahaha/zhengfang-apk @ bb3b80a6d64e4c2057c0544bb417696ceaa2a9ed, ScheduleHeader.kt.
// Original authors and contributors; actual local calendar, sparse weeks and shared controls adapted.

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.tyust.course.ui.system.*
import java.time.LocalDate

// Numeric and CJK fallback fonts share a stable line box. Tabular digits keep
// single- and double-digit dates centered when the selected week changes.
private val ScheduleDateTypography = TextStyle(
    letterSpacing = 0.sp,
    fontFeatureSettings = "tnum",
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
)

@Composable
internal fun scheduleHeaderHeight() = maxOf(56.dp, 44.dp * LocalDensity.current.fontScale) * 2

/** Floating controls at rest; scrolling reveals one retained glass surface beneath them. */
@Composable
internal fun WeekHeaderCompact(
    currentWeek: Int?, weeks: List<Int>,
    selectedDay: Int, dateFor: (Int, Int) -> LocalDate?,
    actualWeek: Int?, actualDay: Int,
    dayView: Boolean, onDayView: (Boolean) -> Unit,
    onDayClick: (Int) -> Unit, onWeekSelect: (Int) -> Unit,
    onTodayClick: () -> Unit, onImportClick: () -> Unit,
    onExportClick: () -> Unit, onSettingsClick: () -> Unit,
    onAddClick: (() -> Unit)?, collapseFraction: Float,
    sampleBackdrop: Backdrop? = LocalAppBackdrop.current
) {
    var calendarOpen by remember { mutableStateOf(false) }
    var toolsOpen by remember { mutableStateOf(false) }
    val date = currentWeek?.let { dateFor(it, selectedDay) }
    val colors = MaterialTheme.colorScheme
    val region = rememberWallpaperRegionState()
    val appearance = rememberWallpaperRegionAppearance(region)
    val height = scheduleHeaderHeight()
    val actionHeight = height / 2
    val fontScale = LocalDensity.current.fontScale
    val lift = collapseFraction.coerceIn(0f, 1f)
    val effectiveBackdrop = sampleBackdrop?.takeIf { isBackdropSupported() }
    val headerBackdrop = if (effectiveBackdrop != null) rememberLayerBackdrop() else null
    val controlBackdrop = if (effectiveBackdrop != null && headerBackdrop != null)
        rememberCombinedBackdrop(effectiveBackdrop, headerBackdrop) else null
    ProvideWallpaperAppearance(appearance) {
        Box(Modifier.fillMaxWidth().height(height).testTag("schedule-header").wallpaperRegion(region)
            .then(wallpaperHeaderScrim())) {
            Box(Modifier.matchParentSize().then(if (headerBackdrop != null) Modifier.layerBackdrop(headerBackdrop) else Modifier)) {
                HeaderGlassSlab(strength = lift, backdrop = effectiveBackdrop, cornerRadius = 26.dp,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 4.dp))
            }
            CompositionLocalProvider(LocalControlBackdrop provides controlBackdrop) {
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().height(actionHeight).padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).heightIn(min = 48.dp).testTag("schedule-header-title")
                            .clip(RoundedCornerShape(14.dp)).clickable(enabled = weeks.isNotEmpty(), role = Role.Button) { calendarOpen = true }
                            .semantics { contentDescription = "选择教学周" }.padding(start = 4.dp),
                            verticalArrangement = Arrangement.Center) {
                            if (date != null) ScheduleDateText("${date.monthValue}月${date.dayOfMonth}日",
                                timestamp = date.toEpochDay(), tag = "schedule-date-title", color = appearance.onSurface,
                                style = ScheduleDateTypography.copy(fontSize = if (fontScale > 1.3f) 15.sp else 22.sp,
                                    lineHeight = 24.sp, fontWeight = FontWeight.Bold))
                            else Text("课表", style = MaterialTheme.typography.titleLarge, color = appearance.onSurface)
                            if (currentWeek != null) ScheduleWeekLabel(currentWeek, false,
                                MaterialTheme.typography.labelSmall, appearance.onSurfaceVariant)
                            else Text("教学周待确认", style = MaterialTheme.typography.labelSmall)
                        }
                        Box(Modifier.width(88.dp).height(48.dp).testTag("schedule-view-toggle"), contentAlignment = Alignment.Center) {
                            LiquidSegmentedControl(listOf("日", "周"), if (dayView) 0 else 1,
                                { onDayView(it == 0) }, height = 36.dp)
                        }
                        TextButton(onClick = { toolsOpen = true }, modifier = Modifier.testTag("schedule-more")) { Text("更多") }
                    }
                    Row(Modifier.fillMaxWidth().height(height - actionHeight).padding(horizontal = scheduleGridPadding()),
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(scheduleTimeColumnWidth() + ScheduleTimeColumnShadowWidth).fillMaxHeight(),
                            contentAlignment = Alignment.Center) {
                            if (actualWeek != null && actualWeek in weeks && (actualWeek != currentWeek || selectedDay != actualDay))
                                TextButton(onTodayClick, contentPadding = PaddingValues(0.dp)) { Text("今天", fontSize = 12.sp) }
                        }
                        (1..7).forEach { day ->
                            val cellDate = currentWeek?.let { dateFor(it, day) }
                            val today = actualWeek != null && currentWeek == actualWeek && day == actualDay
                            val selected = dayView && selectedDay == day
                            Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp))
                                .background(if (selected) colors.primaryContainer else Color.Transparent)
                                .clickable(enabled = currentWeek != null) { onDayClick(day) }
                                .testTag("schedule-weekday-$day").semantics { if (today) stateDescription = "今天" },
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text("一二三四五六日"[day - 1].toString(), style = ScheduleDateTypography,
                                    fontSize = 11.sp, lineHeight = 15.sp, color = appearance.onSurfaceVariant)
                                if (cellDate != null) ScheduleDateText(cellDate.dayOfMonth.toString(), cellDate.toEpochDay(),
                                    "schedule-day-number-$day", ScheduleDateTypography.copy(fontSize = 15.sp, lineHeight = 19.sp,
                                        fontWeight = FontWeight.SemiBold),
                                    if (today || selected) colors.primary else appearance.onSurface, fillColumn = true)
                                Box(Modifier.padding(top = 2.dp).size(3.dp)
                                    .background(if (today) colors.primary else Color.Transparent, RoundedCornerShape(2.dp)))
                            }
                        }
                    }
                }
            }
        }
    }
    if (calendarOpen && currentWeek != null) {
        GlassOptionWheelDialog(weeks.map { "第 $it 周" }, weeks.indexOf(currentWeek).coerceAtLeast(0),
            onConfirm = { weeks.getOrNull(it)?.let(onWeekSelect); calendarOpen = false },
            onDismiss = { calendarOpen = false }, title = "选择教学周")
    }
    if (toolsOpen) AppDialog(onDismissRequest = { toolsOpen = false }, title = { Text("课表操作") },
        text = { Column {
            TextButton(onClick = { toolsOpen = false; onImportClick() }) { Text("导入课表") }
            TextButton(onClick = { toolsOpen = false; onExportClick() }) { Text("导出课表") }
            if (onAddClick != null) TextButton(onClick = { toolsOpen = false; onAddClick() }) { Text("添加课程") }
            TextButton(onClick = { toolsOpen = false; onSettingsClick() }) { Text("课表设置") }
        } }, confirmButton = { TextButton(onClick = { toolsOpen = false }) { Text("关闭") } })
}
