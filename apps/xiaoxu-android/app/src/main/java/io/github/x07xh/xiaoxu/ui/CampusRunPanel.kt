package io.github.x07xh.xiaoxu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
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
import io.github.x07xh.xiaoxu.data.CampusRunImportUnit
import io.github.x07xh.xiaoxu.data.parseCampusRunToolkitJson
import io.github.x07xh.xiaoxu.data.LocalCampusRunStore
import io.github.x07xh.xiaoxu.data.CloudClient
import io.github.x07xh.xiaoxu.data.campusRunVisibleScope
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Keep two decimals for common entries, but never round away exact meters
// from imported official-account results (e.g. 10,003 m = 10.003 km).
private fun kilometerPrecision(value: Long): Int = if (value % 10L == 0L) 2 else 3

private fun formatKm(value: Long?): String =
    value?.let {
        String.format(Locale.ROOT, "%.${kilometerPrecision(it)}f km", it.toDouble() / 1_000.0)
    } ?: "—"

private fun inputKm(value: Long?): String =
    value?.let {
        String.format(Locale.ROOT, "%.${kilometerPrecision(it)}f", it.toDouble() / 1_000.0)
    } ?: ""

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
fun CampusRunPanel(client: CloudClient) {
    // An unverified owner token is never equivalent to the unlocked local
    // profile. In particular, sign-out and switching must hide old data
    // immediately, even if the auth refresh has not finished.
    val scope = campusRunVisibleScope(
        authenticated = client.state.authenticated,
        ownerSessionBinding = client.ownerSessionBinding()
    )
    if (scope == null) {
        XiaoxuCard(modifier = Modifier.fillMaxWidth(), contentPadding = 0.dp) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("校园跑 · 本机记录已锁定",
                    style = MaterialTheme.typography.titleMedium)
                Text("校序账号会话尚未核验或已经失效。重新验证账号后才能查看对应的本机记录，避免切换账号时展示上一位使用者的数据。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    // key() resets every remembered snapshot/input/edit state when owner
    // scope changes. A frame from the old session cannot be re-used.
    key(scope) { CampusRunScopedPanel(scope) }
}

@Composable
private fun CampusRunScopedPanel(sessionScope: String) {
    val context = LocalContext.current
    val store = remember(context, sessionScope) { LocalCampusRunStore(context, sessionScope) }
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf<CampusRunSnapshot?>(null) }
    var loading by remember { mutableStateOf(true) }
    var encryptedUnreadable by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var showJsonImport by rememberSaveable { mutableStateOf(false) }
    var importUnit by remember { mutableStateOf<CampusRunImportUnit?>(null) }
    var importPreview by remember { mutableStateOf<CampusRunSnapshot?>(null) }

    var term by rememberSaveable { mutableStateOf("") }
    var target by rememberSaveable { mutableStateOf("") }
    var credited by rememberSaveable { mutableStateOf("") }
    var semester by rememberSaveable { mutableStateOf("") }
    var dashboard by rememberSaveable { mutableStateOf("") }
    var weekly by rememberSaveable { mutableStateOf("") }
    var totalRuns by rememberSaveable { mutableStateOf("") }
    var validRuns by rememberSaveable { mutableStateOf("") }

    val importPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val selectedUnit = importUnit
        if (uri != null && selectedUnit != null) {
            scope.launch {
                busy = true
                error = null
                importPreview = null
                try {
                    importPreview = withContext(Dispatchers.IO) {
                        val text = context.contentResolver.openInputStream(uri)?.use { stream ->
                            val bytes = java.io.ByteArrayOutputStream()
                            val chunk = ByteArray(8_192)
                            var total = 0
                            while (true) {
                                val read = stream.read(chunk)
                                if (read < 0) break
                                total += read
                                require(total <= 256_000) { "import_file_too_large" }
                                bytes.write(chunk, 0, read)
                            }
                            bytes.toString("UTF-8")
                        } ?: throw IllegalArgumentException("unable_to_open_import")
                        parseCampusRunToolkitJson(text, selectedUnit, System.currentTimeMillis())
                    }
                } catch (_: Exception) {
                    error = "导入解析失败：请选择工具包的 score/all JSON 结果，并核对单位及接口返回状态。原本机记录没有改变。"
                } finally {
                    busy = false
                }
            }
        }
    }

    LaunchedEffect(store) {
        try {
            val loaded = withContext(Dispatchers.IO) {
                store.load(System.currentTimeMillis()) to store.hasEncryptedRecord()
            }
            snapshot = loaded.first
            encryptedUnreadable = loaded.first == null && loaded.second
            if (encryptedUnreadable) {
                editing = false
                showJsonImport = false
                importPreview = null
                confirmDelete = false
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            snapshot = null
            encryptedUnreadable = true
            editing = false
            showJsonImport = false
            importPreview = null
            confirmDelete = false
        } finally {
            loading = false
        }
    }

    XiaoxuCard(modifier = Modifier.fillMaxWidth(), contentPadding = 0.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("校园跑 · 运动世界校园", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold)
            Text("运动世界自动读取尚未授权接通。这里展示你亲自录入或从本机 JSON 确认导入的学期数据；只存在本机，不代表实时官方同步。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            when {
                loading -> Text("正在读取本机记录…")
                encryptedUnreadable -> Text(
                    "本机有加密校园跑记录，但目前无法解密。原记录仍然保留；建议解锁设备后重试，不会自动覆盖或删除。",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                snapshot == null -> Text("暂无本机校园跑记录。不会展示示例里程或假进度。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> {
                    val value = snapshot!!
                    Text(value.termLabel ?: "学期未注明",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium)
                    Text("本机录入或文件导入 · 非实时 · " +
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
                    if (value.totalRunCount != null) {
                        CampusRunMeasure("学期跑步次数", "${value.totalRunCount} 次")
                    }
                    if (value.effectiveRunCount != null) {
                        CampusRunMeasure("学期有效次数", "${value.effectiveRunCount} 次")
                    }
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
                    Text("跑步次数仅采用明确录入或 JSON 学期汇总字段；完整历史记录尚未导入，不能据此确认历史分页完整。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (!editing && !loading && !encryptedUnreadable) {
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
                        totalRuns = previous?.totalRunCount?.toString().orEmpty()
                        validRuns = previous?.effectiveRunCount?.toString().orEmpty()
                        editing = true
                        showJsonImport = false
                        importPreview = null
                        confirmDelete = false
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (snapshot == null) "从官方页面手动录入" else "更新本机记录") }
            }

            if (editing && !encryptedUnreadable) {
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
                OutlinedTextField(
                    value = totalRuns,
                    onValueChange = { totalRuns = it.take(7) },
                    label = { Text("学期跑步次数（可选）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = validRuns,
                    onValueChange = { validRuns = it.take(7) },
                    label = { Text("学期有效次数（可选）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(enabled = !busy, onClick = {
                        busy = true
                        error = null
                        val input = CampusRunManualInput(term, target, credited,
                            semester, dashboard, weekly, totalRuns, validRuns)
                        scope.launch {
                            try {
                                snapshot = withContext(Dispatchers.IO) {
                                    store.save(input, System.currentTimeMillis())
                                }
                                editing = false
                            } catch (_: Exception) {
                                error = "保存失败。请检查公里数最多三位小数、计入成绩里程不大于学期总里程，并至少填写一个数值。旧记录未修改。"
                            } finally { busy = false }
                        }
                    }) { Text(if (busy) "保存中…" else "加密保存到本机") }
                    TextButton(enabled = !busy, onClick = { editing = false; error = null }) {
                        Text("取消")
                    }
                }
            }

            // Local file import is intentionally separate from the official
            // read-only source: it never upgrades USER_IMPORT to TRUSTED_READONLY.
            // It accepts only a reviewed CLI score/all shape with known units.
            if (!editing && !loading && !encryptedUnreadable) {
                TextButton(enabled = !busy, onClick = {
                    showJsonImport = !showJsonImport
                    importPreview = null
                    error = null
                }) {
                    Text(if (showJsonImport) "收起 JSON 导入" else "导入工具包 JSON（仅本机）")
                }
            }
            if (showJsonImport && !editing && !encryptedUnreadable) {
                HorizontalDivider()
                Text("支持已保存的 json_client.py score/all 返回文件，只提取学期总里程、有效里程和对应次数。文件不上传，也不会读取运动世界账号或凭据。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("归档工具包 score_cli.py 标注的单位是米；如输出来自其他版本，请核对后再选。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("原始距离单位（需要确认）",
                    style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = !busy, onClick = {
                        importUnit = CampusRunImportUnit.KILOMETERS
                        importPreview = null
                    }) { Text(if (importUnit == CampusRunImportUnit.KILOMETERS) "✓ 公里" else "公里") }
                    OutlinedButton(enabled = !busy, onClick = {
                        importUnit = CampusRunImportUnit.METERS
                        importPreview = null
                    }) { Text(if (importUnit == CampusRunImportUnit.METERS) "✓ 米" else "米") }
                }
                Button(enabled = !busy, onClick = {
                    if (importUnit == null) {
                        error = "请先确认工具包输出的距离单位。"
                    } else {
                        // Some Android providers expose JSON as octet-stream; accept local
                        // files by picker and enforce strict size/schema validation later.
                        importPicker.launch(arrayOf("*/*"))
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("选择本机 JSON 文件") }

                importPreview?.let { candidate ->
                    Text("导入预览 · 不代表官方实时同步",
                        style = MaterialTheme.typography.titleMedium)
                    CampusRunMeasure("学期", candidate.termLabel ?: "未注明")
                    CampusRunMeasure("学期累计总里程", formatKm(candidate.rawMeters))
                    CampusRunMeasure("计入成绩里程", formatKm(candidate.effectiveMeters))
                    if (candidate.totalRunCount != null) {
                        CampusRunMeasure("学期跑步次数", "${candidate.totalRunCount} 次")
                    }
                    if (candidate.effectiveRunCount != null) {
                        CampusRunMeasure("学期有效次数", "${candidate.effectiveRunCount} 次")
                    }
                    Text("仅导入工具包返回的明确字段；目标、历史详情和官方计分规则未验证，不猜测或补零。确认后会加密保存在当前校序账号对应的本机空间。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (snapshot != null) {
                        Text(
                            "注意：当前仅保留一份本机汇总。本次确认会替换原记录；此前手动录入的目标、首页/本周里程及其他学期信息不会自动合并。若需要保留，请取消导入。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = !busy, onClick = {
                            busy = true
                            error = null
                            scope.launch {
                                try {
                                    snapshot = withContext(Dispatchers.IO) {
                                        store.saveSnapshot(candidate, System.currentTimeMillis())
                                    }
                                    importPreview = null
                                    showJsonImport = false
                                    confirmDelete = false
                                } catch (_: Exception) {
                                    error = "导入保存失败，原本机记录保持不变。"
                                } finally {
                                    busy = false
                                }
                            }
                        }) { Text(if (snapshot == null)
                            "确认并加密保存" else "确认覆盖本机记录") }
                        TextButton(enabled = !busy, onClick = {
                            importPreview = null
                        }) { Text("取消预览") }
                    }
                }
            }

            if (encryptedUnreadable && !loading && !busy) {
                TextButton(onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            val loaded = withContext(Dispatchers.IO) {
                                store.load(System.currentTimeMillis()) to store.hasEncryptedRecord()
                            }
                            snapshot = loaded.first
                            encryptedUnreadable = loaded.first == null && loaded.second
                        } catch (_: Exception) {
                            error = "读取失败，加密记录仍保留。"
                        } finally { busy = false }
                    }
                }) { Text("重试读取本机加密记录") }
            }

            if ((snapshot != null || encryptedUnreadable) && !editing) {
                if (!confirmDelete) {
                    TextButton(enabled = !busy, onClick = { confirmDelete = true }) {
                        Text(if (encryptedUnreadable)
                            "删除不可读取的加密记录" else "清除本机校园跑记录")
                    }
                } else {
                    Text("确定永久删除这份本机加密记录？无法撤销，其他校序任务与课表不受影响。",
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
                                    encryptedUnreadable = false
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
