package io.github.x07xh.xiaoxu.ui.v2

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.School
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
internal fun UiV2SettingsSearchField(query: String, onQuery: (String) -> Unit) {
    UiV2SearchField(query, onQuery, placeholder = "搜索设置")
}

@Composable
internal fun UiV2SettingsConnectionCard(authenticated: Boolean, busy: Boolean, onOpen: () -> Unit) {
    UiV2SettingsGroup {
        UiV2SettingsEntry(
            title = if (authenticated) "云端账户" else "连接校序云端",
            icon = Icons.Outlined.School,
            subtitle = when {
                busy -> "正在同步"
                authenticated -> "云端已登录"
                else -> "本地模式 · 本机任务无需登录"
            },
            onClick = onOpen,
            iconContainer = UiV2Theme.CoolContainer,
            iconTint = UiV2Theme.CoolInk,
            prominent = true
        )
    }
}

private fun settingIcon(route: String): ImageVector = when (route) {
    "timetable-settings" -> Icons.Outlined.DateRange
    "notifications", "notifications-course", "notifications-task" -> Icons.Outlined.NotificationsNone
    "connections" -> Icons.Outlined.School
    "backup" -> Icons.Outlined.CheckCircle
    else -> Icons.Outlined.Info
}

@Composable
internal fun UiV2SettingsIndexedEntry(entry: UiV2Setting, showPath: Boolean = false, onOpen: (String) -> Unit) {
    val colors = when (entry.group) {
        "学习与课程", "通知与提醒" -> UiV2Theme.CoolContainer to UiV2Theme.CoolInk
        "账户与数据" -> UiV2Theme.WarmContainer to UiV2Theme.WarmInk
        else -> UiV2Theme.NeutralContainer to UiV2Theme.NeutralInk
    }
    UiV2SettingsEntry(
        title = entry.title, icon = settingIcon(entry.route),
        subtitle = if (showPath) entry.group else null, statusLabel = null,
        onClick = { onOpen(entry.route) },
        iconContainer = colors.first, iconTint = colors.second
    )
}
