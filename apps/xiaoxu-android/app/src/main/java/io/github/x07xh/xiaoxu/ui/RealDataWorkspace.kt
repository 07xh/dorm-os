package io.github.x07xh.xiaoxu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.x07xh.xiaoxu.data.CloudClient
import io.github.x07xh.xiaoxu.data.CloudTask
import io.github.x07xh.xiaoxu.data.LocalTask
import io.github.x07xh.xiaoxu.data.LocalTaskDao
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

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
    openSchedule: () -> Unit,
    openTasks: () -> Unit,
    openMessages: () -> Unit,
    openProfile: () -> Unit
) {
    val state = client.state
    val today = LocalDate.now(SchoolZone)
    val all = remember(localTasks, state.tasks) { combineWorkspaceTasks(localTasks, state.tasks) }
    val sameWeekday = state.lessons.filter { it.weekday == today.dayOfWeek.value }
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
                    if (state.lessons.isEmpty()) {
                        Text("尚未读取可信的青果课程")
                        OutlinedButton(onClick = openProfile) { Text("检查学校连接") }
                    } else {
                        Text("仅按星期匹配；当前教学周未核实，不代表今天实际有课。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (sameWeekday.isEmpty()) Text("该星期暂无已核验的课程记录")
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

/** A unified VIEW, preserving both write stores and existing live Chaoxing reader. */
@Composable
fun RealAcademicTaskHub(
    localTasks: List<LocalTask>, dao: LocalTaskDao, client: CloudClient,
    openWebsite: () -> Unit
) {
    val state = client.state
    val today = LocalDate.now(SchoolZone)
    val all = remember(localTasks, state.tasks) { combineWorkspaceTasks(localTasks, state.tasks) }
    val filters = listOf("pending" to "待完成", "all" to "全部", "week" to "本周截止",
        "done" to "已完成", "local" to "本机", "cloud" to "云端")
    var filter by rememberSaveable { mutableStateOf("pending") }
    var showLocal by rememberSaveable { mutableStateOf(false) }
    var showCloud by rememberSaveable { mutableStateOf(false) }
    val visible = selectTasks(all, filter, today)
    LazyColumn(
        modifier = Modifier.fillMaxSize().widthIn(max = 860.dp),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("任务中心", style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold)
            Text("来源分明、同屏查看，学校状态必须经过真实读取核验。",
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
            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                items(count = filters.size) { i ->
                    val (key, label) = filters[i]
                    FilterChip(selected = filter == key, onClick = { filter = key },
                        label = { Text(label) })
                }
            }
        }
        item {
            Text("当前显示 " + visible.size + " 条 · 同屏查看不等于合并数据库",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (visible.isEmpty()) item {
            WorkspaceCard {
                Text(if (!state.authenticated) "暂无本机匹配记录；校序云端尚未连接"
                else "当前没有匹配的已保存任务；不能据此推断学校没有作业",
                    modifier = Modifier.padding(18.dp))
            }
        }
        items(count = minOf(visible.size, 80), key = { i -> visible[i].key }) { index ->
            WorkspaceCard { TaskPreview(visible[index]) }
        }
        if (state.privateError != null) item {
            Text("云端部分数据刷新失败，已有记录可能过期",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error)
        }
        item {
            HorizontalDivider()
            Text("学习通真实读取", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold)
        }
        item { LocalSchoolRecordSection(setOf("course", "homework", "activity", "credits")) }
        item { ChaoxingHomeworkPanel(client, openWebsite) }
        item {
            OutlinedButton(onClick = { showLocal = !showLocal }) {
                Text(if (showLocal) "收起本机任务管理" else "新增／管理本机任务")
            }
        }
        if (showLocal) item { LocalTaskBoard(localTasks, dao) }
        item {
            OutlinedButton(onClick = { showCloud = !showCloud }) {
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
