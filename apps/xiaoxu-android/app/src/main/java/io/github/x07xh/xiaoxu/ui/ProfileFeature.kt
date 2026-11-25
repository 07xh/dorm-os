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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
private fun SettingsRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String? = null,
    showDivider: Boolean = true,
    onClick: () -> Unit
) {
    InsetGroupedRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        iconTint = iconTint,
        showDivider = showDivider,
        onClick = onClick,
        trailing = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    )
}

@Composable
internal fun ProfileFeature(data: AppWorkspaceState, actions: AppPageActions) {
    val tint = MaterialTheme.colorScheme.primary
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.section)) {
        InsetGroupedSection(header = "账号与教务") {
            SettingsRow(Icons.Outlined.Person, tint, "云端账户",
                if (data.cloud.authenticated) "已连接" else "未连接", onClick = actions.openWebsite)
            SettingsRow(Icons.Outlined.Link, tint, "数据连接", showDivider = false,
                onClick = { actions.navigate(AppRoute.CONNECTIONS) })
        }
        InsetGroupedSection(header = "学习设置") {
            SettingsRow(Icons.Outlined.DateRange, tint, "课表设置", showDivider = false,
                onClick = { actions.navigate(AppRoute.SCHEDULE_SETTINGS) })
        }
        InsetGroupedSection(header = "应用与支持") {
            SettingsRow(Icons.Outlined.Info, tint, "开源许可", showDivider = false,
                onClick = { actions.navigate(AppRoute.LICENSES) })
        }
    }
}

@Composable
internal fun ConnectionsFeature(data: AppWorkspaceState, actions: AppPageActions) {
    val cloud = data.cloud
    val scope = rememberCoroutineScope()
    val tint = MaterialTheme.colorScheme.primary
    fun status(provider: String): String {
        if (!cloud.authenticated || !cloud.connectionsFresh) return "云端状态尚未核验"
        val record = cloud.platforms.firstOrNull { it.id == provider } ?: return "尚无连接记录"
        return if (record.verified) "云端授权已核验" else "云端授权未确认"
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.section)) {
        AcademicReadAction("刷新连接状态", cloud.busy) { scope.launch { actions.refreshCloud() } }
        AppNotice(cloud.schoolSyncMessage ?: cloud.authMessage)
        cloud.privateError?.let { AppNotice(it, error = true) }
        InsetGroupedSection(header = "教务与学习平台") {
            SettingsRow(Icons.Outlined.School, tint, "华水青果", status("ncwu"), onClick = { actions.authorize("ncwu") })
            SettingsRow(Icons.Outlined.MenuBook, tint, "学习通", "本机读取入口", onClick = { actions.connectLocal("chaoxing") })
            SettingsRow(Icons.Outlined.Event, tint, "第二课堂", "本机读取入口", showDivider = false,
                onClick = { actions.connectLocal("second_class") })
        }
        InsetGroupedSection(header = "云端账户") {
            SettingsRow(Icons.Outlined.Person, tint, "账户管理", showDivider = false, onClick = actions.openWebsite)
        }
    }
}
