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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
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
import com.tyust.course.ui.system.SystemDialog
import com.tyust.course.ui.system.DialogPresentation
import com.tyust.course.ui.system.SystemSecondaryButton
import com.tyust.course.ui.system.SystemPrimaryButton
import com.tyust.course.ui.system.SystemCard
import androidx.compose.foundation.layout.PaddingValues
import com.tyust.course.ui.system.SystemDestructiveButton
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

/** Shared business adapter. Guarded operations retain the existing non-dismissible window until the upstream host supports a dismissal veto. */
@Composable
internal fun XiaoxuBottomSheet(
    title: String,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    cancelLabel: String = "取消",
    showCancel: Boolean = true,
    dismissEnabled: Boolean = true,
    scrollContent: Boolean = true,
    guardDismiss: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    if (guardDismiss || !dismissEnabled) {
        XiaoxuGuardedBottomSheet(title, onDismiss, subtitle, cancelLabel, showCancel, dismissEnabled, scrollContent, content)
        return
    }
    SystemDialog(
        onDismissRequest = onDismiss,
        presentation = DialogPresentation.Bottom,
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }
        },
        dismissButton = if (showCancel) {
            { SystemSecondaryButton(cancelLabel, onDismiss, modifier = Modifier.fillMaxWidth()) }
        } else null
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().then(if (scrollContent) Modifier.verticalScroll(rememberScrollState()) else Modifier),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

/**
 * Platform window is retained only for busy-state dismissal veto. The surface and buttons are upstream components.
 */
@Composable
private fun XiaoxuGuardedBottomSheet(
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
    Dialog(
        onDismissRequest = { if (dismissEnabled) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false,
            dismissOnBackPress = dismissEnabled, dismissOnClickOutside = dismissEnabled)
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
                .padding(horizontal = 16.dp, vertical = 16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            val sheetMaxHeight = maxHeight * 0.88f
            SystemCard(
                modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()
                    .heightIn(max = sheetMaxHeight)
                    .clickable(interactionSource = innerInteraction, indication = null, onClick = {}),
                contentPadding = PaddingValues(20.dp),
                backdrop = null
            ) {
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
                        maxLines = 3,
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
    dismissEnabled: Boolean = true,
    guardDismiss: Boolean = false
) {
    XiaoxuBottomSheet(
        title = title,
        subtitle = subtitle,
        onDismiss = onDismiss,
        cancelLabel = cancelLabel,
        showCancel = showCancel,
        dismissEnabled = dismissEnabled,
        guardDismiss = guardDismiss
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
    val modifier = Modifier.fillMaxWidth().semantics { if (selected) stateDescription = "已选择" }
    when {
        destructive -> SystemDestructiveButton(label, onClick, modifier, enabled)
        selected -> SystemPrimaryButton(label, onClick, modifier, enabled,
            leadingIcon = { Icon(Icons.Outlined.Check, contentDescription = null) })
        else -> SystemSecondaryButton(label, onClick, modifier, enabled)
    }
}
