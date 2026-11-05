package io.github.x07xh.xiaoxu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
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
 * 首页（Issue #268 第一阶段样板）：结构对齐宣传海报，数据全部来自已连接的真实来源。
 *
 * 三条硬约束：
 * 1. 没有可信课表时不画任何课程名——Hero 转为深灰蓝"未核验"卡；
 * 2. 未接入开学日期与节次作息，因此不推断当前教学周、不做上课倒计时；
 * 3. 统计只数已有记录，卡面写清是"本机 + 云端"还是"仅本机"。
 *
 * 手机单列滚动；≥700dp 平板改为左（今天/课表/统计/入口）右（待办/通知/本机作业）双列。
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
    openLocalSchool: (String) -> Unit = {}
) {
    val state = client.state
    val today = LocalDate.now(SchoolZone)
    val all = remember(localTasks, state.tasks) { combineWorkspaceTasks(localTasks, state.tasks) }
    val context = LocalContext.current
    val nativeDao = remember(context) { LocalTaskDatabase.open(context).nativeSchoolRecords() }
    val nativeRows by nativeDao.observe().collectAsState(initial = emptyList())
    val localHomework = nativeRows.filter { it.provider == "chaoxing" && it.kind == "homework" }
    val localSecondClass = nativeRows.filter {
        it.provider == "second_class" && it.kind in setOf("activity", "credits")
    }
    val timetable = if (localLessons.isNotEmpty()) localLessons else state.lessons
    val isLocalTimetable = localLessons.isNotEmpty()
    // 四态判定复用课表页的实现，首页不另写一套来源逻辑。
    val cloudScheduleState = remember(state) { deriveScheduleUiState(state) }
    val sameWeekday = timetable.filter { it.weekday == today.dayOfWeek.value }
        .sortedBy { it.startSection }
    val weekdayLabel = listOf("周一","周二","周三","周四","周五","周六","周日")[today.dayOfWeek.value - 1]
    val pending = all.count { !it.done }
    val doneCount = all.count { it.done }
    val upcoming = all.filter { !it.done }.take(3)
    // 云端任务在刷新失败时会保留上次快照（CloudClient 不清空 state.tasks），
    // 因此口径必须区分"本次读取"与"上次快照"，不能一律写成"仅本机"。
    val cloudSnapshotRows = state.tasks.size
    val metricCaption = when {
        state.tasksFresh -> "本机 + 云端本次读取"
        cloudSnapshotRows > 0 -> "含上次云端快照 " + cloudSnapshotRows + " 条，未重新核验"
        else -> "仅本机记录"
    }
    val doneCaption = if (!state.tasksFresh && cloudSnapshotRows > 0) "本人标记完成 · 含云端快照"
    else "本人标记完成"
    val todoCaption = if (!state.tasksFresh && cloudSnapshotRows > 0) "含上次云端快照，未重新核验"
    else "缺截止日期会单独标注，不推测"

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wideLayout = maxWidth >= 700.dp
        if (wideLayout) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                LazyColumn(
                    modifier = Modifier.weight(1.15f).fillMaxHeight(),
                    contentPadding = PaddingValues(start = 20.dp, top = 14.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item { HomeGreeting(today, weekdayLabel) }
                    item {
                        HomeHero(
                            lesson = sameWeekday.firstOrNull(),
                            isLocalTimetable = isLocalTimetable,
                            hasTimetable = timetable.isNotEmpty(),
                            weekdayLabel = weekdayLabel,
                            state = cloudScheduleState,
                            openSchedule = openSchedule,
                            openProfile = openProfile
                        )
                    }
                    if (timetable.isNotEmpty()) {
                        item {
                            TodayScheduleBlock(
                                sameWeekday = sameWeekday,
                                isLocalTimetable = isLocalTimetable,
                                openSchedule = openSchedule, openProfile = openProfile
                            )
                        }
                    }
                    item { HomeMetricsBlock(pending, doneCount, metricCaption, doneCaption, openTasks) }
                    item { HomeQuickBlock(openSchedule, openTasks, openMessages, openProfile) }
                }
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentPadding = PaddingValues(end = 20.dp, top = 14.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item { HomeTodoBlock(upcoming, today, todoCaption, openTasks) }
                    item { HomeNoticesBlock(state, openMessages) }
                    item {
                        HomeHomeworkBlock(
                            localHomework, localSecondClass, openProfile, openLocalSchool
                        )
                    }
                    item { HomeFooterNote() }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().widthIn(max = 860.dp),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { HomeGreeting(today, weekdayLabel) }
                item {
                    HomeHero(
                        lesson = sameWeekday.firstOrNull(),
                        isLocalTimetable = isLocalTimetable,
                        hasTimetable = timetable.isNotEmpty(),
                        weekdayLabel = weekdayLabel,
                        state = cloudScheduleState,
                        openSchedule = openSchedule,
                        openProfile = openProfile
                    )
                }
                // 一张课表都没有时，Hero 已经说明原因，不再重复一张空卡片。
                if (timetable.isNotEmpty()) {
                    item {
                        TodayScheduleBlock(
                            sameWeekday = sameWeekday,
                            isLocalTimetable = isLocalTimetable,
                            openSchedule = openSchedule, openProfile = openProfile
                        )
                    }
                }
                item { HomeMetricsBlock(pending, doneCount, metricCaption, doneCaption, openTasks) }
                item { HomeQuickBlock(openSchedule, openTasks, openMessages, openProfile) }
                item { HomeTodoBlock(upcoming, today, todoCaption, openTasks) }
                item { HomeNoticesBlock(state, openMessages) }
                item {
                    HomeHomeworkBlock(
                        localHomework, localSecondClass, openProfile, openLocalSchool
                    )
                }
                item { HomeFooterNote() }
            }
        }
    }
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

/**
 * Hero：有真实课表记录才用蓝色渐变；没有可信课表就转深灰蓝并说明原因。
 *
 * 两条真值约束（PR #269 review）：
 * 1. stale 的官方课表只能说"上次读取 · 待刷新"，不得写成"已核验"；
 * 2. 只有星期与节次、没有当前教学周，所以这里描述的是"该星期的课表记录"，
 *    不是"今天确实在上课"。
 */
@Composable
private fun HomeHero(
    lesson: VerifiedLesson?,
    isLocalTimetable: Boolean,
    hasTimetable: Boolean,
    weekdayLabel: String,
    state: ScheduleUiState,
    openSchedule: () -> Unit,
    openProfile: () -> Unit
) {
    val verified = state as? ScheduleUiState.Verified
    val stale = verified?.stale == true
    val refreshing = verified?.refreshing == true
    val fetched = verified?.fetchedAt?.takeIf { it.isNotBlank() }
    val refreshNote = verified?.refreshWarning?.takeIf { it.isNotBlank() }
    // 徽标只描述这份课的来源与新鲜度，不代替核验结论。
    // 顺序：本机导入 > 刷新中 > 待刷新 > 本次读取。
    val badge = when {
        isLocalTimetable -> "待核对"
        refreshing -> "刷新中"
        stale -> "待刷新"
        else -> "已核验"
    }
    val weekNote = lesson?.let { "适用" + formatWeeks(it.weeks) }
    val sourceNote = when {
        isLocalTimetable -> "本机文件导入，不等于学校实时同步。"
        refreshing -> "正在刷新课表，当前显示上次成功读取的结果，刷新完成前不代表最新。"
        stale -> "上次成功读取" + (if (fetched != null) "于 $fetched " else "") +
            "，本次课表刷新未成功，待刷新核对。"
        else -> "来源与核验状态以课表页为准。"
    }
    when {
        lesson != null -> XiaoxuHero(
            kicker = when {
                isLocalTimetable -> "本日课表 · 本机导入 · 待核对"
                refreshing -> "本日课表 · 正在刷新 · 暂显示上次读取"
                stale -> "本日课表 · 上次读取 · 待刷新核对"
                else -> "本日课表 · 官方本次读取"
            },
            title = lesson.title,
            supporting = "第" + lesson.startSection + "–" + lesson.endSection + "节 · " +
                lesson.location.ifBlank { "教室未提供" } + " · " +
                lesson.teacher.ifBlank { "教师未提供" },
            badge = badge,
            meta = (weekNote?.plus("；") ?: "") +
                "按星期匹配，当前教学周未核实，不能确认今天实际开课。" + sourceNote +
                (refreshNote?.let { " 刷新提示：" + it } ?: ""),
            actionLabel = "全部课表",
            onAction = openSchedule
        )
        hasTimetable -> XiaoxuHero(
            kicker = "本日课表",
            title = "课表里" + weekdayLabel + "没有课程记录",
            supporting = "不代表今天停课；适用周次与当前教学周都未核实，请以学校官网为准。",
            badge = badge,
            meta = sourceNote + (refreshNote?.let { " 刷新提示：" + it } ?: ""),
            actionLabel = "打开课表",
            onAction = openSchedule
        )
        state is ScheduleUiState.Failed -> XiaoxuHero(
            kicker = "课程数据",
            title = "课表读取失败",
            supporting = state.message,
            tone = XiaoxuTone.Neutral,
            badge = "未核验",
            actionLabel = "查看连接",
            onAction = openProfile
        )
        state is ScheduleUiState.VerifiedEmpty -> XiaoxuHero(
            kicker = "课程数据",
            title = "官方课表为空",
            supporting = "已核验的课表里没有课程记录；需要本机课表可到「我的」导入。",
            tone = XiaoxuTone.Neutral,
            badge = "无课程",
            actionLabel = "打开课表",
            onAction = openSchedule
        )
        state is ScheduleUiState.UnverifiedSource -> XiaoxuHero(
            kicker = "课程数据",
            title = "课表来源未核验",
            supporting = "当前来源无法证明是最新官方课表，请到「我的」核对连接状态。",
            tone = XiaoxuTone.Neutral,
            badge = "未核验",
            actionLabel = "查看连接",
            onAction = openProfile
        )
        state is ScheduleUiState.Loading -> XiaoxuHero(
            kicker = "课程数据",
            title = "正在读取课表",
            supporting = "读取完成后这里显示当天的课表记录。",
            tone = XiaoxuTone.Neutral,
            badge = "读取中"
        )
        else -> XiaoxuHero(
            kicker = "课程数据",
            title = "还没有可信课表",
            supporting = "连接学校，或在「我的」导入课表文件后，这里显示当天的课表记录。",
            tone = XiaoxuTone.Neutral,
            badge = "未连接",
            actionLabel = "去连接",
            onAction = openProfile
        )
    }
}

/** 本日课表：按节次排序的课程色块；该星期无记录时说明来源边界，不推断停课。 */
@Composable
private fun TodayScheduleBlock(
    sameWeekday: List<VerifiedLesson>,
    isLocalTimetable: Boolean,
    openSchedule: () -> Unit,
    openProfile: () -> Unit
) {
    HomeSection(
        title = "本日课表",
        caption = when {
            sameWeekday.isEmpty() -> "本星期没有对应课程记录"
            isLocalTimetable -> "本机读取 · 待与学校官网核对 · 仅按星期匹配"
            else -> "仅按星期匹配 · 适用周次与教学周待核实"
        },
        actionLabel = "课表",
        onAction = openSchedule
    ) {
        if (sameWeekday.isEmpty()) {
            XiaoxuEmpty(
                title = "课表里这一星期没有课程记录",
                message = "不代表今天停课：适用周次与当前教学周都未核实，请以学校官网为准。",
                actionLabel = "打开课表",
                onAction = openSchedule
            )
        } else {
            XiaoxuCard {
                sameWeekday.take(4).forEach { lesson ->
                    CourseTile(
                        courseKey = lesson.id.ifBlank { lesson.title },
                        title = lesson.title,
                        meta = "第" + lesson.startSection + "–" + lesson.endSection +
                            "节 · " + lesson.location.ifBlank { "教室未提供" },
                        emphasise = true
                    )
                }
                if (sameWeekday.size > 4) {
                    Text(
                        text = "还有 ${sameWeekday.size - 4} 门课，见课表页",
                        style = MaterialTheme.typography.labelSmall,
                        color = XiaoxuPalette.Ink2
                    )
                }
            }
        }
    }
}

/** 两张统计卡：数值与口径都来自已有记录，点击直达任务中心。 */
@Composable
private fun HomeMetricsBlock(
    pending: Int,
    doneCount: Int,
    caption: String,
    doneCaption: String,
    openTasks: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCard(
            label = "待办", value = pending.toString(), caption = caption,
            modifier = Modifier.weight(1f), onClick = openTasks
        )
        MetricCard(
            label = "已完成", value = doneCount.toString(), caption = doneCaption,
            tone = XiaoxuTone.Success, modifier = Modifier.weight(1f), onClick = openTasks
        )
    }
}

/** 校园快捷入口：只列已接入的四页，不做灰色"未接入"按钮。 */
@Composable
private fun HomeQuickBlock(
    openSchedule: () -> Unit,
    openTasks: () -> Unit,
    openMessages: () -> Unit,
    openProfile: () -> Unit
) {
    HomeSection(title = "快捷入口", caption = "只列已接入的页面") {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickEntry("课表", Icons.Outlined.DateRange, openSchedule, Modifier.weight(1f))
            QuickEntry("任务", Icons.Outlined.CheckCircle, openTasks, Modifier.weight(1f),
                XiaoxuTone.Success)
            QuickEntry("消息", Icons.Outlined.MailOutline, openMessages, Modifier.weight(1f),
                XiaoxuTone.Warning)
            QuickEntry("我的", Icons.Outlined.PersonOutline, openProfile, Modifier.weight(1f),
                XiaoxuTone.Neutral)
        }
    }
}

/** 待办与提醒：截止状态复用任务中心判定，缺日期不猜。 */
@Composable
private fun HomeTodoBlock(
    upcoming: List<WorkspaceTask>,
    today: LocalDate,
    caption: String,
    openTasks: () -> Unit
) {
    HomeSection(
        title = "待办与提醒",
        caption = caption,
        actionLabel = "全部任务",
        onAction = openTasks
    ) {
        XiaoxuCard {
            if (upcoming.isEmpty()) {
                Text(
                    text = "当前没有已保存的待办任务",
                    style = MaterialTheme.typography.bodyMedium,
                    color = XiaoxuPalette.Ink2
                )
            } else {
                upcoming.forEachIndexed { index, task ->
                    if (index > 0) HorizontalDivider(color = XiaoxuPalette.Line)
                    HomeTaskRow(task, today)
                }
            }
            Text(
                text = "来自本机与云端已有记录；不代表学校上游刚完成同步。",
                style = MaterialTheme.typography.labelSmall,
                color = XiaoxuPalette.Ink3
            )
        }
    }
}

/** 官方通知：只放已读取的公开公告，标题与日期分行，不编造正文。 */
@Composable
private fun HomeNoticesBlock(state: CloudUiState, openMessages: () -> Unit) {
    HomeSection(
        title = "官方通知",
        caption = "只显示已读取的公开公告",
        actionLabel = "消息中心",
        onAction = openMessages
    ) {
        XiaoxuCard {
            if (state.notices.isEmpty()) {
                Text(
                    text = "暂无已读取的官方通知",
                    style = MaterialTheme.typography.bodyMedium,
                    color = XiaoxuPalette.Ink2
                )
            } else {
                state.notices.take(2).forEachIndexed { index, notice ->
                    if (index > 0) HorizontalDivider(color = XiaoxuPalette.Line)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = notice.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = XiaoxuPalette.Ink,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = notice.dateLabel + " · " +
                                noticeCategoryLabel(notice.category),
                            style = MaterialTheme.typography.labelSmall,
                            color = XiaoxuPalette.Ink3
                        )
                    }
                }
            }
            state.publicError?.let {
                Text(
                    text = "公告刷新失败，旧记录可能过期",
                    style = MaterialTheme.typography.labelSmall,
                    color = XiaoxuPalette.Danger
                )
            }
        }
    }
}

/** 本机学习通作业与第二课堂：仍是本机页面快照，措辞与任务中心一致。 */
@Composable
private fun HomeHomeworkBlock(
    localHomework: List<NativeSchoolRecord>,
    localSecondClass: List<NativeSchoolRecord>,
    openProfile: () -> Unit,
    openLocalSchool: (String) -> Unit
) {
    HomeSection(
        title = "学习通 · 本机作业",
        caption = "只显示本机读取到的标题，不代表提交状态"
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
                    text = "已保存 ${localHomework.size} 条当前可见页面记录；提交状态及截止时间尚未核验。",
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
            if (localSecondClass.isNotEmpty()) {
                HorizontalDivider(color = XiaoxuPalette.Line)
                Text(
                    text = "第二课堂 · 本机保存 ${localSecondClass.size} 条活动／积分页面记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = XiaoxuPalette.Ink
                )
                Text(
                    text = "不是官方累计积分，查看和核对请到「我的」。",
                    style = MaterialTheme.typography.labelSmall,
                    color = XiaoxuPalette.Ink3
                )
                TextButton(onClick = openProfile) { Text("查看第二课堂") }
            }
        }
    }
}

/** 页脚声明：这一页不产生任何新数据。 */
@Composable
private fun HomeFooterNote() {
    Text(
        text = "首页只汇总已保存的记录；未接入的来源按空态显示，不做演示数据。",
        style = MaterialTheme.typography.labelSmall,
        color = XiaoxuPalette.Ink3
    )
}

/** 首页待办行：状态标签沿用任务中心同一套判定（describeDue），不在这里另算。 */
@Composable
private fun HomeTaskRow(task: WorkspaceTask, today: LocalDate) {
    val due = remember(task, today) { describeDue(task, today) }
    val tone = when (due.emphasis) {
        DUE_OVERDUE -> XiaoxuTone.Danger
        DUE_TODAY -> XiaoxuTone.Warning
        DUE_WEEK -> XiaoxuTone.Brand
        else -> XiaoxuTone.Neutral
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
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
    var showLocal by rememberSaveable { mutableStateOf(false) }
    var showCloud by rememberSaveable { mutableStateOf(false) }
    val visible = selectTasks(all, filter, today)
    // 保持原有 80 条展示上限；超过上限时必须明确提示还有未显示的任务。
    val shown = visible.take(80)
    val localRows = shown.filter { it.storage == "local" }
    val cloudRows = shown.filter { it.storage == "cloud" }
    LazyColumn(
        modifier = Modifier.fillMaxSize().widthIn(max = 860.dp),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("任务中心", style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() })
            Text("本机待办与校序云端记录分开统计；学校提交状态必须经过真实读取核验。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCard("待完成", all.count { !it.done }.toString(), Modifier.weight(1f))
                MetricCard("本周截止", all.count { !it.done && thisWeek(it.date, today) }.toString(),
                    Modifier.weight(1f), tone = XiaoxuTone.Warning)
                MetricCard("已完成", all.count { it.done }.toString(), Modifier.weight(1f),
                    tone = XiaoxuTone.Success)
            }
        }
        item {
            Text(
                if (!state.tasksFresh && state.tasks.isNotEmpty())
                    "统计包括上次云端任务快照 " + state.tasks.size + " 条，尚未重新核验；学习通本机快照不计入。"
                else "仅统计已保存的本机与云端任务；学习通本机页面快照不计入。",
                style = MaterialTheme.typography.labelSmall,
                color = XiaoxuPalette.Ink2
            )
        }
        // 本机任务操作直接在任务统计后出现，不再藏在长列表最底部。
        item {
            XiaoxuCard {
                SectionHeader(
                    title = "我的本机待办",
                    caption = "离线保存 · 只修改本人创建的任务",
                    actionLabel = if (showLocal) "收起管理" else "新增 / 管理",
                    onAction = { showLocal = !showLocal }
                )
            }
        }
        if (showLocal) item { LocalTaskBoard(localTasks, dao) }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                items(count = filters.size) { i ->
                    val (key, label) = filters[i]
                    FilterChip(selected = filter == key, onClick = { filter = key },
                        label = { Text(label) })
                }
            }
        }
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
                    onAction = { showLocal = true }
                )
            }
        } else {
            if (localRows.isNotEmpty()) {
                item {
                    TaskSectionHeader(
                        title = "本机本人待办",
                        count = localRows.size,
                        badge = "本机 Room",
                        note = "只存在这台手机上，离线可用；来源与日期都是本人填写。"
                    )
                }
                items(count = localRows.size, key = { i -> localRows[i].key }) { i ->
                    XiaoxuCard(contentPadding = 8.dp) { TaskRow(localRows[i], today) }
                }
            }
            if (cloudRows.isNotEmpty()) {
                item {
                    TaskSectionHeader(
                        title = "校序云端任务",
                        count = cloudRows.size,
                        badge = "校序云端",
                        note = "云端记录，来源与截止日期需与学校官方核对，未核验处已标注。"
                    )
                }
                items(count = cloudRows.size, key = { i -> cloudRows[i].key }) { i ->
                    XiaoxuCard(contentPadding = 8.dp) { TaskRow(cloudRows[i], today) }
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
                note = "课程、活动与学分记录，同样不参与任务统计。",
                showCount = false
            )
        }
        item { LocalSchoolRecordSection(setOf("course", "activity", "credits")) }
        item { ChaoxingHomeworkPanel(client, openSchoolReader = { openLocalSchool("chaoxing") }) }
         item {
            OutlinedButton(
                onClick = { showCloud = !showCloud },
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(if (showCloud) "收起云端任务管理" else "新增／管理云端任务")
            }
        }
        if (showCloud) item { CloudTasksPanel(client) }
        item {
            Text("学习通官方状态只在已核验读取后显示，个人已完成不代表学校已提交。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
                modifier = Modifier.semantics { heading() }
            )
            Spacer(Modifier.width(8.dp))
            TaskSourceBadge(badge, emphasized = true)
            Spacer(Modifier.weight(1f))
            if (showCount) {
                Text(
                    text = "$count 条",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { contentDescription = "本区共 $count 条" }
                )
            }
        }
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

/** 一条任务：标题（最多 3 行）+ 来源徽标 + 截止状态，整行 48dp 以上可读区域。 */
@Composable
private fun TaskRow(task: WorkspaceTask, today: LocalDate) {
    val due = remember(task, today) { describeDue(task, today) }
    val accent = if (task.storage == "local") MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.tertiary
    val dueColor = when (due.emphasis) {
        DUE_OVERDUE -> MaterialTheme.colorScheme.error
        DUE_TODAY -> MaterialTheme.colorScheme.tertiary
        DUE_WEEK -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .height(IntrinsicSize.Min)
            .semantics(mergeDescendants = true) {
                contentDescription = task.title + "；来源：" + task.origin +
                    "；" + (if (task.done) "已完成" else "待办") + "；" + due.spoken
            },
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .padding(vertical = 12.dp)
                .clip(RoundedCornerShape(50))
                .background(accent)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (task.done) TextDecoration.LineThrough else null,
                color = if (task.done) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TaskSourceBadge(task.origin)
                Text(
                    text = due.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = dueColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (task.done) "已完成" else "待办",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (task.done) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
