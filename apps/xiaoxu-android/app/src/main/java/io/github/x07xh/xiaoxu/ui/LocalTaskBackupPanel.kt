package io.github.x07xh.xiaoxu.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.x07xh.xiaoxu.data.LocalTaskBackup
import io.github.x07xh.xiaoxu.data.LocalTaskDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Backup is manually initiated and uses the Android system document picker. */
@Composable
fun LocalTaskBackupPanel() {
    val context = LocalContext.current
    val backup = remember(context) {
        LocalTaskBackup(LocalTaskDatabase.open(context), context.contentResolver)
    }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val create = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null && !busy) {
            busy = true
            scope.launch {
                message = try {
                    val count = withContext(Dispatchers.IO) { backup.exportTo(uri) }
                    "导出完成：$count 条本机任务，含个人步骤及先后关系。请妥善保管此文件。"
                } catch (_: Exception) {
                    "导出失败，备份文件可能不完整，请重新导出。"
                }
                busy = false
            }
        }
    }

    val open = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && !busy) {
            busy = true
            scope.launch {
                message = try {
                    val (added, skipped) = withContext(Dispatchers.IO) {
                        backup.mergeFrom(uri)
                    }
                    "恢复完成：新增加 $added 条，跳过重复 $skipped 条，原有任务未被删除。"
                } catch (_: Exception) {
                    "恢复失败：文件格式、内容或读取权限不符合要求。本机原有任务未更改。"
                }
                busy = false
            }
        }
    }

    // 由上层玻璃 Dialog 提供唯一背景与标题，不再嵌套 Material Card。
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "更换手机或重新安装前，请先导出本机手动任务。签名不一致的安装包通常无法直接覆盖升级；卸载旧版会丢失其本机数据，若旧版不支持导出，新版也无法替旧版取回。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { create.launch("xiaoxu-local-tasks.json") },
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) { Text("导出本机任务") }
                OutlinedButton(
                    onClick = { open.launch(arrayOf("application/json", "text/plain")) },
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) { Text("恢复备份") }
            }
            Text(
                "包含本机任务、个人步骤、先后关系及关联来源标识。JSON 文件未加密；不含登录凭据或学校原始记录。请勿分享含个人信息的备份。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    }
}
