package com.tyust.course.academic.plugin

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tyust.course.ui.system.*
import io.github.x07xh.xiaoxu.data.AcademicNotice
import io.github.x07xh.xiaoxu.data.CloudClient
import io.github.x07xh.xiaoxu.data.CloudTask
import io.github.x07xh.xiaoxu.data.CloudUiState
import io.github.x07xh.xiaoxu.data.MESSAGE_FILTERS
import io.github.x07xh.xiaoxu.data.MSG_FILTER_ALL
import io.github.x07xh.xiaoxu.data.MSG_FILTER_EXAM
import io.github.x07xh.xiaoxu.data.MSG_FILTER_NOTICE
import io.github.x07xh.xiaoxu.data.MSG_FILTER_WECHAT
import io.github.x07xh.xiaoxu.data.MessagePhase
import io.github.x07xh.xiaoxu.data.deriveMessagePhase
import io.github.x07xh.xiaoxu.data.filterNotices
import io.github.x07xh.xiaoxu.data.noticeCategoryLabel
import io.github.x07xh.xiaoxu.data.pendingWechatLabel
import io.github.x07xh.xiaoxu.data.pendingWechatShortLabel
import io.github.x07xh.xiaoxu.data.WechatCandidate
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * The built-in NCWU message page is hosted by the upstream ServicePluginActivity
 * and rendered exclusively with the upstream ServicePageBlock components.
 * CloudClient remains the data/session owner; this file does not create a second
 * navigation, page container, or UI component system.
 */
@Composable
internal fun CloudServiceScreen(
    title: String,
    schoolName: String,
    accountScope: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val hostActivity = serviceHostActivity(context)
    if (hostActivity?.intent?.getStringExtra("pageId") == BundledServiceProviders.SECOND_CLASS_PAGE) {
        SecondClassServiceScreen(
            title = "第二课堂",
            schoolName = schoolName,
            accountScope = accountScope,
            onBack = onBack
        )
        return
    }
    val client = remember(context.applicationContext) { CloudClient(context.applicationContext) }
    val state = client.state
    val scope = rememberCoroutineScope()
    val layout = remember(context, accountScope) {
        ServicePageLayout(context, "$accountScope:${BundledServiceProviders.CLOUD_MESSAGES_ID}")
    }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var noticeFilter by remember { mutableStateOf(MSG_FILTER_ALL) }
    var noticeQuery by remember { mutableStateOf("") }
    var pendingAction by remember { mutableStateOf<JSONObject?>(null) }

    LaunchedEffect(client) { client.refreshOnForeground() }

    val page = remember(state, noticeFilter, noticeQuery) { buildCloudMessagePage(state, noticeFilter, noticeQuery) }
    val blocks = remember(page.toString()) { layout.order(page) }
    val hidden = remember(page.getString("pageId")) { layout.hidden(page.getString("pageId")) }

    fun launchWork(work: suspend () -> Unit) {
        if (busy || state.busy) return
        scope.launch {
            busy = true
            message = ""
            try {
                work()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                message = "操作未完成，请刷新后检查当前状态"
            } finally {
                busy = false
            }
        }
    }

    fun handleAction(action: JSONObject) {
        when (action.optString("actionId")) {
            "cloud.refresh" -> launchWork { client.refresh() }
            "cloud.load_second_class" -> launchWork { client.loadSecondClass() }
            "cloud.login" -> {
                val passphrase = action.optJSONObject("params")?.optString("passphrase").orEmpty()
                if (passphrase.isBlank()) message = "请输入校序云端口令"
                else launchWork {
                    val ok = client.signIn(passphrase)
                    if (!ok) message = client.state.authMessage
                }
            }
            "message.filter" -> {
                val next = action.optJSONObject("params")?.optString("filterKey").orEmpty()
                noticeFilter = next.takeIf { it in setOf(MSG_FILTER_ALL, MSG_FILTER_NOTICE, MSG_FILTER_EXAM, MSG_FILTER_WECHAT) } ?: MSG_FILTER_ALL
            }
            "message.search" -> noticeQuery = action.optJSONObject("params")?.optString("query").orEmpty().take(80)
            "message.clear_search" -> noticeQuery = ""
            "cloud.logout", "wechat.candidate.accept", "wechat.candidate.dismiss", "wechat.task.toggle" ->
                pendingAction = JSONObject(action.toString())
            "notice.open" -> {
                val url = action.optJSONObject("params")?.optString("url").orEmpty()
                val uri = runCatching { Uri.parse(url) }.getOrNull()
                if (uri?.scheme == "https" && uri.host == "www2.ncwu.edu.cn" &&
                    uri.path.orEmpty().startsWith("/jiaowu/info/")) {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                        .onFailure { message = "无法打开学校公告链接" }
                } else message = "公告链接未通过学校域名校验"
            }
        }
    }

    fun confirmPending(action: JSONObject) {
        val params = action.optJSONObject("params") ?: JSONObject()
        when (action.optString("actionId")) {
            "cloud.logout" -> launchWork { client.signOut() }
            "wechat.candidate.accept", "wechat.candidate.dismiss" -> {
                val id = params.optString("id")
                val decision = if (action.optString("actionId") == "wechat.candidate.accept") "accept" else "dismiss"
                launchWork {
                    val ok = client.reviewWechatCandidate(id, decision)
                    if (!ok) message = client.state.wechatError ?: "候选审核未确认，请刷新后核对"
                }
            }
            "wechat.task.toggle" -> {
                val id = params.optString("id")
                val revision = params.optString("revision").toIntOrNull() ?: -1
                val completed = params.optString("completed") == "true"
                val owner = client.state.ownerId.orEmpty()
                launchWork {
                    val ok = client.setWechatTaskCompleted(id, revision, completed, owner)
                    if (!ok) message = client.state.privateError ?: "任务状态未确认，请刷新后核对"
                }
            }
        }
    }

    GlassPageScaffold(
        title = title,
        subtitle = "$schoolName · 校园服务",
        onBack = onBack
    ) { padding ->
        Column(
            Modifier.fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LiquidButton(
                    { launchWork { client.refresh() } },
                    enabled = !busy && !state.busy,
                    modifier = Modifier.weight(1f),
                    style = LiquidButtonStyle.Tinted
                ) { Text(if (busy || state.busy) "刷新中…" else "刷新消息") }
                if (state.authenticated) LiquidButton(
                    { pendingAction = JSONObject().put("actionId", "cloud.logout") },
                    enabled = !busy && !state.busy,
                    horizontalPadding = 14.dp
                ) { Text("退出云端") }
            }
            if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error)
            blocks.filter { it.getString("id") !in hidden }.forEach { block ->
                key(block.getString("id")) {
                    ServicePageBlock(block, enabled = !busy && !state.busy, onAction = ::handleAction)
                }
            }
        }
    }

    pendingAction?.let { action ->
        val actionId = action.optString("actionId")
        val (dialogTitle, dialogBody, confirmLabel) = when (actionId) {
            "cloud.logout" -> Triple("退出校序云端", "将清除此设备保存的校序云端会话，并隐藏当前账号的云端任务与消息快照。", "退出")
            "wechat.candidate.accept" -> Triple("收录微信候选", "确认将这条候选加入本人校序任务？不会向学校平台提交任何内容。", "确认收录")
            "wechat.candidate.dismiss" -> Triple("忽略微信候选", "确认忽略这条候选？此操作只更新本人校序云端的审核状态。", "忽略")
            else -> Triple("更新微信任务状态", "确认更新本人任务的完成状态？此操作不会修改学校作业或成绩。", "确认更新")
        }
        SystemDialog(
            scrollContent = true,
            onDismissRequest = { pendingAction = null },
            title = { Text(dialogTitle) },
            content = { Text(dialogBody) },
            confirmButton = {
                SystemDialogButton(primary = true, onClick = {
                    val selected = action
                    pendingAction = null
                    confirmPending(selected)
                }) { Text(confirmLabel) }
            },
            dismissButton = {
                SystemDialogButton(onClick = { pendingAction = null }) { Text("取消") }
            }
        )
    }
}

internal fun buildCloudMessagePage(
    state: CloudUiState,
    filterKey: String = MSG_FILTER_ALL,
    query: String = ""
): JSONObject {
    val blocks = JSONArray()
    blocks.put(JSONObject()
        .put("id", "message_overview")
        .put("type", "profile")
        .put("title", "消息中心")
        .put("subtitle", if (state.authenticated) "校序云端已连接" else state.authMessage)
        .put("details", JSONArray()
            .put("学校公告、学习通收件箱、微信候选与本人任务统一展示")
            .put("学校数据只读；微信任务状态仅更新本人校序记录"))
        .put("badge", if (state.authenticated) "云端已连接" else "公开消息可查看"))

    if (!state.authenticated) {
        blocks.put(JSONObject()
            .put("id", "cloud_login")
            .put("type", "form")
            .put("title", "连接校序云端")
            .put("fields", JSONArray().put(JSONObject()
                .put("id", "passphrase")
                .put("type", "password")
                .put("label", "校序云端口令")
                .put("placeholder", "口令仅用于本次登录")
                .put("required", true)))
            .put("submit", JSONObject()
                .put("actionId", "cloud.login")
                .put("label", "连接云端")))
    } else {
        val wechatTasks = state.tasks.filter { it.provider == "wechat" }
        blocks.put(JSONObject()
            .put("id", "message_metrics")
            .put("type", "metrics")
            .put("title", "消息概览")
            .put("columns", 3)
            .put("items", JSONArray()
                .put(metric(state.notices.size.toString(), "学校公告"))
                .put(metric(pendingWechatShortLabel(state.pendingWechatCount, state.authenticated, state.wechatError), "待审核候选"))
                .put(metric(wechatTasks.count { it.status != "done" }.toString(), "微信待办"))))
        blocks.put(JSONObject()
            .put("id", "cloud_status")
            .put("type", "notice")
            .put("tone", if (state.tasksFresh && state.connectionsFresh) "positive" else "warning")
            .put("text", buildString {
                append(if (state.tasksFresh) "任务快照已核验" else "任务快照尚未完成刷新")
                append(" · ")
                append(if (state.connectionsFresh) "连接状态已核验" else "连接状态尚未核验")
                state.lastSyncAt?.let { append(" · 最近同步："); append(it) }
            }))
        blocks.put(JSONObject()
            .put("id", "cloud_actions")
            .put("type", "actions")
            .put("title", "数据操作")
            .put("items", JSONArray()
                .put(actionItem("刷新全部消息与任务", "cloud.refresh"))
                .put(actionItem("读取第二课堂只读快照", "cloud.load_second_class"))))
        state.privateError?.takeIf { it.isNotBlank() }?.let {
            blocks.put(noticeBlock("private_error", "云端数据提示：$it", "warning"))
        }
        state.wechatError?.takeIf { it.isNotBlank() }?.let {
            blocks.put(noticeBlock("wechat_error", "微信候选提示：$it", "warning"))
        }
    }

    state.publicError?.takeIf { it.isNotBlank() }?.let {
        blocks.put(noticeBlock("public_error", it, "warning"))
    }

    blocks.put(JSONObject()
        .put("id", "message_filters")
        .put("type", "actions")
        .put("title", "消息筛选")
        .put("items", JSONArray().apply {
            MESSAGE_FILTERS.forEach { option ->
                put(actionItem(
                    (if (filterKey == option.key) "✓ " else "") + option.label,
                    "message.filter",
                    JSONObject().put("filterKey", option.key)
                ))
            }
            put(actionItem(
                (if (filterKey == MSG_FILTER_WECHAT) "✓ " else "") + "微信待审核",
                "message.filter",
                JSONObject().put("filterKey", MSG_FILTER_WECHAT)
            ))
        }))

    if (filterKey != MSG_FILTER_WECHAT) {
        blocks.put(JSONObject()
            .put("id", "message_search")
            .put("type", "form")
            .put("title", "搜索公告")
            .put("fields", JSONArray().put(JSONObject()
                .put("id", "query")
                .put("type", "text")
                .put("label", "关键词")
                .put("placeholder", "标题、日期或分类")
                .put("value", query)
                .put("required", false)))
            .put("submit", JSONObject().put("actionId", "message.search").put("label", "搜索")))
        if (query.isNotBlank()) {
            blocks.put(JSONObject()
                .put("id", "message_search_reset")
                .put("type", "actions")
                .put("items", JSONArray().put(actionItem("清除搜索", "message.clear_search"))))
        }
    }

    if (filterKey != MSG_FILTER_WECHAT) {
        val filtered = filterNotices(state.notices, query, filterKey)
        if (filtered.isNotEmpty()) {
            val items = JSONArray()
            filtered.take(60).forEach { notice ->
                items.put(JSONObject()
                    .put("title", notice.title)
                    .put("subtitle", listOf(noticeCategoryLabel(notice.category), notice.dateLabel).filter { it.isNotBlank() }.joinToString(" · "))
                    .put("action", JSONObject()
                        .put("type", "action")
                        .put("actionId", "notice.open")
                        .put("params", JSONObject().put("url", notice.url))))
            }
            blocks.put(listBlock("school_notices", "学校公告", items))
        } else if (state.notices.isNotEmpty()) {
            blocks.put(noticeBlock("notice_filter_empty", "当前筛选条件下没有符合的学校公告。", "positive"))
        } else {
            when (val phase = deriveMessagePhase(state)) {
                MessagePhase.Loading -> blocks.put(noticeBlock("notice_phase", "正在读取官方学校公告…", "warning"))
                MessagePhase.NotConnected -> blocks.put(noticeBlock("notice_phase", "尚未取得官方公告读取证据；不能把未读取误报为零条。", "warning"))
                is MessagePhase.Failed -> Unit
                MessagePhase.Empty -> blocks.put(noticeBlock("notice_phase", "官方公告读取已核验，当前没有公告。", "positive"))
                is MessagePhase.Content -> Unit
            }
        }
    }

    if (state.authenticated && filterKey == MSG_FILTER_ALL && state.chaoxingInbox.isNotEmpty()) {
        val items = JSONArray()
        state.chaoxingInbox.take(50).forEach { row ->
            items.put(JSONObject()
                .put("title", row.title)
                .put("subtitle", listOf(row.course, row.sentAt, row.dueAt.orEmpty()).filter { it.isNotBlank() }.joinToString(" · "))
                .put("value", if (row.platformRead) "平台已读" else "未读"))
        }
        blocks.put(listBlock("chaoxing_inbox", "学习通收件箱", items))
    }

    if (state.authenticated && filterKey in setOf(MSG_FILTER_ALL, MSG_FILTER_WECHAT)) {
        blocks.put(noticeBlock(
            "wechat_count_semantics",
            pendingWechatLabel(state.pendingWechatCount, state.authenticated, state.wechatError),
            if (state.wechatError != null || state.pendingWechatCount == null) "warning" else "positive"
        ))
        val wechatTasks = state.tasks.filter { it.provider == "wechat" }.take(80)
        if (wechatTasks.isNotEmpty()) {
            val items = JSONArray()
            wechatTasks.forEach { task ->
                val item = JSONObject()
                    .put("title", task.title)
                    .put("subtitle", listOf(task.kind, task.due.orEmpty(), task.sourceCourse.orEmpty()).filter { it.isNotBlank() }.joinToString(" · "))
                    .put("value", if (task.status == "done") "已完成" else "待办")
                if (task.revision >= 0) item.put("action", JSONObject()
                    .put("type", "action")
                    .put("actionId", "wechat.task.toggle")
                    .put("params", JSONObject()
                        .put("id", task.id)
                        .put("revision", task.revision.toString())
                        .put("completed", (task.status != "done").toString())))
                items.put(item)
            }
            blocks.put(listBlock("wechat_tasks", "微信任务", items))
        }

        if (state.wechatCandidates.isNotEmpty()) {
            state.wechatCandidates.take(40).forEachIndexed { index, candidate ->
                blocks.put(candidateBlock(index, candidate))
                blocks.put(candidateActionsBlock(index, candidate))
            }
            blocks.put(noticeBlock("candidate_disclaimer",
                "候选内容仅供本人核对；只有手动确认收录后才会进入本人任务列表。", "warning"))
        } else {
            blocks.put(listBlock("wechat_candidates", "待审核微信候选", JSONArray()))
        }

        if (state.platforms.isNotEmpty()) {
            val items = JSONArray()
            state.platforms.forEach { platform ->
                items.put(JSONObject()
                    .put("title", platform.name)
                    .put("subtitle", listOf(platform.status, platform.sessionState, platform.authorization).filter { it.isNotBlank() }.joinToString(" · "))
                    .put("value", platform.lastSuccessAt ?: "尚无核验记录"))
            }
            blocks.put(listBlock("school_connections", "学校连接状态", items))
        }

        state.secondClass?.let { snapshot ->
            val details = JSONArray()
                .put("状态：" + snapshot.status)
                .put("数据来源：" + (snapshot.provenance ?: "未知"))
                .put("最近读取：" + (snapshot.lastSuccessAt ?: "未知"))
                .put("活动记录：" + snapshot.activities.size)
            snapshot.officialScore?.let {
                details.put("学校汇总质量分：" + (it.qualityScore?.toString() ?: "—"))
                details.put("学校汇总志愿时长：" + (it.volunteerHours?.toString() ?: "—"))
            }
            snapshot.personalScore?.let {
                details.put("本人导入质量分：" + (it.qualityScore?.toString() ?: "—"))
                details.put("本人导入志愿时长：" + (it.volunteerHours?.toString() ?: "—"))
            }
            blocks.put(JSONObject()
                .put("id", "second_class_snapshot")
                .put("type", "profile")
                .put("title", "第二课堂只读快照")
                .put("subtitle", "仅展示校序云端已保存的快照，不代表实时学校会话")
                .put("details", details)
                .put("badge", if (snapshot.provenance == "trusted_readonly") "可信只读来源" else "本人导入数据"))
        }
        state.secondClassError?.takeIf { it.isNotBlank() }?.let {
            blocks.put(noticeBlock("second_class_error", it, "warning"))
        }
    }

    return JSONObject()
        .put("pageId", BundledServiceProviders.CLOUD_MESSAGES_PAGE)
        .put("title", "消息中心")
        .put("blocks", blocks)
}

private fun candidateBlock(index: Int, candidate: WechatCandidate): JSONObject {
    val details = listOf(
        candidate.chatLabel.orEmpty(),
        candidate.kind,
        candidate.sentAt,
        candidate.dueLocalDate.orEmpty(),
        candidate.excerpt.orEmpty()
    ).filter { it.isNotBlank() }.joinToString(" · ")
    return listBlock(
        "candidate_$index",
        "待审核候选",
        JSONArray().put(JSONObject()
            .put("title", candidate.title)
            .put("subtitle", details)
            .put("value", "待核对"))
    )
}

private fun candidateActionsBlock(index: Int, candidate: WechatCandidate): JSONObject =
    JSONObject()
        .put("id", "candidate_actions_$index")
        .put("type", "actions")
        .put("title", "候选审核")
        .put("items", JSONArray()
            .put(actionItem("收录为本人任务", "wechat.candidate.accept", JSONObject().put("id", candidate.id)))
            .put(actionItem("忽略候选", "wechat.candidate.dismiss", JSONObject().put("id", candidate.id))))

private fun metric(value: String, label: String) = JSONObject()
    .put("value", value).put("label", label)

private fun actionItem(label: String, actionId: String, params: JSONObject = JSONObject()) = JSONObject()
    .put("label", label)
    .put("action", JSONObject().put("type", "action").put("actionId", actionId).put("params", params))

private fun listBlock(id: String, title: String, items: JSONArray) = JSONObject()
    .put("id", id).put("type", "list").put("title", title).put("items", items)

private fun noticeBlock(id: String, text: String, tone: String) = JSONObject()
    .put("id", id).put("type", "notice").put("text", text).put("tone", tone)

private fun serviceHostActivity(context: Context): Activity? {
    var current: Context? = context
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}
