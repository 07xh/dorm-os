package io.github.x07xh.xiaoxu.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.x07xh.xiaoxu.data.LocalTask
import io.github.x07xh.xiaoxu.data.CloudTask
import io.github.x07xh.xiaoxu.data.CloudClient
import io.github.x07xh.xiaoxu.data.LocalTaskDao
import io.github.x07xh.xiaoxu.data.TASK_SOURCE_LOCAL
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

/*
 * 校序任务页（本机待办看板）。
 *
 * 数据边界：只读写 Room 的 local_tasks，不碰云端任务、不碰学校接口。
 * source / due 是本人在手机上填的标签与提醒日期，界面一律表述为"本人标注"，
 * 绝不显示成学校平台给出的来源或官方截止时间。
 */

// ---------------------------------------------------------------------------
// 纯逻辑：来源标签 / 截止状态 / 筛选 / 统计（可脱离 Compose 单独验证）
// ---------------------------------------------------------------------------

data class TaskSourceOption(val code: String, val label: String)

val TASK_SOURCES = listOf(
    TaskSourceOption(TASK_SOURCE_LOCAL, "本机自建"),
    TaskSourceOption("course", "课程作业"),
    TaskSourceOption("chaoxing", "学习通"),
    TaskSourceOption("second_class", "第二课堂"),
    TaskSourceOption("other", "其他")
)

fun taskSourceLabel(code: String?): String =
    TASK_SOURCES.firstOrNull { it.code == code }?.label ?: "未标注来源"

const val TASK_FILTER_ALL = "all"
const val TASK_FILTER_TODO = "todo"
const val TASK_FILTER_DONE = "done"
const val TASK_FILTER_WEEK = "week"

data class TaskFilterOption(val key: String, val label: String)

val TASK_FILTERS = listOf(
    TaskFilterOption(TASK_FILTER_ALL, "全部"),
    TaskFilterOption(TASK_FILTER_TODO, "待完成"),
    TaskFilterOption(TASK_FILTER_DONE, "已完成"),
    TaskFilterOption(TASK_FILTER_WEEK, "本周截止")
)

enum class DueState { NONE, OVERDUE, TODAY, SOON, LATER }

fun parseDue(due: String?): LocalDate? {
    if (due.isNullOrBlank()) return null
    return try {
        LocalDate.parse(due)
    } catch (_: DateTimeParseException) {
        null // 手工写坏的值不猜，直接当没有截止时间。
    }
}

fun dueStateOf(task: LocalTask, today: LocalDate): DueState {
    if (task.completed) return DueState.NONE
    val date = parseDue(task.due) ?: return DueState.NONE
    return when {
        date.isBefore(today) -> DueState.OVERDUE
        date.isEqual(today) -> DueState.TODAY
        date.isBefore(today.plusDays(7)) -> DueState.SOON
        else -> DueState.LATER
    }
}

fun filterLocalTasks(tasks: List<LocalTask>, filterKey: String, today: LocalDate): List<LocalTask> =
    when (filterKey) {
        TASK_FILTER_TODO -> tasks.filter { !it.completed }
        TASK_FILTER_DONE -> tasks.filter { it.completed }
        TASK_FILTER_WEEK -> {
            val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val sunday = monday.plusDays(6)
            tasks.filter { task ->
                if (task.completed) return@filter false
                val date = parseDue(task.due) ?: return@filter false
                !date.isBefore(monday) && !date.isAfter(sunday)
            }
        }
        else -> tasks
    }


fun dueLabel(task: LocalTask, today: LocalDate): String {
    val date = parseDue(task.due) ?: return "未填截止"
    val base = date.toString()
    return when (dueStateOf(task, today)) {
        DueState.OVERDUE -> "已逾期 · $base"
        DueState.TODAY -> "今天截止 · $base"
        DueState.SOON -> "临近截止 · $base"
        else -> "截止 $base"
    }
}

// ---------------------------------------------------------------------------
// 视觉常量
// ---------------------------------------------------------------------------

private val ChipShape = RoundedCornerShape(8.dp)

private val SourceChipBg = Color(0xFFEAF2FF)
private val SourceChipInk = Color(0xFF3B6FB8)
private val DueOverdueBg = Color(0xFFFFE7E4)
private val DueOverdueInk = Color(0xFFC0453B)
private val DueTodayBg = Color(0xFFFFF1DE)
private val DueTodayInk = Color(0xFFB0741F)
private val DueLaterBg = Color(0xFFEFF2F8)
private val DueLaterInk = Color(0xFF6B7B93)

// ---------------------------------------------------------------------------
// 入口：MainActivity 任务页调用（作为 LazyColumn 的一个 item）
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalTaskEditor(
    tasks: List<LocalTask>, dao: LocalTaskDao,
    onEditTask: (LocalTask) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val today = LocalDate.now()
    var draft by rememberSaveable { mutableStateOf("") }
    var source by rememberSaveable { mutableStateOf(TASK_SOURCE_LOCAL) }
    var due by rememberSaveable { mutableStateOf<String?>(null) }
    var filterKey by rememberSaveable { mutableStateOf(TASK_FILTER_ALL) }
    // The editor lives inside the parent task feed; compose only a bounded batch
    // instead of eagerly creating 200 nested Material cards on every visit.
    var visibleLimit by rememberSaveable(filterKey) { mutableStateOf(30) }
    var pickerOpen by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<LocalTask?>(null) }

    val visible = remember(tasks, filterKey, today) { filterLocalTasks(tasks, filterKey, today) }

    fun addTask() {
        val title = draft.trim()
        if (title.isEmpty()) return
        draft = ""
        val chosenSource = source
        val chosenDue = due
        scope.launch {
            dao.insert(LocalTask(title = title, source = chosenSource, due = chosenDue))
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // ---------- 新建 ----------
        XiaoxuCard(modifier = Modifier.fillMaxWidth(), contentPadding = 0.dp) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "新建本机待办",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() }
                )
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(120) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    label = { Text("要做什么") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                Text(
                    text = "来源（本人标注）",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(count = TASK_SOURCES.size, key = { index -> TASK_SOURCES[index].code }) { index ->
                        val option = TASK_SOURCES[index]
                        SourcePill(
                            label = option.label,
                            isSelected = source == option.code,
                            a11yLabel = "来源标注为" + option.label,
                            onClick = { source = option.code }
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { pickerOpen = true },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Outlined.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(due ?: "选择截止日期")
                    }
                    if (due != null) {
                        TextButton(onClick = { due = null }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("清除")
                        }
                    }
                }
                Button(
                    onClick = ::addTask,
                    enabled = draft.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("添加任务")
                }
                Text(
                    text = "来源与截止只是本机提醒，不会被当成学校平台的官方截止时间。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // The canonical task center owns all counters and status tabs.
        // Keep only editing filters here so the user can still reach older local
        // records and toggle/delete them.
        // ---------- 本机编辑筛选 ----------
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(count = TASK_FILTERS.size, key = { index -> TASK_FILTERS[index].key }) { index ->
                val option = TASK_FILTERS[index]
                SourcePill(
                    label = option.label,
                    isSelected = filterKey == option.key,
                    a11yLabel = "按" + option.label + "筛选任务",
                    onClick = { filterKey = option.key }
                )
            }
        }
        Text(
            text = "共 ${tasks.size} 条本机任务，当前显示 ${visible.size} 条",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )

        // ---------- 连续玻璃编辑列表：分页渲染，不丢失任何 Room 记录 ----------
        if (visible.isEmpty()) {
            EmptyTaskHint(filterKey = filterKey, hasAny = tasks.isNotEmpty())
        } else {
            val shown = visible.take(visibleLimit)
            XiaoxuGroupSurface {
                shown.forEachIndexed { index, task ->
                    androidx.compose.runtime.key(task.id) {
                        TaskRow(
                            task = task,
                            today = today,
                            onToggle = { checked -> scope.launch { dao.setCompleted(task.id, checked) } },
                            onEdit = { onEditTask(task) },
                            onDelete = { pendingDelete = task }
                        )
                    }
                    if (index < shown.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = XiaoxuPalette.Line.copy(alpha = 0.35f),
                            thickness = 0.5.dp
                        )
                    }
                }
            }
            if (visible.size > shown.size) {
                TextButton(
                    onClick = { visibleLimit = (visibleLimit + 30).coerceAtMost(visible.size) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) { Text("显示更多本机任务（剩余 ${visible.size - shown.size} 条）") }
            }
        }
    }

    if (pickerOpen) {
        val initialMillis = parseDue(due)
            ?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        val dateState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { pickerOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { millis ->
                        due = Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC).toLocalDate().toString()
                    }
                    pickerOpen = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { pickerOpen = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = dateState)
        }
    }

    pendingDelete?.let { task ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这条本机任务？") },
            text = { Text("“" + task.title + "”只存在本机，删除后无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { dao.remove(task.id) }
                    pendingDelete = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}


/**
 * ONE shared editor for personal task fields.
 * Local Room: title/source/due. Approved WeChat: personal target date only.
 * No school deadline, provenance or source message is changed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TaskPersonalEditSheet(
    localTask: LocalTask?,
    cloudTask: CloudTask?,
    dao: LocalTaskDao,
    client: CloudClient,
    onDismiss: () -> Unit
) {
    if ((localTask == null) == (cloudTask == null)) return
    if (cloudTask != null && cloudTask.provider != "wechat") return
    val scope = rememberCoroutineScope()
    val sourceId = if (localTask != null) "local:${localTask.id}" else "cloud:${cloudTask!!.id}"
    val initialDate = if (localTask != null) localTask.due else {
        cloudTask?.personalTargetAt?.let { raw ->
            runCatching { Instant.parse(raw).atZone(ZoneId.of("Asia/Shanghai"))
                .toLocalDate().toString() }.getOrNull()
        }
    }
    var title by rememberSaveable(sourceId) { mutableStateOf(localTask?.title ?: cloudTask!!.title) }
    var source by rememberSaveable(sourceId) { mutableStateOf(localTask?.source) }
    var date by rememberSaveable(sourceId) { mutableStateOf(initialDate) }
    var pickerOpen by remember(sourceId) { mutableStateOf(false) }
    var saving by remember(sourceId) { mutableStateOf(false) }
    var error by remember(sourceId) { mutableStateOf<String?>(null) }

    XiaoxuGlassDialog(
        title = "编辑个人任务",
        onDismiss = { if (!saving) onDismiss() },
        dismissLabel = "取消"
    ) {
        if (localTask != null) {
            OutlinedTextField(
                value = title, onValueChange = { title = it.take(120); error = null },
                label = { Text("任务名称") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text("来源（本人标注）", style = MaterialTheme.typography.labelMedium,
                color = XiaoxuPalette.Ink2)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(count = TASK_SOURCES.size, key = { index -> TASK_SOURCES[index].code }) { index ->
                    val option = TASK_SOURCES[index]
                    SourcePill(
                        label = option.label, isSelected = source == option.code,
                        a11yLabel = "来源标注为" + option.label,
                        onClick = { source = option.code }
                    )
                }
            }
        } else {
            Text(cloudTask!!.title, style = MaterialTheme.typography.titleMedium)
            Text("微信来源标题和原消息不可编辑，仅修改个人计划日期。",
                style = MaterialTheme.typography.bodySmall, color = XiaoxuPalette.Ink2)
            if (!client.state.authenticated || !client.state.tasksFresh) {
                Text("云端数据尚未刷新，请返回任务中心刷新后编辑。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }
        }
        Text(if (localTask != null) "本人截止日期" else "本人计划完成日期",
            style = MaterialTheme.typography.labelMedium, color = XiaoxuPalette.Ink2)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { pickerOpen = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Outlined.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(date ?: "选择日期")
            }
            if (date != null) {
                TextButton(onClick = { date = null; error = null },
                    modifier = Modifier.heightIn(min = 48.dp)) { Text("清除") }
            }
        }
        if (error != null) {
            Text(error!!, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
        }
        Button(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            enabled = !saving && (if (localTask != null) title.isNotBlank()
                else client.state.authenticated && client.state.tasksFresh),
            onClick = {
                if (date != null && parseDue(date) == null) {
                    error = "日期无效，请重新选择或清除"
                } else {
                    saving = true
                    scope.launch {
                        try {
                            val saved = if (localTask != null) {
                                dao.updateDetails(localTask.id, title.trim(), source, date) > 0
                            } else {
                                client.setWechatPersonalPlanDate(
                                    cloudTask!!.id, cloudTask.revision,
                                    date?.let { LocalDate.parse(it) })
                            }
                            if (saved) onDismiss()
                            else error = "保存未成功；请刷新后重试"
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            error = "保存失败，请重试"
                        } finally {
                            saving = false
                        }
                    }
                }
            }
        ) { Text(if (saving) "保存中…" else "保存修改") }
    }

    if (pickerOpen) {
        val millis = parseDue(date)?.atStartOfDay(ZoneOffset.UTC)
            ?.toInstant()?.toEpochMilli()
        val picker = rememberDatePickerState(initialSelectedDateMillis = millis)
        DatePickerDialog(
            onDismissRequest = { pickerOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { chosen ->
                        date = Instant.ofEpochMilli(chosen).atZone(ZoneOffset.UTC)
                            .toLocalDate().toString()
                    }
                    pickerOpen = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { pickerOpen = false }) { Text("取消") }
            }
        ) { DatePicker(state = picker) }
    }
}

// ---------------------------------------------------------------------------
// 组件
// ---------------------------------------------------------------------------

@Composable
private fun SourcePill(
    label: String,
    isSelected: Boolean,
    a11yLabel: String,
    onClick: () -> Unit
) {
    val background by animateColorAsState(
        targetValue = if (isSelected) XiaoxuPalette.Brand
        else XiaoxuPalette.GlassCard,
        animationSpec = tween(XiaoxuMotion.SelectionMs),
        label = "task-pill-bg"
    )
    val foreground by animateColorAsState(
        targetValue = if (isSelected) Color.White
        else XiaoxuPalette.Ink2,
        animationSpec = tween(XiaoxuMotion.SelectionMs),
        label = "task-pill-fg"
    )
    Box(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(50))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .semantics {
                role = Role.Tab
                contentDescription = a11yLabel
                stateDescription = if (isSelected) "已选中" else "未选中"
            },
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isSelected) {
                Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(14.dp), tint = foreground)
                Spacer(Modifier.width(4.dp))
            }
            Text(text = label, style = MaterialTheme.typography.labelLarge, color = foreground)
        }
    }
}

/** Shared UI for personal task completion only; school submission is not writable. */
@Composable
internal fun TaskCompletionCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Checkbox(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier.semantics { contentDescription = description }
    )
}

@Composable
private fun TaskRow(
    task: LocalTask,
    today: LocalDate,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val state = dueStateOf(task, today)
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TaskCompletionCheckbox(
            checked = task.completed,
            onCheckedChange = onToggle,
            description = if (task.completed) "把“" + task.title + "”改为待完成"
                else "把“" + task.title + "”标记为已完成"
        )
            Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (task.completed) TextDecoration.LineThrough else null,
                    color = if (task.completed) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    MiniChip(taskSourceLabel(task.source), SourceChipBg, SourceChipInk)
                    when (state) {
                        DueState.OVERDUE -> MiniChip(dueLabel(task, today), DueOverdueBg, DueOverdueInk)
                        DueState.TODAY -> MiniChip(dueLabel(task, today), DueTodayBg, DueTodayInk)
                        DueState.SOON, DueState.LATER -> MiniChip(dueLabel(task, today), DueLaterBg, DueLaterInk)
                        DueState.NONE -> MiniChip("未填截止", DueLaterBg, DueLaterInk)
                    }
                }
            }
        IconButton(onClick = onEdit, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Outlined.Edit,
                contentDescription = "编辑“" + task.title + "”",
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                Icons.Outlined.DeleteOutline,
                contentDescription = "删除“" + task.title + "”",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MiniChip(text: String, container: Color, ink: Color) {
    Row(
        modifier = Modifier
            .clip(ChipShape)
            .background(container)
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EmptyTaskHint(filterKey: String, hasAny: Boolean) {
    val message = when {
        !hasAny -> "还没有本机待办。上面填一个标题就能开始，来源和截止都可以不填。"
        filterKey == TASK_FILTER_WEEK -> "本周没有设置截止时间的未完成任务。"
        filterKey == TASK_FILTER_TODO -> "没有未完成的任务。"
        filterKey == TASK_FILTER_DONE -> "还没有标记完成的任务。"
        else -> "这个筛选下没有任务。"
    }
    XiaoxuCard(contentPadding = 0.dp) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(16.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Outlined.DateRange,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
