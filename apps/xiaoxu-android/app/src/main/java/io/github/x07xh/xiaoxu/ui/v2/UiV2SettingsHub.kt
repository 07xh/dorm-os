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
    BasicTextField(
        value = query, onValueChange = onQuery, singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = XiaoxuPalette.Ink),
        cursorBrush = SolidColor(XiaoxuPalette.Brand),
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "搜索设置" },
        decorationBox = { field ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .clip(CircleShape).background(Color(0xFFE5EAF3))
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Outlined.Search, contentDescription = null, tint = XiaoxuPalette.Ink3,
                    modifier = Modifier.size(23.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("搜索设置", style = MaterialTheme.typography.bodyLarge,
                        color = XiaoxuPalette.Ink3)
                    field()
                }
                if (query.isNotEmpty()) {
                    XiaoxuSoftIconButton(onClick = { onQuery("") }) {
                        Icon(Icons.Outlined.Close, contentDescription = "清空搜索")
                    }
                }
            }
        }
    )
}

@Composable
internal fun UiV2SettingsConnectionCard(authenticated: Boolean, busy: Boolean, onOpen: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(XiaoxuPalette.Surface.copy(alpha = 0.9f))
    ) {
        UiV2SettingsEntry(
            title = if (authenticated) "云端账户" else "连接校序云端",
            icon = Icons.Outlined.School,
            subtitle = if (authenticated) "管理账户与数据来源" else "本机任务无需登录",
            statusLabel = null,
            onClick = onOpen,
            iconContainer = Color(0xFFDDEDFC),
            iconTint = Color(0xFF245B86),
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
        "学习与课程", "通知与提醒" -> Color(0xFFDDEDFC) to Color(0xFF245B86)
        "账户与数据" -> Color(0xFFF7E8D0) to Color(0xFF805717)
        else -> Color(0xFFE8EAF0) to Color(0xFF525F75)
    }
    UiV2SettingsEntry(
        title = entry.title, icon = settingIcon(entry.route),
        subtitle = if (showPath) entry.group else null, statusLabel = null,
        onClick = { onOpen(entry.route) },
        iconContainer = colors.first, iconTint = colors.second
    )
}
