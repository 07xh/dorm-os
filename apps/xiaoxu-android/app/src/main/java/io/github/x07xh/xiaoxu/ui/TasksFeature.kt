package io.github.x07xh.xiaoxu.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.x07xh.xiaoxu.data.*
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Reuses the original task projection, verification facets and transactional DAO. */
@Composable
internal fun ColumnScope.TasksFeature(data: AppWorkspaceState, actions: AppPageActions,
    target: Long?, consume: () -> Unit) {
    val scope = rememberCoroutineScope()
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var filter by rememberSaveable { mutableStateOf("pending") }
    var query by rememberSaveable { mutableStateOf("") }
    var adding by rememberSaveable { mutableStateOf(false) }
    var detailKey by rememberSaveable { mutableStateOf<String?>(null) }
    var editTaskId by rememberSaveable { mutableStateOf<Long?>(null) }
    var graphOpen by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val filters = listOf("pending" to "待办", "verify" to "待核验", "done" to "已完成", "all" to "全部")
    val today = LocalDate.now(ZoneId.of("Asia/Shanghai"))
    AppSnapshotContent(data.localTasks) { local ->
        val records = when (val rows = data.nativeRecords) {
            is PageSnapshot.Ready -> rows.value
            else -> emptyList()
        }
        val cloud = data.cloud.tasks.takeIf { data.cloud.authenticated && data.cloud.tasksFresh }.orEmpty()
        val combined = remember(local, records, cloud) { combineWorkspaceTasks(local, cloud, records) }
        val visible = remember(combined, filter, query, today) { searchWorkspaceTasks(combined, filter, query, today) }
        val listState = rememberLazyListState()
        LaunchedEffect(target, combined) {
            if (target != null && combined.any { it.key == "local:$target" }) {
                filter = "all"
                query = ""
            }
        }
        LaunchedEffect(target, visible) {
            if (target != null) {
                val index = visible.indexOfFirst { it.key == "local:$target" }
                if (index >= 0) { listState.scrollToItem(index); consume() }
            }
        }
        SystemStatStrip(items = listOf("待办" to combined.count(::isActionableWorkspaceTask).toString(),
            "待核验" to combined.count(::requiresChaoxingVerification).toString(),
            "已完成" to combined.count(::considerCompleteInTaskList).toString()))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Box {
                var menu by remember { mutableStateOf(false) }
                TextButton(onClick = { menu = true }) { Text(filters.firstOrNull { it.first == filter }?.second ?: "全部") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    filters.forEach { (value, label) -> DropdownMenuItem(text = { Text(label) },
                        onClick = { filter = value; menu = false }) }
                }
            }
            TextButton(onClick = { graphOpen = true }, enabled = data.dependencies is PageSnapshot.Ready) { Text("关系图") }
            TextButton(onClick = { adding = true }) { Text("新建任务") }
        }
        GlassFormField(query, { query = it }, "搜索任务", Modifier.fillMaxWidth(), placeholder = "任务名称")
        error?.let { AppNotice(it, error = true) }
        if (data.nativeRecords == PageSnapshot.Loading) AppNotice("正在读取本机平台记录")
        if (data.cloud.authenticated && !data.cloud.tasksFresh) AppNotice("云端任务尚未核验最新状态")
        if (visible.isEmpty()) SystemEmptyState("当前筛选暂无任务", "仅显示已读取的任务，不推断平台状态。")
        LazyColumn(Modifier.weight(1f), state = listState,
            verticalArrangement = Arrangement.spacedBy(AppSpacing.item)) {
            items(visible, key = { it.key }) { task ->
                val localId = task.key.removePrefix("local:").toLongOrNull().takeIf { task.storage == "local" }
                val stateLabel = task.officialStatusRaw ?: officialTaskStatusLabel(task.officialStatus)
                    ?: if (task.done) "个人已完成" else if (requiresChaoxingVerification(task)) "待核验" else null
                InsetGroupedSection {
                    InsetGroupedRow(title = task.title,
                        subtitle = listOfNotNull(task.origin, task.officialDeadline ?: task.date?.toString(), stateLabel).joinToString(" · "),
                        showDivider = false, onClick = { detailKey = task.key },
                        trailing = {
                            if (localId != null) Checkbox(task.done, enabled = !saving, onCheckedChange = { value ->
                                saving = true
                                scope.launch {
                                    try {
                                        error = if (actions.updateLocalTask(localId, value)) null else "请先完成前置任务或步骤"
                                    } catch (cancel: CancellationException) { throw cancel }
                                    catch (_: Exception) { error = "保存失败" }
                                    finally { saving = false }
                                }
                            })
                            else Text(if (considerCompleteInTaskList(task)) "已完成" else if (requiresChaoxingVerification(task)) "待核验" else "待处理",
                                style = MaterialTheme.typography.labelSmall)
                        })
                }
            }
        }
        if (graphOpen) {
            val deps = data.dependencies
            if (deps is PageSnapshot.Ready) LocalTaskDependencyGraphDialog(local, deps.value,
                onEditTask = { graphOpen = false; editTaskId = it },
                onManageTasks = { graphOpen = false; filter = "all"; query = "" },
                onDismiss = { graphOpen = false })
        }
        local.firstOrNull { it.id == editTaskId }?.let { task ->
            LocalTaskEditSheet(task, actions.localTaskDao, onDismiss = { editTaskId = null })
        }
        combined.firstOrNull { it.key == detailKey }?.let { task ->
            AlertDialog(onDismissRequest = { detailKey = null }, title = { Text(task.title) },
                text = { Column(Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(task.origin)
                    task.date?.let { Text("日期：$it · ${task.dateSource}") }
                    task.officialDeadline?.let { Text(it) }
                    task.officialStatusRaw?.let { Text(it) }
                    officialTaskStatusLabel(task.officialStatus)?.let { Text(it) }
                    if (task.storage != "local") AppNotice("平台记录只读，不修改学校提交状态。")
                    else {
                        val id = task.key.removePrefix("local:").toLongOrNull()
                        if (id != null) {
                            val blocked = when (val deps = data.dependencies) {
                                is PageSnapshot.Ready -> id in waitingLocalTaskIds(local, deps.value)
                                else -> true
                            }
                            TextButton(onClick = { detailKey = null; editTaskId = id }) { Text("编辑任务与前置关系") }
                            HorizontalDivider()
                            TaskExecutionPanel(ExecutionRoot("device", task.key), task.done, actions.executionDao,
                                onFinish = { actions.updateLocalTask(id, true) },
                                onReopen = { actions.updateLocalTask(id, false) }, rootBlocked = blocked)
                        }
                    }
                } }, confirmButton = { TextButton(onClick = { detailKey = null }) { Text("关闭") } })
        }
    }
    if (adding) {
        var title by rememberSaveable { mutableStateOf("") }
        var due by rememberSaveable { mutableStateOf("") }
        var inputError by rememberSaveable { mutableStateOf<String?>(null) }
        AlertDialog(onDismissRequest = { if (!saving) adding = false }, title = { Text("新建本机任务") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassFormField(title, { title = it }, "任务名称", Modifier.fillMaxWidth())
                GlassFormField(due, { due = it }, "个人截止日期（可空）", Modifier.fillMaxWidth(), placeholder = "YYYY-MM-DD")
                inputError?.let { AppNotice(it, error = true) }
            } }, confirmButton = { TextButton(enabled = !saving, onClick = {
                val date = due.trim()
                if (title.isBlank() || title.trim().length > 160) inputError = "请输入 1–160 字的任务名称"
                else if (date.isNotEmpty() && runCatching { LocalDate.parse(date) }.isFailure) inputError = "请填写有效日期"
                else {
                    saving = true
                    scope.launch {
                        try {
                            actions.addLocalTask(title.trim(), date.ifBlank { null })
                            adding = false
                            filter = "pending"
                            query = ""
                            error = null
                        } catch (cancel: CancellationException) { throw cancel }
                        catch (_: Exception) { inputError = "保存失败，请重试" }
                        finally { saving = false }
                    }
                }
            }) { Text("保存") } },
            dismissButton = { TextButton(enabled = !saving, onClick = { adding = false }) { Text("取消") } })
    }
}
