package io.github.x07xh.xiaoxu.ui

import androidx.compose.foundation.layout.*
import androidx.compose.ui.platform.LocalContext
import io.github.x07xh.xiaoxu.data.LocalSemesterConfigStore
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.PagerDefaults
import kotlinx.coroutines.flow.collect
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.github.x07xh.xiaoxu.data.VerifiedLesson
import io.github.x07xh.xiaoxu.data.selectableTeachingWeeks
import java.time.LocalDate
import android.widget.Toast
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import io.github.x07xh.xiaoxu.data.LocalScheduleEditor

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
    val scope = rememberCoroutineScope()
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingWeek by rememberSaveable { mutableStateOf<Int?>(null) }
    var editorBusy by remember { mutableStateOf(false) }
    var editorError by remember { mutableStateOf<String?>(null) }
    var requestedWeek by rememberSaveable { mutableIntStateOf(0) }
    var viewMode by rememberSaveable { mutableStateOf(LocalSemesterConfigStore.loadViewMode(context)) }
    var selectedDay by rememberSaveable { mutableIntStateOf(LocalDate.now().dayOfWeek.value) }
    fun selectView(mode: String) {
        viewMode = mode
        LocalSemesterConfigStore.saveViewMode(context, mode)
    }
    val calendar = data.semesterCalendar
    val availableLessons = when (val snapshot = data.lessons) {
        is PageSnapshot.Ready -> snapshot.value
        else -> emptyList()
    }
    val weeks = remember(availableLessons, calendar) { selectableTeachingWeeks(availableLessons, calendar) }
    val today = LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"))
    val todayWeek = calendar?.currentWeek(today)
    val week = requestedWeek.takeIf { it in weeks } ?: todayWeek?.takeIf { it in weeks } ?: weeks.firstOrNull()
    var activeScroll by remember { mutableStateOf<androidx.compose.foundation.ScrollState?>(null) }
    val travel = with(LocalDensity.current) { 64.dp.toPx() }.coerceAtLeast(1f)
    val lift by remember(activeScroll, travel) { derivedStateOf {
        ((activeScroll?.value ?: 0) / travel).coerceIn(0f, 1f)
    } }
    WeekHeaderCompact(currentWeek = week, weeks = weeks, selectedDay = selectedDay,
        dateFor = { w, day -> calendar?.dateFor(w, day) },
        actualWeek = todayWeek, actualDay = today.dayOfWeek.value,
        dayView = viewMode == "day", onDayView = { selectView(if (it) "day" else "week") },
        onDayClick = { selectedDay = it; selectView("day") },
        onWeekSelect = { requestedWeek = it },
        onTodayClick = {
            requestedWeek = todayWeek ?: 0
            selectedDay = today.dayOfWeek.value
            scope.launch { activeScroll?.scrollTo(0) }
        },
        onImportClick = actions.importTimetable, onExportClick = actions.exportTimetable,
        onSettingsClick = { actions.navigate(AppRoute.SCHEDULE_SETTINGS) },
        onAddClick = if (data.lessons is PageSnapshot.Ready) ({
            editingId = null; editingWeek = week; editorError = null; editorOpen = true
        }) else null,
        collapseFraction = lift)
    AppSnapshotContent(data.lessons) { lessons ->
        if (editorOpen) {
            val original = lessons.firstOrNull { it.id == editingId }
            if (editingId != null && original == null) {
                AppDialog(onDismissRequest = { editorOpen = false }, title = { Text("课程已变化") },
                    text = { Text("请关闭后重新选择课程。") },
                    confirmButton = { TextButton(onClick = { editorOpen = false }) { Text("关闭") } })
            } else {
                fun commit(changed: List<VerifiedLesson>) {
                    if (editorBusy) return
                    editorBusy = true
                    editorError = null
                    scope.launch {
                        try {
                            val remindersOk = actions.saveCourseChanges(lessons, changed, editingId)
                            editorOpen = false
                            editingId = null
                            if (!remindersOk) Toast.makeText(context, "课程已保存，但提醒维护失败", Toast.LENGTH_LONG).show()
                        } catch (cancel: CancellationException) { throw cancel }
                        catch (failure: Exception) { editorError = failure.message ?: "本机课表修改失败" }
                        finally { editorBusy = false }
                    }
                }
                CourseEditorDialog(original, editingWeek, editorBusy, editorError,
                    onDismiss = { if (!editorBusy) editorOpen = false },
                    onSave = { replacement, only ->
                        try { commit(LocalScheduleEditor.upsert(lessons, editingId, replacement, editingWeek.takeIf { only })) }
                        catch (failure: Exception) { editorError = failure.message ?: "课程冲突，未保存" }
                    },
                    onDelete = { only ->
                        try { commit(LocalScheduleEditor.delete(lessons, requireNotNull(editingId), editingWeek.takeIf { only })) }
                        catch (failure: Exception) { editorError = failure.message ?: "删除失败" }
                    })
            }
        }
        AppNotice(localImportedScheduleView(lessons).sourceLabel)
        if (lessons.isEmpty()) {
            SystemEmptyState("本机尚无课程", "导入课表后在这里查看课程。")
        } else {
            var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
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
                        val scroll = rememberScrollState()
                        LaunchedEffect(pager.settledPage, page, scroll) {
                            if (pager.settledPage == page) activeScroll = scroll
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
                            Box(Modifier.fillMaxWidth().weight(1f)) {
                                ScheduleGrid(courses = allCourses.map { course ->
                                    courses.firstOrNull { it.id == course.id } ?: course
                                }, currentWeek = pageWeek, periodTimes = periodTimes,
                                    periodCount = periods, scrollState = scroll,
                                    onCourseClick = { selectedId = it.id },
                                    onCourseLongClick = { selectedId = it.id })
                            }
                        }
                    }
                }
                }
            }
            selected?.let { lesson ->
                allCourses.firstOrNull { it.id == lesson.id }?.let { course ->
                    UpstreamCourseDetailSheet(course, allCourses,
                        timeRange = calendar?.periodLabel(lesson.startSection, lesson.endSection).orEmpty(),
                        currentWeek = week ?: 0, onEdit = {
                            editingId = lesson.id; editingWeek = week; editorError = null
                            selectedId = null; editorOpen = true
                        }, onClose = { selectedId = null })
                }
            }
        }
    }
}
