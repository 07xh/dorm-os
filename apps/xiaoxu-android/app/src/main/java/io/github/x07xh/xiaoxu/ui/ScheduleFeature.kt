package io.github.x07xh.xiaoxu.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import io.github.x07xh.xiaoxu.data.LocalSemesterConfigStore
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.animation.core.tween
import kotlinx.coroutines.flow.collect
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
    val context = LocalContext.current
    var viewMode by rememberSaveable { mutableStateOf(LocalSemesterConfigStore.loadViewMode(context)) }
    var selectedDay by rememberSaveable { mutableIntStateOf(LocalDate.now().dayOfWeek.value) }
    fun selectView(mode: String) {
        viewMode = mode
        LocalSemesterConfigStore.saveViewMode(context, mode)
    }
    Row {
        TextButton(onClick = actions.importTimetable) { Text("导入") }
        TextButton(onClick = actions.exportTimetable) { Text("导出") }
        Spacer(Modifier.weight(1f))
        FilterChip(selected = viewMode == "day", onClick = { selectView("day") }, label = { Text("日") })
        Spacer(Modifier.width(6.dp))
        FilterChip(selected = viewMode == "week", onClick = { selectView("week") }, label = { Text("周") })
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
                    selectedDay = lesson.weekday
                    selectedId = lesson.id
                    consume()
                }
            }
            val scheme = MaterialTheme.colorScheme
            val colors = listOf(scheme.primary, scheme.secondary, scheme.tertiary,
                Color(0xFF477D96), Color(0xFF6D8060), Color(0xFF9C6882))
            val allCourses = remember(lessons, colors) {
                lessons.filter { it.weekday in 1..7 && it.startSection in 1..24 &&
                    it.endSection in it.startSection..24 }.map { lesson ->
                    ScheduleCourseUi(name = lesson.title, teacher = lesson.teacher,
                        location = lesson.location, day = lesson.weekday,
                        startPeriod = lesson.startSection, endPeriod = lesson.endSection,
                        weeks = lesson.weeks.joinToString(","), id = lesson.id,
                        color = colors[ScheduleIdentity.colorIndex(lesson.title, colors.size)])
                }
            }
            val periodTimes = remember(calendar) {
                calendar?.sectionTimes?.entries?.sortedBy { it.key }?.map { (period, time) ->
                    PeriodTimeUi(period, time.start.toString(), time.end.toString())
                }.orEmpty()
            }
            if (week == null) {
                AppNotice("课程周次未确认，请检查原始导入记录。")
            } else {
                val index = weeks.indexOf(week)
                // The upstream coordinator uses a contiguous page index. Map it
                // through the real available week list, including sparse imports.
                key(weeks) {
                val pager = rememberPagerState(initialPage = index, pageCount = { weeks.size })
                val sync = remember(pager) { ScheduleWeekPagerSync(index + 1, null, 1, weeks.size) }
                val latestSelect by rememberUpdatedState<(Int) -> Unit>({ selectedIndex ->
                    requestedWeek = weeks[selectedIndex]
                })
                LaunchedEffect(pager, index) {
                    val targetPage = sync.requestPage(index + 1, null)
                    if (targetPage != null) {
                        if (pager.settledPage != targetPage || pager.isScrollInProgress) {
                            pager.animateScrollToPage(targetPage, animationSpec = MotionSpecs.standard())
                        }
                        sync.settledWeek(pager.settledPage)
                    }
                }
                LaunchedEffect(pager) {
                    snapshotFlow { pager.isScrollInProgress to pager.settledPage }.collect { (scrolling, page) ->
                        if (!scrolling) sync.settledWeek(page)?.let { latestSelect(it - 1) }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    val index = weeks.indexOf(week)
                    TextButton(onClick = { requestedWeek = weeks[index - 1] }, enabled = index > 0) { Text("上周") }
                    Box {
                        TextButton(onClick = { weekMenu = true }) {
                            ScheduleWeekLabel(week, false, MaterialTheme.typography.titleMedium, MaterialTheme.colorScheme.primary)
                            if (todayWeek == week) Text(" · 本周", style = MaterialTheme.typography.labelSmall)
                        }
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
                HorizontalPager(pager, modifier = Modifier.fillMaxWidth().weight(1f),
                    beyondViewportPageCount = 0, overscrollEffect = null,
                    flingBehavior = PagerDefaults.flingBehavior(pager, snapAnimationSpec = MotionSpecs.standard())) { page ->
                    val pageWeek = weeks[page]
                    Column(Modifier.fillMaxSize()) {
                        val courses = remember(allCourses, pageWeek) {
                            val weekCourses = allCourses.filter { isInWeek(it.weeks, pageWeek) }
                            val conflictIds = scheduleOverlapGroups(weekCourses.map { it.record() })
                                .filter { it.courses.size > 1 }.flatMap { it.courses }.map { it.id }.toSet()
                            weekCourses.map { it.copy(hasConflict = it.id in conflictIds) }
                        }
                        val periods = maxOf(data.timetableSections, courses.maxOfOrNull { it.endPeriod } ?: 1)
                            .coerceIn(1, 24)
                        val rowLabels = listOf("一", "二", "三", "四", "五", "六", "日")
                        val timeWidth = 36.dp
                        val scroll = rememberScrollState()
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            if (viewMode == "week") Spacer(Modifier.width(timeWidth))
                            rowLabels.forEachIndexed { index, label ->
                                Column(Modifier.weight(1f).clickable {
                                    selectedDay = index + 1
                                    selectView("day")
                                }.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    val dayColor = if (viewMode == "day" && selectedDay == index + 1)
                                        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    Text(label, style = MaterialTheme.typography.labelMedium, color = dayColor)
                                    calendar?.dateFor(pageWeek, index + 1)?.let { date ->
                                        ScheduleDateText("${date.monthValue}/${date.dayOfMonth}", date.toEpochDay(),
                                            "schedule-day-number-${index + 1}", MaterialTheme.typography.labelSmall,
                                            dayColor, fillColumn = true)
                                    }
                                }
                            }
                        }
                        if (viewMode == "day") {
                            val daily = remember(courses, selectedDay) {
                                courses.filter { it.day == selectedDay }.sortedWith(
                                    compareBy<ScheduleCourseUi> { it.startPeriod }.thenBy { it.name })
                            }
                            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(scroll),
                                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("当日 ${daily.size} 堂", style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (daily.isEmpty()) ScheduleNotice("当天没有已导入课程")
                                daily.forEach { course ->
                                    key(course.id) {
                                        ScheduleDayCourse(course, periodTimes, compact = false,
                                            onClick = { selectedId = course.id },
                                            onLongClick = { selectedId = course.id })
                                    }
                                }
                            }
                        } else {
                            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                                val width = (maxWidth - timeWidth).coerceAtLeast(1.dp)
                                val rowHeight = rememberFullCoursePeriodHeight(courses, width / 7,
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
                    }
                }
                }
            }
            selected?.let { lesson ->
                allCourses.firstOrNull { it.id == lesson.id }?.let { course ->
                    UpstreamCourseDetailSheet(course, allCourses,
                        timeRange = calendar?.periodLabel(lesson.startSection, lesson.endSection).orEmpty(),
                        currentWeek = week ?: 0, onClose = { selectedId = null })
                }
            }
        }
    }
}
