package io.github.x07xh.xiaoxu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.x07xh.xiaoxu.data.ChaoxingCourseCoverage
import io.github.x07xh.xiaoxu.data.summarizeChaoxingAudit
import io.github.x07xh.xiaoxu.data.CloudClient
import io.github.x07xh.xiaoxu.data.CloudUiState
import io.github.x07xh.xiaoxu.data.CloudTask
import io.github.x07xh.xiaoxu.data.LocalTask
import io.github.x07xh.xiaoxu.data.LocalTaskDao
import io.github.x07xh.xiaoxu.data.LocalTaskDatabase
import io.github.x07xh.xiaoxu.data.NativeSchoolRecord
import io.github.x07xh.xiaoxu.data.VerifiedLesson
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.text.DateFormat
import java.util.Date
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private val SchoolZone = ZoneId.of("Asia/Shanghai")

/** In-memory combination for DISPLAY ONLY. Room, D1 and school data remain separate. */
internal data class WorkspaceTask(
    val key: String,
    val title: String,
    val origin: String,
    val done: Boolean,
    val date: LocalDate?,
    val dateSource: String,
    val storage: String,
    val category: String = storage
)

internal fun parseWorkspaceDue(value: String?): LocalDate? {
    if (value.isNullOrBlank()) return null
    return runCatching { LocalDate.parse(value) }.getOrNull()
        ?: runCatching { Instant.parse(value).atZone(SchoolZone).toLocalDate() }.getOrNull()
}

internal fun combineWorkspaceTasks(local: List<LocalTask>, cloud: List<CloudTask>): List<WorkspaceTask> =
    buildList {
        local.forEach { task ->
            val source = when (task.source) {
                "chaoxing" -> "本人标注学习通"
                "second_class" -> "本人标注第二课堂"
                "course" -> "本人标注课程"
                else -> "本机待办"
            }
            add(WorkspaceTask("local:" + task.id, task.title, source, task.completed,
                parseWorkspaceDue(task.due), "本人填写", "local"))
        }
        cloud.forEach { task ->
            val source = when (task.provider) {
                "wechat" -> "微信任务"
                "chaoxing" -> "学习通相关云端记录"
                "ncwu" -> "青果相关云端记录"
                "second_class" -> "第二课堂相关云端记录"
                else -> "校序云端任务"
            }
            add(WorkspaceTask("cloud:" + task.id, task.title, source,
                task.status == "done", parseWorkspaceDue(task.due),
                "云端记录，需核对来源", "cloud",
                if (task.provider == "wechat") "wechat" else "cloud"))
        }
    }.sortedWith(compareBy<WorkspaceTask> { it.done }
        .thenBy { it.date ?: LocalDate.MAX }.thenBy { it.key })

private fun thisWeek(date: LocalDate?, today: LocalDate): Boolean {
    if (date == null) return false
    val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    return !date.isBefore(monday) && date.isBefore(monday.plusDays(7))
}

private fun selectTasks(list: List<WorkspaceTask>, filter: String, today: LocalDate) =
    list.filter { task ->
        when (filter) {
            "pending" -> !task.done
            "done" -> task.done
            "week" -> !task.done && thisWeek(task.date, today)
            "local" -> task.storage == "local"
            "cloud" -> task.storage == "cloud"
            else -> true
        }
    }

/** Independent source/deadline facets over already-read tasks. */
internal fun facetWorkspaceTasks(
    tasks: List<WorkspaceTask>, source: String, thisWeekOnly: Boolean, today: LocalDate
): List<WorkspaceTask> = tasks.filter { task ->
    (source == "all" || task.category == source) &&
        (!thisWeekOnly || (!task.done && thisWeek(task.date, today)))
}

/** The search only filters already-read Room/D1 records; it never guesses school tasks. */
internal fun searchWorkspaceTasks(
    tasks: List<WorkspaceTask>, filter: String, query: String, today: LocalDate
): List<WorkspaceTask> {
    val needle = query.trim()
    return selectTasks(tasks, filter, today).filter { task ->
        needle.isEmpty() || task.title.contains(needle, ignoreCase = true) ||
            task.origin.contains(needle, ignoreCase = true)
    }
}

/** LazyColumn index of the first open editor, before local/cloud rows.
 * Always update this when adding/removing items before the editor.
 */
internal fun taskEditorStartIndex(
    staleSnapshot: Boolean,
    searchOpen: Boolean,
    advancedFiltersOpen: Boolean,
    managementOpen: Boolean
): Int = 3 + listOf(staleSnapshot, searchOpen, advancedFiltersOpen, managementOpen).count { it }

/**
 * 首页 V2：本机课程、真实任务、公告和学业入口组成唯一学习工作台。
 * 不推断当前教学周、上课时间或学校提交状态；云端任务保留快照标识。
 * 手机、平板共享单一滚动状态，不创建独立的海报或模拟页面。
 */
@Composable
fun RealAcademicHome(
    localTasks: List<LocalTask>,
    client: CloudClient,
    localLessons: List<VerifiedLesson> = emptyList(),
    openSchedule: () -> Unit,
    openTasks: () -> Unit,
    openMessages: () -> Unit,
    openProfile: () -> Unit,
    openAcademic: () -> Unit,
    openLocalSchool: (String) -> Unit = {},
    openFileImport: () -> Unit,
    openGrades: () -> Unit,
    openExams: () -> Unit,
    openSecondClass: () -> Unit,
    openArticle: (String) -> Unit
) {
    val state = client.state
    val today = LocalDate.now(SchoolZone)
    val all = remember(localTasks, state.tasks) { combineWorkspaceTasks(localTasks, state.tasks) }
    val context = LocalContext.current
    val nativeDao = remember(context) { LocalTaskDatabase.open(context).nativeSchoolRecords() }
    val nativeRows by nativeDao.observe().collectAsState(initial = emptyList())
    val localHomework = nativeRows.filter { it.provider == "chaoxing" && it.kind == "homework" }

    // Both Home and Schedule use the SAME locally imported timetable. Never fall
    // back to cloud course snapshots; the timetable is available while offline.
    val sameWeekday = localLessons.filter { it.weekday == today.dayOfWeek.value }
        .sortedBy { it.startSection }
    val weekdayLabel = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[today.dayOfWeek.value - 1]
    val pending = all.count { !it.done }
    val attention = all.count { !it.done && it.date != null && !it.date.isAfter(today.plusDays(3)) }
    val upcoming = all.filter { !it.done }.take(3)
    val cloudSnapshotRows = state.tasks.size
    val metricCaption = when {
        state.tasksFresh -> "本机 + 云端本次读取"
        cloudSnapshotRows > 0 -> "含上次云端快照 $cloudSnapshotRows 条"
        else -> "仅本机记录"
    }
    val todoCaption = if (!state.tasksFresh && cloudSnapshotRows > 0)
        "含上次云端快照 · 期限待核对"
    else "按已记录截止日排序 · 未注明日期的排后"

    // Sheets look up the current records by stable keys, not saved copies of
    // possibly stale local/cloud data. Reuse Schedule/Message details directly.
    var openedLessonKey by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedHomeCourseKey by rememberSaveable { mutableStateOf<String?>(null) }
    var openedTaskKey by rememberSaveable { mutableStateOf<String?>(null) }
    var openedNoticeKey by rememberSaveable { mutableStateOf<String?>(null) }
    val onLessonOpen: (VerifiedLesson) -> Unit = { openedLessonKey = homeLessonKey(it) }
    val onTaskOpen: (WorkspaceTask) -> Unit = { openedTaskKey = it.key }
    val onNoticeOpen: (String) -> Unit = { openedNoticeKey = it }

    val hero: @Composable () -> Unit = {
        HomeHero(
            lessons = sameWeekday,
            hasTimetable = localLessons.isNotEmpty(),
            weekdayLabel = weekdayLabel,
            selectedCourseKey = selectedHomeCourseKey,
            onSelectCourse = { selectedHomeCourseKey = it },
            openSchedule = openSchedule,
            openFileImport = openFileImport,
            onOpenLesson = onLessonOpen
        )
    }
    val tasks: @Composable () -> Unit = {
        HomeTodoBlock(upcoming, today, todoCaption, openTasks, onTaskOpen)
    }
    val metrics: @Composable () -> Unit = {
        HomeMetricsBlock(pending, attention, metricCaption, openTasks)
    }
    val academic: @Composable () -> Unit = {
        HomeQuickBlock(openAcademic, openGrades, openExams, openSecondClass,
            openChaoxing = { openLocalSchool("chaoxing") })
    }
    val notices: @Composable () -> Unit = {
        HomeNoticesBlock(state, openMessages, onNoticeOpen)
    }
    val homework: @Composable () -> Unit = {
        HomeHomeworkBlock(localHomework, openLocalSchool)
    }

    val homeListState = rememberLazyListState()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wideLayout = maxWidth >= 700.dp
        // One scroll owner on phones AND tablets; neither column scrolls separately.
        LazyColumn(
            state = homeListState,
            modifier = Modifier.fillMaxSize().widthIn(max = 1200.dp),
            contentPadding = PaddingValues(horizontal = if (wideLayout) 20.dp else 18.dp,
                vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { HomeGreeting(today, weekdayLabel) }
            if (wideLayout) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1.15f),
                            verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            hero()
                            metrics()
                        }
                        Column(Modifier.weight(1f)) { tasks() }
                    }
                }
                item { academic() }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) { notices() }
                        Column(Modifier.weight(1f)) { homework() }
                    }
                }
            } else {
                item { hero() }
                item { tasks() }
                item { metrics() }
                item { academic() }
                item { notices() }
                item { homework() }
            }
        }
    }
    val selectedLesson = localLessons.firstOrNull { homeLessonKey(it) == openedLessonKey }
    if (selectedLesson != null) {
        HomeCourseDetailSheet(selectedLesson, onDismiss = { openedLessonKey = null })
    }
    val selectedTask = all.firstOrNull { it.key == openedTaskKey }
    if (selectedTask != null) {
        WorkspaceTaskDetailSheet(
            selectedTask, today, state.tasksFresh,
            onDismiss = { openedTaskKey = null },
            onManage = { openedTaskKey = null; openTasks() }
        )
    }
    val selectedNotice = state.notices.firstOrNull {
        it.id.ifBlank { it.url } == openedNoticeKey
    }
    if (selectedNotice != null) {
        NoticeDetailSheet(selectedNotice, openArticle = openArticle,
            onDismiss = { openedNoticeKey = null })
    }
}

private fun homeLessonKey(lesson: VerifiedLesson): String =
    "${lesson.id}:${lesson.term}:${lesson.weekday}:${lesson.startSection}:" +
        "${lesson.endSection}:${lesson.title}:${lesson.weeks.sorted().joinToString(",")}"

internal fun resolveHomeCourseIndex(courseKeys: List<String>, selectedKey: String?): Int {
    if (selectedKey == null) return 0
    val index = courseKeys.indexOf(selectedKey)
    return if (index >= 0) index else 0
}

/** 日期与问候：只报今天，不写"第几教学周"（没有开学日期就无法得出）。 */
@Composable
private fun HomeGreeting(today: LocalDate, weekdayLabel: String) {
    val hour = remember { LocalTime.now(SchoolZone).hour }
    val greeting = when (hour) {
        in 5..10 -> "早上好"
        in 11..12 -> "中午好"
        in 13..17 -> "下午好"
        in 18..22 -> "晚上好"
        else -> "夜深了"
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = today.monthValue.toString() + "月" + today.dayOfMonth + "日 · " + weekdayLabel,
            style = MaterialTheme.typography.labelLarge,
            color = XiaoxuPalette.Ink2
        )
        Text(
            text = greeting,
            style = MaterialTheme.typography.headlineMedium,
            color = XiaoxuPalette.Ink
        )
    }
}

/** 分区外壳：标题 + 内容，统一 8dp 间距，页面不再各写各的。 */
@Composable
private fun HomeSection(
    title: String,
    caption: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SectionHeader(
            title = title, caption = caption,
            actionLabel = actionLabel, onAction = onAction
        )
        content()
    }
}

/** 本机导入课表按星期展示课程记录；周次未知时不得暗示今日真实开课。 */
@Composable
private fun HomeHero(
    lessons: List<VerifiedLesson>,
    hasTimetable: Boolean,
    weekdayLabel: String,
    selectedCourseKey: String?,
    onSelectCourse: (String) -> Unit,
    openSchedule: () -> Unit,
    openFileImport: () -> Unit,
    onOpenLesson: (VerifiedLesson) -> Unit
) {
    // The same weekday may contain many week-specific course blocks. Preserve
    // one main card, but make every imported block reachable without leaving Home.
    val selectedIndex = resolveHomeCourseIndex(lessons.map(::homeLessonKey), selectedCourseKey)
    val lesson = lessons.getOrNull(selectedIndex)
    when {
        lesson != null -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            XiaoxuHero(
                kicker = "${weekdayLabel}课程记录 · 本机课表",
                title = lesson.title,
                supporting = "第${lesson.startSection}–${lesson.endSection}节 · " +
                    lesson.location.ifBlank { "教室未提供" } + " · " +
                    lesson.teacher.ifBlank { "教师未提供" },
                badge = if (lessons.size > 1)
                    "${selectedIndex + 1}/${lessons.size} · 待核对" else "待核对",
                meta = "适用${formatWeeks(lesson.weeks)} · 未核定当前教学周",
                modifier = Modifier.clickable(onClickLabel = "查看课程详情") {
                    onOpenLesson(lesson)
                },
                actionLabel = "全部课表",
                onAction = openSchedule
            )
            if (lessons.size > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${weekdayLabel}共 ${lessons.size} 条课块记录",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = XiaoxuPalette.Ink2,
                        maxLines = 2
                    )
                    OutlinedButton(
                        onClick = { onSelectCourse(homeLessonKey(lessons[selectedIndex - 1])) },
                        enabled = selectedIndex > 0,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) { Text("上一条") }
                    OutlinedButton(
                        onClick = { onSelectCourse(homeLessonKey(lessons[selectedIndex + 1])) },
                        enabled = selectedIndex < lessons.lastIndex,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) { Text("下一条") }
                }
            }
        }
        hasTimetable -> XiaoxuHero(
            kicker = "${weekdayLabel}课程记录",
            title = "没有对应星期的课程记录",
            supporting = "不等于今天停课，请以实际教学周和学校通知为准。",
            badge = "本机导入",
            tone = XiaoxuTone.Neutral,
            actionLabel = "查看课表",
            onAction = openSchedule
        )
        else -> XiaoxuHero(
            kicker = "我的课表",
            title = "还没有导入课表",
            supporting = "导入 ICS / JSON 文件，课程将保存在本机并可离线查看。",
            tone = XiaoxuTone.Neutral,
            badge = "未导入",
            actionLabel = "从文件导入课表",
            onAction = openFileImport
        )
    }
}

/** 两张统计卡：数值与口径都来自已有记录，点击直达任务中心。 */
@Composable
private fun HomeMetricsBlock(
    pending: Int,
    attention: Int,
    caption: String,
    openTasks: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCard(label = "待完成", value = pending.toString(), caption = caption,
            modifier = Modifier.weight(1f), onClick = openTasks)
        MetricCard(label = "临近截止", value = attention.toString(),
            caption = "含逾期与未来三天 · 仅已记录期限",
            tone = XiaoxuTone.Warning,
            modifier = Modifier.weight(1f), onClick = openTasks)
    }
}

/** 学业服务直达真实子导航；未接通的平台保留诚实的空态。 */
@Composable
private fun HomeQuickBlock(
    openAcademic: () -> Unit,
    openGrades: () -> Unit,
    openExams: () -> Unit,
    openSecondClass: () -> Unit,
    openChaoxing: () -> Unit
) {
    HomeSection(title = "学业服务", actionLabel = "全部教务", onAction = openAcademic) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth < 420.dp) {
                // Four single-line labels in a 320–400dp phone would become
                // cramped. Keep native QuickEntry, arranged in two clear rows.
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        QuickEntry("成绩", Icons.Outlined.School, openGrades, Modifier.weight(1f))
                        QuickEntry("考试", Icons.Outlined.DateRange, openExams, Modifier.weight(1f))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        QuickEntry("第二课堂", Icons.Outlined.CheckCircle, openSecondClass,
                            Modifier.weight(1f), XiaoxuTone.Success)
                        QuickEntry("学习通", Icons.Outlined.MailOutline, openChaoxing,
                            Modifier.weight(1f), XiaoxuTone.Warning)
                    }
                }
            } else {
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                    QuickEntry("成绩", Icons.Outlined.School, openGrades, Modifier.weight(1f))
                    QuickEntry("考试", Icons.Outlined.DateRange, openExams, Modifier.weight(1f))
                    QuickEntry("第二课堂", Icons.Outlined.CheckCircle, openSecondClass,
                        Modifier.weight(1f), XiaoxuTone.Success)
                    QuickEntry("学习通", Icons.Outlined.MailOutline, openChaoxing,
                        Modifier.weight(1f), XiaoxuTone.Warning)
                }
            }
        }
    }
}

/** 待办与提醒：截止状态复用任务中心判定，缺日期不猜。 */
@Composable
private fun HomeTodoBlock(
    upcoming: List<WorkspaceTask>,
    today: LocalDate,
    caption: String,
    openTasks: () -> Unit,
    onTaskOpen: (WorkspaceTask) -> Unit
) {
    HomeSection(title = "优先处理", caption = caption,
        actionLabel = "全部任务", onAction = openTasks) {
        XiaoxuCard {
            if (upcoming.isEmpty()) {
                Text("当前没有已保存的待办任务",
                    style = MaterialTheme.typography.bodyMedium, color = XiaoxuPalette.Ink2)
            } else {
                upcoming.forEachIndexed { index, task ->
                    if (index > 0) HorizontalDivider(color = XiaoxuPalette.Line)
                    HomeTaskRow(task, today, onOpen = { onTaskOpen(task) })
                }
            }
        }
    }
}

/** 官方通知：只放已读取的公开公告，标题与日期分行，不编造正文。 */
@Composable
private fun HomeNoticesBlock(
    state: CloudUiState,
    openMessages: () -> Unit,
    onNoticeOpen: (String) -> Unit
) {
    HomeSection(title = "校园动态",
        caption = "来自已读取的学校官网公告",
        actionLabel = "消息中心", onAction = openMessages) {
        XiaoxuCard {
            if (state.notices.isEmpty()) {
                Text("暂无已读取的官方通知",
                    style = MaterialTheme.typography.bodyMedium, color = XiaoxuPalette.Ink2)
            } else {
                state.notices.take(2).forEachIndexed { index, notice ->
                    if (index > 0) HorizontalDivider(color = XiaoxuPalette.Line)
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .clickable(onClickLabel = "查看公告详情") {
                                onNoticeOpen(notice.id.ifBlank { notice.url })
                            }
                            .padding(vertical = 9.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(notice.title, style = MaterialTheme.typography.titleSmall,
                            color = XiaoxuPalette.Ink,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(notice.dateLabel + " · " + noticeCategoryLabel(notice.category),
                            style = MaterialTheme.typography.labelSmall,
                            color = XiaoxuPalette.Ink3)
                    }
                }
            }
            state.publicError?.let {
                Text("公告刷新失败，旧记录可能过期",
                    style = MaterialTheme.typography.labelSmall,
                    color = XiaoxuPalette.Danger)
            }
        }
    }
}

/** 本机学习通页面快照：不参与真实任务统计或学校提交状态。 */
@Composable
private fun HomeHomeworkBlock(
    localHomework: List<NativeSchoolRecord>,
    openLocalSchool: (String) -> Unit
) {
    HomeSection(
        title = "学习通 · 本机作业",
        caption = "本机读取"
    ) {
        XiaoxuCard {
            if (localHomework.isEmpty()) {
                Text(
                    text = "还没有本机读取到的作业标题",
                    style = MaterialTheme.typography.bodyMedium,
                    color = XiaoxuPalette.Ink2
                )
                TextButton(onClick = { openLocalSchool("chaoxing") }) {
                    Text("在 App 内读取学习通作业")
                }
            } else {
                Text(
                    text = "已保存 ${localHomework.size} 条",
                    style = MaterialTheme.typography.bodySmall,
                    color = XiaoxuPalette.Ink2
                )
                localHomework.take(3).forEachIndexed { index, work ->
                    if (index > 0) HorizontalDivider(color = XiaoxuPalette.Line)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = work.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = XiaoxuPalette.Ink,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "采集时间：" + DateFormat.getDateTimeInstance(
                                DateFormat.SHORT, DateFormat.SHORT
                            ).format(Date(work.capturedAt)),
                            style = MaterialTheme.typography.labelSmall,
                            color = XiaoxuPalette.Ink3
                        )
                    }
                }
                TextButton(onClick = { openLocalSchool("chaoxing") }) {
                    Text("重新读取官网当前页面")
                }
            }
        }
    }
}



/** 首页待办行：状态标签沿用任务中心同一套判定（describeDue），不在这里另算。 */
@Composable
private fun HomeTaskRow(task: WorkspaceTask, today: LocalDate, onOpen: () -> Unit) {
    val due = remember(task, today) { describeDue(task, today) }
    val tone = when (due.emphasis) {
        DUE_OVERDUE -> XiaoxuTone.Danger
        DUE_TODAY -> XiaoxuTone.Warning
        DUE_WEEK -> XiaoxuTone.Brand
        else -> XiaoxuTone.Neutral
    }
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .clickable(onClickLabel = "查看任务详情", onClick = onOpen)
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = task.title,
            style = MaterialTheme.typography.titleSmall,
            color = XiaoxuPalette.Ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            StatusChip(due.label, tone)
            Text(
                text = task.origin,
                style = MaterialTheme.typography.labelSmall,
                color = XiaoxuPalette.Ink3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * A unified VIEW, preserving both write stores and existing live Chaoxing reader.
 *
 * 视觉分区（本机 / 云端 / 本机页面快照）只是展示层分组：三个数据来源依旧各自存储，
 * 本机学习通快照不参与待完成 / 本周截止 / 已完成三项统计，也不合成任何任务或截止日。
 */
@Composable
fun RealAcademicTaskHub(
    localTasks: List<LocalTask>, dao: LocalTaskDao, client: CloudClient,
    openWebsite: () -> Unit,
    openLocalSchool: (String) -> Unit = {},
    onMessages: () -> Unit
) {
    val state = client.state
    val today = LocalDate.now(SchoolZone)
    val all = remember(localTasks, state.tasks) { combineWorkspaceTasks(localTasks, state.tasks) }
    val context = LocalContext.current
    val schoolDao = remember(context) { LocalTaskDatabase.open(context).nativeSchoolRecords() }
    val schoolRows by schoolDao.observe().collectAsState(initial = emptyList())
    val capturedHomework = schoolRows.filter {
        it.provider == "chaoxing" && it.kind == "homework"
    }
    var filter by rememberSaveable { mutableStateOf("pending") }
    var sourceFilter by rememberSaveable { mutableStateOf("all") }
    var sourceMenuOpen by rememberSaveable { mutableStateOf(false) }
    var onlyThisWeek by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var showManager by rememberSaveable { mutableStateOf(false) }
    var showAdvancedFilters by rememberSaveable { mutableStateOf(false) }
    var showCourseTasks by rememberSaveable { mutableStateOf(true) }
    var showWechatTasks by rememberSaveable { mutableStateOf(true) }
    var showPersonalTasks by rememberSaveable { mutableStateOf(true) }
    val taskScope = rememberCoroutineScope()
    var showSchoolRecords by rememberSaveable { mutableStateOf(false) }
    // This report is for the CURRENT owner session, not Room or the task count.
    var showOfficialAudit by rememberSaveable(state.chaoxingBatchCheckedAt) {
        mutableStateOf(state.chaoxingBatchCheckedAt != null)
    }
    var showAllOfficialHomework by rememberSaveable(state.chaoxingBatchCheckedAt) {
        mutableStateOf(false)
    }
    val auditSummary = remember(state.chaoxingBatchAudit, state.chaoxingCourses,
        state.chaoxingBatchTaskLedgerFresh) {
        summarizeChaoxingAudit(state.chaoxingBatchAudit, state.chaoxingCourses.size,
            state.chaoxingBatchTaskLedgerFresh)
    }
    val officialRows = remember(state.chaoxingBatchAudit) {
        state.chaoxingBatchAudit.flatMap { course ->
            course.homework.map { work -> course to work }
        }
    }
    var showLocal by rememberSaveable { mutableStateOf(false) }
    var showCloud by rememberSaveable { mutableStateOf(false) }
    // All optional rows must be counted. Fixed indexes shifted when old helper lines
    // were removed, so local/cloud buttons could land on the wrong section.
    val taskListState = rememberLazyListState()
    var jumpTo by remember { mutableStateOf<String?>(null) }
    val staleTasksShown = !state.tasksFresh && state.tasks.isNotEmpty()
    LaunchedEffect(jumpTo, showLocal, showCloud, showManager, showSearch,
        showAdvancedFilters, staleTasksShown) {
        val managerStart = taskEditorStartIndex(
            staleTasksShown, showSearch, showAdvancedFilters, showManager
        )
        val targetIndex = when (jumpTo) {
            "local" -> if (showLocal) managerStart else null
            "cloud" -> if (showCloud) managerStart + (if (showLocal) 1 else 0) else null
            "filters" -> 1
            else -> null
        }
        if (targetIndex != null) {
            taskListState.animateScrollToItem(targetIndex)
            jumpTo = null
        }
    }
    // 详情只保存稳定 key，不缓存可能过期的学校/云端任务对象。
    var openedTaskKey by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedTask = all.firstOrNull { it.key == openedTaskKey }
    val visible = facetWorkspaceTasks(
        searchWorkspaceTasks(all, filter, searchQuery, today),
        sourceFilter, onlyThisWeek, today
    )
    // 保持原有 80 条展示上限；超过上限时必须明确提示还有未显示的任务。
    val shown = visible.take(80)
    val localRows = shown.filter { it.category == "local" }
    val cloudRows = shown.filter { it.category == "cloud" }
    val wechatRows = shown.filter { it.category == "wechat" }
    LazyColumn(
        state = taskListState,
        modifier = Modifier.fillMaxSize().widthIn(max = 860.dp),
        contentPadding = PaddingValues(18.dp),
        // Zero inter-item gap is required for connected task rows. Section items
        // add their own spacing so we keep LazyColumn's per-task virtualization.
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("任务中心", style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 24.sp, lineHeight = 31.sp),
                        color = XiaoxuPalette.Ink,
                        modifier = Modifier.semantics { heading() })
                    Text(
                        today.format(DateTimeFormatter.ofPattern("M月d日 · EEEE",
                            java.util.Locale.CHINA)),
                        style = MaterialTheme.typography.labelMedium,
                        color = XiaoxuPalette.Ink2
                    )
                }
                IconButton(onClick = onMessages) {
                    Icon(Icons.Outlined.NotificationsNone,
                        contentDescription = "打开消息中心", tint = XiaoxuPalette.Ink2)
                }
                IconButton(
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                        .background(XiaoxuPalette.Surface),
                    onClick = { showSearch = !showSearch }
                ) {
                    Icon(Icons.Outlined.Search, contentDescription = "搜索任务",
                        tint = XiaoxuPalette.Brand)
                }
                Spacer(Modifier.width(7.dp))
                IconButton(
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                        .background(XiaoxuPalette.Surface),
                    onClick = { taskScope.launch { client.refreshPrivate() } },
                    enabled = state.authenticated && !state.busy
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "刷新校序云端任务",
                        tint = if (state.authenticated) XiaoxuPalette.Brand else XiaoxuPalette.Ink3)
                }
                Spacer(Modifier.width(7.dp))
                IconButton(
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                        .background(XiaoxuPalette.Brand),
                    onClick = {
                        if (showManager && showLocal) {
                            showManager = false
                            showLocal = false
                            showCloud = false
                            jumpTo = null
                        } else {
                            showManager = true
                            showLocal = true
                            showCloud = false
                            jumpTo = "local"
                        }
                    }
                ) {
                    Icon(
                        if (showManager && showLocal) Icons.Outlined.Close else Icons.Outlined.Add,
                        contentDescription = if (showManager && showLocal)
                            "收起新建本机任务" else "新增本机任务",
                        tint = Color.White
                    )
                }
            }
        }
        item {
            // One continuous status strip; values come from real saved tasks.
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(XiaoxuPalette.Surface)
                    .border(1.dp, XiaoxuPalette.Line, RoundedCornerShape(18.dp))
                    .padding(5.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TaskStatusTab(
                    label = "待完成", count = all.count { !it.done },
                    active = filter == "pending", modifier = Modifier.weight(1f),
                    onClick = { filter = "pending"; jumpTo = "filters" }
                )
                TaskStatusTab(
                    label = "全部", count = all.size,
                    active = filter == "all", modifier = Modifier.weight(1f),
                    onClick = { filter = "all"; jumpTo = "filters" }
                )
                TaskStatusTab(
                    label = "已完成", count = all.count { it.done },
                    active = filter == "done", modifier = Modifier.weight(1f),
                    onClick = { filter = "done"; jumpTo = "filters" }
                )
            }
        }
        if (!state.tasksFresh && state.tasks.isNotEmpty()) item {
            Text("含上次云端快照 " + state.tasks.size + " 条",
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.labelSmall,
                color = XiaoxuPalette.Ink2)
        }
        if (showSearch) item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it.take(100) },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                singleLine = true,
                label = { Text("搜索任务标题或来源") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = if (searchQuery.isNotEmpty()) {
                    {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Outlined.Close, contentDescription = "清除搜索")
                        }
                    }
                } else null
            )
        }
        item {
            // Source and management controls stay compact; full filters are on demand.
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    TextButton(
                        onClick = { sourceMenuOpen = true },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(
                            when (sourceFilter) {
                                "local" -> "本机来源"
                                "cloud" -> "云端来源"
                                "wechat" -> "微信任务"
                                else -> "全部来源"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = XiaoxuPalette.Ink
                        )
                        Icon(Icons.Outlined.ExpandMore, contentDescription = "选择任务来源",
                            tint = XiaoxuPalette.Ink2, modifier = Modifier.size(20.dp))
                    }
                    DropdownMenu(
                        expanded = sourceMenuOpen,
                        onDismissRequest = { sourceMenuOpen = false }
                    ) {
                        val sources = listOf("all" to "全部来源",
                            "cloud" to "云端来源", "wechat" to "微信任务",
                            "local" to "本机来源")
                        sources.forEach { (source, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    sourceFilter = source
                                    sourceMenuOpen = false
                                }
                            )
                        }
                    }
                }
                TextButton(
                    onClick = {
                        if (showManager) {
                            showManager = false
                            showLocal = false
                            showCloud = false
                            jumpTo = null
                        } else {
                            showManager = true
                        }
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(if (showManager) "收起管理" else "管理",
                        style = MaterialTheme.typography.labelLarge)
                }
                IconButton(
                    onClick = { showAdvancedFilters = !showAdvancedFilters },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Outlined.Tune,
                        contentDescription = if (showAdvancedFilters) "收起日期筛选" else "筛选截止日期",
                        tint = XiaoxuPalette.Ink2, modifier = Modifier.size(21.dp))
                }
            }
        }
        if (showAdvancedFilters) item {
            LazyRow(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = !onlyThisWeek,
                        onClick = { onlyThisWeek = false },
                        label = { Text("全部日期") }
                    )
                }
                item {
                    FilterChip(
                        selected = onlyThisWeek,
                        onClick = { onlyThisWeek = true },
                        label = { Text("本周截止") }
                    )
                }
            }
        }
        if (showManager) item {
            Row(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { showLocal = !showLocal; if (showLocal) jumpTo = "local" },
                    modifier = Modifier.weight(1f)
                ) { Text(if (showLocal) "收起本机管理" else "新建 / 管理本机") }
                OutlinedButton(
                    onClick = { showCloud = !showCloud; if (showCloud) jumpTo = "cloud" },
                    modifier = Modifier.weight(1f)
                ) { Text(if (showCloud) "收起云端新增" else "新增云端任务") }
            }
        }
        if (showManager && showLocal) item {
            Column(Modifier.padding(top = 12.dp)) { LocalTaskEditor(localTasks, dao) }
        }
        if (showManager && showCloud) item {
            Column(Modifier.padding(top = 12.dp)) { CloudTaskEditor(client) }
        }
        if (visible.size > 80) item {
            Text("已显示 " + shown.size + " / " + visible.size + " 条，可调整筛选查看剩余任务",
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (visible.isEmpty()) {
            item {
                XiaoxuEmpty(
                    modifier = Modifier.padding(top = 12.dp),
                    title = if (filter == "done" && onlyThisWeek) "当前筛选条件互相冲突"
                        else "没有匹配的已保存任务",
                    message = when {
                        filter == "done" && onlyThisWeek ->
                            "“本周截止”只筛选未完成任务；请取消该日期筛选后查看已完成记录。"
                        searchQuery.isNotBlank() ->
                            "搜索未匹配到已保存任务，可清除搜索词；不能据此推断学校没有作业。"
                        sourceFilter != "all" ->
                            "当前来源没有匹配记录；本机与云端数据分别保存，未显示不代表没有学校作业。"
                        onlyThisWeek ->
                            "已保存记录中没有匹配的本周未完成任务；尚未核验的学校作业不计入。"
                        !state.authenticated ->
                            "校序云端尚未连接，可先新增本机待办。"
                        else -> "当前筛选没有任务，不能据此推断学校没有作业。"
                    },
                    actionLabel = when {
                        filter == "done" && onlyThisWeek -> "清除本周截止筛选"
                        searchQuery.isNotBlank() -> "清除搜索词"
                        sourceFilter != "all" -> "查看全部来源"
                        onlyThisWeek -> "清除本周截止筛选"
                        else -> "新增 / 管理本机任务"
                    },
                    onAction = {
                        when {
                            filter == "done" && onlyThisWeek -> onlyThisWeek = false
                            searchQuery.isNotBlank() -> searchQuery = ""
                            sourceFilter != "all" -> sourceFilter = "all"
                            onlyThisWeek -> onlyThisWeek = false
                            else -> {
                                showManager = true
                                showLocal = true
                                jumpTo = "local"
                            }
                        }
                    }
                )
            }
        } else {
            if (cloudRows.isNotEmpty()) {
                item {
                    TaskGroupHeader(
                        title = "课程任务",
                        count = cloudRows.size,
                        badge = "",
                        note = "",
                        icon = Icons.Outlined.MenuBook,
                        expanded = showCourseTasks,
                        onClick = { showCourseTasks = !showCourseTasks }
                    )
                }
                if (showCourseTasks) {
                    items(count = cloudRows.size, key = { i -> cloudRows[i].key }) { i ->
                        ConnectedTaskShell(index = i, total = cloudRows.size) {
                            TaskRow(cloudRows[i], today, onOpen = { openedTaskKey = cloudRows[i].key })
                        }
                    }
                }
            }
            if (wechatRows.isNotEmpty()) {
                item {
                    TaskGroupHeader(
                        title = "微信任务",
                        count = wechatRows.size,
                        badge = "",
                        note = "",
                        icon = Icons.Outlined.ChatBubbleOutline,
                        expanded = showWechatTasks,
                        onClick = { showWechatTasks = !showWechatTasks }
                    )
                }
                if (showWechatTasks) {
                    items(count = wechatRows.size, key = { i -> wechatRows[i].key }) { i ->
                        ConnectedTaskShell(index = i, total = wechatRows.size) {
                            TaskRow(wechatRows[i], today,
                                onOpen = { openedTaskKey = wechatRows[i].key })
                        }
                    }
                }
            }
            if (localRows.isNotEmpty()) {
                item {
                    TaskGroupHeader(
                        title = "本机任务",
                        count = localRows.size,
                        badge = "",
                        note = "",
                        icon = Icons.Outlined.CheckBox,
                        expanded = showPersonalTasks,
                        onClick = { showPersonalTasks = !showPersonalTasks }
                    )
                }
                if (showPersonalTasks) {
                    items(count = localRows.size, key = { i -> localRows[i].key }) { i ->
                        ConnectedTaskShell(index = i, total = localRows.size) {
                            TaskRow(localRows[i], today,
                                onOpen = { openedTaskKey = localRows[i].key },
                                onToggleLocal = { checked ->
                                    // 仅编辑 Room 中本人待办，不写学校提交状态或云端任务。
                                    val id = localRows[i].key.removePrefix("local:").toLongOrNull()
                                    if (id != null) taskScope.launch { dao.setCompleted(id, checked) }
                                }
                            )
                        }
                    }
                }
            }
        }
        if (state.privateError != null) {
            item {
                Text("云端部分数据刷新失败，已有记录可能过期",
                    modifier = Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error)
            }
        }
        // The authenticated Chaoxing read is a separate evidence layer. It
        // never enters owner-completed task counters or guesses missing dates.
        item {
            TaskGroupHeader(
                title = "学习通 · 官方作业核验",
                count = auditSummary.verifiedWorkRows,
                badge = if (auditSummary.fullyCoveredAndRefreshed)
                    "全部课程条数已核验" else "覆盖未全部证实",
                note = when {
                    state.chaoxingBatchBusy -> "正在逐门读取本人课程的官方状态"
                    state.chaoxingBatchCheckedAt != null ->
                        "本次 ${auditSummary.checkedCourses}/${auditSummary.totalCourses} 门课程有结果"
                    else -> "尚无本次逐门核验结果，不能据此判断作业总数"
                },
                icon = Icons.Outlined.MenuBook,
                expanded = showOfficialAudit,
                onClick = { showOfficialAudit = !showOfficialAudit },
                // In-progress batch is not a confirmed zero-result read.
                showCount = state.chaoxingBatchCheckedAt != null
            )
        }
        if (showOfficialAudit) {
            item {
                XiaoxuCard(modifier = Modifier.padding(top = 12.dp)) {
                    Text(
                        "仅展示本人授权会话中实际核实的课程和作业状态；不会自动提交或标记本机任务完成。",
                        style = MaterialTheme.typography.bodySmall, color = XiaoxuPalette.Ink2
                    )
                    OutlinedButton(onClick = { openLocalSchool("chaoxing") },
                        modifier = Modifier.fillMaxWidth()) {
                        Text("在 App 内打开学习通官网核对")
                    }
                    val linked = state.platforms.any {
                        it.id == "chaoxing" && it.sessionState == "active"
                    }
                    OutlinedButton(
                        enabled = linked && state.authenticated && !state.busy &&
                            !state.chaoxingBusy && !state.chaoxingBatchBusy &&
                            !state.chaoxingDetailBusy,
                        onClick = {
                            taskScope.launch { client.refreshChaoxingCoursesAndHomework() }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (state.chaoxingBatchBusy) "正在逐门核验..."
                            else "重新读取并核验全部课程")
                    }
                    if (!linked) {
                        Text(
                            "尚未取得有效云端学习通授权；仍可使用上方本机官方页面读取。",
                            style = MaterialTheme.typography.labelSmall, color = XiaoxuPalette.Ink2
                        )
                    }
                    state.chaoxingBatchStatus?.let { report ->
                        Text(report, style = MaterialTheme.typography.bodySmall,
                            color = XiaoxuPalette.Ink2)
                    }
                    if (state.chaoxingBatchCheckedAt != null) {
                        Text(
                            "本次核验：${auditSummary.countVerifiedCourses} 门作业条数可核对；" +
                                "${auditSummary.listOnlyCourses} 门仅列表有效；" +
                                "${auditSummary.failedCourses} 门失败；" +
                                "${auditSummary.notCheckedCourses} 门未检查。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = XiaoxuPalette.Ink
                        )
                        Text(
                            "官方返回的已核验条目 ${auditSummary.verifiedWorkRows} 条：" +
                                "未提交 ${auditSummary.statuses["todo"] ?: 0}、" +
                                "已提交 ${auditSummary.statuses["submitted"] ?: 0}、" +
                                "已批阅 ${auditSummary.statuses["graded"] ?: 0}、" +
                                "已退回 ${auditSummary.statuses["returned"] ?: 0}、" +
                                "状态未知 ${auditSummary.statuses["unknown"] ?: 0}。",
                            style = MaterialTheme.typography.labelMedium,
                            color = XiaoxuPalette.Ink2
                        )
                        Text(
                            if (auditSummary.fullyCoveredAndRefreshed)
                                "已核验全部课程的返回条数，云端任务列表也已刷新；仍需本人官网对照。"
                            else "本次尚不能确认全部作业覆盖；不以缺失、失败或未检查记录推定已完成。",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (auditSummary.fullyCoveredAndRefreshed)
                                XiaoxuPalette.Success else XiaoxuPalette.Warning
                        )
                        Text(
                            "本次核验时间：${state.chaoxingBatchCheckedAt} · 仅当前会话有效",
                            style = MaterialTheme.typography.labelSmall, color = XiaoxuPalette.Ink3
                        )
                    }
                }
            }
            if (state.chaoxingBatchAudit.isNotEmpty()) {
                items(count = state.chaoxingBatchAudit.size,
                    key = { i -> "cx-audit:" + state.chaoxingBatchAudit[i].courseKey }) { i ->
                    val course = state.chaoxingBatchAudit[i]
                    val coverage = when (course.coverage) {
                        ChaoxingCourseCoverage.COUNT_VERIFIED -> "条数已核验"
                        ChaoxingCourseCoverage.LIST_ONLY -> "列表有效 · 总量未核验"
                        ChaoxingCourseCoverage.FAILED -> "本次读取失败"
                        ChaoxingCourseCoverage.NOT_CHECKED -> "本次未检查"
                    }
                    XiaoxuCard(modifier = Modifier.padding(top = 12.dp), contentPadding = 10.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(course.courseTitle, modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.width(8.dp))
                            Text(coverage, style = MaterialTheme.typography.labelSmall,
                                color = if (course.coverage == ChaoxingCourseCoverage.COUNT_VERIFIED)
                                    XiaoxuPalette.Success else XiaoxuPalette.Warning)
                        }
                        if (course.homework.isNotEmpty()) {
                            Text("已核实 ${course.homework.size} 项官方状态",
                                style = MaterialTheme.typography.labelSmall,
                                color = XiaoxuPalette.Ink2)
                        }
                    }
                }
            }
            if (officialRows.isNotEmpty()) {
                item {
                    Text("逐项官方状态（本次本人会话读取） · 显示 " +
                        (if (showAllOfficialHomework) officialRows.size else
                            minOf(30, officialRows.size)) + " / " + officialRows.size,
                        modifier = Modifier.padding(top = 12.dp),
                        style = MaterialTheme.typography.titleSmall, color = XiaoxuPalette.Ink)
                }
                items(
                    count = if (showAllOfficialHomework) officialRows.size
                        else minOf(30, officialRows.size),
                    key = { i ->
                        val (course, work) = officialRows[i]
                        "cx-work:" + course.courseKey + ":" + work.workId
                    }
                ) { i ->
                    val (course, work) = officialRows[i]
                    val status = when (work.statusCode) {
                        "todo" -> "官方：未提交"
                        "submitted" -> "官方：已提交／待批阅"
                        "graded" -> "官方：已批阅"
                        "returned" -> "官方：已退回"
                        else -> "官方：状态未知"
                    }
                    XiaoxuCard(modifier = Modifier.padding(top = 12.dp), contentPadding = 12.dp) {
                        Text(work.title, fontWeight = FontWeight.SemiBold,
                            color = XiaoxuPalette.Ink)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(course.courseTitle, modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelSmall,
                                color = XiaoxuPalette.Ink2)
                            Text(status, style = MaterialTheme.typography.labelSmall,
                                color = if (work.statusCode == "todo")
                                    XiaoxuPalette.Warning else XiaoxuPalette.Brand)
                        }
                        Text("官方核验状态；不代表已经核对所有课程或取得完整截止时间。",
                            style = MaterialTheme.typography.labelSmall,
                            color = XiaoxuPalette.Ink3)
                    }
                }
                if (officialRows.size > 30) {
                    item {
                        TextButton(
                            modifier = Modifier.padding(top = 12.dp),
                            onClick = { showAllOfficialHomework = !showAllOfficialHomework }
                        ) {
                            Text(if (showAllOfficialHomework) "收起至前 30 条"
                                else "展开其余 ${officialRows.size - 30} 条官方作业")
                        }
                    }
                }
            }
        }
        item {
            XiaoxuCard(modifier = Modifier.padding(top = 12.dp)) {
                SectionHeader(
                    title = "学习通与本机页面快照",
                    caption = "本机作业 ${capturedHomework.size} 条 · 不计入正式任务统计",
                    actionLabel = if (showSchoolRecords) "收起" else "展开查看与同步",
                    onAction = { showSchoolRecords = !showSchoolRecords }
                )
            }
        }
        if (showSchoolRecords) {
        item {
            HorizontalDivider(Modifier.padding(top = 6.dp))
        }
        item {
            TaskSectionHeader(
                title = "学习通 · 本机作业快照",
                count = capturedHomework.size,
                badge = "本机页面快照",
                note = "本机读取到的作业名称单独展示，不纳入待完成/已完成数量，不推定截止日期或学校提交状态。"
            )
            OutlinedButton(
                onClick = { openLocalSchool("chaoxing") },
                modifier = Modifier.heightIn(min = 48.dp).padding(top = 4.dp)
            ) {
                Text(if (capturedHomework.isEmpty()) "在 App 内读取学习通作业"
                     else "更新本机学习通作业")
            }
        }
        if (capturedHomework.isEmpty()) {
            item {
                Text("暂无本机作业记录；可到「我的」使用 App 内学习通读取。",
                    modifier = Modifier.padding(top = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            // Bounded Room pagination allows 500 stored records; LazyColumn
            // must not silently hide pages after the first 120.
            items(count = minOf(capturedHomework.size, 500),
                key = { i -> "school:" + capturedHomework[i].fingerprint }) { index ->
                val work = capturedHomework[index]
                XiaoxuCard(modifier = Modifier.padding(top = 12.dp),
                    contentPadding = 8.dp) { SnapshotRow(work) }
            }
        }
        item {
            TaskSectionHeader(
                title = "本机其他已读取记录",
                count = 0,
                badge = "本机页面快照",
                note = "课程记录不参与任务统计。",
                showCount = false
            )
        }
        item {
            Box(Modifier.padding(top = 12.dp)) {
                LocalSchoolRecordSection(setOf("course"))
            }
        }
        item {
            Box(Modifier.padding(top = 12.dp)) {
                ChaoxingHomeworkPanel(client, openSchoolReader = { openLocalSchool("chaoxing") })
            }
        }
         item {
            Text("学习通官方状态只在已核验读取后显示，个人已完成不代表学校已提交。",
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        }
    }

    if (selectedTask != null) {
        WorkspaceTaskDetailSheet(selectedTask, today, state.tasksFresh,
            onDismiss = { openedTaskKey = null },
            onManage = {
                showManager = true
                if (selectedTask.storage == "local") {
                    showLocal = true
                    showCloud = false
                    jumpTo = "local"
                } else {
                    showCloud = true
                    showLocal = false
                    jumpTo = "cloud"
                }
                openedTaskKey = null
            })
    }
}

/** 首页与任务中心共用任务详情，不在首页重建独立状态机。 */
@Composable
internal fun WorkspaceTaskDetailSheet(
    selectedTask: WorkspaceTask,
    today: LocalDate,
    tasksFresh: Boolean,
    onDismiss: () -> Unit,
    onManage: () -> Unit
) {
    val selectedDue = describeDue(selectedTask, today)
    XiaoxuSheet(onDismiss = onDismiss, title = "任务详情") {
        Text(selectedTask.title, style = MaterialTheme.typography.titleLarge,
            color = XiaoxuPalette.Ink)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SourceBadge(
                when (selectedTask.category) {
                    "wechat" -> "微信任务"
                    "local" -> "本机任务"
                    else -> "云端任务"
                },
                if (selectedTask.category == "local") XiaoxuTone.Brand else XiaoxuTone.Neutral
            )
            StatusChip(if (selectedTask.done) "已完成（个人记录）" else "待完成",
                if (selectedTask.done) XiaoxuTone.Success else XiaoxuTone.Neutral)
        }
        Text("来源：" + selectedTask.origin, style = MaterialTheme.typography.bodyMedium)
        Text("截止：" + selectedDue.label, style = MaterialTheme.typography.bodyMedium)
        Text(if (selectedTask.storage == "cloud") {
            if (tasksFresh) "本次云端读取 · 截止日期待核对"
            else "上次云端快照 · 待核对"
        } else "本机记录 · 离线保存",
            style = MaterialTheme.typography.bodySmall, color = XiaoxuPalette.Ink2)
        OutlinedButton(onClick = onManage,
            modifier = Modifier.heightIn(min = 48.dp).fillMaxWidth()) {
            Text(if (selectedTask.storage == "local")
                "打开本机任务管理" else "新增云端任务")
        }
    }
}




/** Three real-data statuses in a single container, sized through the shared typography. */
@Composable
private fun TaskStatusTab(
    label: String, count: Int, active: Boolean, modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) XiaoxuPalette.BrandSoft else Color.Transparent)
            .clickable(onClickLabel = "筛选$label", onClick = onClick)
            .heightIn(min = 74.dp)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            color = if (active) XiaoxuPalette.BrandDeep else XiaoxuPalette.Ink2,
            maxLines = 1
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, lineHeight = 24.sp),
            fontWeight = FontWeight.SemiBold,
            color = if (active) XiaoxuPalette.BrandDeep else XiaoxuPalette.Ink,
            maxLines = 1
        )
    }
}

/** 轻量分组标题：一层布局保留真实来源及核验信息，不再套图标大卡与独立徽标。 */
@Composable
private fun TaskGroupHeader(
    title: String,
    count: Int,
    badge: String,
    note: String,
    icon: ImageVector,
    expanded: Boolean,
    onClick: () -> Unit,
    showCount: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClickLabel = if (expanded) "收起$title" else "展开$title", onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append(title)
                    if (showCount) append("，$count 条")
                    if (badge.isNotBlank()) append("，").append(badge)
                    if (note.isNotBlank()) append("，").append(note)
                    append(if (expanded) "，点击收起" else "，点击展开")
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, contentDescription = null, tint = XiaoxuPalette.Ink2,
            modifier = Modifier.size(20.dp))
        Column(modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                title + if (showCount) " ($count)" else "",
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, lineHeight = 21.sp),
                color = XiaoxuPalette.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() }
            )
            if (badge.isNotBlank() || note.isNotBlank()) {
                Text(
                    listOf(badge, note).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = XiaoxuPalette.Ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Icon(
            if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            contentDescription = null,
            tint = XiaoxuPalette.Ink2,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * 分区标题：来源徽标 + 当前组条数 + 一句来源边界说明。
 * 读屏把标题当 heading 朗读，数字单独报出，避免"若干任务"这种模糊表述。
 */
@Composable
private fun TaskSectionHeader(
    title: String,
    count: Int,
    badge: String,
    note: String,
    showCount: Boolean = true
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                modifier = Modifier.weight(1f).semantics { heading() }
            )
            if (showCount) {
                Text(
                    text = "$count 条",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { contentDescription = "本区共 $count 条" }
                )
            }
        }
        TaskSourceBadge(badge, emphasized = true)
        Text(
            text = note,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 来源徽标：只描述数据从哪来，不代表任何学校核验结论。 */
@Composable
private fun TaskSourceBadge(text: String, emphasized: Boolean = false) {
    // 保留既有调用接口，仅替换视觉实现；来源标签不等于学校提交或同步认证。
    SourceBadge(text, tone = if (emphasized) XiaoxuTone.Brand else XiaoxuTone.Neutral)
}

/** 截止日展示：只呈现已有日期，缺失就说缺失，不推算、不留空。 */
private data class DueUi(val label: String, val spoken: String, val emphasis: Int)

private fun describeDue(task: WorkspaceTask, today: LocalDate): DueUi {
    val date = task.date
    if (date == null) {
        return if (task.storage == "cloud") {
            DueUi("截止未核验", "截止日期未核验", DUE_MISSING)
        } else {
            DueUi("截止未记录", "没有记录截止日期", DUE_MISSING)
        }
    }
    // 不做 locale 相关格式化，避免某些区域把数字换成非 ASCII 字形。
    val text = date.monthValue.toString().padStart(2, '0') + "-" +
        date.dayOfMonth.toString().padStart(2, '0')
    val prefix = if (task.storage == "cloud") "待核对 · " else ""
    val spokenPrefix = if (task.storage == "cloud") "云端待核对，" else ""
    return when {
        !task.done && date.isBefore(today) ->
            DueUi(prefix + "已逾期 · $text", spokenPrefix + "已逾期，截止 $text", DUE_OVERDUE)
        !task.done && date.isEqual(today) ->
            DueUi(prefix + "今天截止", spokenPrefix + "今天截止", DUE_TODAY)
        !task.done && thisWeek(date, today) ->
            DueUi(prefix + "本周 · $text", spokenPrefix + "本周截止 $text", DUE_WEEK)
        else -> DueUi(prefix + text, spokenPrefix + "截止 $text", DUE_NORMAL)
    }
}

private const val DUE_MISSING = 0
private const val DUE_NORMAL = 1
private const val DUE_WEEK = 2
private const val DUE_TODAY = 3
private const val DUE_OVERDUE = 4

/** Consecutive rows share one visual surface and light separators.
 * They remain separate keyed LazyColumn items, even with 80 saved tasks.
 */
@Composable
private fun ConnectedTaskShell(
    index: Int,
    total: Int,
    content: @Composable () -> Unit
) {
    val radius = 16.dp
    val corners = when {
        total <= 1 -> RoundedCornerShape(radius)
        index == 0 -> RoundedCornerShape(
            topStart = radius, topEnd = radius, bottomStart = 0.dp, bottomEnd = 0.dp)
        index == total - 1 -> RoundedCornerShape(
            topStart = 0.dp, topEnd = 0.dp, bottomStart = radius, bottomEnd = radius)
        else -> RoundedCornerShape(0.dp)
    }
    Column(
        modifier = Modifier.fillMaxWidth()
            .padding(top = if (index == 0) 8.dp else 0.dp)
            .clip(corners)
            .background(XiaoxuPalette.Surface)
            .border(0.5.dp, XiaoxuPalette.Line, corners)
    ) {
        if (index > 0) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 14.dp),
                thickness = 0.5.dp,
                color = XiaoxuPalette.Line
            )
        }
        content()
    }
}

/** Compact row based on the reference image; click still opens the original real-data sheet. */
@Composable
private fun TaskRow(
    task: WorkspaceTask,
    today: LocalDate,
    onOpen: () -> Unit,
    onToggleLocal: ((Boolean) -> Unit)? = null
) {
    val due = remember(task, today) { describeDue(task, today) }
    val dueTone = when (due.emphasis) {
        DUE_OVERDUE -> XiaoxuPalette.Danger
        DUE_TODAY -> XiaoxuPalette.Warning
        DUE_WEEK -> XiaoxuPalette.Brand
        else -> XiaoxuPalette.Ink2
    }
    val statusText = if (task.done) "个人已完成" else due.label
    val statusInk = if (task.done) XiaoxuPalette.Success else dueTone
    val statusBackground = when {
        task.done -> XiaoxuPalette.SuccessSoft
        due.emphasis == DUE_OVERDUE -> XiaoxuPalette.DangerSoft
        due.emphasis == DUE_TODAY -> XiaoxuPalette.WarningSoft
        due.emphasis == DUE_WEEK -> XiaoxuPalette.BrandSoft
        else -> XiaoxuPalette.SurfaceAlt
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 82.dp)
            .clickable(onClickLabel = "查看任务详情", onClick = onOpen)
            .semantics(mergeDescendants = true) {
                contentDescription = task.title + "；来源：" + task.origin +
                    "；" + (if (task.done) "个人标记已完成" else "待办") +
                    "；" + due.spoken + "；点击查看任务详情"
            }
            .padding(start = 12.dp, end = 9.dp, top = 11.dp, bottom = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 同样的勾选视觉：本机 Room 任务可直接更改个人完成状态；
        // 校序云端记录仅展示保存的状态，不提供会被误认成学校提交的写操作。
        if (task.storage == "local" && onToggleLocal != null) {
            Checkbox(
                checked = task.done,
                onCheckedChange = onToggleLocal,
                modifier = Modifier.size(48.dp).semantics {
                    contentDescription = if (task.done) "将本机任务“" + task.title + "”改为待完成"
                    else "将本机任务“" + task.title + "”标记为个人已完成"
                }
            )
        } else {
            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(
                    if (task.done) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = XiaoxuPalette.Ink3
                )
            }
        }
        Column(modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                task.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp, lineHeight = 20.sp),
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (task.done) TextDecoration.LineThrough else null,
                color = if (task.done) XiaoxuPalette.Ink2 else XiaoxuPalette.Ink
            )
            // 微信任务已在独立分组，列表行不重复显示来源小字。
            if (task.category != "wechat") {
                Text(
                    task.origin,
                    style = MaterialTheme.typography.labelSmall,
                    color = XiaoxuPalette.Ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Column(horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier.widthIn(max = 108.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(statusBackground)
                    .padding(horizontal = 7.dp, vertical = 5.dp)
            ) {
                Text(statusText, color = statusInk, fontSize = 11.sp,
                    fontWeight = FontWeight.Medium, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null,
                tint = XiaoxuPalette.Ink3, modifier = Modifier.size(18.dp))
        }
    }
}

/** 学习通本机页面快照：只有页面读到的标题、详情与采集时间，没有学校提交状态。 */
@Composable
private fun SnapshotRow(work: NativeSchoolRecord) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .height(IntrinsicSize.Min)
            .semantics(mergeDescendants = true) {
                contentDescription = work.title + "；学习通本机页面快照；采集时间 " +
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                        .format(Date(work.capturedAt))
            },
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .padding(vertical = 12.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = work.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (work.details.isNotBlank()) {
                Text(
                    text = work.details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TaskSourceBadge("本机页面快照")
                Text(
                    text = work.sourceHost,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                        .format(Date(work.capturedAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
