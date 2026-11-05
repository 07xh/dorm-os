package io.github.x07xh.xiaoxu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Box
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
import io.github.x07xh.xiaoxu.data.CloudTask
import io.github.x07xh.xiaoxu.data.LocalTask
import io.github.x07xh.xiaoxu.data.LocalTaskDao
import io.github.x07xh.xiaoxu.data.LocalTaskDatabase
import io.github.x07xh.xiaoxu.data.NativeSchoolRecord
import io.github.x07xh.xiaoxu.data.VerifiedLesson
import java.time.Instant
import java.time.LocalDate
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
private fun TaskPreview(task: WorkspaceTask) {
    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(task.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(task.origin + " · " + if (task.done) "个人已完成" else "待办",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            if (task.date == null) "未记录截止日期"
            else task.dateSource + " · " + task.date.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = if (!task.done && task.date != null && task.date.isBefore(LocalDate.now(SchoolZone)))
                MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
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
 * Practical home powered by existing verified records. Does NOT invent next class,
 * actual school week, overdue official assignment state or official course data.
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
    val sameWeekday = timetable.filter { it.weekday == today.dayOfWeek.value }
        .sortedBy { it.startSection }
    val weekdayLabel = listOf("周一","周二","周三","周四","周五","周六","周日")[today.dayOfWeek.value - 1]
    LazyColumn(
        modifier = Modifier.fillMaxSize().widthIn(max = 860.dp),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("今天", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(today.monthValue.toString() + "月" + today.dayOfMonth + "日 · " + weekdayLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric("本机待办", localTasks.count { !it.completed }.toString(), Modifier.weight(1f))
                Metric("云端记录", if (state.authenticated) state.tasks.size.toString() else "—", Modifier.weight(1f))
                Metric("微信待审核", if (state.authenticated)
                    (state.pendingWechatCount?.let { if (it >= 30) "30+" else it.toString() } ?: "—")
                    else "—", Modifier.weight(1f))
            }
        }
        item {
            WorkspaceCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("课表", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = openSchedule) { Text("查看") }
                    }
                    if (timetable.isEmpty()) {
                        Text("暂无已保存的青果课程")
                        OutlinedButton(onClick = openProfile) { Text("在 App 中连接青果") }
                    } else {
                        Text(
                            (if (isLocalTimetable) "本机读取 · 待与学校官网核对。"
                             else "校序云端课程 · 来源状态以课表页为准。") +
                                " 仅按星期匹配，当前教学周未核实。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (sameWeekday.isEmpty()) Text("该星期没有已保存的课程课块")
                        sameWeekday.take(4).forEach { lesson ->
                            HorizontalDivider()
                            Text(lesson.title + " · 第" + lesson.startSection + "–" +
                                lesson.endSection + "节", fontWeight = FontWeight.Medium)
                            Text(lesson.location.ifBlank { "教室未提供" },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        item {
            WorkspaceCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("最近待办", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = openTasks) { Text("全部任务") }
                    }
                    val upcoming = all.filter { !it.done }.take(3)
                    if (upcoming.isEmpty()) Text("当前没有已保存的待办任务")
                    upcoming.forEach { task -> TaskPreview(task) }
                    Text("来自本机与云端已有记录；不代表学校上游刚完成同步。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        item {
            WorkspaceCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("学习通 · 本机作业", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = openTasks) { Text("查看") }
                    }
                    if (localHomework.isEmpty()) {
                        Text("还没有本机读取到的作业标题")
                        OutlinedButton(onClick = { openLocalSchool("chaoxing") }) {
                            Text("在 App 内读取学习通作业")
                        }
                    } else {
                        Text("已保存 ${localHomework.size} 条当前可见页面记录；提交状态及截止时间尚未核验。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { openLocalSchool("chaoxing") }) {
                            Text("重新读取官网当前页面")
                        }
                        localHomework.take(3).forEach { work ->
                            HorizontalDivider()
                            Text(work.title, maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.Medium)
                            Text("采集时间：" + DateFormat.getDateTimeInstance(
                                    DateFormat.SHORT, DateFormat.SHORT).format(Date(work.capturedAt)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (localSecondClass.isNotEmpty()) {
                        HorizontalDivider()
                        Text("第二课堂 · 本机保存 ${localSecondClass.size} 条活动／积分页面记录",
                            style = MaterialTheme.typography.bodyMedium)
                        Text("不是官方累计积分，查看和核对请到「我的」。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = openProfile) { Text("查看第二课堂") }
                    }
                }
            }
        }
        item {
            WorkspaceCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("官方通知", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = openMessages) { Text("消息中心") }
                    }
                    if (state.notices.isEmpty()) Text("暂无已读取的官方通知")
                    state.notices.take(2).forEach { notice ->
                        Text(notice.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    state.publicError?.let {
                        Text("公告刷新失败，旧记录可能过期",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
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
    // 上限与旧实现一致：只截断展示，不隐藏数据来源（分区标题里给出当前组条数）。
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
                Metric("待完成", all.count { !it.done }.toString(), Modifier.weight(1f))
                Metric("本周截止", all.count { !it.done && thisWeek(it.date, today) }.toString(),
                    Modifier.weight(1f))
                Metric("已完成", all.count { it.done }.toString(), Modifier.weight(1f))
            }
        }
        item {
            Text("三项统计只算本机待办与校序云端记录，学习通本机页面快照不计入。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
            Text("当前显示 " + shown.size + " 条 · 同屏查看不等于合并数据库",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (visible.isEmpty()) {
            item {
                WorkspaceCard {
                    Text(if (!state.authenticated) "暂无本机匹配记录；校序云端尚未连接"
                    else "当前没有匹配的已保存任务；不能据此推断学校没有作业",
                        modifier = Modifier.padding(18.dp))
                }
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
                    WorkspaceCard { TaskRow(localRows[i], today) }
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
                    WorkspaceCard { TaskRow(cloudRows[i], today) }
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
                WorkspaceCard { SnapshotRow(work) }
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
                onClick = { showLocal = !showLocal },
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(if (showLocal) "收起本机任务管理" else "新增／管理本机任务")
            }
        }
        if (showLocal) item { LocalTaskBoard(localTasks, dao) }
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
            SourceBadge(badge, emphasized = true)
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
private fun SourceBadge(text: String, emphasized: Boolean = false) {
    val container = if (emphasized) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant
    val content = if (emphasized) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = content,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(container)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
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
            DueUi("已逾期 · $text", spokenPrefix + "已逾期，截止 $text", DUE_OVERDUE)
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
                SourceBadge(task.origin)
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
                SourceBadge("本机页面快照")
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
