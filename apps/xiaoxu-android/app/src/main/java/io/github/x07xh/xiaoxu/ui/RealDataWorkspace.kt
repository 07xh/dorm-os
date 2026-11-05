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
