package io.github.x07xh.xiaoxu.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
@Composable
internal fun SemesterSettingsDialog(
    current: SemesterCalendar?,
    reminderEnabled: Boolean,
    initialViewMode: String,
    initialSections: Int,
    onViewModeChange: (String) -> Unit,
    onSectionsChange: (Int) -> Unit,
    onAddCourse: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (SemesterCalendar, Boolean) -> Unit
) {
    val context = LocalContext.current
    var monday by rememberSaveable { mutableStateOf(current?.firstMonday?.toString().orEmpty()) }
    var weeks by rememberSaveable { mutableStateOf(current?.totalWeeks?.toString().orEmpty()) }
    var times by rememberSaveable { mutableStateOf(current?.editableTimes().orEmpty()) }
    var viewMode by rememberSaveable { mutableStateOf(initialViewMode) }
    var sections by rememberSaveable { mutableStateOf(initialSections.coerceIn(1, 24)) }
    var wantReminders by rememberSaveable { mutableStateOf(reminderEnabled) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var sectionsMenu by remember { mutableStateOf(false) }
    var weeksMenu by remember { mutableStateOf(false) }

    fun editSectionTime(index: Int) {
        val existing = findSectionTime(times, index)
        val start = existing?.first ?: LocalTime.of(8, 0)
        TimePickerDialog(context, { _, sh, sm ->
            val pickedStart = LocalTime.of(sh, sm)
            val end = existing?.second ?: pickedStart.plusMinutes(45)
            TimePickerDialog(context, { _, eh, em ->
                times = replaceSectionTime(times, index, pickedStart, LocalTime.of(eh, em))
                error = null
            }, end.hour, end.minute, true).show()
        }, start.hour, start.minute, true).show()
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier.fillMaxSize()
                .background(XiaoxuPalette.PageGradient)
                .systemBarsPadding()
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
                        onViewModeChange(viewMode)
                        onSectionsChange(sections)
                        if (config == null) onDismiss() else onSave(config, wantReminders)
                    } catch (failure: IllegalArgumentException) {
                        error = failure.message ?: "课表设置无效"
                    }
                }) { Text("完成", color = XiaoxuPalette.Brand, fontSize = 16.sp) }
            }

            SettingsGroupTitle("显示设置")
            SettingsSurface {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("默认课表视图", modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge, color = XiaoxuPalette.Ink)
                    Row(
                        modifier = Modifier.clip(RoundedCornerShape(22.dp))
                            .background(Color(0xFFE9E9F1)).padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        listOf("week" to "周视图", "day" to "日视图").forEach { (mode, name) ->
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(20.dp))
                                    .background(if (viewMode == mode) Color(0xFFD7E7FA)
                                        else Color.Transparent)
                                    .clickable { viewMode = mode }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(name, color = if (viewMode == mode) XiaoxuPalette.Brand
                                    else XiaoxuPalette.Ink2,
                                    style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }

            SettingsGroupTitle("自定义课程")
            SettingsSurface {
                SettingsActionRow(
                    title = "添加课程",
                    subtitle = "课程只保存在当前账号",
                    onClick = onAddCourse
                )
            }

            SettingsGroupTitle("基础设置")
            SettingsSurface {
                SettingsActionRow(
                    title = "第一周开始日期",
                    subtitle = "按所选日期所在周的周一计算周次",
                    value = runCatching { LocalDate.parse(monday).format(DateTimeFormatter.ofPattern("yyyy年M月d日")) }
                        .getOrNull() ?: "选择日期",
                    icon = "date",
                    onClick = {
                        val date = runCatching { LocalDate.parse(monday) }.getOrNull()
                            ?: LocalDate.now()
                        DatePickerDialog(context, { _, y, m, d ->
                            monday = LocalDate.of(y, m + 1, d)
                                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                                .toString()
                            error = null
                        }, date.year, date.monthValue - 1, date.dayOfMonth).show()
                    }
                )
                HorizontalDivider(color = XiaoxuPalette.Line, thickness = 0.6.dp)
                SettingsActionRow(
                    title = "每天节数",
                    subtitle = "课程纵向的节次总数",
                    value = sections.toString() + " 节",
                    icon = "time",
                    onClick = { sectionsMenu = true }
                )
                Box {
                    DropdownMenu(expanded = sectionsMenu, onDismissRequest = { sectionsMenu = false }) {
                        listOf(8, 10, 12, 14, 16, 18, 20, 24).forEach { n ->
                            DropdownMenuItem(text = { Text(n.toString() + " 节") }, onClick = {
                                sections = n
                                sectionsMenu = false
                            })
                        }
                    }
                }
                HorizontalDivider(color = XiaoxuPalette.Line, thickness = 0.6.dp)
                SettingsActionRow(
                    title = "学期周数",
                    value = if (weeks.isBlank()) "设置周数" else weeks + " 周",
                    onClick = { weeksMenu = true }
                )
                Box {
                    DropdownMenu(expanded = weeksMenu, onDismissRequest = { weeksMenu = false }) {
                        (1..35).forEach { n ->
                            DropdownMenuItem(text = { Text(n.toString() + " 周") }, onClick = {
                                weeks = n.toString()
                                weeksMenu = false
                                error = null
                            })
                        }
                    }
                }
            }

            SettingsGroupTitle("课表数据")
            SettingsSurface {
                SettingsActionRow(
                    title = "同步课表",
                    subtitle = "从本机文件更新正在查看的学期",
                    onClick = { onDismiss(); onImport() }
                )
                HorizontalDivider(color = XiaoxuPalette.Line, thickness = 0.6.dp)
                SettingsActionRow(
                    title = "导出备份",
                    subtitle = "保存当前课表与学期作息",
                    onClick = { onDismiss(); onExport() }
                )
            }

            SettingsGroupTitle("课程提醒")
            SettingsSurface {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.NotificationsNone, contentDescription = null,
                        tint = XiaoxuPalette.Ink2, modifier = Modifier.size(22.dp))
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text("上课提醒", style = MaterialTheme.typography.bodyLarge,
                            color = XiaoxuPalette.Ink)
                        Text("上课前约 15 分钟 · 系统可能延后",
                            style = MaterialTheme.typography.bodySmall, color = XiaoxuPalette.Ink2)
                    }
                    Switch(checked = wantReminders, onCheckedChange = { wantReminders = it })
                }
            }

            SettingsGroupTitle("节次时间")
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
            Text("点任意一节可修改此时间", modifier = Modifier.padding(start = 16.dp, top = 12.dp),
                style = MaterialTheme.typography.bodySmall, color = XiaoxuPalette.Ink2)
            if (error != null) {
                Text(error.orEmpty(), modifier = Modifier.padding(top = 14.dp),
                    color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun SettingsGroupTitle(title: String) {
    Text(title, modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 10.dp),
        style = MaterialTheme.typography.bodySmall, color = XiaoxuPalette.Ink2)
}

@Composable
private fun SettingsSurface(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.74f)),
        content = { content() }
    )
}

@Composable
private fun SettingsActionRow(
    title: String,
    subtitle: String? = null,
    value: String? = null,
    icon: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = if (subtitle == null) 55.dp else 66.dp)
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
            Text(title, style = MaterialTheme.typography.bodyLarge, color = XiaoxuPalette.Ink)
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
