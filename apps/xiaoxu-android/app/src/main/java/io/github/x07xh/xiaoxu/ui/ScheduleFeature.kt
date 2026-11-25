package io.github.x07xh.xiaoxu.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.github.x07xh.xiaoxu.data.VerifiedLesson
import io.github.x07xh.xiaoxu.data.selectableTeachingWeeks
import java.time.LocalDate

/**
 * Xiaoxu-only data binding. The actual grid, cards, text fit and overlapping
 * course layout come from the upstream ScheduleScreen port.
 * Local imports remain local; no second schedule store or school client.
 */
@Composable
internal fun ColumnScope.ScheduleFeature(
    data: AppWorkspaceState, actions: AppPageActions,
    target: String?, consume: () -> Unit
) {
    Row {
        TextButton(onClick = actions.importTimetable) { Text("导入") }
        TextButton(onClick = actions.exportTimetable) { Text("导出") }
    }
    AppSnapshotContent(data.lessons) { lessons ->
        if (lessons.isEmpty()) {
            SystemEmptyState("本机尚无课程", "导入课表后在这里查看课程。")
        } else {
            val calendar = data.semesterCalendar
            val weeks = remember(lessons, calendar) { selectableTeachingWeeks(lessons, calendar) }
            val todayWeek = calendar?.currentWeek(LocalDate.now())
            var requestedWeek by rememberSaveable { mutableIntStateOf(0) }
            val week = requestedWeek.takeIf { it in weeks } ?: todayWeek?.takeIf { it in weeks }
                ?: weeks.firstOrNull()
            var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
            var weekMenu by remember { mutableStateOf(false) }
            val selected = lessons.firstOrNull { it.id == selectedId }
            LaunchedEffect(target, lessons) {
                val lesson = target?.let { id -> lessons.firstOrNull { it.id == id } }
                if (lesson != null) {
                    requestedWeek = lesson.weeks.firstOrNull { it == todayWeek }
                        ?: lesson.weeks.firstOrNull() ?: 0
                    selectedId = lesson.id
                    consume()
                }
            }
            if (week == null) {
                AppNotice("课程周次未确认，请检查原始导入记录。")
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    val index = weeks.indexOf(week)
                    TextButton(onClick = { requestedWeek = weeks[index - 1] }, enabled = index > 0) { Text("上周") }
                    Box {
                        TextButton(onClick = { weekMenu = true }) { Text("第 $week 周" + if (todayWeek == week) " · 本周" else "") }
                        DropdownMenu(expanded = weekMenu, onDismissRequest = { weekMenu = false }) {
                            weeks.forEach { item ->
                                DropdownMenuItem(text = { Text("第 $item 周") },
                                    onClick = { requestedWeek = item; weekMenu = false })
                            }
                        }
                    }
                    TextButton(onClick = { requestedWeek = weeks[index + 1] }, enabled = index < weeks.lastIndex) { Text("下周") }
                }
                if (calendar == null) AppNotice("未配置学期起始日期；仅按已导入周次显示。")
                val scheme = MaterialTheme.colorScheme
                val colors = listOf(scheme.primary, scheme.secondary, scheme.tertiary,
                    Color(0xFF477D96), Color(0xFF6D8060), Color(0xFF9C6882))
                val courses = remember(lessons, week, colors) {
                    lessons.filter { week in it.weeks && it.weekday in 1..7 &&
                        it.startSection in 1..24 && it.endSection in it.startSection..24 }.map { lesson ->
                        ScheduleCourseUi(name = lesson.title, teacher = lesson.teacher,
                            location = lesson.location, day = lesson.weekday,
                            startPeriod = lesson.startSection, endPeriod = lesson.endSection,
                            weeks = lesson.weeks.joinToString(","), id = lesson.id,
                            color = colors[ScheduleIdentity.colorIndex(lesson.title, colors.size)])
                    }
                }
                val periods = maxOf(data.timetableSections, courses.maxOfOrNull { it.endPeriod } ?: 1)
                    .coerceIn(1, 24)
                val rowLabels = listOf("一", "二", "三", "四", "五", "六", "日")
                val timeWidth = 36.dp
                val scroll = rememberScrollState()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(timeWidth))
                    rowLabels.forEachIndexed { index, label ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(label, style = MaterialTheme.typography.labelMedium)
                            calendar?.dateFor(week, index + 1)?.let { date ->
                                Text("${date.monthValue}/${date.dayOfMonth}",
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                    val width = (maxWidth - timeWidth).coerceAtLeast(1.dp)
                    val columnPx = with(LocalDensity.current) { (width / 7).roundToPx().coerceAtLeast(1) }
                    val rowHeight = rememberFullCoursePeriodHeight(courses, columnPx,
                        rememberScreenMetrics().tall(84.dp, 68.dp))
                    Row(Modifier.fillMaxSize().verticalScroll(scroll)) {
                        Column(Modifier.width(timeWidth)) {
                            repeat(periods) { i ->
                                Box(Modifier.height(rowHeight).fillMaxWidth(),
                                    contentAlignment = Alignment.TopCenter) {
                                    Text((i + 1).toString(), style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                        }
                        Box(Modifier.weight(1f).height(rowHeight * periods)) {
                            TimetableBackground(periods, rowHeight)
                            TimetableLayout(courses, periods, rowHeight,
                                onCourseClick = { selectedId = it.id },
                                onCourseLongClick = { selectedId = it.id })
                        }
                    }
                }
                if (courses.isEmpty()) AppNotice("本周无已导入课程。")
            }
            selected?.let { lesson ->
                AlertDialog(onDismissRequest = { selectedId = null },
                    title = { Text(lesson.title) },
                    text = {
                        Column(Modifier.verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("教师：${lesson.teacher.ifBlank { "未记录" }}")
                            Text("教室：${lesson.location.ifBlank { "未记录" }}")
                            Text("星期${lesson.weekday} · 第${lesson.startSection}–${lesson.endSection}节")
                            calendar?.periodLabel(lesson.startSection, lesson.endSection)?.let { Text(it) }
                            Text("周次：${lesson.weeks.joinToString("、")}")
                            Text("来源：本机课表", style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    confirmButton = { TextButton(onClick = { selectedId = null }) { Text("关闭") } })
            }
        }
    }
}
