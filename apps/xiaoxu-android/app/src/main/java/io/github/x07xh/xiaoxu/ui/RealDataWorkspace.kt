package io.github.x07xh.xiaoxu.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
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
    val category: String = storage,
    val revision: Int = -1,
    val provider: String? = null,
    val links: List<String> = emptyList()
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
                "chaoxing" -> "学习通记录"
                "ncwu" -> "青果记录"
                "second_class" -> "第二课堂记录"
                else -> "校序云端任务"
            }
            add(WorkspaceTask("cloud:" + task.id, task.title, source,
                task.status == "done", parseWorkspaceDue(task.due),
                "云端记录，需核对来源", "cloud",
                if (task.provider == "wechat") "wechat" else "cloud",
                task.revision, task.provider, task.links))
        }
    }.sortedWith(compareBy<WorkspaceTask> { it.done }
        .thenBy { it.date ?: LocalDate.MAX }.thenBy { it.key })

/** Only self-entered dates are reliable as personal task deadlines.
 * CloudTask has no verified school deadline or work-ID provenance yet.
 */
internal fun countUpcomingLocalDeadlines(tasks: List<WorkspaceTask>, today: LocalDate): Int =
    tasks.count { task ->
        task.storage == "local" && !task.done &&
            task.date?.let { !it.isBefore(today) && !it.isAfter(today.plusDays(3)) } == true
    }

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
            "week" -> task.storage == "local" && !task.done && thisWeek(task.date, today)
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
        (!thisWeekOnly || (task.storage == "local" && !task.done &&
            thisWeek(task.date, today)))
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
    // Only personally entered, future dates can be counted as upcoming.
    // Unverified cloud dates and overdue dates must not inflate "近期截止".
    val attention = countUpcomingLocalDeadlines(all, today)
    val upcoming = all.filter { !it.done }.take(3)
    val cloudSnapshotRows = state.tasks.size
    // 正常时不把来源解释反复铺在首页；云端快照不新鲜时必须保留提醒。
    // One provenance hint in the task section; do not repeat the same technical
    // disclaimer under every task and statistic.
    val todoCaption: String? = when {
        cloudSnapshotRows == 0 -> null
        !state.tasksFresh -> "云端快照 · 日期仅供参考"
        else -> "云端日期仅供参考"
    }

    // Sheets look up the current records by stable keys, not saved copies of
    // possibly stale local/cloud data. Reuse Schedule/Message details directly.
    var openedLessonKey by rememberSaveable { mutableStateOf<String?>(null) }
    var openedTaskKey by rememberSaveable { mutableStateOf<String?>(null) }
    var openedNoticeKey by rememberSaveable { mutableStateOf<String?>(null) }
    val onLessonOpen: (VerifiedLesson) -> Unit = { openedLessonKey = homeLessonKey(it) }
    val onTaskOpen: (WorkspaceTask) -> Unit = { openedTaskKey = it.key }
    val onNoticeOpen: (String) -> Unit = { openedNoticeKey = it }

    val hero: @Composable () -> Unit = {
        HomeCourseList(
            lessons = sameWeekday,
            hasTimetable = localLessons.isNotEmpty(),
            weekdayLabel = weekdayLabel,
            openSchedule = openSchedule,
            openFileImport = openFileImport,
            onOpenLesson = onLessonOpen
        )
    }
    val tasks: @Composable () -> Unit = {
        HomeTodoBlock(upcoming, today, todoCaption, openTasks, onTaskOpen)
    }
    val metrics: @Composable () -> Unit = {
        HomeMetricsBlock(pending, attention, openTasks)
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
            onVerifyChaoxing = { openLocalSchool("chaoxing") },
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

/** 按原始顺序截取首页最多四条真实课程记录。 */
internal fun <T> homeCoursePreview(records: List<T>): List<T> = records.take(4)

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
    // 首页主标题由唯一顶栏显示；这里仅给日期与问候，不再堆第二个大标题。
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = today.monthValue.toString() + "月" + today.dayOfMonth + "日 · " + weekdayLabel,
            style = MaterialTheme.typography.titleSmall,
            color = XiaoxuPalette.Ink2,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = greeting,
            style = MaterialTheme.typography.labelMedium,
            color = XiaoxuPalette.Ink3
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

/**
 * 首屏只展示本人本机导入的同星期课块。周次未知时仅称“课程记录”，避免冒充今日实际开课。
 * 课程行复用原课程详情抽屉，不再保留旧单卡轮播。
 */
@Composable
private fun HomeCourseList(
    lessons: List<VerifiedLesson>,
    hasTimetable: Boolean,
    weekdayLabel: String,
    openSchedule: () -> Unit,
    openFileImport: () -> Unit,
    onOpenLesson: (VerifiedLesson) -> Unit
) {
    if (hasTimetable && lessons.isEmpty()) {
        // One compact, aligned row; verification caveat stays on timetable page.
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "暂无本机课程记录",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = XiaoxuPalette.Ink2
            )
            TextButton(onClick = openSchedule) { Text("课表") }
        }
    } else {
        HomeSection(
            title = "课程记录",
            actionLabel = if (hasTimetable) "查看课表" else null,
            onAction = if (hasTimetable) openSchedule else null
        ) {
            XiaoxuCard(contentPadding = 12.dp) {
                when {
                    lessons.isNotEmpty() -> {
                        homeCoursePreview(lessons).forEachIndexed { index, lesson ->
                        if (index > 0) HorizontalDivider(color = XiaoxuPalette.Line)
                        HomeCourseRecordRow(lesson, onOpen = { onOpenLesson(lesson) })
                    }
                    if (lessons.size > 4) {
                        TextButton(onClick = openSchedule) {
                            Text("查看其余 ${lessons.size - 4} 条课程记录")
                        }
                    }
                }
                    else -> {
                        Text("还没有导入课表", style = MaterialTheme.typography.bodyMedium,
                            color = XiaoxuPalette.Ink2)
                        TextButton(onClick = openFileImport) { Text("导入课表") }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeCourseRecordRow(lesson: VerifiedLesson, onOpen: () -> Unit) {
    val courseColor = xiaoxuCourseColor(lesson.id.ifBlank { lesson.title })
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = XiaoxuSpacing.listRowMin)
            .clickable(onClickLabel = "查看课程详情", onClick = onOpen)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.width(4.dp).height(44.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(courseColor.accent)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(lesson.title.ifBlank { "未命名课程" },
                style = MaterialTheme.typography.titleMedium,
                color = XiaoxuPalette.Ink, maxLines = 2,
                overflow = TextOverflow.Ellipsis)
            Text(
                "第${lesson.startSection}–${lesson.endSection}节 · " +
                    lesson.location.ifBlank { "教室未提供" } + " · " + formatWeeks(lesson.weeks),
                style = MaterialTheme.typography.labelMedium,
                color = XiaoxuPalette.Ink2,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null,
            tint = XiaoxuPalette.Ink3, modifier = Modifier.size(20.dp))
    }
}

/** 两张统计卡：数值与口径都来自已有记录，点击直达任务中心。 */
@Composable
private fun HomeMetricsBlock(
    pending: Int,
    attention: Int,
    openTasks: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCard(label = "待完成", value = pending.toString(),
            modifier = Modifier.weight(1f), onClick = openTasks)
        MetricCard(label = "近期截止", value = attention.toString(),
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
    caption: String?,
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
    HomeSection(title = "学习通 · 本机作业") {
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
    var updatingWechatKey by remember { mutableStateOf<String?>(null) }
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
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
        // Keep connected task rows as separate lazy items with zero gaps.
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text("任务中心",
                        style = MaterialTheme.typography.titleLarge,
                        color = XiaoxuPalette.Ink,
                        maxLines = 1,
                        modifier = Modifier.semantics { heading() })
                    Text(
                        today.format(DateTimeFormatter.ofPattern("M月d日 · EEEE",
                            java.util.Locale.CHINA)),
                        style = MaterialTheme.typography.labelSmall,
                        color = XiaoxuPalette.Ink2,
                        maxLines = 1
                    )
                }
                XiaoxuToolbarAction(
                    icon = Icons.Outlined.NotificationsNone,
                    label = "消息中心",
                    onClick = onMessages
                )
                XiaoxuToolbarAction(
                    icon = Icons.Outlined.Search,
                    label = "搜索任务",
                    onClick = { showSearch = !showSearch }
                )
                XiaoxuToolbarAction(
                    icon = Icons.Outlined.Refresh,
                    label = "刷新校序云端任务",
                    onClick = { taskScope.launch { client.refreshPrivate() } },
                    enabled = state.authenticated && !state.busy
                )
                XiaoxuToolbarAction(
                    icon = if (showManager && showLocal) Icons.Outlined.Close else Icons.Outlined.Add,
                    label = if (showManager && showLocal)
                        "收起新建本机任务" else "新增本机任务",
                    emphasized = true,
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
                )
            }
        }
        item {
            // One continuous status strip; values come from real saved tasks.
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(XiaoxuRadius.card))
                    .background(XiaoxuPalette.GlassTrack)
                    .border(XiaoxuSpacing.glassStroke, XiaoxuPalette.GlassBorder,
                        RoundedCornerShape(XiaoxuRadius.card))
                    .padding(5.dp)
                    .selectableGroup(),
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
                        joinedRows = true,
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
                        joinedRows = true,
                        onClick = { showWechatTasks = !showWechatTasks }
                    )
                }
                if (showWechatTasks) {
                    items(count = wechatRows.size, key = { i -> wechatRows[i].key }) { i ->
                        ConnectedTaskShell(index = i, total = wechatRows.size) {
                            val row = wechatRows[i]
                            TaskRow(
                                row, today,
                                onOpen = { openedTaskKey = row.key },
                                onToggleWechat = if (state.tasksFresh && row.revision >= 0) {
                                    { checked ->
                                        if (updatingWechatKey == null) {
                                            updatingWechatKey = row.key
                                            taskScope.launch {
                                                try {
                                                    client.setWechatTaskCompleted(
                                                        row.key.removePrefix("cloud:"),
                                                        row.revision, checked
                                                    )
                                                } finally {
                                                    updatingWechatKey = null
                                                }
                                            }
                                        }
                                    }
                                } else null,
                                actionEnabled = updatingWechatKey == null
                            )
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
                        joinedRows = true,
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
            onVerifyChaoxing = { openLocalSchool("chaoxing") },
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
    onManage: () -> Unit,
    onVerifyChaoxing: (() -> Unit)? = null
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
            if (tasksFresh) "云端任务记录；不代表学习通官方截止或提交状态"
            else "云端历史快照；日期与完成情况可能已变化"
        } else "本机记录 · 离线保存",
            style = MaterialTheme.typography.bodySmall, color = XiaoxuPalette.Ink2)
        if (selectedTask.category == "wechat") {
            WechatTaskLinkActions(selectedTask.links)
        }
        if (selectedTask.provider == "chaoxing" && onVerifyChaoxing != null) {
            OutlinedButton(
                onClick = onVerifyChaoxing,
                modifier = Modifier.heightIn(min = 48.dp).fillMaxWidth()
            ) { Text("打开学习通核对") }
            Text(
                "官方核验结果在任务中心的「学习通 · 官方作业核验」查看；" +
                    "本条云端记录尚无对应的官方作业 ID，不能自动标记为已核实。",
                style = MaterialTheme.typography.bodySmall,
                color = XiaoxuPalette.Ink2
            )
        }
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
            .clip(RoundedCornerShape(XiaoxuRadius.tile))
            .background(if (active) XiaoxuPalette.GlassIndicator else Color.Transparent)
            .selectable(selected = active, role = Role.Tab, onClick = onClick)
            .semantics { contentDescription = "$label，$count 条" }
            .heightIn(min = 56.dp)
            .padding(horizontal = 4.dp, vertical = 5.dp),
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
    showCount: Boolean = true,
    joinedRows: Boolean = false
) {
    val shape = if (joinedRows && expanded) RoundedCornerShape(
        topStart = XiaoxuRadius.card, topEnd = XiaoxuRadius.card,
        bottomStart = 0.dp, bottomEnd = 0.dp
    ) else RoundedCornerShape(XiaoxuRadius.card)
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(XiaoxuMotion.SelectionMs),
        label = "task-group-chevron"
    )
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(top = 8.dp)
            .clip(shape)
            .background(XiaoxuPalette.GlassCard)
            .border(XiaoxuSpacing.glassStroke, XiaoxuPalette.GlassBorder, shape)
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
            Icons.Outlined.ExpandMore,
            contentDescription = null,
            tint = XiaoxuPalette.Ink2,
            modifier = Modifier.size(20.dp).rotate(chevronRotation)
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
            DueUi("日期未记录", "云端任务没有已核实的截止日期", DUE_MISSING)
        } else {
            DueUi("截止未记录", "没有记录截止日期", DUE_MISSING)
        }
    }
    // CloudTask has no source work ID or verified official deadline. Show its
    // recorded date, but never color it as an officially overdue school work.
    val text = date.monthValue.toString().padStart(2, '0') + "-" +
        date.dayOfMonth.toString().padStart(2, '0')
    if (task.storage == "cloud") {
        return DueUi("记录 $text", "云端记录日期 $text，非学校核实的截止时间", DUE_NORMAL)
    }
    return when {
        !task.done && date.isBefore(today) ->
            DueUi("已逾期 · $text", "个人记录已逾期，截止 $text", DUE_OVERDUE)
        !task.done && date.isEqual(today) ->
            DueUi("今天截止", "个人记录今天截止", DUE_TODAY)
        !task.done && thisWeek(date, today) ->
            DueUi("本周 · $text", "个人记录本周截止 $text", DUE_WEEK)
        else -> DueUi(text, "个人记录日期 $text", DUE_NORMAL)
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
    val radius = XiaoxuRadius.card
    // LazyColumn items remain individually keyed; group header forms the shared top edge.
    val corners = when {
        index == total - 1 -> RoundedCornerShape(
            topStart = 0.dp, topEnd = 0.dp, bottomStart = radius, bottomEnd = radius)
        else -> RoundedCornerShape(0.dp)
    }
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(corners)
            .background(XiaoxuPalette.GlassCard)
            .border(XiaoxuSpacing.glassStroke, XiaoxuPalette.GlassBorder, corners)
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
    onToggleLocal: ((Boolean) -> Unit)? = null,
    onToggleWechat: ((Boolean) -> Unit)? = null,
    actionEnabled: Boolean = true
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
            .heightIn(min = XiaoxuSpacing.listRowMin)
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
        } else if (task.category == "wechat" && onToggleWechat != null) {
            Checkbox(
                checked = task.done,
                enabled = actionEnabled,
                onCheckedChange = onToggleWechat,
                modifier = Modifier.size(48.dp).semantics {
                    contentDescription = if (task.done)
                        "将微信任务“" + task.title + "”改为待完成"
                    else "将微信任务“" + task.title + "”标记为个人已完成"
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
        // 任务标题占满内容列；状态放第二行，不再挤在右侧 108dp 窄框中截断。
        Column(modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                task.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (task.done) TextDecoration.LineThrough else null,
                color = if (task.done) XiaoxuPalette.Ink2 else XiaoxuPalette.Ink
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier.widthIn(max = 162.dp)
                        .clip(RoundedCornerShape(XiaoxuRadius.tile))
                        .background(statusBackground)
                        .padding(horizontal = 7.dp, vertical = 4.dp)
                ) {
                    Text(statusText, color = statusInk,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2)
                }
                // 微信任务已在独立分组，列表行不重复显示来源。
                if (task.category != "wechat") {
                    Text(
                        task.origin,
                        style = MaterialTheme.typography.labelSmall,
                        color = XiaoxuPalette.Ink2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null,
            tint = XiaoxuPalette.Ink3, modifier = Modifier.size(18.dp))
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
