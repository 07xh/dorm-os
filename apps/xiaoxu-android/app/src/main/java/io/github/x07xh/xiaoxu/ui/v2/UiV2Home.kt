package io.github.x07xh.xiaoxu.ui.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.LocalIndication
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.x07xh.xiaoxu.ui.SectionHeader
import io.github.x07xh.xiaoxu.ui.XiaoxuPalette

/** Shared static surfaces; only the navigation chrome samples the live backdrop. */
@Composable
internal fun UiV2HomeSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(XiaoxuPalette.Surface.copy(alpha = 0.9f))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) { content() }
}

@Composable
internal fun UiV2HomeSection(
    title: String, caption: String?, actionLabel: String?,
    onAction: (() -> Unit)?, content: @Composable () -> Unit
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(title = title, caption = caption,
            actionLabel = actionLabel, onAction = onAction,
            modifier = Modifier.padding(horizontal = 4.dp))
        UiV2HomeSurface(content = content)
    }
}

@Composable
internal fun UiV2HomeGreeting(date: String, greeting: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(date, style = MaterialTheme.typography.labelLarge, color = XiaoxuPalette.Ink2)
            Text(greeting, style = MaterialTheme.typography.headlineLarge,
                color = XiaoxuPalette.Ink, modifier = Modifier.semantics { heading() })
        }
    }
}

@Composable
internal fun UiV2HomeQuickAction(
    title: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val warm = title == "第二课堂" || title == "学习通"
    val container = if (warm) Color(0xFFF7E8D0) else Color(0xFFDDEDFC)
    val tint = if (warm) Color(0xFF805717) else Color(0xFF245B86)
    Column(
        modifier.uiV2Press(interaction).clip(RoundedCornerShape(14.dp))
            .heightIn(min = 76.dp)
            .clickable(interactionSource = interaction, indication = LocalIndication.current,
                role = Role.Button, onClickLabel = "打开$title", onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)
    ) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(container),
            contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = tint)
        }
        Text(title, style = MaterialTheme.typography.labelMedium,
            color = XiaoxuPalette.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
