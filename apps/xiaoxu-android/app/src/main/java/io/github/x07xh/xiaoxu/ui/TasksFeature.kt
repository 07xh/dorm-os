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
import androidx.compose.ui.platform.LocalContext
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
    val context = LocalContext.current
    val archiveStore = remember(context) { LocalTaskArchiveStore.get(context) }
    val archivedKeys = archiveStore.archivedKeys
    var showArchived by rememberSaveable { mutableStateOf(false) }
    var deleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var filter by rememberSaveable { mutableStateOf("pending") }
    var query by rememberSaveable { mutableStateOf("") }
    var sourceFilter by rememberSaveable { mutableStateOf("all") }
    var sourceMenuOpen by remember { mutableStateOf(false) }
    var onlyThisWeek by rememberSaveable { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var chooseDestination by rememberSaveable { mutableStateOf(false) }
    var addingCloud by rememberSaveable { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
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
        val allRecords = remember(local, records, cloud) { combineWorkspaceTasks(local, cloud, records) }
        val combined = remember(allRecords, archivedKeys) { visibleWorkspaceTasks(allRecords, archivedKeys) }
        val archivedRows = remember(allRecords, archivedKeys) { allRecords.filter { it.key in archivedKeys } }
        val visible = remember(combined, filter, query, sourceFilter, onlyThisWeek, today) {
            facetWorkspaceTasks(searchWorkspaceTasks(combined, filter, query, today),
                sourceFilter, onlyThisWeek, today)
        }
        val listState = rememberLazyListState()
        LaunchedEffect(target, combined) {
            if (target != null && combined.any { it.key == "local:$target" }) {
                filter = "all"
                query = ""
                sourceFilter = "all"
                onlyThisWeek = false
            }
        }
        LaunchedEffect(target, visible, filter, query) {
            if (target != null) {
                val index = visible.indexOfFirst { it.key == "local:$target" }
                if (index >= 0 && filter == "all" && query.isEmpty()) {
                    listState.scrollToItem(index)
                    detailKey = visible[index].key
                    consume()
                }
            }
        }
        SystemStatStrip(items = listOf("待办" to combined.count(::isActionableWorkspaceTask).toString(),
            "待核验" to combined.count(::requiresChaoxingVerification).toString(),
            "已完成" to combined.count(::considerCompleteInTaskList).toString()))
        LiquidSegmentedControl(options = filters.map { it.second },
            selectedIndex = filters.indexOfFirst { it.first == filter }.coerceAtLeast(0),
            onSelect = { filter = filters[it].first }, height = 40.dp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { showArchived = true }) { Text("归档 (${archivedRows.size})") }
            TextButton(onClick = { graphOpen = true }, enabled = data.dependencies is PageSnapshot.Ready) { Text("关系图") }
            TextButton(enabled = !refreshing && !data.cloud.busy, onClick = {
                refreshing = true
                scope.launch {
                    try { actions.refreshCloud() }
                    finally { refreshing = false }
                }
            }) { Text("刷新") }
            TextButton(onClick = { if (data.cloud.authenticated) chooseDestination = true else adding = true }) { Text("新建任务") }
        }
        GlassFormField(query, { query = it }, "搜索任务", Modifier.fillMaxWidth(), placeholder = "任务名称")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                TextButton(onClick = { sourceMenuOpen = true },
                    modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(when (sourceFilter) {
                        "local" -> "本机来源"
                        "cloud" -> "云端来源"
                        "wechat" -> "微信任务"
                        "chaoxing" -> "学习通任务"
                        else -> "全部来源"
                    })
                }
                DropdownMenu(expanded = sourceMenuOpen, onDismissRequest = { sourceMenuOpen = false }) {
                    listOf("all" to "全部来源", "cloud" to "云端来源",
                        "wechat" to "微信任务", "chaoxing" to "学习通任务",
                        "local" to "本机来源").forEach { (source, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = {
                            sourceFilter = source
                            sourceMenuOpen = false
                        })
                    }
                }
            }
            FilterChip(selected = onlyThisWeek, onClick = { onlyThisWeek = !onlyThisWeek },
                label = { Text("本周日期") })
            if (sourceFilter != "all" || onlyThisWeek || query.isNotBlank()) {
                TextButton(onClick = { sourceFilter = "all"; onlyThisWeek = false; query = "" }) {
                    Text("清除")
                }
            }
        }
        error?.let { AppNotice(it, error = true) }
        if (data.nativeRecords == PageSnapshot.Loading) AppNotice("正在读取本机平台记录")
        if (data.cloud.authenticated && !data.cloud.tasksFresh) AppNotice("云端任务尚未核验最新状态")
        if (visible.isEmpty()) SystemEmptyState("当前筛选暂无任务", "仅显示已读取的任务，不推断平台状态。")
        LazyColumn(Modifier.weight(1f), state = listState,
            verticalArrangement = Arrangement.spacedBy(AppSpacing.item)) {
            items(visible, key = { it.key }) { task ->
                val localId = task.key.removePrefix("local:").toLongOrNull().takeIf { task.storage == "local" }
                val ownerId = data.cloud.ownerId
                val canToggleWechat = task.storage == "cloud" && task.provider == "wechat" &&
                    task.revision >= 0 && data.cloud.authenticated && data.cloud.tasksFresh && !ownerId.isNullOrBlank()
                val stateLabel = task.officialStatusRaw ?: officialTaskStatusLabel(task.officialStatus)
                    ?: if (task.done) "个人已完成" else if (requiresChaoxingVerification(task)) "待核验" else null
                InsetGroupedSection {
                    InsetGroupedRow(title = task.title,
                        subtitle = listOfNotNull(task.origin, task.officialDeadline ?: task.date?.toString(), stateLabel).joinToString(" · "),
                        showDivider = false, onClick = { detailKey = task.key },
                        trailing = {
                            if (localId != null) TaskCompletionCheckbox(checked = task.done, enabled = !saving,
                                description = "标记" + task.title + if (task.done) "为未完成" else "为完成", onCheckedChange = { value ->
                                saving = true
                                scope.launch {
                                    try {
                                        error = if (actions.updateLocalTask(localId, value)) null else "请先完成前置任务或步骤"
                                    } catch (cancel: CancellationException) { throw cancel }
                                    catch (_: Exception) { error = "保存失败" }
                                    finally { saving = false }
                                }
                            })
                            else if (canToggleWechat && ownerId != null) TaskCompletionCheckbox(
                                checked = task.done, enabled = !saving,
                                description = "标记微信任务" + task.title + if (task.done) "为未完成" else "为完成",
                                onCheckedChange = { value ->
                                    saving = true
                                    scope.launch {
                                        try {
                                            val saved = actions.updateWechatTask(task.key.removePrefix("cloud:"), task.revision, value, ownerId)
                                            error = if (saved) null else "未确认云端任务更新，请刷新后重试"
                                        } catch (cancel: CancellationException) { throw cancel }
                                        catch (_: Exception) { error = "云端更新失败，请刷新后重试" }
                                        finally { saving = false }
                                    }
                                })
                            else Text(if (considerCompleteInTaskList(task)) "已完成" else if (requiresChaoxingVerification(task)) "待核验" else "待处理",
                                style = MaterialTheme.typography.labelSmall)
                        })
                }
            }
        }
        if (showArchived) {
            AppDialog(onDismissRequest = { showArchived = false }, title = { Text("本机归档") },
                text = {
                    Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppNotice("归档仅隐藏本机列表，不改变学校或云端记录。")
                        if (archivedRows.isEmpty()) Text("当前账户没有可恢复的已读取归档记录")
                        archivedRows.forEach { task ->
                            InsetGroupedSection {
                                InsetGroupedRow(title = task.title, subtitle = task.origin, showDivider = false,
                                    trailing = { TextButton(onClick = { archiveStore.restore(task.key) }) { Text("恢复") } })
                            }
                        }
                    }
                }, confirmButton = { TextButton(onClick = { showArchived = false }) { Text("关闭") } })
        }
        val deleting = local.firstOrNull { it.id == deleteId }
        if (deleting != null) {
            AppDialog(onDismissRequest = { if (!saving) deleteId = null }, title = { Text("删除本机任务？") },
                text = { Text("将删除「${deleting.title}」及其本机执行步骤和关联关系。此操作不会修改平台记录。") },
                confirmButton = { TextButton(enabled = !saving, onClick = {
                    saving = true
                    scope.launch {
                        try {
                            actions.localTaskDao.remove(deleting.id)
                            deleteId = null
                            detailKey = null
                            error = null
                        } catch (cancel: CancellationException) { throw cancel }
                        catch (_: Exception) { error = "删除失败，请重试" }
                        finally { saving = false }
                    }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
                dismissButton = { TextButton(enabled = !saving, onClick = { deleteId = null }) { Text("取消") } })
        }
        if (graphOpen) {
            val deps = data.dependencies
            if (deps is PageSnapshot.Ready) LocalTaskDependencyGraphDialog(local, deps.value,
                onEditTask = { graphOpen = false; editTaskId = it },
                onManageTasks = { graphOpen = false; filter = "all"; query = ""; sourceFilter = "local"; onlyThisWeek = false },
                onDismiss = { graphOpen = false })
        }
        local.firstOrNull { it.id == editTaskId }?.let { task ->
            LocalTaskEditSheet(task, actions.localTaskDao, onDismiss = { editTaskId = null })
        }
        combined.firstOrNull { it.key == detailKey }?.let { task ->
            AppDialog(onDismissRequest = { detailKey = null }, title = { Text(task.title) },
                text = { Column(Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(task.origin)
                    task.date?.let { Text("日期：$it · ${task.dateSource}") }
                    task.officialDeadline?.let { Text(it) }
                    task.officialStatusRaw?.let { Text(it) }
                    officialTaskStatusLabel(task.officialStatus)?.let { Text(it) }
                    if (task.storage != "local") {
                        if (task.storage == "cloud" && task.provider == "wechat") {
                            AppNotice("微信任务完成状态仅为本人整理，不代表学校提交状态。")
                            WechatTaskLinkActions(task.links)
                        } else AppNotice("平台记录只读，不修改学校提交状态。")
                        if (task.storage == "cloud" && task.provider == "chaoxing" && considerCompleteInTaskList(task)) {
                            TextButton(onClick = { archiveStore.archive(task.key); detailKey = null }) { Text("移至本机归档") }
                        }
                    } else {
                        val id = task.key.removePrefix("local:").toLongOrNull()
                        if (id != null) {
                            val blocked = when (val deps = data.dependencies) {
                                is PageSnapshot.Ready -> id in waitingLocalTaskIds(local, deps.value)
                                else -> true
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                TextButton(onClick = { detailKey = null; editTaskId = id }) { Text("编辑任务") }
                                TextButton(enabled = !saving, onClick = { detailKey = null; deleteId = id }) {
                                    Text("删除任务", color = MaterialTheme.colorScheme.error)
                                }
                            }
                            HorizontalDivider()
                            TaskExecutionPanel(ExecutionRoot("device", task.key), task.done, actions.executionDao,
                                onFinish = { actions.updateLocalTask(id, true) },
                                onReopen = { actions.updateLocalTask(id, false) }, rootBlocked = blocked)
                        }
                    }
                } }, confirmButton = { TextButton(onClick = { detailKey = null }) { Text("关闭") } })
        }
    }
    if (chooseDestination) AppDialog(onDismissRequest = { chooseDestination = false },
        title = { Text("新建任务") },
        text = {
            InsetGroupedSection {
                InsetGroupedRow(title = "本机任务", subtitle = "保存在本设备", onClick = {
                    chooseDestination = false
                    adding = true
                })
                InsetGroupedRow(title = "云端任务", subtitle = "同步到本人校序账户", showDivider = false,
                    onClick = { chooseDestination = false; addingCloud = true })
            }
        },
        confirmButton = { TextButton(onClick = { chooseDestination = false }) { Text("取消") } })
    if (addingCloud) CloudTaskEditor(data.cloud, actions,
        onDismiss = { addingCloud = false },
        onCreated = {
            addingCloud = false
            filter = "pending"
            query = ""
            sourceFilter = "cloud"
            onlyThisWeek = false
        })
    if (adding) LocalTaskCreateSheet(actions.localTaskDao,
        onDismiss = { adding = false },
        onCreated = {
            adding = false
            filter = "pending"
            query = ""
            sourceFilter = "all"
            onlyThisWeek = false
            error = null
        })
}


/** Existing owner-protected task creation contract; no local shadow record. */
@Composable
private fun CloudTaskEditor(state: CloudUiState, actions: AppPageActions, onDismiss: () -> Unit,
    onCreated: () -> Unit) {
    val owner = state.ownerId
    val scope = rememberCoroutineScope()
    var title by rememberSaveable(owner) { mutableStateOf("") }
    var submitting by remember(owner) { mutableStateOf(false) }
    var uncertain by remember(owner) { mutableStateOf(false) }
    val connected = state.authenticated && !owner.isNullOrBlank()
    AppDialog(onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text("新建云端任务") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppNotice("仅写入本人校序云端，不代表学校官方任务。")
                if (!connected) AppNotice("请先连接校序云端。", error = true)
                GlassFormField(title, { title = it.take(120) }, "任务名称", Modifier.fillMaxWidth(),
                    enabled = connected && !submitting && !uncertain)
                if (uncertain) AppNotice("提交结果未确认，请关闭并刷新任务列表后核对。", error = true)
            }
        },
        confirmButton = {
            Button(enabled = connected && !state.busy && !submitting && !uncertain && title.isNotBlank(),
                onClick = {
                    val boundOwner = owner ?: return@Button
                    val value = title.trim()
                    submitting = true
                    scope.launch {
                        try {
                            if (actions.addCloudTask(value, boundOwner)) onCreated()
                            else uncertain = true
                        } catch (cancel: CancellationException) { throw cancel }
                        catch (_: Exception) { uncertain = true }
                        finally { submitting = false }
                    }
                }) { Text(if (submitting) "保存中" else "保存至云端") }
        },
        dismissButton = { TextButton(enabled = !submitting, onClick = onDismiss) { Text("取消") } })
}
