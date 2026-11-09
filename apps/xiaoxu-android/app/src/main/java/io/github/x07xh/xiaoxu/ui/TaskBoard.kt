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
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.runtime.collectAsState
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
import io.github.x07xh.xiaoxu.data.LocalTaskDao
import io.github.x07xh.xiaoxu.data.LocalTaskDependency
import io.github.x07xh.xiaoxu.data.wouldCreateDependencyCycle
import io.github.x07xh.xiaoxu.data.TASK_SOURCE_LOCAL
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

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
    onEditTask: (LocalTask) -> Unit,
    onCreateTask: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()
    var filterKey by rememberSaveable { mutableStateOf(TASK_FILTER_ALL) }
    var visibleLimit by rememberSaveable(filterKey) { mutableStateOf(30) }
    var pendingDelete by remember { mutableStateOf<LocalTask?>(null) }
    val dependencies by dao.observeDependencies().collectAsState(initial = emptyList())
    val visible = remember(tasks, filterKey, today) { filterLocalTasks(tasks, filterKey, today) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "已保存 ${tasks.size} 条本机任务",
                style = MaterialTheme.typography.titleSmall,
                color = XiaoxuPalette.Ink,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onCreateTask) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("新建")
            }
        }

        if (tasks.isEmpty()) {
            Text(
                "还没有本机任务。点击「新建」输入任务名称即可添加，分类与截止日期可以以后再填。",
                style = MaterialTheme.typography.bodyMedium,
                color = XiaoxuPalette.Ink2
            )
        } else {
            Text(
                "筛选本机任务",
                style = MaterialTheme.typography.labelMedium,
                color = XiaoxuPalette.Ink2
            )
            // Two rows instead of an overflowing strip: every filter is visible
            // on narrow Android screens without horizontal scrolling.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TASK_FILTERS.chunked(2).forEach { options ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        options.forEach { option ->
                            Box(modifier = Modifier.weight(1f)) {
                                SourcePill(
                                    label = option.label,
                                    isSelected = filterKey == option.key,
                                    a11yLabel = "按" + option.label + "筛选本机任务",
                                    onClick = { filterKey = option.key }
                                )
                            }
                        }
                    }
                }
            }
            Text(
                "当前显示 ${visible.size} 条",
                style = MaterialTheme.typography.labelSmall,
                color = XiaoxuPalette.Ink2,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
            if (visible.isEmpty()) {
                EmptyTaskHint(filterKey = filterKey, hasAny = true)
            } else {
                val shown = visible.take(visibleLimit)
                XiaoxuGroupSurface {
                    shown.forEachIndexed { index, task ->
                        androidx.compose.runtime.key(task.id) {
                            TaskRow(
                                task = task,
                                today = today,
                                onToggle = { checked ->
                                    scope.launch { dao.setCompleted(task.id, checked) }
                                },
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
                        onClick = {
                            visibleLimit = (visibleLimit + 30).coerceAtMost(visible.size)
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) { Text("显示更多（剩余 ${visible.size - shown.size} 条）") }
                }
            }
        }
    }

    pendingDelete?.let { task ->
        val downstreamCount = dependencies.count { it.prerequisiteId == task.id }
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除本机任务？") },
            text = {
                Text(
                    "“" + task.title + "”删除后不可恢复。" +
                        if (downstreamCount > 0)
                            " 另有 ${downstreamCount} 条后置任务将失去这项前置关系。"
                        else ""
                )
            },
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
 * One-field-first task creation. Not nested inside the task management list:
 * the user sees one short form, never a large card + filters + empty list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LocalTaskCreateSheet(
    dao: LocalTaskDao,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var title by rememberSaveable { mutableStateOf("") }
    var source by rememberSaveable { mutableStateOf<String?>(TASK_SOURCE_LOCAL) }
    var due by rememberSaveable { mutableStateOf<String?>(null) }
    var pickerOpen by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun create() {
        val clean = title.trim()
        if (clean.isEmpty() || saving) return
        val sourceValue = source
        val dueValue = due
        saving = true
        error = null
        scope.launch {
            try {
                dao.insert(LocalTask(title = clean, source = sourceValue, due = dueValue))
                onDismiss()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error = "保存到本机失败，请重试"
            } finally {
                saving = false
            }
        }
    }

    XiaoxuGlassDialog(
        title = "新建本机任务",
        onDismiss = { if (!saving) onDismiss() },
        showDismissButton = false
    ) {
        Text(
            "只需填写任务名称。分类和日期均可选，之后还能编辑。",
            style = MaterialTheme.typography.bodySmall,
            color = XiaoxuPalette.Ink2
        )
        OutlinedTextField(
            value = title,
            onValueChange = { title = it.take(120); error = null },
            label = { Text("任务名称") },
            placeholder = { Text("例如：完成电路实验报告") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        LocalTaskSourcePicker(source = source, onSelect = { source = it })
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = { pickerOpen = true },
                enabled = !saving,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Icon(Icons.Outlined.DateRange, contentDescription = null,
                    modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(due ?: "添加截止日期（可选）")
            }
            if (due != null) {
                TextButton(onClick = { due = null }, enabled = !saving) {
                    Text("清除")
                }
            }
        }
        if (error != null) {
            Text(error!!, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onDismiss,
                enabled = !saving,
                modifier = Modifier.heightIn(min = 48.dp)
            ) { Text("取消") }
            Button(
                onClick = ::create,
                enabled = !saving && title.isNotBlank(),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
            ) {
                Text(if (saving) "保存中…" else "添加任务")
            }
        }
        Text(
            "保存在本机；个人分类与日期不代表学校平台的官方状态。",
            style = MaterialTheme.typography.labelSmall,
            color = XiaoxuPalette.Ink2
        )
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
        ) { DatePicker(state = dateState) }
    }
}

/** One readable chooser rather than five truncated horizontal source chips. */
@Composable
private fun LocalTaskSourcePicker(
    source: String?,
    onSelect: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Text(
        "个人分类（可选）",
        style = MaterialTheme.typography.labelMedium,
        color = XiaoxuPalette.Ink2
    )
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) {
            Text(
                taskSourceLabel(source),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text("⌄")
        }
        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            androidx.compose.material3.DropdownMenuItem(
                text = { Text("不标注") },
                onClick = {
                    onSelect(null)
                    expanded = false
                }
            )
            TASK_SOURCES.forEach { option ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onSelect(option.code)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * Existing Room record editor. Reuses the shared native glass dialog; only
 * title / user-labelled source / user-entered deadline can be changed.
 * It never modifies school submissions, personal completion or original creation time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LocalTaskEditSheet(task: LocalTask, dao: LocalTaskDao, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var title by rememberSaveable(task.id) { mutableStateOf(task.title) }
    var source by rememberSaveable(task.id) { mutableStateOf(task.source) }
    var due by rememberSaveable(task.id) { mutableStateOf(task.due) }
    var pickerOpen by remember(task.id) { mutableStateOf(false) }
    var saving by remember(task.id) { mutableStateOf(false) }
    var error by remember(task.id) { mutableStateOf<String?>(null) }
    val allLocal by dao.observe().collectAsState(initial = emptyList())
    val allEdges by dao.observeDependencies().collectAsState(initial = emptyList())
    // Null means no edit; it protects existing edges while Room loads.
    var editedPrerequisites by remember(task.id) { mutableStateOf<Set<Long>?>(null) }
    val chosen = editedPrerequisites ?: allEdges.asSequence()
        .filter { it.taskId == task.id }.map { it.prerequisiteId }.toSet()
    val otherTasks = remember(allLocal, task.id) {
        allLocal.filter { it.id != task.id }.sortedWith(compareBy<LocalTask> { it.completed }
            .thenBy { it.title }.thenBy { it.id })
    }
    val graphWithoutThis = allEdges.filter { it.taskId != task.id } +
        chosen.map { LocalTaskDependency(task.id, it) }

    XiaoxuGlassDialog(title = "编辑本机任务", onDismiss = onDismiss, dismissLabel = "取消") {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it.take(120); error = null },
            label = { Text("任务名称") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        LocalTaskSourcePicker(
            source = source,
            onSelect = { selected -> source = selected; error = null }
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = { pickerOpen = true },
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Icon(Icons.Outlined.DateRange, contentDescription = null,
                    modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(due ?: "选择截止日期")
            }
            if (due != null) {
                TextButton(onClick = { due = null }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("清除")
                }
            }
        }
        Text("前置任务 · 本人设置", style = MaterialTheme.typography.labelMedium,
            color = XiaoxuPalette.Ink2)
        if (otherTasks.isEmpty()) {
            Text("先添加另一条本机任务，就能为当前事项设置前置关系。",
                style = MaterialTheme.typography.bodySmall, color = XiaoxuPalette.Ink2)
        } else {
            // Bound the editor height: a large task database stays lazy.
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 176.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(count = otherTasks.size, key = { otherTasks[it].id }) { index ->
                    val candidate = otherTasks[index]
                    val checked = candidate.id in chosen
                    val wouldCycle = !checked && wouldCreateDependencyCycle(
                        graphWithoutThis, task.id, candidate.id)
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TaskCompletionCheckbox(
                            checked = checked,
                            enabled = !saving && !wouldCycle,
                            description = "将“" + candidate.title + "”设为前置任务",
                            onCheckedChange = { isChecked ->
                                editedPrerequisites = if (isChecked) chosen + candidate.id
                                    else chosen - candidate.id
                                error = null
                            }
                        )
                        Text(candidate.title +
                            if (candidate.completed) " · 已完成" else
                                if (wouldCycle) " · 会形成循环" else "",
                            modifier = Modifier.weight(1f),
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
        Text("所选任务全部完成后，当前事项才会出现在“现在可做”；保存修改时一并生效。",
            style = MaterialTheme.typography.labelSmall, color = XiaoxuPalette.Ink2)
        if (error != null) {
            Text(error!!, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
        }
        Button(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            enabled = !saving && title.isNotBlank(),
            onClick = {
                val cleanTitle = title.trim()
                if (due != null && parseDue(due) == null) {
                    error = "截止日期无效，请重新选择或清除"
                } else if (cleanTitle.isNotEmpty()) {
                    saving = true
                    scope.launch {
                        try {
                            val changed = if (editedPrerequisites == null) {
                                dao.updateDetails(task.id, cleanTitle, source, due) > 0
                            } else {
                                dao.updateDetailsWithDependencies(task.id, cleanTitle, source,
                                    due, editedPrerequisites!!)
                            }
                            if (changed) onDismiss()
                            else error = "保存失败：任务已删除或前置关系有冲突"
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
        val millis = parseDue(due)?.atStartOfDay(ZoneOffset.UTC)
            ?.toInstant()?.toEpochMilli()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = millis)
        DatePickerDialog(
            onDismissRequest = { pickerOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { value ->
                        due = Instant.ofEpochMilli(value).atZone(ZoneOffset.UTC)
                            .toLocalDate().toString()
                    }
                    pickerOpen = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { pickerOpen = false }) { Text("取消") }
            }
        ) { DatePicker(state = pickerState) }
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
        IconButton(
            onClick = onEdit,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                Icons.Outlined.Edit,
                contentDescription = "编辑“" + task.title + "”",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
