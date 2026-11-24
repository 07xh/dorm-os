package io.github.x07xh.xiaoxu.ui.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.x07xh.xiaoxu.ui.XiaoxuPalette
import io.github.x07xh.xiaoxu.ui.XiaoxuSoftIconButton

@Composable
internal fun UiV2SettingsSearchField(query: String, onQuery: (String) -> Unit) {
    UiV2SearchField(query, onQuery, placeholder = "搜索设置")
}

@Composable
internal fun UiV2SettingsConnectionCard(authenticated: Boolean, busy: Boolean, onOpen: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(UiV2Theme.GroupShape)
            .background(XiaoxuPalette.Surface.copy(alpha = UiV2Theme.GroupOpacity))
    ) {
        UiV2SettingsEntry(
            title = if (authenticated) "云端账户" else "连接校序云端",
            icon = Icons.Outlined.School,
            subtitle = if (authenticated) "管理账户与数据来源" else "本机任务无需登录",
            statusLabel = null,
            onClick = onOpen,
            iconContainer = UiV2Theme.CoolContainer,
            iconTint = UiV2Theme.CoolInk,
            prominent = true
        )
        UiV2SettingsDivider()
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(
                if (authenticated) XiaoxuPalette.Success else XiaoxuPalette.Ink3
            ))
            Text(
                when {
                    busy -> "正在同步"
                    authenticated -> "云端已登录"
                    else -> "本地模式 · 云端未登录"
                },
                style = MaterialTheme.typography.bodySmall, color = XiaoxuPalette.Ink2
            )
        }
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
