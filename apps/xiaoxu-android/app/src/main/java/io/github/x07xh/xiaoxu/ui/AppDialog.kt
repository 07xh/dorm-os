package io.github.x07xh.xiaoxu.ui

// SPDX-License-Identifier: GPL-3.0-only
// Source: znjhahaha/zhengfang-apk @ bb3b80a6d64e4c2057c0544bb417696ceaa2a9ed, SystemUi.kt SystemDialogContent.
// Original authors and contributors. Use upstream layout in native window fallback;
// do not sample the backdrop across window boundaries.
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import com.kyant.backdrop.Backdrop
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

internal val LocalAppDialogBackdrop = staticCompositionLocalOf<Backdrop?> { null }

// Forms and nested native windows retain platform focus and IME behavior.
// Opt-in read-only/selection dialogs use the upstream same-window portal.
@Composable
internal fun AppDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable (() -> Unit),
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    usePortal: Boolean = false
) {
    val host = LocalDialogHost.current
    val backdrop = LocalAppDialogBackdrop.current
    val accessibility = rememberGlassAccessibilityMode()
    val currentDismiss by rememberUpdatedState(onDismissRequest)
    val body: @Composable () -> Unit = {
        AppDialogBody(confirmButton, dismissButton, icon, title, text,
            backdrop = backdrop.takeIf { usePortal && !accessibility.highContrast && host != null })
    }
    if (usePortal && host != null && backdrop != null && !accessibility.highContrast) {
        val currentBody by rememberUpdatedState(body)
        DisposableEffect(host) {
            val handle = host.show(onDismiss = { currentDismiss() }) { currentBody() }
            onDispose { host.dismiss(handle, notify = false) }
        }
    } else {
        Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            val dialogView = LocalView.current
            DisposableEffect(dialogView) {
                val window = (dialogView.parent as? DialogWindowProvider)?.window
                window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                window?.setDimAmount(DialogScrimAlpha)
                onDispose { }
            }
            var visible by remember { mutableStateOf(accessibility.reduceMotion) }
            LaunchedEffect(Unit) { withFrameNanos { }; visible = true }
            AnimatedVisibility(visible, enter = fadeIn() + scaleIn(initialScale = 0.92f)) {
                CompositionLocalProvider(LocalDialogHost provides null, LocalAppDialogBackdrop provides null) {
                    AppDialogBody(confirmButton, dismissButton, icon, title, text, backdrop = null)
                }
            }
        }
    }
}

@Composable
private fun AppDialogBody(
    confirmButton: @Composable (() -> Unit),
    dismissButton: @Composable (() -> Unit)?,
    icon: @Composable (() -> Unit)?,
    title: @Composable (() -> Unit)?,
    text: @Composable (() -> Unit)?,
    backdrop: Backdrop?
) {
    val screen = rememberScreenMetrics()
    val dialogWidth = minOf(if (screen.widthDp < 600.dp) 320.dp else 560.dp, screen.widthDp - 40.dp).coerceAtLeast(200.dp)
    val dialogHorizontalPadding = screen.tall(24.dp, 20.dp)
    val dialogVerticalPadding = screen.tall(24.dp, 18.dp)
    val dialogHeaderGap = screen.tall(16.dp, 12.dp)
    val dialogButtonGap = screen.tall(24.dp, 16.dp)
    val shape = RoundedCornerShape(28.dp)
    val colors = MaterialTheme.colorScheme
    val panel = if (backdrop != null) Modifier.glassSheet(backdrop) else Modifier.clip(shape)
        .background(colors.surface).border(0.75.dp, colors.outlineVariant, shape)
    Box(Modifier.width(dialogWidth).then(panel).semantics { paneTitle = "对话框" }) {
        CompositionLocalProvider(LocalContentColor provides colors.onSurface, LocalOverlayBody provides true) {
            Column(
                modifier = Modifier.padding(
                    horizontal = dialogHorizontalPadding,
                    vertical = dialogVerticalPadding
                ),
                horizontalAlignment = Alignment.Start
            ) {
            if (icon != null) {
                icon()
                Spacer(modifier = Modifier.height(dialogHeaderGap))
            }
            if (title != null) {
                CompositionLocalProvider(
                    androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurface
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        title()
                    }
                }
                Spacer(modifier = Modifier.height(dialogHeaderGap))
            }
            Box(
                modifier = Modifier
                    .weight(weight = 1f, fill = false)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    content = { text?.invoke() }
                )
            }
            if (dismissButton != null || confirmButton != null) {
                Spacer(modifier = Modifier.height(dialogButtonGap))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (dismissButton != null) {
                        Box(modifier = Modifier.weight(1f), propagateMinConstraints = true) {
                            dismissButton.invoke()
                        }
                    }
                    if (confirmButton != null) {
                        Box(modifier = Modifier.weight(1f), propagateMinConstraints = true) {
                            confirmButton.invoke()
                        }
                    }
                }
            }
        }
        }
    }
}
