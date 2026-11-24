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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.LocalIndication
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
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
        modifier.fillMaxWidth().clip(UiV2Theme.GroupShape)
            .background(XiaoxuPalette.Surface.copy(alpha = UiV2Theme.GroupOpacity))
            .padding(horizontal = UiV2Theme.PageGutter, vertical = UiV2Theme.TileRadius)
    ) { content() }
}

@Composable
internal fun UiV2HomeSection(
    title: String, caption: String? = null, actionLabel: String? = null,
    onAction: (() -> Unit)? = null, content: @Composable () -> Unit
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(UiV2Theme.GroupGap)) {
        SectionHeader(title = title, caption = caption,
            actionLabel = actionLabel, onAction = onAction,
            modifier = Modifier.padding(horizontal = 4.dp))
        UiV2HomeSurface(content = content)
    }
}

@Composable
internal fun UiV2HomeQuickAction(
    title: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val warm = title == "第二课堂" || title == "学习通"
    val container = if (warm) UiV2Theme.WarmContainer else UiV2Theme.CoolContainer
    val tint = if (warm) UiV2Theme.WarmInk else UiV2Theme.CoolInk
    Row(
        modifier.uiV2Press(interaction).clip(UiV2Theme.GroupShape)
            .background(XiaoxuPalette.Surface.copy(alpha = UiV2Theme.GroupOpacity))
            .heightIn(min = 68.dp)
            .clickable(interactionSource = interaction, indication = LocalIndication.current,
                role = Role.Button, onClickLabel = "打开$title", onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(UiV2Theme.TileRadius)).background(container),
            contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = tint)
        }
        Text(title, modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            color = XiaoxuPalette.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
