package io.github.x07xh.xiaoxu.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.x07xh.xiaoxu.data.LocalTaskBackup
import io.github.x07xh.xiaoxu.data.LocalTaskDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings page and task-hub sheet share the same real SAF import/export transactions.
 * The compact variant avoids another glass card inside a floating dialog.
 */
@Composable
fun LocalTaskBackupPanel(
    embedded: Boolean = false,
    onBusyChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val backup = remember(context) {
        LocalTaskBackup(LocalTaskDatabase.open(context), context.contentResolver)
    }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var guidanceExpanded by remember { mutableStateOf(false) }

    val create = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null && !busy) {
            busy = true
            onBusyChange(true)
            message = null
            scope.launch {
                message = try {
                    val count = withContext(Dispatchers.IO) { backup.exportTo(uri) }
                    "导出完成：$count 条本机任务，含个人步骤及先后关系。请妥善保管此文件。"
                } catch (_: Exception) {
                    "导出失败，备份文件可能不完整，请重新导出。"
                }
                busy = false
                onBusyChange(false)
            }
        }
    }

    val open = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && !busy) {
            busy = true
            onBusyChange(true)
            message = null
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
                onBusyChange(false)
            }
        }
    }

    val backupActions: @Composable () -> Unit = {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = if (embedded) 0.dp else 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "本机任务、个人步骤与前置关系",
                style = MaterialTheme.typography.bodyMedium,
                color = XiaoxuPalette.Ink
            )
            Button(
                onClick = { create.launch("xiaoxu-local-tasks.json") },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) {
                Icon(Icons.Outlined.FileDownload, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (busy) "正在处理…" else "导出本机任务")
            }
            OutlinedButton(
                onClick = { open.launch(arrayOf("application/json", "text/plain")) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) {
                Icon(Icons.Outlined.FileUpload, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("从备份恢复")
            }
            Text(
                "恢复仅合并新记录，重复项跳过；不清空当前任务。",
                style = MaterialTheme.typography.bodySmall,
                color = XiaoxuPalette.Ink2
            )
        }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (embedded) {
            backupActions()
        } else {
            XiaoxuGroupedSection(title = "任务数据") { backupActions() }
        }
        message?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = if (it.contains("失败")) XiaoxuPalette.Danger else XiaoxuPalette.Ink,
                modifier = Modifier.padding(horizontal = if (embedded) 0.dp else 14.dp)
            )
        }
        if (embedded) {
            TextButton(onClick = { guidanceExpanded = !guidanceExpanded }) {
                Text(if (guidanceExpanded) "收起备份说明" else "查看备份说明")
            }
            if (guidanceExpanded) {
                BackupGuidance()
            }
        } else {
            XiaoxuGroupedSection(title = "安全与说明") {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .heightIn(min = 58.dp)
                        .clickable(role = Role.Button, onClickLabel = "查看备份说明") {
                            guidanceExpanded = !guidanceExpanded
                        }.padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Info, contentDescription = null,
                        tint = XiaoxuPalette.Brand)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (guidanceExpanded) "收起备份说明" else "查看备份说明",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        color = XiaoxuPalette.Ink
                    )
                    Icon(Icons.Outlined.ChevronRight, contentDescription = null,
                        tint = XiaoxuPalette.Ink3)
                }
                if (guidanceExpanded) {
                    HorizontalDivider(
                        color = XiaoxuPalette.Line, thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 18.dp)
                    )
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(18.dp)
                    ) { BackupGuidance() }
                }
            }
        }
    }
}

@Composable
private fun BackupGuidance() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "更换手机或重新安装前，请先导出备份。不同签名的安装包通常无法直接覆盖升级；卸载旧版会丢失本机数据。旧版若不支持导出，新版无法替它找回。",
            style = MaterialTheme.typography.bodySmall,
            color = XiaoxuPalette.Ink2
        )
        Text(
            "JSON 备份文件未加密，包含本机任务、个人步骤、关系及来源标识；不包含学校账号登录凭据或学校原始记录。请勿公开分享含个人信息的备份。",
            style = MaterialTheme.typography.bodySmall,
            color = XiaoxuPalette.Ink2
        )
    }
}
