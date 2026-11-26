package io.github.x07xh.xiaoxu.ui

// SPDX-License-Identifier: GPL-3.0-only
// Source: znjhahaha/zhengfang-apk @ bb3b80a6d64e4c2057c0544bb417696ceaa2a9ed, ui/screen/SettingsScreen.kt
// Original authors and contributors; SettingsRow directly reused.
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.CancellationException
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import io.github.x07xh.xiaoxu.data.PlatformConnection

@Composable
internal fun ConnectionsFeature(data: AppWorkspaceState, actions: AppPageActions) {
    val cloud = data.cloud
    val scope = rememberCoroutineScope()
    val tint = MaterialTheme.colorScheme.primary
    val lessonsLabel = when (val snapshot = data.lessons) {
        PageSnapshot.Loading -> "尚无已读取课表"
        is PageSnapshot.Unavailable -> "课表暂不可读取"
        is PageSnapshot.Ready -> "已保存 ${snapshot.value.size} 条本机课程"
    }
    val localRows = (data.nativeRecords as? PageSnapshot.Ready)?.value
    val localStatuses = remember(localRows) {
        localRows?.filter { it.kind != "account_binding" }?.groupBy { it.provider }?.mapValues { (_, rows) ->
            val stamp = rows.maxOf { it.capturedAt }
            val time = android.text.format.DateFormat.format("MM-dd HH:mm", stamp)
            "已保存 ${rows.size} 条 · $time"
        }
    }
    fun localStatus(provider: String): String =
        if (localStatuses == null) "本机记录尚未读取" else localStatuses[provider] ?: "暂无已保存记录"
    fun status(provider: String): String {
        if (!cloud.authenticated || !cloud.connectionsFresh) return "云端状态尚未核验"
        val record = cloud.platforms.firstOrNull { it.id == provider } ?: return "尚无连接记录"
        return connectionStatusLabel(record)
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.section)) {
        InsetGroupedSection(animateSize = false) {
            InsetGroupedRow(title = if (cloud.authenticated) "校序云端已连接" else "校序云端未连接",
                subtitle = cloud.schoolSyncMessage ?: cloud.authMessage.takeUnless { it == "云端未连接" },
                showDivider = false, trailing = {
                    AcademicReadAction("刷新", cloud.busy) { scope.launch { actions.refreshCloud() } }
                })
        }
        cloud.privateError?.let { AppNotice(it, error = true) }
        InsetGroupedSection(header = "本机学校读取", animateSize = false,
            footer = "仅保存本人学校记录，无需连接校序云端。") {
            SettingsRow(Icons.Outlined.School, tint, "华水青果", lessonsLabel, onClick = { actions.connectLocal("ncwu") })
            AppGroupedEntry("查看本机课表", onClick = { actions.navigate(AppRoute.SCHEDULE) })
            SettingsRow(Icons.Outlined.MenuBook, tint, "学习通", localStatus("chaoxing"), onClick = { actions.connectLocal("chaoxing") })
            AppGroupedEntry("查看作业与任务", onClick = { actions.navigate(AppRoute.TASKS) })
            AppGroupedEntry("查看课程进度", onClick = { actions.navigate(AppRoute.STUDY_PROGRESS) })
            SettingsRow(Icons.Outlined.Event, tint, "第二课堂", localStatus("second_class"),
                onClick = { actions.connectLocal("second_class") })
            AppGroupedEntry("查看活动与积分", showDivider = false, onClick = { actions.navigate(AppRoute.SECOND_CLASS) })
        }
        InsetGroupedSection(header = "云端学校授权", animateSize = false, footer = "云端授权与本机读取独立。") {
            SettingsRow(Icons.Outlined.School, tint, "青果教务授权", status("ncwu"),
                onClick = { if (cloud.authenticated) actions.authorize("ncwu") else actions.navigate(AppRoute.ACCOUNT) })
            SettingsRow(Icons.Outlined.MenuBook, tint, "学习通云端授权", status("chaoxing"),
                onClick = {
                    if (!cloud.authenticated) actions.navigate(AppRoute.ACCOUNT)
                    else actions.authorize("chaoxing")
                })
            val linked = cloud.platforms.firstOrNull { it.id == "second_class" }
            val verified = cloud.authenticated && cloud.connectionsFresh
            SettingsRow(Icons.Outlined.Event, tint, "第二课堂云端授权", status("second_class"),
                onClick = { if (cloud.authenticated) actions.authorize("second_class") else actions.navigate(AppRoute.ACCOUNT) })
            if (verified && linked?.sessionState == "active") {
                linked.lastSuccessAt?.let { InsetGroupedRow(title = "最近成功读取", subtitle = it, showDivider = false) }
                TextButton(enabled = !cloud.busy && cloud.schoolSyncBusyProvider == null,
                    onClick = { scope.launch { actions.syncAuthorizedSchool("second_class") } }) {
                    Text(if (cloud.schoolSyncBusyProvider == "second_class") "正在同步" else "同步已授权第二课堂")
                }
            }
        }
        InsetGroupedSection(header = "云端账户") {
            SettingsRow(Icons.Outlined.Person, tint, "账户管理", showDivider = false, onClick = { actions.navigate(AppRoute.ACCOUNT) })
        }
    }
}

// Original CloudPanels.kt connection status projection.
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


/** Restores the original CloudClient sign-in / refresh / sign-out actions.
 * Password is ephemeral and never kept in saved state. */
@Composable
internal fun AccountFeature(data: AppWorkspaceState, actions: AppPageActions) {
    val state = data.cloud
    val scope = rememberCoroutineScope()
    var passphrase by remember { mutableStateOf("") }
    var signingOut by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.section)) {
        InsetGroupedSection(header = "校序云端") {
            InsetGroupedRow(title = if (state.authenticated) "已连接" else "未连接",
                subtitle = state.authMessage, showDivider = false)
            if (state.authenticated) {
                state.lastSyncAt?.let { AppNotice("最近读取：$it") }
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(enabled = !state.busy && !signingOut, onClick = {
                        scope.launch { actions.refreshCloud() }
                    }) { Text("刷新已保存数据") }
                    TextButton(enabled = !state.busy && !signingOut,
                        onClick = { confirmExit = true }) { Text("退出云端") }
                }
            } else {
                OutlinedTextField(value = passphrase, onValueChange = { passphrase = it.take(256) },
                    label = { Text("校序个人口令") }, supportingText = { Text("不是学校账户密码") },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true,
                    enabled = !state.signingIn, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))
                TextButton(enabled = passphrase.isNotBlank() && !state.signingIn, onClick = {
                    val submitted = passphrase
                    passphrase = ""
                    scope.launch { actions.signInCloud(submitted) }
                }) { Text(if (state.signingIn) "正在连接" else "连接云端") }
            }
        }
        state.privateError?.let { AppNotice(it, error = true) }
        InsetGroupedSection(header = "账户服务") {
            AppGroupedEntry("学校数据连接", showDivider = true,
                onClick = { actions.navigate(AppRoute.CONNECTIONS) })
            AppGroupedEntry("网页账户管理", showDivider = false, onClick = actions.openWebsite)
        }
        AppNotice("退出云端不会删除本机课表和本机任务。")
    }
    if (confirmExit) AppDialog(onDismissRequest = { if (!signingOut) confirmExit = false },
        title = { Text("退出校序云端？") },
        text = { Text("将清除本机云端登录会话，本机任务和课表保留。") },
        confirmButton = { TextButton(enabled = !signingOut, onClick = {
            signingOut = true
            scope.launch {
                try { actions.signOutCloud(); confirmExit = false }
                finally { signingOut = false }
            }
        }) { Text("退出") } },
        dismissButton = { TextButton(enabled = !signingOut, onClick = { confirmExit = false }) { Text("取消") } })
}
