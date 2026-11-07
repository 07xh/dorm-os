package io.github.x07xh.xiaoxu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.x07xh.xiaoxu.data.SemesterCalendar
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import java.time.format.DateTimeFormatter

/*
 * Settings and course detail follow the supplied timetable reference.
 * The lesson grid, weekday selector and course colors are deliberately untouched.
 * Unknown school dates/times remain unset: never insert demo timetable data.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SemesterSettingsPage(
    current: SemesterCalendar?,
    initialViewMode: String,
    initialSections: Int,
    onAddCourse: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (SemesterCalendar?, String, Int) -> Unit
) {
    val context = LocalContext.current
    var monday by rememberSaveable { mutableStateOf(current?.firstMonday?.toString().orEmpty()) }
    var weeks by rememberSaveable { mutableStateOf(current?.totalWeeks?.toString().orEmpty()) }
    var times by rememberSaveable { mutableStateOf(current?.editableTimes().orEmpty()) }
    var viewMode by rememberSaveable { mutableStateOf(initialViewMode) }
    var sections by rememberSaveable { mutableStateOf(initialSections.coerceIn(1, 24)) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val errorField = semesterSettingsErrorField(error)
    var sectionsMenu by remember { mutableStateOf(false) }
    var weeksMenu by remember { mutableStateOf(false) }
    var dateOpen by remember { mutableStateOf(false) }
    var editingSection by remember { mutableIntStateOf(0) }
    var editingEnd by remember { mutableStateOf(false) }
    var stagedStart by remember { mutableStateOf(LocalTime.of(8, 0)) }
    var timeError by remember { mutableStateOf<String?>(null) }

    fun editSectionTime(index: Int) {
        editingSection = index
        editingEnd = false
        stagedStart = findSectionTime(times, index)?.first ?: LocalTime.of(8, 0)
        timeError = null
    }

    // Routed content in the existing Activity window, not a full-screen Dialog.
    Column(
            modifier = Modifier.fillMaxSize()
                .background(XiaoxuPalette.PageGradient)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(46.dp)
                        .clip(CircleShape).background(Color.White.copy(alpha = 0.65f))
                ) { Icon(Icons.Outlined.Close, contentDescription = "关闭课表设置") }
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text("课表设置", fontSize = 27.sp, fontWeight = FontWeight.SemiBold,
                        color = XiaoxuPalette.Ink)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(XiaoxuPalette.Brand))
                        Spacer(Modifier.width(7.dp))
                        Text("学期起始与节次时间", style = MaterialTheme.typography.bodySmall,
                            color = XiaoxuPalette.Ink2)
                    }
                }
                TextButton(onClick = {
                    try {
                        // The view preference is valid even before any semester is configured.
                        val config = if (monday.isBlank() && weeks.isBlank() && times.isBlank())
                            null else SemesterCalendar.parse(monday, weeks, times)
                        onSave(config, viewMode, sections)
                    } catch (failure: IllegalArgumentException) {
                        error = failure.message ?: "课表设置无效"
                    }
                }) { Text("完成", color = XiaoxuPalette.Brand, fontSize = 16.sp) }
            }

            // Save is in the header. Validation feedback must appear beside it,
            // rather than below a long list of period rows.
            if (error != null) {
                Text(
                    text = error.orEmpty(),
                    modifier = Modifier.fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .semantics { liveRegion = LiveRegionMode.Assertive },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }

            // Use SleepDown's compact grouped hierarchy without adding mock
            // timetable features or replacing the existing Xiaoxu controls.
            SettingsGroupTitle("学期与显示")
            SettingsSurface {
                SettingsActionRow(
                    title = "学期周数",
                    value = if (weeks.isBlank()) "设置周数" else weeks + " 周",
                    hasError = errorField == "weeks",
                    onClick = { weeksMenu = true }
                )
                HorizontalDivider(color = XiaoxuPalette.Line, thickness = 0.6.dp)
                SettingsActionRow(
                    title = "第一周开始日期",
                    subtitle = "按所在周的周一计算",
                    value = runCatching {
                        LocalDate.parse(monday).format(
                            DateTimeFormatter.ofPattern("yyyy年M月d日"))
                    }.getOrNull() ?: "选择日期",
                    icon = "date",
                    hasError = errorField == "date",
                    onClick = { dateOpen = true }
                )
                HorizontalDivider(color = XiaoxuPalette.Line, thickness = 0.6.dp)
                // Keep the label and its choice aligned with other settings rows.
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val controlWidth = ((maxWidth - 32.dp) * 0.56f).coerceAtMost(210.dp)
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("默认课表视图",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                            color = XiaoxuPalette.Ink,
                            maxLines = 1)
                        Spacer(Modifier.width(8.dp))
                        XiaoxuSegmentedControl(
                            options = listOf(
                                XiaoxuSegmentOption("week", "周视图"),
                                XiaoxuSegmentOption("day", "日视图")
                            ),
                            selectedKey = viewMode,
                            onSelected = { viewMode = it },
                            modifier = Modifier.width(controlWidth),
                            fillWidth = false,
                            compact = true
                        )
                    }
                }
                HorizontalDivider(color = XiaoxuPalette.Line, thickness = 0.6.dp)
                SettingsActionRow(
                    title = "每天节数",
                    value = sections.toString() + " 节",
                    icon = "time",
                    onClick = { sectionsMenu = true }
                )
            }

            SettingsGroupTitle("课程与数据")
            SettingsSurface {
                SettingsActionRow(title = "添加课程", onClick = onAddCourse)
                HorizontalDivider(color = XiaoxuPalette.Line, thickness = 0.6.dp)
                SettingsActionRow(
                    title = "导入课表",
                    subtitle = "从 .ics 或 .json 文件导入",
                    onClick = onImport
                )
                HorizontalDivider(color = XiaoxuPalette.Line, thickness = 0.6.dp)
                SettingsActionRow(title = "导出备份", onClick = onExport)
            }

            SettingsGroupTitle("本课表作息")
            SettingsSurface {
                repeat(sections) { zeroBased ->
                    val index = zeroBased + 1
                    val pair = findSectionTime(times, index)
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 55.dp)
                            .clickable { editSectionTime(index) }
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("第 " + index + " 节", modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge, color = XiaoxuPalette.Ink)
                        Box(
                            Modifier.clip(RoundedCornerShape(18.dp))
                                .background(Color(0xFFDCE9F8))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                pair?.let { it.first.toString() + " - " + it.second.toString() }
                                    ?: "设置时间",
                                style = MaterialTheme.typography.bodyMedium,
                                color = XiaoxuPalette.Brand
                            )
                        }
                    }
                    if (index < sections) HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = XiaoxuPalette.Line, thickness = 0.6.dp
                    )
                }
            }
        }

    // Use Material3 date/time controls instead of Android framework teal dialogs.
    // The editor writes only to local draft state; the top-right 完成 persists settings.
    if (dateOpen) {
        val initialDay = runCatching { LocalDate.parse(monday) }.getOrNull() ?: LocalDate.now()
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = initialDay.toEpochDay() * 86_400_000L
        )
        DatePickerDialog(
            onDismissRequest = { dateOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { epoch ->
                        monday = LocalDate.ofEpochDay(epoch / 86_400_000L)
                            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                            .toString()
                        error = null
                    }
                    dateOpen = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { dateOpen = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = dateState, showModeToggle = false)
        }
    }

    if (editingSection > 0) {
        // Re-create the state between start/end steps, otherwise the previous
        // wheel selection would also be used as the end time.
        androidx.compose.runtime.key(editingSection, editingEnd) {
            val existing = findSectionTime(times, editingSection)
            val initial = if (editingEnd) existing?.second ?: stagedStart.plusMinutes(45)
                else stagedStart
            val pickerState = rememberTimePickerState(
                initialHour = initial.hour,
                initialMinute = initial.minute,
                is24Hour = true
            )
            val shape = RoundedCornerShape(28.dp)
            Dialog(
                onDismissRequest = { editingSection = 0 },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 440.dp)
                        .clip(shape).background(XiaoxuPalette.PageGradient)
                        .border(0.5.dp, Color.White.copy(alpha = 0.8f), shape)
                        .padding(horizontal = 12.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "第 " + editingSection + " 节" +
                            if (editingEnd) " · 结束时间" else " · 开始时间",
                        style = MaterialTheme.typography.titleLarge,
                        color = XiaoxuPalette.Ink
                    )
                    TimePicker(state = pickerState)
                    if (timeError != null) {
                        Text(timeError.orEmpty(), color = MaterialTheme.colorScheme.error)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { editingSection = 0 }) { Text("取消") }
                        TextButton(onClick = {
                            val chosen = LocalTime.of(pickerState.hour, pickerState.minute)
                            if (!editingEnd) {
                                stagedStart = chosen
                                editingEnd = true
                                timeError = null
                            } else if (!chosen.isAfter(stagedStart)) {
                                timeError = "结束时间应晚于开始时间"
                            } else {
                                times = replaceSectionTime(times, editingSection, stagedStart, chosen)
                                error = null
                                timeError = null
                                editingSection = 0
                            }
                        }) { Text(if (editingEnd) "保存时间" else "下一步") }
                    }
                }
            }
        }
    }

    // Both selectors use the same centered glass overlay as the app's detail views.
    // The bounded inner scroll prevents 35 weeks from covering the entire settings page.
    if (sectionsMenu || weeksMenu) {
        val choosingSections = sectionsMenu
        val dismissChooser = { sectionsMenu = false; weeksMenu = false }
        val options = if (choosingSections) listOf(8, 10, 12, 14, 16, 18, 20, 24)
            else (1..35).toList()
        XiaoxuGlassDialog(
            title = if (choosingSections) "每天节数" else "学期周数",
            onDismiss = dismissChooser,
            dismissLabel = "取消"
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 312.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                options.forEach { number ->
                    val selected = if (choosingSections) number == sections
                        else number.toString() == weeks
                    SettingsActionRow(
                        title = number.toString() + if (choosingSections) " 节" else " 周",
                        value = if (selected) "已选择" else null,
                        onClick = {
                            if (choosingSections) sections = number
                            else weeks = number.toString()
                            error = null
                            dismissChooser()
                        }
                    )
                    if (number != options.last()) {
                        HorizontalDivider(
                            color = XiaoxuPalette.Line,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp
                        )
                    }
                }
            }
        }
    }
}


/** Maps the existing parser's field-specific validation to the corresponding row. */
private fun semesterSettingsErrorField(message: String?): String = when {
    message == null -> ""
    "周数" in message -> "weeks"
    "周一" in message || "日期" in message -> "date"
    "时间" in message || "节次" in message -> "times"
    else -> ""
}

@Composable
private fun SettingsGroupTitle(title: String) {
    Text(title, modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 10.dp),
        style = MaterialTheme.typography.bodySmall, color = XiaoxuPalette.Ink2)
}

@Composable
private fun SettingsSurface(content: @Composable () -> Unit) {
    // Keep 北化's original group spacing and controls; share only the glass container.
    XiaoxuGroupSurface { content() }
}

@Composable
private fun SettingsActionRow(
    title: String,
    subtitle: String? = null,
    value: String? = null,
    icon: String? = null,
    hasError: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = if (subtitle == null) 55.dp else 66.dp)
            .background(if (hasError) MaterialTheme.colorScheme.error.copy(alpha = 0.07f)
                else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier.size(32.dp).clip(RoundedCornerShape(9.dp))
                    .background(if (icon == "date") Color(0xFF1688F4) else Color(0xFFFFA317)),
                contentAlignment = Alignment.Center
            ) {
                Icon(if (icon == "date") Icons.Outlined.DateRange else Icons.Outlined.Schedule,
                    contentDescription = null, modifier = Modifier.size(22.dp), tint = Color.White)
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge,
                color = if (hasError) MaterialTheme.colorScheme.error else XiaoxuPalette.Ink)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = XiaoxuPalette.Ink2)
            }
        }
        if (value != null) {
            Spacer(Modifier.width(8.dp))
            Text(value, style = MaterialTheme.typography.bodyMedium, color = XiaoxuPalette.Brand)
        }
    }
}

private fun findSectionTime(raw: String, number: Int): Pair<LocalTime, LocalTime>? {
    val line = raw.lineSequence().firstOrNull {
        it.trim().substringBefore(" ").toIntOrNull() == number
    }?.trim() ?: return null
    val parts = line.substringAfter(" ", "").split("-")
    if (parts.size != 2) return null
    val a = runCatching { LocalTime.parse(parts[0].trim()) }.getOrNull() ?: return null
    val b = runCatching { LocalTime.parse(parts[1].trim()) }.getOrNull() ?: return null
    return a to b
}

private fun replaceSectionTime(raw: String, index: Int, start: LocalTime, end: LocalTime): String {
    val map = linkedMapOf<Int, String>()
    raw.lineSequence().filter { it.isNotBlank() }.forEach { line ->
        val key = line.trim().substringBefore(" ").toIntOrNull()
        if (key != null) map[key] = line
    }
    map[index] = index.toString() + " " + start + "-" + end
    return map.toSortedMap().values.joinToString("\n")
}
