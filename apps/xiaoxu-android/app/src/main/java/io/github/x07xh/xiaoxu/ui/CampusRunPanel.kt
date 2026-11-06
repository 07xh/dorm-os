package io.github.x07xh.xiaoxu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.x07xh.xiaoxu.data.CampusRunManualInput
import io.github.x07xh.xiaoxu.data.CampusRunSnapshot
import io.github.x07xh.xiaoxu.data.LocalCampusRunStore
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun formatKm(value: Long?): String =
    value?.let { String.format(Locale.ROOT, "%.2f km", it.toDouble() / 1_000.0) } ?: "—"

private fun inputKm(value: Long?): String =
    value?.let { String.format(Locale.ROOT, "%.2f", it.toDouble() / 1_000.0) } ?: ""

@Composable
private fun CampusRunMeasure(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CampusRunKmInput(
    label: String, value: String, onValue: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValue(it.take(20)) },
        label = { Text(label) },
        suffix = { Text("km") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * Real, usable local summary; never show the user's screenshots as bundled
 * data or confuse manual values with trusted upstream synchronization.
 *
 * Android Keystore storage is separate from credentials/cloud DB/Room tasks.
 */
@Composable
fun CampusRunPanel() {
    val context = LocalContext.current
    val store = remember(context) { LocalCampusRunStore(context) }
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf<CampusRunSnapshot?>(null) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    var term by rememberSaveable { mutableStateOf("") }
    var target by rememberSaveable { mutableStateOf("") }
    var credited by rememberSaveable { mutableStateOf("") }
    var semester by rememberSaveable { mutableStateOf("") }
    var dashboard by rememberSaveable { mutableStateOf("") }
    var weekly by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(store) {
        snapshot = withContext(Dispatchers.IO) {
            store.load(System.currentTimeMillis())
        }
        loading = false
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("校园跑 · 运动世界校园", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold)
            Text("当前尚未完成本人官方账号的自动读取授权。这里保存的是你从官方 App 页面亲自核对后录入的汇总数据；仅存本机，非实时官方同步。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            when {
                loading -> Text("正在读取本机记录…")
                snapshot == null -> Text("暂无本机校园跑记录。不会展示示例里程或假进度。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> {
                    val value = snapshot!!
                    Text(value.termLabel ?: "学期未注明",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium)
                    Text("本人手动录入 · 非实时 · " +
                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                            .format(Date(value.capturedAtEpochMillis)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    CampusRunMeasure("学期目标", formatKm(value.targetMeters))
                    CampusRunMeasure("计入成绩里程", formatKm(value.effectiveMeters))
                    CampusRunMeasure("学期累计总里程", formatKm(value.rawMeters))
                    CampusRunMeasure("首页累计跑量（区间未核验）",
                        formatKm(value.dashboardAccumulatedMeters))
                    CampusRunMeasure("本周跑量", formatKm(value.weeklyMeters))
                    val percent = value.progressPercent()
                    if (percent != null) {
                        LinearProgressIndicator(
                            progress = { (percent / 100.0).toFloat() },
                            modifier = Modifier.fillMaxWidth()
                        )
                        CampusRunMeasure("完成比例", String.format(
                            Locale.ROOT, "%.1f%%", percent))
                        CampusRunMeasure("距学期目标尚需计入",
                            formatKm(value.remainingMeters()))
                    } else {
                        Text("目标或计入成绩里程未知，不计算完成比例。",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Text("历史记录和跑步次数尚未导入；不能以汇总里程倒推有效跑步次数。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (!editing && !loading) {
                Button(
                    enabled = !busy,
                    onClick = {
                        val previous = snapshot
                        term = previous?.termLabel.orEmpty()
                        target = inputKm(previous?.targetMeters)
                        credited = inputKm(previous?.effectiveMeters)
                        semester = inputKm(previous?.rawMeters)
                        dashboard = inputKm(previous?.dashboardAccumulatedMeters)
                        weekly = inputKm(previous?.weeklyMeters)
                        editing = true
                        confirmDelete = false
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (snapshot == null) "从官方页面手动录入" else "更新本机记录") }
            }

            if (editing) {
                HorizontalDivider()
                Text("只填写官方页面确实显示的数字，未知字段留空；不要填个人 ID 或登录凭据。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = term,
                    onValueChange = { term = it.take(80) },
                    label = { Text("学期名称（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                CampusRunKmInput("学期目标", target) { target = it }
                CampusRunKmInput("计入成绩里程", credited) { credited = it }
                CampusRunKmInput("学期累计总里程", semester) { semester = it }
                CampusRunKmInput("首页累计跑量", dashboard) { dashboard = it }
                CampusRunKmInput("本周跑量", weekly) { weekly = it }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(enabled = !busy, onClick = {
                        busy = true
                        error = null
                        val input = CampusRunManualInput(term, target, credited,
                            semester, dashboard, weekly)
                        scope.launch {
                            try {
                                snapshot = withContext(Dispatchers.IO) {
                                    store.save(input, System.currentTimeMillis())
                                }
                                editing = false
                            } catch (_: Exception) {
                                error = "保存失败。请检查公里数最多两位小数、计入成绩里程不大于学期总里程，并至少填写一个数值。旧记录未修改。"
                            } finally { busy = false }
                        }
                    }) { Text(if (busy) "保存中…" else "加密保存到本机") }
                    TextButton(enabled = !busy, onClick = { editing = false; error = null }) {
                        Text("取消")
                    }
                }
            }

            if (snapshot != null && !editing) {
                if (!confirmDelete) {
                    TextButton(enabled = !busy, onClick = { confirmDelete = true }) {
                        Text("清除本机校园跑记录")
                    }
                } else {
                    Text("确定删除这份本机汇总？其他校序任务与课表不受影响。",
                        style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(enabled = !busy, onClick = { confirmDelete = false }) {
                            Text("取消")
                        }
                        TextButton(enabled = !busy, onClick = {
                            busy = true
                            error = null
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) { store.clear() }
                                    snapshot = null
                                    confirmDelete = false
                                } catch (_: Exception) {
                                    error = "删除失败，请重试。"
                                } finally { busy = false }
                            }
                        }) { Text("确认清除") }
                    }
                }
            }
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
