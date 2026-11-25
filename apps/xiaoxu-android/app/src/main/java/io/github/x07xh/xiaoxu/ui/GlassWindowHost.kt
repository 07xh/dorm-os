package io.github.x07xh.xiaoxu.ui

// SPDX-License-Identifier: GPL-3.0-only
// Source: znjhahaha/zhengfang-apk @ bb3b80a6d64e4c2057c0544bb417696ceaa2a9ed, ui/system/GlassWindowHost.kt
// Original authors and contributors; existing verified platform policy retained.

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.tyust.course.manager.AppearanceSettingsManager
import com.tyust.course.ui.system.LocalAppBackdrop
import com.tyust.course.ui.system.LocalControlBackdrop
import com.tyust.course.ui.system.LocalModalBackdrop
import com.tyust.course.ui.system.drawWallpaperPattern
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.*
import com.kyant.backdrop.Backdrop

private val LocalGlassWindowOwner = staticCompositionLocalOf<android.view.View?> { null }

/** Each actual window owns its sources; pages inside that window reuse the provided locals. */
@Composable
fun GlassWindowHost(modifier: Modifier = Modifier, capturePage: Boolean = true, isolatePage: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    val view = LocalView.current
    val appWallpaper = com.tyust.course.ui.theme.rememberAppWallpaperStyle()
    if (LocalGlassWindowOwner.current === view && !isolatePage) {
        Box(modifier.fillMaxSize()) { content() }
        return
    }
    val tier = rememberNavigationMaterialTier()
    val accessibility = rememberGlassAccessibilityMode()
    val optical = tier == NavigationMaterialTier.Refractive && !accessibility.highContrast
    val wallpaper = if (optical) rememberLayerBackdrop() else null
    val page = if (wallpaper != null && capturePage) rememberLayerBackdrop() else null
    val modal = if (wallpaper != null && page != null) rememberCombinedBackdrop(wallpaper, page) else wallpaper
    val density = LocalDensity.current
    val dialogs = rememberDialogHostState()
    CompositionLocalProvider(
        LocalGlassWindowOwner provides view,
        LocalAppBackdrop provides wallpaper,
        LocalControlBackdrop provides wallpaper,
        LocalModalBackdrop provides modal,
        LocalAppDialogBackdrop provides modal,
        LocalDialogHost provides dialogs.takeIf { modal != null }
    ) {
        GlassOverlayHost(modifier) {
            Canvas(Modifier.fillMaxSize().then(if (wallpaper != null) Modifier.layerBackdrop(wallpaper) else Modifier)) {
                drawWallpaperPattern(appWallpaper, microTexture = wallpaper != null)
            }
            Box(Modifier.fillMaxSize().then(if (page != null && dialogs.hasBlockingSurface) Modifier.layerBackdrop(page) else Modifier)
                .then(if (dialogs.hasBlockingSurface) Modifier.semantics { hideFromAccessibility() } else Modifier)) { content() }
            DialogHost(dialogs)
        }
    }
}
