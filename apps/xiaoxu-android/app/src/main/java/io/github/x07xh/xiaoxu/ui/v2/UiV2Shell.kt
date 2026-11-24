package io.github.x07xh.xiaoxu.ui.v2

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import io.github.x07xh.xiaoxu.MainDestination
import io.github.x07xh.xiaoxu.ui.PrimaryBottomNavigation
import io.github.x07xh.xiaoxu.ui.PrimaryBottomNavigationReserve
import io.github.x07xh.xiaoxu.ui.PrimarySideNavigation
import io.github.x07xh.xiaoxu.ui.XiaoxuPalette

/**
 * V2 presentation boundary. The caller owns destinations, back stack, data,
 * saveable state and callbacks. Existing navigation chrome is a migration adapter.
 * The real backdrop is captured once; the dock sits outside the capture subtree.
 */
@Composable
internal fun UiV2Shell(
    selected: MainDestination,
    showNavigation: Boolean,
    onSelect: (MainDestination) -> Unit,
    backPreview: UiV2BackPreview? = null,
    content: @Composable () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize().background(XiaoxuPalette.PageGradient)) {
        val rail = uiV2Window(maxWidth.value, maxHeight.value) == UiV2Window.Expanded
        val backdrop = if (!rail && showNavigation && Build.VERSION.SDK_INT >= 33) {
            rememberLayerBackdrop()
        } else null
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                modifier = Modifier.fillMaxSize().then(
                    if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier
                ),
                containerColor = Color.Transparent,
                topBar = {},
                bottomBar = {
                    if (!rail && showNavigation) {
                        PrimaryBottomNavigationReserve(glass = backdrop != null)
                    }
                }
            ) { padding ->
                Row(Modifier.fillMaxSize().padding(padding)) {
                    if (rail && showNavigation) {
                        PrimarySideNavigation(selected = selected, onSelect = onSelect)
                    }
                    Surface(
                        color = Color.Transparent,
                        modifier = Modifier.weight(1f).fillMaxSize().uiV2BackLayer(backPreview),
                        content = content
                    )
                }
            }
            if (!rail && showNavigation) {
                Box(Modifier.align(Alignment.BottomCenter)) {
                    PrimaryBottomNavigation(selected = selected, backdrop = backdrop, onSelect = onSelect)
                }
            }
        }
    }
}
