package io.github.x07xh.xiaoxu.ui

// SPDX-License-Identifier: GPL-3.0-only
// Source: znjhahaha/zhengfang-apk @ bb3b80a6d64e4c2057c0544bb417696ceaa2a9ed, ui/system/AnchoredGlassOverlay.kt.
// Original authors and contributors. Package and existing host bindings adapted.

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.tyust.course.ui.system.*

internal val LocalOverlayControls = staticCompositionLocalOf { Rect.Zero }

/** Unlike the connected picker portal, an optical panel NEVER lives in its anchor's subtree. */
@Composable
internal fun AnchoredGlassOverlay(
    active: Boolean,
    renderedHeight: Dp,
    desiredHeight: Dp,
    width: Dp,
    onDismiss: () -> Unit,
    onSpace: (Float, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onClosed: () -> Unit = {},
    diagnosticTag: String = "glass-panel",
    phase: String = "open",
    anchorBounds: Rect? = null,
    anchor: @Composable () -> Unit,
    body: @Composable () -> Unit
) {
    val host = LocalGlassPortals.current
    val dialogs = LocalDialogHost.current
    val density = LocalDensity.current
    val controls = LocalOverlayControls.current
    val currentBody by rememberUpdatedState(body)
    val currentDismiss by rememberUpdatedState(onDismiss)
    val currentSpace by rememberUpdatedState(onSpace)
    val currentClosed by rememberUpdatedState(onClosed)
    val locals by rememberUpdatedState(currentCompositionLocalContext)
    val entry = remember {
        GlassPortalEntry(content = {
            CompositionLocalProvider(locals) {
                CompositionLocalProvider(LocalOverlayBody provides true) { currentBody() }
            }
        }, dismiss = { currentDismiss() }, onSpace = { space, up -> currentSpace(space, up) })
            .apply { bodyOnly = true }
    }
    DisposableEffect(active, dialogs) {
        if (active) dialogs?.beginPortal()
        onDispose { if (active) dialogs?.endPortal() }
    }
    SideEffect {
        if (anchorBounds != null) entry.anchor = anchorBounds
        entry.header = 0f
        entry.rendered = with(density) { renderedHeight.toPx() }
        entry.desiredBody = with(density) { desiredHeight.toPx() }
        entry.preferredWidth = with(density) { width.toPx() }
        entry.controls = controls.takeIf { it.width > 0f } ?: entry.anchor
        if (host != null) {
            if (active && entry !in host.entries) {
                host.entries.toList().forEach { it.dismiss() }
                host.entries.add(entry)
            } else if (!active) host.entries.remove(entry)
        }
    }
    DisposableEffect(host, entry) { onDispose { host?.entries?.remove(entry) } }
    val registered = host?.entries?.contains(entry) == true
    LaunchedEffect(active, registered) { if (!active && !registered) currentClosed() }
    Box(modifier.onGloballyPositioned {
        if (anchorBounds == null) entry.anchor = Rect(it.positionInWindow(), Size(it.size.width.toFloat(), it.size.height.toFloat()))
    }) { anchor() }

    if (host == null && active) {
        // A different window cannot sample this page's RenderNodes. A readable solid
        // fallback also preserves close/back semantics when a caller omitted the host.
        val available = with(density) { (LocalConfiguration.current.screenHeightDp.dp * .65f).toPx() }
        SideEffect { currentSpace(available, false) }
        Dialog(onDismissRequest = onDismiss) {
            CompositionLocalProvider(LocalModalBackdrop provides null, LocalControlBackdrop provides null,
                LocalAppBackdrop provides null, LocalGlassLensAnchor provides null,
                LocalGlassLensModalAnchor provides null, LocalOverlayBody provides true) {
                Box(Modifier.widthIn(max = width).height(renderedHeight)) {
                    if (renderedHeight > 1.dp) currentBody()
                }
            }
        }
    }
}
