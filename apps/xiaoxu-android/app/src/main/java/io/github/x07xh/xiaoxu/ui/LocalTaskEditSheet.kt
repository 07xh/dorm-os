package io.github.x07xh.xiaoxu.ui

// Recovered from the pre-rebuild TaskBoard.kt; original validation and transaction retained.
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.x07xh.xiaoxu.data.*
import java.time.*
import java.time.format.DateTimeParseException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

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

@Composable
private fun LocalTaskSourcePicker(source: String?, onSelect: (String?) -> Unit, enabled: Boolean = true) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text("个人分类：" + taskSourceLabel(source))
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("不标注") }, onClick = { onSelect(null); expanded = false })
            TASK_SOURCES.forEach { option ->
                DropdownMenuItem(text = { Text(option.label) }, onClick = { onSelect(option.code); expanded = false })
            }
        }
    }
}
@Composable
private fun EditorCompletionCheckbox(checked: Boolean, enabled: Boolean, description: String, onCheckedChange: (Boolean) -> Unit) {
    Checkbox(checked, onCheckedChange = onCheckedChange, enabled = enabled,
        modifier = Modifier.semantics { contentDescription = description })
}

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
    val allLocal by rememberSnapshotRows(dao) { dao.observe() }
    val allEdges by rememberSnapshotRows(dao) { dao.observeDependencies() }
    val loaded = allLocal != null && allEdges != null
    val localRows = allLocal.orEmpty()
    val edgeRows = allEdges.orEmpty()
    // Null means no edit; it protects existing edges while Room loads.
    var editedPrerequisites by remember(task.id) { mutableStateOf<Set<Long>?>(null) }
    var prerequisitesExpanded by rememberSaveable(task.id) { mutableStateOf(false) }
    val chosen = editedPrerequisites ?: edgeRows.asSequence()
        .filter { it.taskId == task.id }.map { it.prerequisiteId }.toSet()
    val otherTasks = remember(localRows, task.id) {
        localRows.filter { it.id != task.id }.sortedWith(compareBy<LocalTask> { it.completed }
            .thenBy { it.title }.thenBy { it.id })
    }
    val graphWithoutThis = edgeRows.filter { it.taskId != task.id } +
        chosen.map { LocalTaskDependency(task.id, it) }

    AppDialog(onDismissRequest = { if (!saving) onDismiss() }, title = { Text("编辑本机任务") }, text = {
        Column(Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GlassFormField(title, { title = it.take(120); error = null }, "任务名称", Modifier.fillMaxWidth(), enabled = !saving)
        LocalTaskSourcePicker(
            source = source,
            onSelect = { selected -> source = selected; error = null },
            enabled = !saving
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
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (otherTasks.isEmpty()) {
                Text("先添加另一条本机任务，就能为当前事项设置前置关系。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            EditorCompletionCheckbox(
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
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (error != null) {
            Text(error!!, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
        }
        Button(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            enabled = loaded && !saving && title.isNotBlank(),
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
    }, confirmButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("取消") } })

    if (pickerOpen) {
        GlassDatePickerDialog(initialDate = parseDue(due) ?: LocalDate.now(),
            title = "选择截止日期", preserveInitial = true,
            onDismiss = { pickerOpen = false },
            onConfirm = { date -> due = date.toString(); pickerOpen = false; error = null })
    }
}
