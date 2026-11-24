package io.github.x07xh.xiaoxu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.runtime.LaunchedEffect
import io.github.x07xh.xiaoxu.ui.v2.UiV2SettingsDivider
import io.github.x07xh.xiaoxu.ui.v2.UiV2Theme
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.x07xh.xiaoxu.ui.v2.UiV2SheetHandle
import io.github.x07xh.xiaoxu.ui.v2.UiV2SheetPolicy
import io.github.x07xh.xiaoxu.ui.v2.rememberUiV2SheetDragState
import io.github.x07xh.xiaoxu.ui.v2.uiV2SheetMotion
import io.github.x07xh.xiaoxu.ui.v2.uiV2Press

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
    dismissEnabled: Boolean = true,
    scrollContent: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val outerInteraction = remember { MutableInteractionSource() }
    val innerInteraction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(UiV2SheetPolicy.CornerDp.dp)
    val dragState = rememberUiV2SheetDragState()
    Dialog(
        onDismissRequest = { if (dismissEnabled) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = dismissEnabled,
            dismissOnClickOutside = dismissEnabled
        )
    ) {
        XiaoxuDialogDim(dimAmount = 0.26f)
        // Size against the usable dialog window, not the full screen.
        // The inset-aware surface stays above the soft keyboard.
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .clickable(
                    enabled = dismissEnabled,
                    interactionSource = outerInteraction,
                    indication = null,
                    onClick = onDismiss
                )
                .padding(horizontal = UiV2Theme.PageGutter, vertical = 12.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            val compactHeight = maxHeight.value < UiV2SheetPolicy.CompactHeightDp
            val sheetMaxHeight = maxHeight * UiV2SheetPolicy.heightFraction(maxHeight.value)
            LaunchedEffect(compactHeight) { if (compactHeight) dragState.offset = 0f }
            Column(
                modifier = Modifier.widthIn(max = UiV2SheetPolicy.MaxWidthDp.dp).fillMaxWidth()
                    .heightIn(max = sheetMaxHeight)
                    .uiV2SheetMotion(dragState)
                    .clip(shape)
                    .background(XiaoxuPalette.GlassModalFallback)
                    .border(0.5.dp, XiaoxuPalette.GlassRim, shape)
                    .clickable(interactionSource = innerInteraction, indication = null, onClick = {})
                    .padding(horizontal = UiV2SheetPolicy.GutterDp.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (compactHeight) 6.dp else 10.dp)
            ) {
                if (!compactHeight) UiV2SheetHandle(
                    state = dragState,
                    enabled = dismissEnabled,
                    onDismiss = onDismiss,
                    color = XiaoxuPalette.Ink3.copy(alpha = 0.35f)
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title, style = MaterialTheme.typography.titleLarge,
                        color = XiaoxuPalette.Ink,
                        modifier = Modifier.weight(1f).semantics { heading() }
                    )
                    if (compactHeight && dismissEnabled) {
                        XiaoxuSoftIconButton(onClick = onDismiss) {
                            Icon(Icons.Outlined.Close, contentDescription = "关闭")
                        }
                    }
                }
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle, style = MaterialTheme.typography.bodyMedium,
                        color = XiaoxuPalette.Ink2,
                        textAlign = TextAlign.Start,
                        maxLines = if (compactHeight) 2 else 3,
                        overflow = TextOverflow.Ellipsis,
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
                    XiaoxuSheetPill(
                        label = cancelLabel,
                        onClick = onDismiss,
                        enabled = dismissEnabled
                    )
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
    showCancel: Boolean = true,
    dismissEnabled: Boolean = true
) {
    XiaoxuBottomSheet(
        title = title,
        subtitle = subtitle,
        onDismiss = onDismiss,
        cancelLabel = cancelLabel,
        showCancel = showCancel,
        dismissEnabled = dismissEnabled
    ) {
        Column(Modifier.fillMaxWidth().clip(UiV2Theme.GroupShape)
            .background(XiaoxuPalette.Surface.copy(alpha = UiV2Theme.GroupOpacity))) {
            actions.forEachIndexed { index, action ->
                if (index > 0) UiV2SettingsDivider()
                XiaoxuSheetActionRow(action)
            }
        }
    }
}

@Composable
private fun XiaoxuSheetPill(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            .uiV2Press(interaction, enabled)
            .clip(RoundedCornerShape(UiV2Theme.PillRadius))
            .background(Color.White.copy(alpha = if (enabled) 0.82f else 0.42f))
            .clickable(
                enabled = enabled, role = Role.Button,
                interactionSource = interaction,
                indication = ripple(bounded = true, color = XiaoxuPalette.PressTint),
                onClick = onClick
            )
            .padding(horizontal = 18.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) XiaoxuPalette.Ink else XiaoxuPalette.Ink3,
            textAlign = TextAlign.Center
        )
    }
}

/** Compact grouped action rows, independent of caller-owned callbacks and selection. */
@Composable
private fun XiaoxuSheetActionRow(action: XiaoxuSheetAction) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .uiV2Press(interaction, action.enabled)
            .clickable(
                enabled = action.enabled, role = Role.Button,
                interactionSource = interaction,
                indication = ripple(bounded = true, color = XiaoxuPalette.PressTint),
                onClick = action.onClick
            )
            .semantics { if (action.selected) stateDescription = "已选择" }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            action.label, modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = when {
                !action.enabled -> XiaoxuPalette.Ink3
                action.destructive -> XiaoxuPalette.Danger
                action.selected -> XiaoxuPalette.BrandDeep
                else -> XiaoxuPalette.Ink
            }
        )
        if (action.selected) Icon(Icons.Outlined.Check, contentDescription = null,
            tint = XiaoxuPalette.BrandDeep)
    }
}
