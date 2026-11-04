package io.github.x07xh.xiaoxu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.x07xh.xiaoxu.data.CloudClient
import kotlinx.coroutines.launch

/** Existing first-party API; school sessions are not transferred to Android. */
@Composable
fun CloudTasksPanel(client: CloudClient) {
    val state = client.state
    val scope = rememberCoroutineScope()
    var title by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("校序云端任务", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold)
        if (!state.authenticated) {
            Text("连接校序云端后显示已有任务。本机任务不受影响。",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it.take(120) },
                        label = { Text("新增云端任务") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        enabled = title.isNotBlank() && !state.busy,
                        onClick = {
                            val value = title
                            scope.launch { if (client.addCloudTask(value)) title = "" }
                        }
                    ) { Text("保存至云端") }
                }
            }
            if (state.tasks.isEmpty()) {
                Text("云端暂无可展示任务。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.tasks.take(50).forEach { task ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(task.title, fontWeight = FontWeight.Medium)
                        val source = when (task.provider) {
                            "chaoxing" -> "学习通来源（不代表已核验完成状态）"
                            "ncwu" -> "青果来源"
                            "second_class" -> "第二课堂来源"
                            else -> "校序个人任务"
                        }
                        Text(source + " · " + when (task.status) {
                            "done" -> "个人标记已完成"
                            "in_progress" -> "进行中"
                            else -> "待办"
                        }, style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!task.due.isNullOrBlank()) {
                            Text("截止：" + task.due,
                                style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
        if (state.privateError != null) {
            Text(state.privateError, color = MaterialTheme.colorScheme.error)
        }
        Text("本机任务与云端任务分开显示，不自动合并，避免重复或误覆盖。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ConnectionsPanel(client: CloudClient, openWebsite: () -> Unit) {
    val state = client.state
    val scope = rememberCoroutineScope()
    // Never save the passphrase across configuration changes.
    var passphrase by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("校序云端", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold)
        Text(state.authMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!state.authenticated) {
            OutlinedTextField(
                value = passphrase, onValueChange = { passphrase = it.take(256) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("校序个人口令（不是学校密码）") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true
            )
            Button(enabled = passphrase.isNotBlank() && !state.signingIn,
                onClick = {
                    val submitted = passphrase
                    passphrase = ""
                    scope.launch { client.signIn(submitted) }
                }) { Text(if (state.signingIn) "正在连接" else "连接校序云端") }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { scope.launch { client.refresh() } }, enabled = !state.busy) {
                    Text("刷新云端数据")
                }
                TextButton(onClick = { scope.launch { client.signOut() } }) { Text("退出云端") }
            }
            if (state.lastSyncAt != null) {
                Text("最近读取校序云端：" + state.lastSyncAt,
                    style = MaterialTheme.typography.labelSmall)
            }
        }
        TextButton(onClick = openWebsite) {
            Text("打开现有数据连接中心（官方登录与诊断）")
        }
        HorizontalDivider()
        Text("学校平台连接状态", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold)
        val providers = listOf(
            "chaoxing" to "学习通",
            "ncwu" to "青果教务",
            "second_class" to "第二课堂"
        )
        providers.forEach { (id, title) ->
            val platform = state.platforms.firstOrNull { it.id == id }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, fontWeight = FontWeight.SemiBold)
                    val status = if (!state.authenticated) "未读取校序云端连接状态"
                        else if (platform == null) "连接状态未读取"
                        else if (platform.verified) "已验证会话（学校数据仍需单独比对）"
                        else "未通过本人数据连接验收"
                    Text(status, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (platform?.lastSuccessAt != null) {
                        Text("上次记录：" + platform.lastSuccessAt,
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        if (state.privateError != null) {
            Text(state.privateError, color = MaterialTheme.colorScheme.error)
        }
    }
}
