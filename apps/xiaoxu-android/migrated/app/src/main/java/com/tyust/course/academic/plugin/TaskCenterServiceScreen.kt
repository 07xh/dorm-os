package com.tyust.course.academic.plugin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tyust.course.ui.system.*
import io.github.x07xh.xiaoxu.data.*
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Independent task center hosted by the upstream Service Extension page renderer.
 * Room remains the owner of personal tasks and execution steps; CloudClient remains
 * the only writer for owner-scoped WeChat tasks; Chaoxing records stay read-only.
 */
@Composable
internal fun TaskCenterServiceScreen(
    title: String,
    schoolName: String,
    accountScope: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val client = remember(context.applicationContext) { CloudClient(context.applicationContext) }
    val state = client.state
    val scope = rememberCoroutineScope()
    val database = remember(context) { LocalTaskDatabase.open(context) }
    val taskDao = remember(database) { database.tasks() }
    val schoolDao = remember(database) { database.nativeSchoolRecords() }
    val executionDao = remember(database) { database.executions() }
    val archiveStore = remember(context) { LocalTaskArchiveStore.get(context) }
    val localTasks by remember(taskDao) { taskDao.observe() }.collectAsState(initial = emptyList())
    val dependencies by remember(taskDao) { taskDao.observeDependencies() }.collectAsState(initial = emptyList())
    val schoolRows by remember(schoolDao) { schoolDao.observe() }.collectAsState(initial = emptyList())
    val steps by remember(executionDao) { executionDao.observeSteps() }.collectAsState(initial = emptyList())
    val edges by remember(executionDao) { executionDao.observeEdges() }.collectAsState(initial = emptyList())
    val executions by remember(executionDao) { executionDao.observeExecutions() }.collectAsState(initial = emptyList())

    val layout = remember(context, accountScope) {
        ServicePageLayout(context, "$accountScope:${BundledServiceProviders.CLOUD_MESSAGES_ID}")
    }
    var layoutRevision by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var filterKey by rememberSaveable { mutableStateOf("now") }
    var sourceFilter by rememberSaveable { mutableStateOf("all") }
    var query by rememberSaveable { mutableStateOf("") }
    var pendingAction by remember { mutableStateOf<JSONObject?>(null) }
    var stepDialogRoot by remember { mutableStateOf<ExecutionRoot?>(null) }
    var stepDraft by rememberSaveable { mutableStateOf("") }

    val today = LocalDate.now(ZoneId.of("Asia/Shanghai"))
    val archivedKeys = archiveStore.archivedKeys
    val allRows = remember(localTasks, state.tasks, schoolRows, archivedKeys) {
        visibleWorkspaceTasks(
            combineWorkspaceTasks(localTasks, state.tasks, schoolRows),
            archivedKeys
        )
    }
    val schoolBinding = remember(schoolRows) {
        schoolRows.singleOrNull {
            it.provider == "chaoxing" && it.kind == "account_binding" && it.fingerprint == "current"
        }?.details?.takeIf { it.matches(Regex("[a-f0-9]{64}")) }
    }
    val ownerId = state.ownerId.takeIf { state.authenticated }
    val resolveExecution: (WorkspaceTask) -> ExecutionRoot? = remember(ownerId, schoolBinding) {
        { row -> taskExecutionRoot(row, ownerId, schoolBinding) }
    }

    LaunchedEffect(client) { client.refreshOnForeground() }

    val page = remember(
        state, allRows, localTasks, dependencies, steps, edges, executions,
        filterKey, sourceFilter, query, today, ownerId, schoolBinding
    ) {
        buildTaskCenterPage(
            state = state,
            rows = allRows,
            localTasks = localTasks,
            dependencies = dependencies,
            steps = steps,
            edges = edges,
            executions = executions,
            filterKey = filterKey,
            sourceFilter = sourceFilter,
            query = query,
            today = today,
            ownerId = ownerId,
            schoolBinding = schoolBinding
        )
    }
    val blocks = remember(page.toString(), layoutRevision) { layout.order(page) }
    val hidden = remember(page.getString("pageId"), layoutRevision) {
        layout.hidden(page.getString("pageId"))
    }

    fun launchWork(work: suspend () -> Unit) {
        if (busy || state.busy) return
        scope.launch {
            busy = true
            message = ""
            try {
                work()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                message = "操作未完成，请刷新后检查任务状态"
            } finally {
                busy = false
            }
        }
    }

    suspend fun reopenExecution(root: ExecutionRoot): Boolean {
        if (root.scope.startsWith("wechat:")) {
            val remoteId = root.key.removePrefix("cloud:")
            val remote = client.state.tasks.firstOrNull {
                it.id == remoteId && it.provider == "wechat"
            } ?: return false
            if (remote.status == "done") {
                val owner = client.state.ownerId ?: return false
                if (!client.setWechatTaskCompleted(remote.id, remote.revision, false, owner)) return false
            }
        }
        executionDao.reopen(root)
        return true
    }

    suspend fun addExecutionStep(root: ExecutionRoot, rawTitle: String): Boolean {
        val stepTitle = rawTitle.trim()
        if (stepTitle.isBlank() || stepTitle.length > 120) {
            message = "步骤名称不能为空，且不能超过 120 个字符"
            return false
        }
        if (root.scope.startsWith("wechat:")) {
            val remoteId = root.key.removePrefix("cloud:")
            val remote = client.state.tasks.firstOrNull {
                it.id == remoteId && it.provider == "wechat"
            } ?: return false
            val ended = executionDao.executionFor(root.scope, root.key)?.ended == true
            if (remote.status == "done" || ended) {
                if (!reopenExecution(root)) {
                    message = client.state.privateError ?: "微信父任务尚未成功重新打开"
                    return false
                }
            }
        }
        val saved = executionDao.addSteps(root, listOf(stepTitle))
        if (!saved) message = "步骤未保存，请检查父任务状态或重复名称"
        return saved
    }

    suspend fun finishExecution(root: ExecutionRoot): Boolean {
        if (root.scope == "device") {
            val localId = root.key.removePrefix("local:").toLongOrNull() ?: return false
            if (!taskDao.trySetCompleted(localId, true)) {
                message = "父任务仍有未完成的前置任务或执行步骤"
                return false
            }
            return executionDao.finish(root)
        }
        if (root.scope.startsWith("wechat:")) {
            val remoteId = root.key.removePrefix("cloud:")
            val remote = client.state.tasks.firstOrNull {
                it.id == remoteId && it.provider == "wechat"
            } ?: return false
            val owner = client.state.ownerId ?: return false
            if (!client.setWechatTaskCompleted(remote.id, remote.revision, true, owner)) {
                message = client.state.privateError ?: "微信任务完成状态未获服务器确认"
                return false
            }
        }
        // For Chaoxing this only closes the user's personal execution steps.
        // It never writes an official submission or completion state.
        return executionDao.finish(root)
    }

    fun handleAction(action: JSONObject) {
        val actionId = action.optString("actionId")
        val params = action.optJSONObject("params") ?: JSONObject()
        when {
            actionId == "task.refresh" -> launchWork { client.refresh() }
            actionId == "task.filter" -> {
                val next = params.optString("filterKey")
                filterKey = next.takeIf { it in setOf("now", "pending", "all", "done", "verify", "week") } ?: "now"
            }
            actionId == "task.source" -> {
                val next = params.optString("source")
                sourceFilter = next.takeIf { it in setOf("all", "local", "chaoxing", "wechat", "cloud") } ?: "all"
            }
            actionId == "task.search" -> query = params.optString("query").take(80)
            actionId == "task.clear_search" -> query = ""
            actionId == "task.local.create" -> {
                val name = params.optString("title").trim()
                val due = params.optString("due").trim().ifBlank { null }
                if (name.isBlank()) message = "请填写任务名称"
                else if (name.length > 120) message = "任务名称不能超过 120 个字符"
                else if (due != null && runCatching { LocalDate.parse(due) }.isFailure) {
                    message = "日期格式应为 YYYY-MM-DD"
                } else launchWork {
                    taskDao.insert(LocalTask(title = name, source = TASK_SOURCE_LOCAL, due = due))
                    message = "已保存到本机任务"
                }
            }
            actionId == "task.local.toggle" -> {
                val id = params.optString("id").toLongOrNull()
                val completed = params.optString("completed") == "true"
                if (id == null) message = "任务编号无效"
                else launchWork {
                    if (!taskDao.trySetCompleted(id, completed)) {
                        message = "未完成前置任务或执行步骤，暂不能结束父任务"
                    }
                }
            }
            actionId == "task.wechat.toggle" -> pendingAction = JSONObject(action.toString())
            actionId == "task.execution.prompt" -> {
                val root = ExecutionRoot(params.optString("scope"), params.optString("rootKey"))
                if (root.scope.isBlank() || root.key.isBlank()) message = "任务身份无效"
                else {
                    stepDialogRoot = root
                    stepDraft = ""
                }
            }
            actionId == "task.step.toggle" -> {
                val root = ExecutionRoot(params.optString("scope"), params.optString("rootKey"))
                val stepId = params.optString("stepId").toLongOrNull()
                val completed = params.optString("completed") == "true"
                if (stepId == null) message = "步骤编号无效"
                else launchWork {
                    if (!executionDao.toggleStep(root, stepId, completed)) {
                        message = if (root.scope.startsWith("wechat:")) {
                            "步骤未保存；已结束的微信父任务需要先重新打开"
                        } else "步骤未保存，可能仍有未完成的前置步骤"
                    }
                }
            }
            actionId.startsWith("task.execution.add_step|") -> {
                val parts = actionId.split("|", limit = 3)
                if (parts.size != 3) message = "任务身份无效"
                else {
                    val root = ExecutionRoot(parts[1], parts[2])
                    val name = params.optString("title")
                    launchWork { addExecutionStep(root, name) }
                }
            }
            actionId.startsWith("task.execution.finish|") -> pendingAction = JSONObject(action.toString())
            actionId.startsWith("task.execution.reopen|") -> {
                val parts = actionId.split("|", limit = 3)
                if (parts.size != 3) message = "任务身份无效"
                else {
                    val root = ExecutionRoot(parts[1], parts[2])
                    launchWork {
                        if (!reopenExecution(root)) {
                            message = client.state.privateError ?: "父任务未能重新打开"
                        }
                    }
                }
            }
        }
    }

    fun confirmPending(action: JSONObject) {
        val actionId = action.optString("actionId")
        val params = action.optJSONObject("params") ?: JSONObject()
        when {
            actionId == "task.wechat.toggle" -> {
                val id = params.optString("id")
                val revision = params.optString("revision").toIntOrNull() ?: -1
                val completed = params.optString("completed") == "true"
                val owner = client.state.ownerId.orEmpty()
                launchWork {
                    if (!client.setWechatTaskCompleted(id, revision, completed, owner)) {
                        message = client.state.privateError ?: "微信任务状态未获服务器确认"
                    }
                }
            }
            actionId.startsWith("task.execution.finish|") -> {
                val parts = actionId.split("|", limit = 3)
                if (parts.size != 3) {
                    message = "任务身份无效"
                } else {
                    val root = ExecutionRoot(parts[1], parts[2])
                    launchWork {
                        if (!finishExecution(root)) {
                            if (message.isBlank()) message = "任务结束状态未能保存，请刷新后重试"
                        }
                    }
                }
            }
        }
    }

    GlassPageScaffold(
        title = title,
        subtitle = "$schoolName · 任务工作台",
        onBack = onBack
    ) { padding ->
        Column(
            Modifier.fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (message.isNotBlank()) {
                Text(message, color = MaterialTheme.colorScheme.error)
            }
            blocks.filter { it.getString("id") !in hidden }.forEach { block ->
                key(block.getString("id")) {
                    ServicePageBlock(block, enabled = !busy && !state.busy, onAction = ::handleAction)
                }
            }
        }
    }

    pendingAction?.let { action ->
        val actionId = action.optString("actionId")
        val finish = actionId.startsWith("task.execution.finish|")
        SystemDialog(
            scrollContent = true,
            onDismissRequest = { pendingAction = null },
            title = { Text(if (finish) "确认结束任务" else "更新微信任务") },
            content = {
                Text(
                    if (finish) "确认个人执行步骤已全部完成？本机任务将标记完成；微信任务会向校序云端请求更新；学习通官方状态不会被修改。"
                    else "确认更新本人微信任务的完成状态？此操作不会修改学校作业或成绩。"
                )
            },
            confirmButton = {
                SystemDialogButton(primary = true, onClick = {
                    val selected = action
                    pendingAction = null
                    confirmPending(selected)
                }) { Text("确认") }
            },
            dismissButton = {
                SystemDialogButton(onClick = { pendingAction = null }) { Text("取消") }
            }
        )
    }

    stepDialogRoot?.let { root ->
        SystemDialog(
            scrollContent = true,
            onDismissRequest = { stepDialogRoot = null; stepDraft = "" },
            title = { Text("添加执行步骤") },
            content = {
                OutlinedTextField(
                    value = stepDraft,
                    onValueChange = { stepDraft = it.take(120) },
                    label = { Text("步骤名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxSize()
                )
                Text("步骤只保存在本机；学校官方状态保持独立。", style = MaterialTheme.typography.bodySmall)
            },
            confirmButton = {
                SystemDialogButton(primary = true, onClick = {
                    val selected = root
                    val draft = stepDraft
                    stepDialogRoot = null
                    stepDraft = ""
                    launchWork { addExecutionStep(selected, draft) }
                }) { Text("保存步骤") }
            },
            dismissButton = {
                SystemDialogButton(onClick = { stepDialogRoot = null; stepDraft = "" }) { Text("取消") }
            }
        )
    }
}

internal fun buildTaskCenterPage(
    state: CloudUiState,
    rows: List<WorkspaceTask>,
    localTasks: List<LocalTask>,
    dependencies: List<LocalTaskDependency>,
    steps: List<TaskStep>,
    edges: List<TaskStepEdge>,
    executions: List<TaskExecution>,
    filterKey: String,
    sourceFilter: String,
    query: String,
    today: LocalDate,
    ownerId: String?,
    schoolBinding: String?
): JSONObject {
    val ready = readyWorkspaceTasks(rows, localTasks, dependencies)
    val base = when (filterKey) {
        "now" -> ready.filter {
            query.isBlank() || it.title.contains(query.trim(), true) || it.origin.contains(query.trim(), true)
        }
        else -> searchWorkspaceTasks(rows, filterKey, query, today)
    }
    val shown = facetWorkspaceTasks(base, sourceFilter, filterKey == "week", today)
    val blocks = JSONArray()

    val pendingCount = rows.count(::isActionableWorkspaceTask)
    val completedCount = rows.count(::considerCompleteInTaskList)
    val verifyCount = rows.count(::requiresChaoxingVerification)
    blocks.put(JSONObject()
        .put("id", "task_overview")
        .put("type", "metrics")
        .put("title", "任务概览")
        .put("columns", 4)
        .put("items", JSONArray()
            .put(taskMetric(ready.size.toString(), "现在可做"))
            .put(taskMetric(pendingCount.toString(), "待完成"))
            .put(taskMetric(completedCount.toString(), "已完成"))
            .put(taskMetric(verifyCount.toString(), "待核对"))))

    blocks.put(JSONObject()
        .put("id", "task_refresh")
        .put("type", "actions")
        .put("title", "数据操作")
        .put("items", JSONArray().put(taskActionItem("刷新任务与学校快照", "task.refresh"))))

    if (!state.tasksFresh && state.tasks.isNotEmpty()) {
        blocks.put(taskNoticeBlock("task_stale", "当前显示的是上次成功读取的云端任务快照；刷新完成前不把它当作最新状态。", "warning"))
    }
    if (!state.authenticated) {
        blocks.put(taskNoticeBlock("task_cloud_offline", "校序云端尚未连接。本机任务仍可正常使用；连接云端后才能读取和更新本人微信任务。", "warning"))
    }

    blocks.put(JSONObject()
        .put("id", "task_status_filters")
        .put("type", "actions")
        .put("title", "任务状态")
        .put("items", JSONArray().apply {
            listOf(
                "now" to "现在可做",
                "pending" to "待完成",
                "all" to "全部",
                "done" to "已完成",
                "verify" to "待核对",
                "week" to "本周个人日期"
            ).forEach { (key, label) ->
                put(taskActionItem((if (filterKey == key) "✓ " else "") + label, "task.filter",
                    JSONObject().put("filterKey", key)))
            }
        }))

    blocks.put(JSONObject()
        .put("id", "task_source_filters")
        .put("type", "actions")
        .put("title", "任务来源")
        .put("items", JSONArray().apply {
            listOf(
                "all" to "全部来源",
                "local" to "本机",
                "chaoxing" to "学习通",
                "wechat" to "微信",
                "cloud" to "其他云端"
            ).forEach { (key, label) ->
                put(taskActionItem((if (sourceFilter == key) "✓ " else "") + label, "task.source",
                    JSONObject().put("source", key)))
            }
        }))

    blocks.put(JSONObject()
        .put("id", "task_search")
        .put("type", "form")
        .put("title", "搜索任务")
        .put("fields", JSONArray().put(JSONObject()
            .put("id", "query")
            .put("type", "text")
            .put("label", "任务名称或来源")
            .put("placeholder", "搜索已读取的任务")
            .put("value", query)
            .put("required", false)))
        .put("submit", JSONObject().put("actionId", "task.search").put("label", "搜索")))
    if (query.isNotBlank()) {
        blocks.put(JSONObject()
            .put("id", "task_search_reset")
            .put("type", "actions")
            .put("items", JSONArray().put(taskActionItem("清除搜索", "task.clear_search"))))
    }

    blocks.put(JSONObject()
        .put("id", "task_local_create")
        .put("type", "form")
        .put("title", "新建本机任务")
        .put("fields", JSONArray()
            .put(JSONObject()
                .put("id", "title")
                .put("type", "text")
                .put("label", "任务名称")
                .put("placeholder", "例如：完成高数作业")
                .put("required", true))
            .put(JSONObject()
                .put("id", "due")
                .put("type", "text")
                .put("label", "个人日期（YYYY-MM-DD，可选）")
                .put("placeholder", "只记录本人填写的日期")
                .put("required", false)))
        .put("submit", JSONObject().put("actionId", "task.local.create").put("label", "添加到本机任务")))

    if (shown.isEmpty()) {
        blocks.put(taskNoticeBlock("task_empty", when {
            filterKey == "verify" -> "当前没有待核对的学习通任务。"
            query.isNotBlank() -> "没有匹配的任务；清除搜索或切换来源筛选可恢复列表。"
            filterKey == "now" -> "当前没有可立即执行的任务。检查等待前置任务或刷新数据。"
            else -> "当前筛选条件下没有任务。"
        }, "positive"))
    } else {
        val categoryOrder = listOf("local", "chaoxing", "wechat", "cloud")
        val categoryNames = mapOf(
            "local" to "本机任务",
            "chaoxing" to "学习通任务（只读核验）",
            "wechat" to "本人微信任务",
            "cloud" to "其他云端任务"
        )
        categoryOrder.forEach { category ->
            val group = shown.filter { it.category == category }
            if (group.isNotEmpty()) {
                val items = JSONArray()
                val relatedBlocks = mutableListOf<JSONObject>()
                group.forEachIndexed { index, row ->
                    val root = taskExecutionRoot(row, ownerId, schoolBinding)
                    val childSteps = root?.let { rootKey ->
                        steps.filter { it.scope == rootKey.scope && it.rootKey == rootKey.key }
                    }.orEmpty()
                    val doneSteps = childSteps.count { it.completed }
                    val due = trustedExecutionDate(row)
                    val status = when {
                        row.provider == "chaoxing" -> officialTaskStatusLabel(row.officialStatus) ?: "待核对"
                        row.done -> "已完成"
                        else -> "待完成"
                    }
                    val subtitle = buildList {
                        add(row.origin)
                        due?.let { add(if (row.storage == "local") "个人日期 $it" else "官方截止 $it") }
                        if (childSteps.isNotEmpty()) add("步骤 $doneSteps/${childSteps.size}")
                    }.joinToString(" · ")
                    val item = JSONObject()
                        .put("title", row.title)
                        .put("subtitle", subtitle)
                        .put("value", if (childSteps.isNotEmpty()) "步骤 $doneSteps/${childSteps.size}" else status)
                    if (childSteps.isEmpty()) {
                        when {
                            row.storage == "local" -> item.put("action", taskAction("task.local.toggle",
                                JSONObject().put("id", row.key.removePrefix("local:"))
                                    .put("completed", (!row.done).toString())))
                            row.provider == "wechat" && row.revision >= 0 && state.authenticated ->
                                item.put("action", taskAction("task.wechat.toggle",
                                    JSONObject().put("id", row.key.removePrefix("cloud:"))
                                        .put("revision", row.revision.toString())
                                        .put("completed", (!row.done).toString())))
                        }
                    }
                    items.put(item)

                    if (root != null && childSteps.isNotEmpty()) {
                        val readyIds = readyTaskSteps(childSteps, edges).mapTo(hashSetOf()) { it.id }
                        val stepItems = JSONArray()
                        childSteps.sortedWith(compareBy<TaskStep> { it.position }.thenBy { it.id }).forEach { step ->
                            val ready = step.id in readyIds
                            val stepItem = JSONObject()
                                .put("title", step.title)
                                .put("subtitle", if (step.completed) "个人步骤已完成" else if (ready) "现在可做" else "等待前置步骤")
                                .put("value", if (step.completed) "已完成" else if (ready) "可执行" else "等待")
                            if (step.completed || ready) {
                                stepItem.put("action", taskAction("task.step.toggle",
                                    JSONObject()
                                        .put("scope", root.scope)
                                        .put("rootKey", root.key)
                                        .put("stepId", step.id.toString())
                                        .put("completed", (!step.completed).toString())))
                            }
                            stepItems.put(stepItem)
                        }
                        relatedBlocks += taskListBlock("task_steps_${category}_$index", "执行步骤 · ${row.title}", stepItems)

                        val execution = executions.firstOrNull { it.scope == root.scope && it.rootKey == root.key }
                        if (childSteps.all { it.completed } && execution?.ended != true) {
                            relatedBlocks += JSONObject()
                                .put("id", "task_finish_${category}_$index")
                                .put("type", "actions")
                                .put("title", "步骤已全部完成")
                                .put("items", JSONArray().put(taskActionItem(
                                    if (row.provider == "chaoxing") "确认个人步骤结束（学校状态不变）" else "确认结束父任务",
                                    "task.execution.finish|${root.scope}|${root.key}"
                                ))))
                        }
                        if (root.scope.startsWith("wechat:") && execution?.ended == true) {
                            relatedBlocks += JSONObject()
                                .put("id", "task_reopen_${category}_$index")
                                .put("type", "actions")
                                .put("title", "微信父任务已结束")
                                .put("items", JSONArray().put(taskActionItem(
                                    "重新打开父任务",
                                    "task.execution.reopen|${root.scope}|${root.key}"
                                ))))
                        }
                    }
                    if (root != null && filterKey in setOf("now", "pending", "all")) {
                        relatedBlocks += JSONObject()
                            .put("id", "task_add_step_${category}_$index")
                            .put("type", "actions")
                            .put("title", "个人执行步骤")
                            .put("items", JSONArray().put(taskActionItem(
                                if (childSteps.isEmpty()) "分解为执行步骤" else "添加执行步骤",
                                "task.execution.prompt",
                                JSONObject().put("scope", root.scope).put("rootKey", root.key)
                            ))))
                    }
                }
                blocks.put(taskListBlock("task_group_$category", categoryNames[category] ?: category, items))
                relatedBlocks.forEach { blocks.put(it) }
            }
        }
    }

    return JSONObject()
        .put("pageId", BundledServiceProviders.TASK_CENTER_PAGE)
        .put("title", "任务中心")
        .put("blocks", blocks)
}

private fun taskMetric(value: String, label: String) = JSONObject()
    .put("value", value).put("label", label)

private fun taskActionItem(label: String, actionId: String, params: JSONObject = JSONObject()) = JSONObject()
    .put("label", label)
    .put("action", taskAction(actionId, params))

private fun taskAction(actionId: String, params: JSONObject = JSONObject()) =
    JSONObject().put("type", "action").put("actionId", actionId).put("params", params)

private fun taskListBlock(id: String, title: String, items: JSONArray) =
    JSONObject().put("id", id).put("type", "list").put("title", title).put("items", items)

private fun taskNoticeBlock(id: String, text: String, tone: String) =
    JSONObject().put("id", id).put("type", "notice").put("text", text).put("tone", tone)
