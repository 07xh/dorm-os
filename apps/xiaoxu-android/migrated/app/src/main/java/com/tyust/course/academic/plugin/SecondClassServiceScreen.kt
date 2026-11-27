package com.tyust.course.academic.plugin

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import com.tyust.course.ui.system.SystemDialog
import com.tyust.course.ui.system.SystemDialogButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tyust.course.ui.system.*
import io.github.x07xh.xiaoxu.NativeSchoolConnectActivity
import io.github.x07xh.xiaoxu.data.LocalTaskDatabase
import io.github.x07xh.xiaoxu.data.NativeSchoolRecord
import io.github.x07xh.xiaoxu.data.SecondClassAutoRefresh
import io.github.x07xh.xiaoxu.data.SecondClassPendingRead
import io.github.x07xh.xiaoxu.data.SecondClassReadOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal fun buildSecondClassServicePage(
    records: List<NativeSchoolRecord>,
    status: String,
    lastSuccessAt: Long,
    autoEnabled: Boolean,
    backgroundEnabled: Boolean,
    notice: String? = null
): JSONObject {
    val own = records.filter { it.provider == "second_class" }
    val binding = own.singleOrNull { it.kind == "account_binding" }
    val scores = own.filter { it.kind == "credits" }.sortedByDescending { it.title == "学校积分合计" }
    val activities = own.filter { it.kind == "activity" }.sortedByDescending { it.capturedAt }
    val activitySummary = own.singleOrNull { it.kind == "activity_summary" }
    val catalog = own.filter { it.kind == "catalog_activity" }.sortedBy { it.title }
    val emptyFilters = own.filter { it.kind == "catalog_empty" }.sortedBy { it.title }
    val capturedAt = maxOf(lastSuccessAt, scores.maxOfOrNull { it.capturedAt } ?: 0L)
    val timeLabel = if (capturedAt > 0) {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(capturedAt))
    } else "尚无成功保存记录"
    val statusLabel = when (status) {
        "reading" -> "正在读取学校只读数据…"
        "needs_reconnect" -> "学校会话已失效，请重新连接"
        "needs_confirmation" -> "账号绑定需要重新确认"
        "failed" -> "本次读取未完成，已保留上次数据"
        "updated" -> "本机只读快照已保存"
        else -> "等待首次连接或下一次自动读取"
    }
    val blocks = JSONArray()
    blocks.put(JSONObject()
        .put("id", "secondclass_overview")
        .put("type", "profile")
        .put("title", "第二课堂本机快照")
        .put("subtitle", statusLabel)
        .put("details", JSONArray()
            .put(if (binding != null) "本人账号绑定：已确认" else "本人账号绑定：尚未确认")
            .put("积分记录：${scores.size} 条")
            .put("本人报名活动：${activities.size} 条")
            .put("活动广场名称：${catalog.size} 条")
            .put("已核验空筛选：${emptyFilters.size}/3")
            .put("最近成功保存：${timeLabel}")
            .put("自动读取：${if (autoEnabled) "已启用（12 小时周期）" else "已关闭"}")
            .put("后台读取：${if (backgroundEnabled) "已启用" else "未启用"}"))
        .put("badge", "本机只读"))
    blocks.put(JSONObject()
        .put("id", "secondclass_actions")
        .put("type", "actions")
        .put("title", "数据操作")
        .put("items", JSONArray()
            .put(secondClassAction("连接／重新确认学校账号", "secondclass.connect"))
            .put(secondClassAction("立即同步学校只读数据", "secondclass.refresh"))
            .put(secondClassAction(if (autoEnabled) "关闭 12 小时自动读取" else "开启 12 小时自动读取", "secondclass.toggle_auto"))
            .put(secondClassAction(if (backgroundEnabled) "关闭后台读取" else "开启后台读取", "secondclass.toggle_background"))))
    notice?.takeIf { it.isNotBlank() }?.let {
        blocks.put(JSONObject().put("id", "secondclass_action_notice")
            .put("type", "notice").put("tone", "warning").put("text", it))
    }
    if (scores.isNotEmpty()) {
        blocks.put(JSONObject()
            .put("id", "secondclass_scores")
            .put("type", "list")
            .put("title", "积分记录")
            .put("items", JSONArray().apply {
                scores.take(50).forEach { row ->
                    put(JSONObject()
                        .put("title", row.title)
                        .put("subtitle", row.details.replace("\n", " · ").take(240))
                        .put("value", if (row.title == "学校积分合计") "学校汇总" else "学期记录"))
                }
            }))
    } else {
        blocks.put(JSONObject().put("id", "secondclass_scores_empty")
            .put("type", "notice").put("tone", "warning")
            .put("text", "尚无已保存的积分记录。先连接学校账号并确认本人身份。"))
    }
    if (activities.isNotEmpty() || activitySummary != null) {
        blocks.put(JSONObject()
            .put("id", "secondclass_activities")
            .put("type", "list")
            .put("title", "本人报名活动")
            .put("items", JSONArray().apply {
                activitySummary?.let {
                    put(JSONObject().put("title", it.title)
                        .put("subtitle", it.details.take(240)).put("value", "分页汇总"))
                }
                activities.take(60).forEach { row ->
                    put(JSONObject().put("title", row.title)
                        .put("subtitle", row.details.replace("\n", " · ").take(240))
                        .put("value", "本人记录"))
                }
            }))
    }
    if (catalog.isNotEmpty() || emptyFilters.isNotEmpty()) {
        blocks.put(JSONObject()
            .put("id", "secondclass_catalog")
            .put("type", "list")
            .put("title", "活动广场只读目录")
            .put("items", JSONArray().apply {
                emptyFilters.forEach { row ->
                    put(JSONObject().put("title", row.title)
                        .put("subtitle", "本次筛选已核验为空").put("value", "0 条"))
                }
                catalog.take(120).forEach { row ->
                    put(JSONObject().put("title", row.title)
                        .put("subtitle", row.details.replace("\n", " · ").take(180))
                        .put("value", "名称已核验"))
                }
            }))
    }
    return JSONObject().put("pageId", BundledServiceProviders.SECOND_CLASS_PAGE)
        .put("title", "第二课堂").put("blocks", blocks)
}

private fun secondClassAction(label: String, actionId: String) = JSONObject()
    .put("label", label)
    .put("action", JSONObject().put("type", "action").put("actionId", actionId))

@Composable
internal fun SecondClassServiceScreen(
    title: String,
    schoolName: String,
    accountScope: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext
    val dao = remember(app) { LocalTaskDatabase.open(app).nativeSchoolRecords() }
    val scope = rememberCoroutineScope()
    val layout = remember(context, accountScope) {
        ServicePageLayout(context, "$accountScope:${BundledServiceProviders.CLOUD_MESSAGES_ID}:secondclass")
    }
    var records by remember { mutableStateOf<List<NativeSchoolRecord>>(emptyList()) }
    var revision by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var pendingConfirmation by remember { mutableStateOf<SecondClassPendingRead?>(null) }
    val status = remember(revision) { SecondClassAutoRefresh.status(app) }
    val autoEnabled = remember(revision) { SecondClassAutoRefresh.enabled(app) }
    val backgroundEnabled = remember(revision) { SecondClassAutoRefresh.background(app) }
    val lastSuccess = remember(revision) { SecondClassAutoRefresh.lastSuccess(app) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val loginReturned = result.resultCode == Activity.RESULT_OK &&
            result.data?.getBooleanExtra(NativeSchoolConnectActivity.EXTRA_LOGIN_ONLY_RESULT, false) == true
        message = if (loginReturned)
            "学校官方会话已返回；请点击“立即同步学校只读数据”"
        else ""
        scope.launch {
            records = withContext(Dispatchers.IO) {
                dao.snapshot().filter { it.provider == "second_class" }
            }
            revision++
        }
    }

    fun reload() {
        scope.launch {
            records = withContext(Dispatchers.IO) {
                dao.snapshot().filter { it.provider == "second_class" }
            }
            revision++
        }
    }
    LaunchedEffect(Unit) {
        SecondClassAutoRefresh.reconcile(app)
        when (val outcome = SecondClassAutoRefresh.refreshIfDue(app)) {
            is SecondClassReadOutcome.Failed -> message = when (outcome.status) {
                "needs_reconnect" -> "学校会话已失效，请重新连接"
                "needs_confirmation" -> "账号绑定需要本人确认"
                else -> "自动读取未完成；旧快照保持不变"
            }
            else -> Unit
        }
        reload()
    }

    fun launchWork(work: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true
            message = ""
            try {
                work()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                message = "操作未完成；旧的本机快照保持不变"
            } finally {
                busy = false
                records = withContext(Dispatchers.IO) {
                    dao.snapshot().filter { it.provider == "second_class" }
                }
                revision++
            }
        }
    }

    fun handleAction(action: JSONObject) {
        when (action.optString("actionId")) {
            "secondclass.connect" -> launcher.launch(
                Intent(context, NativeSchoolConnectActivity::class.java)
                    .putExtra(NativeSchoolConnectActivity.EXTRA_PROVIDER, "second_class")
                    .putExtra(NativeSchoolConnectActivity.EXTRA_LOGIN_ONLY, true)
            )
            "secondclass.refresh" -> launchWork {
                when (val outcome = SecondClassAutoRefresh.refreshIfDue(context, userRequested = true)) {
                    is SecondClassReadOutcome.Saved ->
                        message = "第二课堂纯 HTTP 只读快照已更新：积分 ${outcome.scoreCount} 条、活动 ${outcome.activityCount} 条"
                    is SecondClassReadOutcome.NeedsConfirmation -> {
                        pendingConfirmation = outcome.pending
                        message = "读取完成，等待本人确认账号"
                    }
                    is SecondClassReadOutcome.Failed -> message = when (outcome.status) {
                        "needs_reconnect" -> "学校登录已失效，请先重新连接"
                        "needs_confirmation" -> "账号状态无法核验；旧快照保持不变"
                        else -> "读取失败；旧快照保持不变"
                    }
                    SecondClassReadOutcome.Skipped ->
                        message = "读取未启动：可能已有任务运行，或尚未到手动重试间隔"
                }
            }
            "secondclass.toggle_auto" -> {
                SecondClassAutoRefresh.configure(app, !SecondClassAutoRefresh.enabled(app),
                    SecondClassAutoRefresh.background(app))
                revision++
            }
            "secondclass.toggle_background" -> {
                SecondClassAutoRefresh.configure(app, SecondClassAutoRefresh.enabled(app),
                    !SecondClassAutoRefresh.background(app))
                revision++
            }
        }
    }

    val page = remember(records, status, lastSuccess, autoEnabled, backgroundEnabled, message) {
        buildSecondClassServicePage(records, status, lastSuccess, autoEnabled, backgroundEnabled, message)
    }
    val blocks = remember(page.toString()) { layout.order(page) }
    val hidden = remember(page.getString("pageId")) { layout.hidden(page.getString("pageId")) }

    GlassPageScaffold(title = title, subtitle = "$schoolName · 本机只读", onBack = onBack) { padding ->
        Column(
            Modifier.fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            if (busy) Text("正在读取…", color = MaterialTheme.colorScheme.secondary)
            blocks.filter { it.getString("id") !in hidden }.forEach { block ->
                key(block.getString("id")) {
                    ServicePageBlock(block, enabled = !busy, onAction = ::handleAction)
                }
            }
        }
    }
    pendingConfirmation?.let { pending ->
        SystemDialog(
            scrollContent = true,
            onDismissRequest = { pendingConfirmation = null },
            title = { Text("确认第二课堂本人账号") },
            content = {
                Text("本次通过固定 HTTPS 只读接口读取到：")
                Text("积分记录：${pending.scoreCount} 条")
                Text("本人报名活动：${pending.activityCount} 条")
                pending.aggregateScore?.let { Text("学校汇总素质分：$it") }
                Text("活动目录核验：${pending.catalogStatus}")
                Text("请确认当前学校官方页面属于你本人。确认后只替换本机第二课堂快照，不会向学校提交任何写入请求。")
            },
            confirmButton = {
                SystemDialogButton(primary = true, onClick = {
                    val selected = pending
                    pendingConfirmation = null
                    launchWork {
                        when (val outcome = SecondClassAutoRefresh.confirmPending(app, selected)) {
                            is SecondClassReadOutcome.Saved ->
                                message = "本人账号已确认并保存：积分 ${outcome.scoreCount} 条、活动 ${outcome.activityCount} 条"
                            is SecondClassReadOutcome.Failed ->
                                message = "确认未完成（${outcome.status}）；旧快照保持不变"
                            else -> message = "确认未完成；请重新读取"
                        }
                    }
                }) { Text("确认并保存") }
            },
            dismissButton = {
                SystemDialogButton(onClick = { pendingConfirmation = null }) { Text("取消") }
            }
        )
    }

}
