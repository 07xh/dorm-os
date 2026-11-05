package io.github.x07xh.xiaoxu.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.x07xh.xiaoxu.data.CloudClient
import io.github.x07xh.xiaoxu.data.PlatformConnection
import kotlinx.coroutines.launch

/** Trusted owner cloud status is independent of the existing local official-page reader. */
private fun connectionStatusLabel(connection: PlatformConnection?): String = when {
    connection == null -> "尚未取得真实连接状态"
    connection.sessionState == "expired" -> "学校登录已过期"
    connection.sessionState == "unreadable" -> "学校会话无法验证，需重新授权"
    connection.sessionState == "review_pending" -> "本人课表等待核对"
    connection.sessionState == "active" && connection.verified ->
        "已建立本人授权和可信读取记录"
    connection.sessionState == "active" -> "已有学校会话，个人数据尚未验收"
    else -> "未连接本人学校账号"
}

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
            if (!state.tasksFresh) {
                Text("本次任务列表未能核验最新结果；下方若有记录，仅为旧快照。",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }
            if (state.tasks.isEmpty() && state.tasksFresh) {
                Text("当前云端任务列表为空（已完成本次读取）。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
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
fun ConnectionsPanel(
    client: CloudClient, openWebsite: () -> Unit,
    openLocalSchool: (String) -> Unit, startSchoolAuthorization: (String) -> Unit,
    recentImport: LocalSchoolImportReceipt? = null
) {
    val state = client.state
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var foreground by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) foreground = true
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP)
                foreground = false
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // Never save the passphrase across configuration changes.
    var passphrase by remember { mutableStateOf("") }
    var cloudAdvanced by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("学校数据", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold)
        Text("在 App 内打开学校官方页面，登录本人账号后主动读取；不需要先访问校序网站或连接云端。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        // Native-first path already implemented in the existing local school activities.
        // This is deliberately separate from verified Cloudflare sessions.
        LocalSchoolConnectionPanel(openLocalSchool, recentImport)
        HorizontalDivider()
        TextButton(onClick = { cloudAdvanced = !cloudAdvanced }) {
            Text(if (cloudAdvanced) "收起云端授权与高级同步" else "云端授权与高级同步（可选）")
        }
        if (cloudAdvanced) {
        Text("校序安全连接", style = MaterialTheme.typography.titleLarge,
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
        HorizontalDivider()
        Text("云端学校授权", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold)
        Text("仅用于云端可信同步。本机读取和云端授权是不同的数据来源。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        val providers = listOf(
            "chaoxing" to "学习通",
            "ncwu" to "青果教务",
            "second_class" to "第二课堂"
        )
        val qrStartedAt = state.chaoxingQr.startedAt
        LaunchedEffect(qrStartedAt, foreground) {
            if (qrStartedAt == 0L || !foreground) return@LaunchedEffect
            // Bounded, foreground-only: no always-on polling or worker.
            repeat(24) {
                delay(4500L)
                if (client.state.chaoxingQr.status !in setOf("pending", "scanned"))
                    return@LaunchedEffect
                client.pollChaoxingQr()
            }
        }
        providers.forEach { (id, title) ->
            val platform = state.platforms.firstOrNull { it.id == id }
            val canRead = state.authenticated && state.connectionsFresh &&
                platform?.sessionState == "active"
            val refreshing = state.schoolSyncBusyProvider == id ||
                (id == "chaoxing" && state.chaoxingBusy)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, fontWeight = FontWeight.SemiBold)
                    Text(if (!state.authenticated) "请先连接校序云端"
                        else if (!state.connectionsFresh)
                            "本次无法核验连接状态；缓存仅供参考，请刷新重试"
                        else connectionStatusLabel(platform),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state.connectionsFresh && platform?.verified == true &&
                        platform.lastSuccessAt != null) {
                        Text("上次可信读取：" + platform.lastSuccessAt,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (id == "chaoxing") {
                            OutlinedButton(
                                enabled = canRead && !state.busy && !state.chaoxingBusy,
                                modifier = Modifier.heightIn(min = 48.dp),
                                onClick = { scope.launch { client.loadChaoxingCourses() } }
                            ) {
                                Text(if (refreshing) "正在读取…" else "读取本人课程")
                            }
                        } else {
                            OutlinedButton(
                                enabled = canRead && !state.busy && state.schoolSyncBusyProvider == null,
                                modifier = Modifier.heightIn(min = 48.dp),
                                onClick = { scope.launch { client.syncAuthorizedSchool(id) } }
                            ) {
                                Text(if (refreshing) "正在读取…" else "刷新本人数据")
                            }
                        }
                        Button(
                            enabled = client.ownerSessionBinding() != null,
                            onClick = { startSchoolAuthorization(id) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text(when (platform?.sessionState) {
                                "expired", "unreadable" -> "重新授权"
                                "active" -> "管理授权"
                                else -> "连接账号"
                            })
                        }
                    }
                    // Reuse the native school reader for one-phone users.
                    // Local visible records remain separate from verified cloud sync.
                    TextButton(
                        onClick = { openLocalSchool(id) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(when (id) {
                            "chaoxing" -> "只有一台手机？打开学习通官网"
                            "ncwu" -> "本机打开青果官方课表"
                            else -> "本机打开第二课堂官网"
                        })
                    }
                    if (id == "chaoxing") {
                        val qr = state.chaoxingQr
                        // Scan stays on this native page. No additional login screen or server connector is required.
                        OutlinedButton(
                            enabled = state.authenticated && !qr.busy && !state.busy,
                            onClick = { scope.launch { client.startChaoxingQr() } }
                        ) {
                            Text(if (qr.status in setOf("pending", "scanned"))
                                "重新生成扫码二维码" else "显示扫码二维码（需另一设备）")
                        }
                        if (qr.status != "idle") {
                            qr.message?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall,
                                    color = if (qr.status == "failed")
                                        MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (qr.status in setOf("pending", "scanned")) {
                                val qrBitmap = remember(qr.image) {
                                    qr.image?.let { raw ->
                                        val bounds = BitmapFactory.Options().apply {
                                            inJustDecodeBounds = true
                                        }
                                        BitmapFactory.decodeByteArray(
                                            raw, 0, raw.size, bounds)
                                        if (bounds.outWidth in 1..2048 &&
                                            bounds.outHeight in 1..2048) {
                                            BitmapFactory.decodeByteArray(
                                                raw, 0, raw.size)?.asImageBitmap()
                                        } else null
                                    }
                                }
                                if (qrBitmap != null) {
                                    Image(
                                        bitmap = qrBitmap,
                                        contentDescription = "学习通官方扫码登录二维码",
                                        modifier = Modifier.size(208.dp)
                                    )
                                } else {
                                    Text("二维码图片无法安全显示，请改用网页备用连接")
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = {
                                        scope.launch { client.pollChaoxingQr() }
                                    }, enabled = !qr.busy) {
                                        Text("已扫码，立即检查")
                                    }
                                    TextButton(onClick = client::cancelChaoxingQr) {
                                        Text("取消扫码")
                                    }
                                }
                            }
                        }
                        if (state.chaoxingCourses.isNotEmpty()) {
                            Text("已读取本人 " + state.chaoxingCourses.size + " 门课程",
                                style = MaterialTheme.typography.labelSmall)
                            OutlinedButton(
                                enabled = state.authenticated && state.connectionsFresh &&
                                    platform?.verified == true && !state.chaoxingBusy &&
                                    !state.chaoxingDetailBusy && !state.busy &&
                                    state.schoolSyncBusyProvider == null,
                                onClick = {
                                    scope.launch { client.syncChaoxingAllCourses() }
                                }
                            ) {
                                Text(if (state.chaoxingBatchBusy) "正在逐门核验作业…"
                                    else "一键核验全部课程作业")
                            }
                            Text("按已有可信只读接口逐门核验；失败或未读到的课程不标记成功。",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        state.chaoxingBatchStatus?.let { result ->
                            Text(result, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        state.chaoxingError?.let {
                            Text(it, color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall)
                        }
                        if (platform?.autoSyncEnabled == true) {
                            Text("每小时检查已登记；实际同步结果以任务页为准",
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (state.schoolSyncResultProvider == id && state.schoolSyncMessage != null) {
                        Text(state.schoolSyncMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (state.privateError != null) {
            Text(state.privateError, color = MaterialTheme.colorScheme.error)
        }
        Text(
            "本机官网读取仅保存当前可见记录，不等于校序云端授权或官方完整作业同步。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall
        )
        TextButton(onClick = openWebsite) { Text("网页备用连接 / 诊断") }
        } // Optional Cloudflare-based school connector management
    }
}
