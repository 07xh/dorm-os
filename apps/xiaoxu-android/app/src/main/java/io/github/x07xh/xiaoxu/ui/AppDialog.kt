package io.github.x07xh.xiaoxu.ui

// SPDX-License-Identifier: GPL-3.0-only
// Source: znjhahaha/zhengfang-apk @ bb3b80a6d64e4c2057c0544bb417696ceaa2a9ed, SystemUi.kt SystemDialogContent.
// Original authors and contributors. Use upstream layout in native window fallback;
// do not sample the backdrop across window boundaries.
import android.view.WindowManager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.RoundedRectangle
import com.kyant.shapes.RoundedCornerStyle
import com.tyust.course.ui.system.*
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
        SystemDialogContent(
            backdrop = backdrop.takeIf { usePortal && !accessibility.highContrast && host != null },
            useVisualEffects = true, confirmButton = confirmButton, dismissButton = dismissButton,
            icon = icon, title = title, scrollContent = false
        ) { text?.invoke() }
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
                    SystemDialogContent(
                        backdrop = null, useVisualEffects = false, confirmButton = confirmButton,
                        dismissButton = dismissButton, icon = icon, title = title, scrollContent = false
                    ) { text?.invoke() }
                }
            }
        }
    }
}

@Composable
private fun SystemDialogContent(
    backdrop: Backdrop?,
    useVisualEffects: Boolean,
    confirmButton: @Composable (() -> Unit)?,
    dismissButton: @Composable (() -> Unit)?,
    icon: @Composable (() -> Unit)?,
    title: @Composable (() -> Unit)?,
    scrollContent: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    // iOS squircle：连续曲率，避免圆弧与直边交界处的折角感
    val dialogShape = RoundedRectangle(
        cornerRadius = GlassRecipe.DialogCornerDp.dp,
        style = RoundedCornerStyle.Continuous
    )
    val accessibility = rememberGlassAccessibilityMode()
    val dialogMaterial = GlassMaterials.resolve(
        role = GlassMaterialRole.Modal,
        accessibility = accessibility
    )
    val glassBackdrop = backdrop?.takeIf { useVisualEffects && isBackdropSupported() }
    val regionState = rememberWallpaperRegionState()
    val appearance = themedSurfaceAppearance()
    val isLightTheme = appearance.usesDarkForeground
    // Keep the optics, but give text a sufficiently opaque surface on busy wallpapers.
    val dialogSurfaceColor = appearance.surface.copy(
        alpha = modalSurfaceAlpha(!isLightTheme, accessibility.highContrast)
    )
    val dialogBorderColor = appearance.border
    val dialogShadowColor = Color.Black.copy(alpha = dialogMaterial.shadowAlpha)

    val dialogLayerBackdrop = rememberLayerBackdrop()
    val nestedControlBackdrop = if (glassBackdrop != null) {
        rememberCombinedBackdrop(glassBackdrop, dialogLayerBackdrop)
    } else {
        null
    }

    // ## API 31/32
    //
    // 卡片本体折射身后的页面，用**模糊版**的 App 全局区域：33+ 这里的管线是
    // vibrancy → blur → lens，lens 采的是已经模糊过的像素，而那层模糊只能烤进
    // 底图（见 LocalGlassLensModalAnchor）。
    //
    // Nested pickers sample the page plus this panel, not the root wallpaper alone.
    val dialogDensity = LocalDensity.current
    val panelLensAnchor = LocalGlassLensModalAnchor.current.takeIf { glassBackdrop != null }
    val nestedLensAnchor = if (nestedControlBackdrop != null) rememberGlassLensRegion(
        "dialog-controls", appearance.surface, glassBackdrop,
        freshness = LocalPageGlassFreshness.current
    ) { coordinates ->
        drawBackdropSource(nestedControlBackdrop, dialogDensity, coordinates)
    } else null

    // 320dp 是设计宽度；窄屏上按屏宽收，两侧至少留 20dp，不顶满边缘。
    val screen = rememberScreenMetrics()
    val dialogWidth = minOf(if (screen.widthDp < 600.dp) 320.dp else 560.dp, screen.widthDp - 40.dp)
    // 短屏把卡片内边距与两处间距一起收，合计给正文多让出约 30dp。
    val dialogHorizontalPadding = screen.tall(24.dp, 20.dp)
    val dialogVerticalPadding = screen.tall(24.dp, 18.dp)
    val dialogHeaderGap = screen.tall(16.dp, 12.dp)
    val dialogButtonGap = screen.tall(24.dp, 16.dp)

    Box(
        modifier = Modifier
            .width(dialogWidth)
            .semantics { paneTitle = "对话框" }
            .glassLensAnchor(nestedLensAnchor)
            .wallpaperRegion(regionState)
    ) {
        if (glassBackdrop != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .layerBackdrop(dialogLayerBackdrop)
                    // 31/32：折射由自家着色器在这里完成，就着 App 全局区域的底图。
                    // 必须在 drawBackdrop **上游**，它下面那层会把背景再画一遍。
                    .glassLens(
                        panelLensAnchor,
                        optics = { w, h ->
                            glassLensOpticsFrom(
                                material = dialogMaterial,
                                density = dialogDensity,
                                cornerRadiusPx = lensCornerRadiusPx(
                                    dialogShape, w, h, dialogDensity
                                ),
                                minDimensionPx = minOf(w, h),
                                // 静态面板：不随按压变化，也不要静止色散
                                pressScalesRefraction = false,
                                chromaticAberrationAtRest = false
                            )
                        }
                    )
                    .drawBackdrop(
                        backdrop = glassBackdrop,
                        shape = { dialogShape },
                        // 与官方控件同源的模板管线：vibrancy → blur → lens，
                        // 参数取 Modal 材质档，不再走 resolvePhysicalLens 自定义组合。
                        effects = {
                            vibrancy()
                            if (isRuntimeLensEnabled()) {
                                blur(GlassRecipe.DialogBlurDp.dp.toPx())
                                lens(
                                    refractionHeight = GlassRecipe.DialogRefractionHeightDp.dp.toPx(),
                                    refractionAmount = GlassRecipe.DialogRefractionAmountDp.dp.toPx()
                                )
                            } else if (panelLensAnchor != null) {
                                // 31/32：什么都不加。模糊已经烤进底图（见
                                // LocalGlassLensModalAnchor），折射也已在上游画完，
                                // 这里再 blur 是对着空图层做的，白付一次离屏。
                                //
                                // 原先这条分支是 2× 模糊 —— 那是"这台机器没有折射"
                                // 时的补偿；现在有折射了，补偿要一起撤，否则是
                                // 糊 + 折射叠着，比 33+ 糊一倍。
                            } else {
                                blur((GlassRecipe.DialogBlurDp * 2f).dp.toPx())
                            }
                        },
                        onDrawBackdrop = { drawBackdrop ->
                            // 31/32 上背景已由 glassLens 以折射方式画过
                            if (panelLensAnchor == null) drawBackdrop()
                        },
                        // 仅保留模板需要的光学表面和阴影，不再叠加常驻边缘高光描边。
                        highlight = { null },
                        shadow = { Shadow(alpha = dialogMaterial.shadowAlpha) },
                        onDrawSurface = { drawRect(dialogSurfaceColor) }
                    )
                    // 库的 Highlight shader 只认 CornerBasedShape，对连续曲率 squircle
                    // 会退化成 minDimension/2 的圆角，高光位置整体错位。
                    // 这里直接按 outline 描边，圆角多大就贴多大。
                    .glassRim(dialogShape, intensity = 0.9f, isLightTheme = isLightTheme)
            )
        } else {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .shadow(
                        elevation = GlassRecipe.DialogShadowElevationDp.dp,
                        shape = dialogShape,
                        clip = false,
                        ambientColor = dialogShadowColor,
                        spotColor = dialogShadowColor
                    )
                    .clip(dialogShape)
                    .background(appearance.solidSurface)
                    .border(0.75.dp, dialogBorderColor, dialogShape)
            )
        }

        CompositionLocalProvider(LocalControlBackdrop provides nestedControlBackdrop, LocalGlassLensAnchor provides nestedLensAnchor,
            LocalThemedContent provides true, LocalGlassAppearanceOverride provides appearance,
            LocalOverlayBody provides true,
            androidx.compose.material3.LocalContentColor provides appearance.onSurface) {
        ProvideWallpaperAppearance(appearance) {
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
                    androidx.compose.material3.LocalContentColor provides appearance.onSurface
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
                    modifier = Modifier.fillMaxWidth().then(if (scrollContent) Modifier.verticalScroll(rememberScrollState()) else Modifier),
                    content = content
                )
            }
            if (confirmButton != null || dismissButton != null) {
                Spacer(modifier = Modifier.height(dialogButtonGap))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (dismissButton != null) {
                        Box(modifier = Modifier.weight(1f), propagateMinConstraints = true) {
                            dismissButton()
                        }
                    }
                    if (confirmButton != null) {
                        Box(modifier = Modifier.weight(1f), propagateMinConstraints = true) {
                            confirmButton()
                        }
                    }
                }
            }
        }
    }
}
}
}
