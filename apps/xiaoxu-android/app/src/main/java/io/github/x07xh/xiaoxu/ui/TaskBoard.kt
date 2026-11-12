package io.github.x07xh.xiaoxu.ui

import android.widget.Toast

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
    var prerequisitesExpanded by rememberSaveable(task.id) { mutableStateOf(false) }
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
        // Keep the common title/date edit short; the optional dependency editor
        // is available on demand and never changes the saved DAG unless edited.
        TextButton(
            onClick = { prerequisitesExpanded = !prerequisitesExpanded },
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
            Text("所选任务全部完成后，当前事项才会出现在“现在可做”；保存修改时一并生效。",
                style = MaterialTheme.typography.labelSmall, color = XiaoxuPalette.Ink2)
        }
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
