package io.github.x07xh.xiaoxu.ui


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import io.github.x07xh.xiaoxu.ui.v2.UiV2FormField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.x07xh.xiaoxu.data.LocalTask
import io.github.x07xh.xiaoxu.data.LocalTaskDao
import io.github.x07xh.xiaoxu.data.LocalTaskDependency
import io.github.x07xh.xiaoxu.data.wouldCreateDependencyCycle
import io.github.x07xh.xiaoxu.data.TASK_SOURCE_LOCAL
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
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

fun parseDue(due: String?): LocalDate? {
    if (due.isNullOrBlank()) return null
    return try {
        LocalDate.parse(due)
    } catch (_: DateTimeParseException) {
        null // 手工写坏的值不猜，直接当没有截止时间。
    }
}

/**
 * One-field-first task creation. Not nested inside the task management list:
 * the user sees one short form, never a large card + filters + empty list.
 */
@Composable
internal fun LocalTaskCreateSheet(
    dao: LocalTaskDao,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var title by rememberSaveable { mutableStateOf("") }
    var source by rememberSaveable { mutableStateOf<String?>(TASK_SOURCE_LOCAL) }
    var due by rememberSaveable { mutableStateOf<String?>(null) }
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
        LocalTaskFormFields(
            title = title, source = source, due = due, enabled = !saving,
            onTitle = { title = it; error = null },
            onSource = { source = it; error = null },
            onDue = { due = it; error = null }
        )
        if (error != null) {
            Text(error!!, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
        }
        LocalTaskFormActions(saving = saving, valid = title.isNotBlank(),
            saveLabel = "添加任务", onCancel = onDismiss, onSave = ::create)
    }
}

/** One presentation for create and edit; drafts and Room operations remain with callers. */
@Composable
private fun LocalTaskFormFields(
    title: String, source: String?, due: String?, enabled: Boolean,
    onTitle: (String) -> Unit, onSource: (String?) -> Unit, onDue: (String?) -> Unit
) {
    UiV2FormField(value = title, onChange = { onTitle(it.take(120)) },
        label = "任务名称", placeholder = "例如：完成电路实验报告", enabled = enabled)
    LocalTaskSourcePicker(source = source, onSelect = onSource, enabled = enabled)
    LocalTaskDuePicker(due = due, onSelect = onDue, enabled = enabled)
    Text("分类和日期由本人设置，不代表学校官方状态。",
        style = MaterialTheme.typography.labelSmall, color = XiaoxuPalette.Ink2)
}

@Composable
private fun LocalTaskFormActions(
    saving: Boolean, valid: Boolean, saveLabel: String,
    onCancel: () -> Unit, onSave: () -> Unit
) {
    Row(Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onCancel, enabled = !saving,
            modifier = Modifier.heightIn(min = 48.dp)) { Text("取消") }
        Button(onClick = onSave, enabled = !saving && valid,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
            Text(if (saving) "保存中…" else saveLabel)
        }
    }
}

/** Shared personal deadline control. The caller owns the draft and Room save. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocalTaskDuePicker(
    due: String?,
    onSelect: (String?) -> Unit,
    enabled: Boolean
) {
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = { pickerOpen = true },
            enabled = enabled,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
        ) {
            Icon(Icons.Outlined.DateRange, contentDescription = null,
                modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(due ?: "截止日期（可选）")
        }
        if (due != null) {
            TextButton(
                onClick = { onSelect(null) },
                enabled = enabled,
                modifier = Modifier.heightIn(min = 48.dp)
                    .semantics { contentDescription = "清除截止日期" }
            ) { Text("清除") }
        }
    }
    if (pickerOpen && enabled) {
        val millis = parseDue(due)?.atStartOfDay(ZoneOffset.UTC)
            ?.toInstant()?.toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = millis)
        DatePickerDialog(
            onDismissRequest = { pickerOpen = false },
            confirmButton = {
                TextButton(
                    enabled = state.selectedDateMillis != null,
                    onClick = {
                        state.selectedDateMillis?.let { value ->
                            onSelect(Instant.ofEpochMilli(value).atZone(ZoneOffset.UTC)
                                .toLocalDate().toString())
                        }
                        pickerOpen = false
                    }
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { pickerOpen = false }) { Text("取消") }
            }
        ) { DatePicker(state = state) }
    }
}

/** Task classification uses the same readable bottom action sheet as settings. */
@Composable
private fun LocalTaskSourcePicker(
    source: String?,
    onSelect: (String?) -> Unit,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    Text(
        "个人分类（可选）",
        style = MaterialTheme.typography.labelMedium,
        color = XiaoxuPalette.Ink2
    )
    OutlinedButton(
        onClick = { expanded = true },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
    ) {
        Text(
            taskSourceLabel(source),
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Icon(
            Icons.Outlined.Edit,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
    }
    if (expanded) {
        val options = listOf(
            XiaoxuSheetAction(
                label = "不标注",
                selected = source == null,
                onClick = {
                    onSelect(null)
                    expanded = false
                }
            )
        ) + TASK_SOURCES.map { option ->
            XiaoxuSheetAction(
                label = option.label,
                selected = source == option.code,
                onClick = {
                    onSelect(option.code)
                    expanded = false
                }
            )
        }
        XiaoxuBottomActionSheet(
            title = "选择个人分类",
            subtitle = "仅用于本人整理任务，不代表学校官方来源",
            onDismiss = { expanded = false },
            actions = options
        )
    }
}

/**
 * Existing Room record editor. Reuses the shared native glass dialog; only
 * title / user-labelled source / user-entered deadline can be changed.
 * It never modifies school submissions, personal completion or original creation time.
 */
@Composable
internal fun LocalTaskEditSheet(task: LocalTask, dao: LocalTaskDao, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var title by rememberSaveable(task.id) { mutableStateOf(task.title) }
    var source by rememberSaveable(task.id) { mutableStateOf(task.source) }
    var due by rememberSaveable(task.id) { mutableStateOf(task.due) }
    var saving by remember(task.id) { mutableStateOf(false) }
    var error by remember(task.id) { mutableStateOf<String?>(null) }
    val allLocal by dao.observe().collectAsState(initial = emptyList())
    val allEdges by dao.observeDependencies().collectAsState(initial = emptyList())
    // Null means no edit; it protects existing edges while Room loads.
    var editedPrerequisites by remember(task.id) { mutableStateOf<Set<Long>?>(null) }
    var prerequisitesExpanded by rememberSaveable(task.id) { mutableStateOf(false) }
    val chosen = editedPrerequisites ?: allEdges.asSequence()
        .filter { it.taskId == task.id }.map { it.prerequisiteId }.toSet()
    val otherTasks = remember(allLocal, task.id) {
        allLocal.filter { it.id != task.id }.sortedWith(compareBy<LocalTask> { it.completed }
            .thenBy { it.title }.thenBy { it.id })
    }
    val graphWithoutThis = allEdges.filter { it.taskId != task.id } +
        chosen.map { LocalTaskDependency(task.id, it) }

    XiaoxuGlassDialog(title = "编辑本机任务", onDismiss = { if (!saving) onDismiss() },
        showDismissButton = false) {
        LocalTaskFormFields(
            title = title, source = source, due = due, enabled = !saving,
            onTitle = { title = it; error = null },
            onSource = { source = it; error = null },
            onDue = { due = it; error = null }
        )
        // Keep the common title/date edit short; the optional dependency editor
        // is available on demand and never changes the saved DAG unless edited.
        TextButton(
            onClick = { prerequisitesExpanded = !prerequisitesExpanded },
            enabled = !saving,
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Text(if (prerequisitesExpanded) "收起前置关系（${chosen.size}）"
                else "前置关系（${chosen.size}）")
        }
        if (prerequisitesExpanded) {
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
            Text("前置任务全部完成后，当前任务才可执行。",
                style = MaterialTheme.typography.labelSmall, color = XiaoxuPalette.Ink2)
        }
        if (error != null) {
            Text(error!!, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
        }
        LocalTaskFormActions(
            saving = saving, valid = title.isNotBlank(), saveLabel = "保存修改",
            onCancel = onDismiss,
            onSave = {
                if (!saving) {
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
            }
        )
    }
}

// ---------------------------------------------------------------------------
// 组件
// ---------------------------------------------------------------------------

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
