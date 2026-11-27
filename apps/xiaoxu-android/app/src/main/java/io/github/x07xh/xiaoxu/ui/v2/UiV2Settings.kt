package io.github.x07xh.xiaoxu.ui.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.x07xh.xiaoxu.ui.XiaoxuNavigationAction
import io.github.x07xh.xiaoxu.ui.XiaoxuPalette
import io.github.x07xh.xiaoxu.ui.XiaoxuSettingsSwitch
import io.github.x07xh.xiaoxu.ui.XiaoxuSoftIconButton
import io.github.x07xh.xiaoxu.ui.xiaoxuPageWidth

/** DSH Go SettingsMetrics-inspired geometry, shared by navigation and switch rows. */
internal object UiV2SettingsMetrics {
    val Inset = 16.dp
    val IconSize = 32.dp
    val IconGap = 12.dp
    val SingleLine = 52.dp
    val TwoLine = 64.dp
}

@Composable
internal fun UiV2SettingsHeader(
    title: String,
    onBack: (() -> Unit)?,
    onRefresh: (() -> Unit)?,
    refreshing: Boolean,
    onMessages: (() -> Unit)?,
    compact: Boolean = false,
    onNavigation: (() -> Unit)? = null
) {
    Row(
        Modifier.xiaoxuPageWidth()
            .padding(horizontal = UiV2Theme.PageGutter, vertical = if (compact) 4.dp else 8.dp)
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (onBack != null) {
            XiaoxuSoftIconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
            }
        }
        if (onBack == null) XiaoxuNavigationAction(onNavigation)
        Text(
            title,
            modifier = Modifier.weight(1f).semantics { heading() },
            style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineLarge,
            color = XiaoxuPalette.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis
        )
        if (onRefresh != null) {
            XiaoxuSoftIconButton(onClick = onRefresh, enabled = !refreshing) {
                Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
            }
        }
        if (onMessages != null) {
            XiaoxuSoftIconButton(onClick = onMessages) {
                Icon(Icons.Outlined.NotificationsNone, contentDescription = "消息中心")
            }
        }
    }
}

@Composable
internal fun UiV2SettingsGroup(title: String? = null, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(UiV2Theme.GroupGap)
    ) {
        if (!title.isNullOrBlank()) Text(
            title,
            modifier = Modifier.padding(start = UiV2SettingsMetrics.Inset).semantics { heading() },
            style = MaterialTheme.typography.labelLarge,
            color = XiaoxuPalette.Ink2
        )
        Column(
            Modifier.fillMaxWidth().clip(UiV2Theme.GroupShape)
                .background(XiaoxuPalette.Surface.copy(alpha = UiV2Theme.GroupOpacity)),
            content = { content() }
        )
    }
}

@Composable
internal fun UiV2SettingsDivider() {
    HorizontalDivider(
        color = XiaoxuPalette.Line, thickness = 0.5.dp,
        modifier = Modifier.padding(horizontal = UiV2SettingsMetrics.Inset)
    )
}

@Composable
private fun UiV2SettingsRow(
    title: String,
    subtitle: String?,
    icon: ImageVector?,
    onClick: (() -> Unit)?,
    iconContainer: Color = XiaoxuPalette.BrandSoft,
    iconTint: Color = XiaoxuPalette.BrandDeep,
    prominent: Boolean = false,
    trailing: @Composable () -> Unit
) {
    val m = UiV2SettingsMetrics
    val twoLines = !subtitle.isNullOrBlank()
    Row(
        Modifier.fillMaxWidth().heightIn(min = if (prominent) 82.dp else if (twoLines) m.TwoLine else m.SingleLine)
            .then(if (onClick != null) Modifier.clickable(
                role = Role.Button, onClickLabel = "打开$title", onClick = onClick
            ) else Modifier)
            .padding(horizontal = m.Inset, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                Modifier.size(if (prominent) 52.dp else m.IconSize).clip(CircleShape).background(iconContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint,
                    modifier = Modifier.size(if (prominent) 26.dp else 18.dp))
            }
            Spacer(Modifier.width(m.IconGap))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = if (prominent) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                color = XiaoxuPalette.Ink)
            if (twoLines && subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = XiaoxuPalette.Ink2)
            }
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}

@Composable
internal fun UiV2SettingsEntry(
    title: String, icon: ImageVector? = null, subtitle: String? = null, statusLabel: String? = null,
    onClick: () -> Unit,
    iconContainer: Color = XiaoxuPalette.BrandSoft,
    iconTint: Color = XiaoxuPalette.BrandDeep,
    prominent: Boolean = false
) {
    UiV2SettingsRow(title, subtitle, icon, onClick, iconContainer, iconTint, prominent) {
        if (!statusLabel.isNullOrBlank()) {
            Text(statusLabel, modifier = Modifier.widthIn(max = 112.dp),
                style = MaterialTheme.typography.labelSmall, color = XiaoxuPalette.Ink2,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(4.dp))
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null,
            modifier = Modifier.size(20.dp), tint = XiaoxuPalette.Ink3)
    }
}

@Composable
internal fun UiV2SettingsToggle(
    title: String, subtitle: String, checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    // The switch is the sole interactive target: its checked semantics and permission
    // callback are unchanged, and the surrounding row does not toggle a second time.
    UiV2SettingsRow(title, subtitle, icon = null, onClick = null) {
        XiaoxuSettingsSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
