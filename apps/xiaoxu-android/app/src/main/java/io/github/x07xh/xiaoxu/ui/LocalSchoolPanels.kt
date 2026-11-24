package io.github.x07xh.xiaoxu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.x07xh.xiaoxu.data.LocalTaskDatabase
import io.github.x07xh.xiaoxu.data.NativeSchoolRecord
import io.github.x07xh.xiaoxu.data.SecondClassAutoRefresh
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private fun schoolLabel(provider: String) = when (provider) {
    "ncwu" -> "青果教务"
    "chaoxing" -> "学习通"
    "second_class" -> "第二课堂"
    else -> "未知来源"
}
private fun kindLabel(kind: String) = when (kind) {
    "schedule" -> "课表"
    "course" -> "课程"
    "homework" -> "作业"
    "study_task" -> "学习计划"
    "exam_notice" -> "考试通知"
    "activity" -> "活动"
    "credits" -> "积分／学时"
    else -> "记录"
}

/** A bounded, data-free Activity result after Room commit (not official verification). */
data class LocalSchoolImportReceipt(
    val provider: String, val kind: String, val savedCount: Int, val serial: Long
)

/**
 * Explicit mobile-only school authorization and local snapshots.
 * This is independent of Cloudflare, school passwords and the cloud task ledger.
 */
@Composable
fun LocalSchoolConnectionPanel(
    openLocal: (String) -> Unit,
    recentImport: LocalSchoolImportReceipt? = null
) {
    val context = LocalContext.current
    val dao = remember(context) { LocalTaskDatabase.open(context).nativeSchoolRecords() }
    val records by rememberLocalRows(dao) { dao.observe() }
    val scope = rememberCoroutineScope()
    var syncingSecondClass by remember { mutableStateOf(false) }
    var secondClassSyncMessage by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for ((provider, name) in listOf(
            "chaoxing" to "学习通", "ncwu" to "青果教务",
            "second_class" to "第二课堂")) {
            val list = records.filter { it.provider == provider && it.kind != "account_binding" }
            val justImported = recentImport?.takeIf { it.provider == provider }
            // Always put the just-confirmed source/type first, even if another
            // course or activity was saved more recently.
            val prioritized = if (justImported == null) list else
                list.filter { it.kind == justImported.kind } +
                    list.filter { it.kind != justImported.kind }
            var platformOpen by rememberSaveable(provider, recentImport?.serial) {
                mutableStateOf(justImported != null)
            }
            var allRecordsExpanded by rememberSaveable(provider, recentImport?.serial) {
                mutableStateOf(false)
            }
            XiaoxuExpandableGlassRow(
                title = name,
                subtitle = if (provider == "ncwu") "课表在课表页查看"
                    else if (list.isEmpty()) "暂无本机读取记录"
                    else "已保存 ${list.size} 条本机记录",
                expanded = platformOpen,
                onToggle = { platformOpen = !platformOpen }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(when (provider) {
                        "chaoxing" -> "通知、学习计划、课程作业 · 共用本机读取"
                        "ncwu" -> "个人课表 · 本机导入后直接在课表页查看"
                        else -> "官网本人积分、六类明细与分学期记录 · 保存后离线可查看"
                    }, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        onClick = { openLocal(provider) }
                    ) {
                        Text(when (provider) {
                            "chaoxing" -> "连接学习通／刷新全部任务"
                            "ncwu" -> "在 App 中导入青果课表"
                            else -> "打开第二课堂"
                        })
                    }
                    if (provider == "second_class") {
                        val snapshotAt = list.filter { it.kind == "credits" }
                            .map { it.capturedAt }.distinct().singleOrNull()
                        SecondClassAutoReadControls(snapshotAt)
                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !syncingSecondClass,
                            onClick = {
                                if (snapshotAt == null) openLocal(provider)
                                else {
                                    syncingSecondClass = true
                                    secondClassSyncMessage = null
                                    scope.launch {
                                        try {
                                            val updated = SecondClassAutoRefresh.refreshIfDue(
                                                context.applicationContext, userRequested = true)
                                            secondClassSyncMessage = if (updated) "已同步本人积分与活动"
                                                else when (SecondClassAutoRefresh.status(context.applicationContext)) {
                                                    "needs_reconnect" -> "学校会话已失效，请打开第二课堂重新授权"
                                                    "needs_confirmation" -> "账号已变化，请打开第二课堂重新确认"
                                                    "reading" -> "已有同步任务正在进行"
                                                    else -> "本次未更新，已保留上次数据"
                                                }
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (_: Exception) {
                                            secondClassSyncMessage = "同步失败，已保留上次数据"
                                        } finally {
                                            syncingSecondClass = false
                                        }
                                    }
                                }
                            }
                        ) { Text(if (syncingSecondClass) "同步中…" else if (snapshotAt == null) "前往首次连接" else "立即同步") }
                        secondClassSyncMessage?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                    }
                    if (provider == "second_class") Text(
                        "首次正常登录；学校会话有效时无需重复登录，过期后重新授权。下方为上次保存结果，不代表当前仍在线。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (justImported != null) {
                        val stored = list.filter { it.kind == justImported.kind }
                        Text(
                            if (stored.size == justImported.savedCount)
                                "本机保存完成：${kindLabel(justImported.kind)} " +
                                    "${stored.size} 条，已从本机数据库重新读取"
                            else
                                "保存回执：${justImported.savedCount} 条；" +
                                    "正在核对本机数据库显示的记录",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (stored.isNotEmpty()) {
                            Text(
                                "来源：${stored.first().sourceHost} · " +
                                    "仅为本次官方网页可见部分，请逐项比对名称和状态",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    prioritized.take(if (allRecordsExpanded) prioritized.size
                        else if (justImported != null) 12 else 3)
                        .forEach { item ->
                            Text(kindLabel(item.kind) + " · " + item.title,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall)
                            if (item.details.isNotBlank()) Text(
                                item.details,
                                maxLines = if (allRecordsExpanded || provider == "second_class" && item.kind == "credits") 8 else 2,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "本机采集：${DateFormat.getDateTimeInstance(
                                    DateFormat.SHORT, DateFormat.SHORT
                                ).format(Date(item.capturedAt))} · 不代表当前官网状态",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    if (list.size > (if (justImported != null) 12 else 3)) {
                        TextButton(onClick = { allRecordsExpanded = !allRecordsExpanded }) {
                            Text(if (allRecordsExpanded) "收起记录" else "查看本机全部 ${list.size} 条记录")
                        }
                    }
                }
            }
        }
        Text("本机读取成功 ≠ 已核验学校官方提交状态。首次须在本人账号页面逐项对照。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun LocalSchoolRecordSection(types: Set<String>) {
    val context = LocalContext.current
    val dao = remember(context) { LocalTaskDatabase.open(context).nativeSchoolRecords() }
    val all by rememberLocalRows(dao) { dao.observe() }
    val rows = all.filter { it.kind in types }
    if (rows.isEmpty()) return
    var expanded by rememberSaveable(types.sorted().joinToString("|")) {
        mutableStateOf(false)
    }
    XiaoxuExpandableGlassRow(
        title = "手机本机读取",
        subtitle = "已保存 ${rows.size} 条本机记录",
        expanded = expanded,
        onToggle = { expanded = !expanded }
    ) {
        Text("来自本人在官方网页明确触发的只读导入；不是实时官方接口同步。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall)
        rows.take(12).forEach { record: NativeSchoolRecord ->
            Text(schoolLabel(record.provider) + " · " + kindLabel(record.kind) +
                " · " + record.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium)
            if (record.details.isNotBlank()) {
                Text(record.details, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall)
            }
        }
        if (rows.size > 12) {
            Text("还有 " + (rows.size - 12) + " 条本机记录，请到「我的」查看各平台。",
                style = MaterialTheme.typography.labelSmall)
        }
    }
}
