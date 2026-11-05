package io.github.x07xh.xiaoxu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.x07xh.xiaoxu.data.LocalTaskDatabase
import io.github.x07xh.xiaoxu.data.NativeSchoolRecord

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
    val records by dao.observe().collectAsState(initial = emptyList())
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("手机本机连接", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold)
        Text("在手机上登录学校或学习通官网，再读取当前已显示的列表；数据只保存在本机，不经过校序 Cloudflare。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        for ((provider, name) in listOf(
            "chaoxing" to "学习通", "ncwu" to "青果教务",
            "second_class" to "第二课堂")) {
            val list = records.filter { it.provider == provider }
            val justImported = recentImport?.takeIf { it.provider == provider }
            var expanded by rememberSaveable(provider, recentImport?.serial) {
                mutableStateOf(false)
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(name, fontWeight = FontWeight.SemiBold)
                        Text(if (provider == "ncwu") "专用课表导入 · 课表页查看"
                             else if (list.isEmpty()) "暂无本机读取记录"
                             else "已保存 " + list.size + " 条",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick = { openLocal(provider) }) {
                        Text(if (provider == "ncwu") "本机导入青果课表"
                             else if (list.isEmpty()) "手机登录并读取"
                             else "打开官网／更新")
                    }
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
                    list.take(if (expanded) list.size else if (justImported != null) 12 else 3)
                        .forEach { item ->
                            Text(kindLabel(item.kind) + " · " + item.title,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall)
                            if (item.details.isNotBlank()) Text(
                                item.details,
                                maxLines = if (expanded) 8 else 2,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    if (list.size > (if (justImported != null) 12 else 3)) {
                        TextButton(onClick = { expanded = !expanded }) {
                            Text(if (expanded) "收起记录" else "查看本机全部 ${list.size} 条记录")
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
    val all by dao.observe().collectAsState(initial = emptyList())
    val rows = all.filter { it.kind in types }
    if (rows.isEmpty()) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("手机本机读取", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold)
            Text("来自本人在官方网页明确触发的只读导入；不是实时官方接口同步。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall)
            rows.take(12).forEach { record: NativeSchoolRecord ->
                Text(schoolLabel(record.provider) + " · " + kindLabel(record.kind) +
                    " · " + record.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium)
                if (record.details.isNotBlank())
                    Text(record.details, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall)
            }
            if (rows.size > 12) {
                Text("还有 " + (rows.size - 12) + " 条本机记录，请到「我的」查看各平台。",
                    style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
