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
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
private val WorkspaceBlue = Color(0xFF2563EB)

/** In-memory combination for DISPLAY ONLY. Room, D1 and school data remain separate. */
internal data class WorkspaceTask(
    val key: String,
    val title: String,
    val origin: String,
    val done: Boolean,
    val date: LocalDate?,
    val dateSource: String,
    val storage: String
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
                "chaoxing" -> "学习通相关云端记录"
                "ncwu" -> "青果相关云端记录"
                "second_class" -> "第二课堂相关云端记录"
                else -> "校序云端任务"
            }
            add(WorkspaceTask("cloud:" + task.id, task.title, source,
                task.status == "done", parseWorkspaceDue(task.due),
                "云端记录，需核对来源", "cloud"))
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

@Composable
private fun WorkspaceCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) { content() }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                color = WorkspaceBlue)
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

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
    var openedTaskKey by rememberSaveable { mutableStateOf<String?>(null) }
    var openedNoticeKey by rememberSaveable { mutableStateOf<String?>(null) }
    val onLessonOpen: (VerifiedLesson) -> Unit = { openedLessonKey = homeLessonKey(it) }
    val onTaskOpen: (WorkspaceTask) -> Unit = { openedTaskKey = it.key }
    val onNoticeOpen: (String) -> Unit = { openedNoticeKey = it }

    val hero: @Composable () -> Unit = {
        HomeHero(sameWeekday.firstOrNull(), localLessons.isNotEmpty(), weekdayLabel,
            openSchedule, openFileImport, onLessonOpen)
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
    "${lesson.id}:${lesson.weekday}:${lesson.startSection}:${lesson.endSection}:${lesson.title}"

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
    lesson: VerifiedLesson?,
    hasTimetable: Boolean,
    weekdayLabel: String,
    openSchedule: () -> Unit,
    openFileImport: () -> Unit,
    onOpenLesson: (VerifiedLesson) -> Unit
) {
    when {
        lesson != null -> XiaoxuHero(
            kicker = "${weekdayLabel}课程记录 · 本机课表",
            title = lesson.title,
            supporting = "第${lesson.startSection}–${lesson.endSection}节 · " +
                lesson.location.ifBlank { "教室未提供" } + " · " +
                lesson.teacher.ifBlank { "教师未提供" },
            badge = "待核对",
            meta = "适用${formatWeeks(lesson.weeks)} · 未核定当前教学周",
            modifier = Modifier.clickable(onClickLabel = "查看课程详情") {
                onOpenLesson(lesson)
            },
            actionLabel = "全部课表",
            onAction = openSchedule
        )
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
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickEntry("成绩", Icons.Outlined.School, openGrades, Modifier.weight(1f))
            QuickEntry("考试", Icons.Outlined.DateRange, openExams, Modifier.weight(1f))
            QuickEntry("第二课堂", Icons.Outlined.CheckCircle, openSecondClass,
                Modifier.weight(1f), XiaoxuTone.Success)
            QuickEntry("学习通", Icons.Outlined.MailOutline, openChaoxing,
                Modifier.weight(1f), XiaoxuTone.Warning)
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
    openLocalSchool: (String) -> Unit = {}
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
    val filters = listOf("pending" to "待完成", "all" to "全部", "week" to "本周截止",
        "done" to "已完成", "local" to "本机", "cloud" to "云端")
    var filter by rememberSaveable { mutableStateOf("pending") }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var showManager by rememberSaveable { mutableStateOf(false) }
    var showAdvancedFilters by rememberSaveable { mutableStateOf(false) }
    var showCourseTasks by rememberSaveable { mutableStateOf(true) }
    var showPersonalTasks by rememberSaveable { mutableStateOf(true) }
    var showCandidates by rememberSaveable { mutableStateOf(true) }
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
    // Source and filters share the same LazyColumn, including all management forms.
    val taskListState = rememberLazyListState()
    var jumpTo by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(jumpTo, showLocal, showCloud, showManager, showSearch, showAdvancedFilters) {
        val managerStart = 4 + (if (showSearch) 1 else 0) +
            (if (showAdvancedFilters) 1 else 0) + (if (showManager) 1 else 0)
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
    val visible = searchWorkspaceTasks(all, filter, searchQuery, today)
    // 保持原有 80 条展示上限；超过上限时必须明确提示还有未显示的任务。
    val shown = visible.take(80)
    val localRows = shown.filter { it.storage == "local" }
    val cloudRows = shown.filter { it.storage == "cloud" }
    LazyColumn(
        state = taskListState,
        modifier = Modifier.fillMaxSize().widthIn(max = 860.dp),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        today.format(DateTimeFormatter.ofPattern("yyyy年M月d日 · EEEE",
                            java.util.Locale.CHINA)),
                        style = MaterialTheme.typography.labelSmall,
                        color = XiaoxuPalette.Ink2
                    )
                    Text("任务中心", style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold, color = XiaoxuPalette.Ink,
                        modifier = Modifier.semantics { heading() })
                    Text("课程任务 · 群消息审核 · 个人待办",
                        style = MaterialTheme.typography.bodySmall, color = XiaoxuPalette.Ink2)
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
                    onClick = { showManager = true; showLocal = true; jumpTo = "local" }
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "新增本机任务",
                        tint = Color.White)
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                TaskStatusTab(
                    label = "待完成", count = all.count { !it.done },
                    active = filter == "pending", modifier = Modifier.weight(1f),
                    onClick = { filter = "pending"; jumpTo = "filters" }
                )
                TaskStatusTab(
                    label = "本周截止",
                    count = all.count { !it.done && thisWeek(it.date, today) },
                    active = filter == "week", modifier = Modifier.weight(1f),
                    onClick = { filter = "week"; jumpTo = "filters" }
                )
                TaskStatusTab(
                    label = "已完成", count = all.count { it.done },
                    active = filter == "done", modifier = Modifier.weight(1f),
                    onClick = { filter = "done"; jumpTo = "filters" }
                )
            }
        }
        item {
            Text(
                if (!state.tasksFresh && state.tasks.isNotEmpty())
                    "含上次云端快照 " + state.tasks.size + " 条"
                else "仅统计已保存的任务",
                style = MaterialTheme.typography.labelSmall,
                color = XiaoxuPalette.Ink2
            )
        }
        if (showSearch) item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it.take(100) },
                modifier = Modifier.fillMaxWidth(),
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("任务来源分开保存，学校提交状态需另行核验",
                    style = MaterialTheme.typography.labelSmall,
                    color = XiaoxuPalette.Ink2, modifier = Modifier.weight(1f))
                TextButton(onClick = { showAdvancedFilters = !showAdvancedFilters }) {
                    Icon(Icons.Outlined.Tune, contentDescription = null,
                        modifier = Modifier.size(16.dp))
                    Text("筛选")
                }
                TextButton(onClick = { showManager = !showManager }) {
                    Text(if (showManager) "收起管理" else "任务管理")
                }
            }
        }
        if (showAdvancedFilters) item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                items(count = filters.size) { i ->
                    val (key, label) = filters[i]
                    FilterChip(selected = filter == key, onClick = { filter = key },
                        label = { Text(label) })
                }
            }
        }
        if (showManager) item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { showLocal = !showLocal; if (showLocal) jumpTo = "local" },
                    modifier = Modifier.weight(1f)
                ) { Text(if (showLocal) "收起本机管理" else "新建 / 管理本机") }
                OutlinedButton(
                    onClick = { showCloud = !showCloud; if (showCloud) jumpTo = "cloud" },
                    modifier = Modifier.weight(1f)
                ) { Text(if (showCloud) "收起云端管理" else "云端任务管理") }
            }
        }
        if (showLocal) item { LocalTaskBoard(localTasks, dao) }
        if (showCloud) item { CloudTasksPanel(client) }
        item {
            Text("当前显示 " + shown.size + " / " + visible.size + " 条（最多80条） · 同屏查看不等于合并数据库",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (visible.isEmpty()) {
            item {
                XiaoxuEmpty(
                    title = "没有匹配的已保存任务",
                    message = if (!state.authenticated)
                        "校序云端尚未连接，可先新增本机待办。"
                    else "当前筛选没有任务，不能据此推断学校没有作业。",
                    actionLabel = "新增 / 管理本机任务",
                    onAction = {
                        showLocal = true
                        jumpTo = "local"
                    }
                )
            }
        } else {
            if (cloudRows.isNotEmpty()) {
                item {
                    TaskGroupHeader(
                        title = "课程相关与云端任务",
                        count = cloudRows.size,
                        badge = "校序云端",
                        note = "来自已保存云端任务 · 官方提交状态待核验",
                        icon = Icons.Outlined.MenuBook,
                        expanded = showCourseTasks,
                        onClick = { showCourseTasks = !showCourseTasks }
                    )
                }
                if (showCourseTasks) {
                    items(count = cloudRows.size, key = { i -> cloudRows[i].key }) { i ->
                        XiaoxuCard(contentPadding = 0.dp, shape = RoundedCornerShape(14.dp)) {
                            TaskRow(cloudRows[i], today, onOpen = { openedTaskKey = cloudRows[i].key })
                        }
                    }
                }
            }
            if (localRows.isNotEmpty()) {
                item {
                    TaskGroupHeader(
                        title = "我的本机待办",
                        count = localRows.size,
                        badge = "本机 Room",
                        note = "离线可用 · 来源与截止时间由本人填写",
                        icon = Icons.Outlined.CheckBox,
                        expanded = showPersonalTasks,
                        onClick = { showPersonalTasks = !showPersonalTasks }
                    )
                }
                if (showPersonalTasks) {
                    items(count = localRows.size, key = { i -> localRows[i].key }) { i ->
                        XiaoxuCard(contentPadding = 0.dp, shape = RoundedCornerShape(14.dp)) {
                            TaskRow(localRows[i], today, onOpen = { openedTaskKey = localRows[i].key })
                        }
                    }
                }
            }
        }
        if (state.privateError != null) {
            item {
                Text("云端部分数据刷新失败，已有记录可能过期",
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
                showCount = state.chaoxingBatchCheckedAt != null || state.chaoxingBatchBusy
            )
        }
        if (showOfficialAudit) {
            item {
                XiaoxuCard {
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
                    XiaoxuCard(contentPadding = 10.dp) {
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
                    XiaoxuCard(contentPadding = 12.dp) {
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
                        TextButton(onClick = {
                            showAllOfficialHomework = !showAllOfficialHomework
                        }) {
                            Text(if (showAllOfficialHomework) "收起至前 30 条"
                                else "展开其余 ${officialRows.size - 30} 条官方作业")
                        }
                    }
                }
            }
        }
        // 同一 WechatReviewPanel 同时可从任务页和消息页进入，不复制审核状态机。
        // 候选在本人确认前不是课程作业，也不进入三项统计。
        if (state.authenticated || state.pendingWechatCount != null) {
            item {
                TaskGroupHeader(
                    title = "群消息任务候选",
                    count = state.pendingWechatCount ?: 0,
                    badge = if (state.wechatError != null)
                        "候选快照 · 待刷新" else "本人云端 · 待审核",
                    note = "需要人工确认，加入后才进入个人任务列表",
                    icon = Icons.Outlined.ChatBubbleOutline,
                    expanded = showCandidates,
                    onClick = { showCandidates = !showCandidates },
                    showCount = state.pendingWechatCount != null
                )
            }
            if (showCandidates) item {
                XiaoxuCard {
                    WechatReviewPanel(
                        client = client,
                        pendingCount = state.pendingWechatCount,
                        openWebsite = openWebsite
                    )
                }
            }
        }
        item {
            XiaoxuCard {
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            // Bounded Room pagination allows 500 stored records; LazyColumn
            // must not silently hide pages after the first 120.
            items(count = minOf(capturedHomework.size, 500),
                key = { i -> "school:" + capturedHomework[i].fingerprint }) { index ->
                val work = capturedHomework[index]
                XiaoxuCard(contentPadding = 8.dp) { SnapshotRow(work) }
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
        item { LocalSchoolRecordSection(setOf("course")) }
        item { ChaoxingHomeworkPanel(client, openSchoolReader = { openLocalSchool("chaoxing") }) }
         item {
            Text("学习通官方状态只在已核验读取后显示，个人已完成不代表学校已提交。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        }
    }

    if (selectedTask != null) {
        WorkspaceTaskDetailSheet(selectedTask, today, state.tasksFresh,
            onDismiss = { openedTaskKey = null },
            onManage = {
                if (selectedTask.storage == "local") {
                    showLocal = true
                    jumpTo = "local"
                } else {
                    showCloud = true
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
            SourceBadge(if (selectedTask.storage == "local") "本机本人待办" else "校序云端任务",
                if (selectedTask.storage == "local") XiaoxuTone.Brand else XiaoxuTone.Warning)
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
                "打开本机任务管理" else "打开云端任务管理")
        }
    }
}




/** Main three blue-and-white pills, populated only from saved tasks. */
@Composable
private fun TaskStatusTab(
    label: String, count: Int, active: Boolean, modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(if (active) XiaoxuPalette.Brand else XiaoxuPalette.Surface)
            .border(1.dp, if (active) XiaoxuPalette.Brand else XiaoxuPalette.Line, shape)
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 7.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 12.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            color = if (active) Color.White else XiaoxuPalette.Ink2, maxLines = 1)
        Spacer(Modifier.width(5.dp))
        Text(count.toString(), fontSize = 14.sp, fontWeight = FontWeight.Bold,
            color = if (active) Color.White else XiaoxuPalette.Ink)
    }
}

/** Shared white group header, with a blue icon tile and reliable source caption. */
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
    XiaoxuCard(contentPadding = 14.dp, onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Box(
                modifier = Modifier.size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(XiaoxuPalette.BrandSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = XiaoxuPalette.Brand,
                    modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    title + if (showCount) " ($count)" else "",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = XiaoxuPalette.Ink,
                    modifier = Modifier.semantics { heading() }
                )
                Text(note, style = MaterialTheme.typography.labelSmall,
                    color = XiaoxuPalette.Ink2, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
            }
            Icon(
                if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = if (expanded) "折叠分区" else "展开分区",
                tint = XiaoxuPalette.Ink2
            )
        }
        TaskSourceBadge(badge, emphasized = true)
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
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
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

/** Compact row based on the reference image; click still opens the original real-data sheet. */
@Composable
private fun TaskRow(task: WorkspaceTask, today: LocalDate, onOpen: () -> Unit) {
    val due = remember(task, today) { describeDue(task, today) }
    val dueTone = when (due.emphasis) {
        DUE_OVERDUE -> XiaoxuPalette.Danger
        DUE_TODAY -> XiaoxuPalette.Warning
        DUE_WEEK -> XiaoxuPalette.Brand
        else -> XiaoxuPalette.Ink2
    }
    val cloud = task.storage == "cloud"
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
            .heightIn(min = 76.dp)
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
        Box(
            modifier = Modifier.size(42.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(if (cloud) XiaoxuPalette.BrandSoft else XiaoxuPalette.SuccessSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (cloud) Icons.Outlined.MenuBook else Icons.Outlined.CheckBox,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = if (cloud) XiaoxuPalette.Brand else XiaoxuPalette.Success
            )
        }
        Column(modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                task.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (task.done) TextDecoration.LineThrough else null,
                color = if (task.done) XiaoxuPalette.Ink2 else XiaoxuPalette.Ink
            )
            Text(
                task.origin,
                style = MaterialTheme.typography.labelSmall,
                color = XiaoxuPalette.Ink2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
