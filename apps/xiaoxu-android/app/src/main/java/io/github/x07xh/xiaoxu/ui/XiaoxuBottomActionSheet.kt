package io.github.x07xh.xiaoxu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * SleepDown 参考的轻量底部浮层。只承载链接、选择、确认等短内容；
 * 课程编辑、长表单、日期与时间轮盘继续使用各自的专用页面。
 * Dialog 仍由系统负责返回键和外部触摸关闭，不模拟系统授权弹窗。
 */
@Composable
internal fun XiaoxuBottomSheet(
    title: String,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    cancelLabel: String = "取消",
    showCancel: Boolean = true,
    scrollContent: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.84f
    val outerInteraction = remember { MutableInteractionSource() }
    val innerInteraction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(XiaoxuRadius.modal)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        XiaoxuDialogDim(dimAmount = 0.26f)
        Box(
            modifier = Modifier.fillMaxSize().clickable(
                interactionSource = outerInteraction, indication = null,
                onClick = onDismiss
            ).padding(horizontal = 16.dp, vertical = 16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .clip(shape)
                    .background(XiaoxuPalette.GlassModalFallback)
                    .border(0.5.dp, XiaoxuPalette.GlassRim, shape)
                    .clickable(interactionSource = innerInteraction, indication = null, onClick = {})
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    Modifier.width(36.dp).height(4.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(XiaoxuPalette.Ink3.copy(alpha = 0.28f))
                )
                Text(
                    title, style = MaterialTheme.typography.titleLarge,
                    color = XiaoxuPalette.Ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().semantics { heading() }
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle, style = MaterialTheme.typography.bodyMedium,
                        color = XiaoxuPalette.Ink2,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .weight(1f, fill = false)
                        .then(if (scrollContent) Modifier.verticalScroll(rememberScrollState())
                            else Modifier),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = content
                )
                if (showCancel) {
                    XiaoxuSheetPill(label = cancelLabel, onClick = onDismiss)
                }
            }
        }
    }
}

internal data class XiaoxuSheetAction(
    val label: String,
    val onClick: () -> Unit,
    val destructive: Boolean = false,
    val enabled: Boolean = true,
    val selected: Boolean = false
)

@Composable
internal fun XiaoxuBottomActionSheet(
    title: String,
    onDismiss: () -> Unit,
    actions: List<XiaoxuSheetAction>,
    subtitle: String? = null,
    cancelLabel: String = "取消",
    showCancel: Boolean = true
) {
    XiaoxuBottomSheet(
        title = title,
        subtitle = subtitle,
        onDismiss = onDismiss,
        cancelLabel = cancelLabel,
        showCancel = showCancel
    ) {
        actions.forEach { action ->
            XiaoxuSheetPill(
                label = action.label,
                selected = action.selected,
                onClick = action.onClick,
                destructive = action.destructive,
                enabled = action.enabled
            )
        }
    }
}

@Composable
private fun XiaoxuSheetPill(
    label: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
    enabled: Boolean = true,
    selected: Boolean = false
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = if (enabled) 0.82f else 0.42f))
            .clickable(
                enabled = enabled, role = Role.Button,
                interactionSource = interaction,
                indication = ripple(bounded = true, color = XiaoxuPalette.PressTint),
                onClick = onClick
            ).semantics { if (selected) stateDescription = "已选择" }
            .padding(horizontal = 18.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                color = when {
                    !enabled -> XiaoxuPalette.Ink3
                    destructive -> XiaoxuPalette.Danger
                    selected -> XiaoxuPalette.BrandDeep
                    else -> XiaoxuPalette.Ink
                },
                textAlign = TextAlign.Center
            )
            if (selected) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                    tint = XiaoxuPalette.BrandDeep
                )
            }
        }
    }
}
