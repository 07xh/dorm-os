package io.github.x07xh.xiaoxu.ui.v2

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import io.github.x07xh.xiaoxu.ui.PrimaryNavigationDrawer
import kotlinx.coroutines.launch
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
    content: @Composable (openNavigation: (() -> Unit)?) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize().background(XiaoxuPalette.PageGradient)) {
        val rail = uiV2Window(maxWidth.value, maxHeight.value) == UiV2Window.Expanded
        val canExpandSidebar = maxWidth.value >= 1100f
        val drawerEnabled = !rail && showNavigation
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        LaunchedEffect(drawerEnabled) {
            if (!drawerEnabled) drawerState.snapTo(DrawerValue.Closed)
        }
        val openNavigation: (() -> Unit)? = if (drawerEnabled) {
            { scope.launch { drawerState.open() }; Unit }
        } else null
        val backdrop = if (!rail && showNavigation && Build.VERSION.SDK_INT >= 33) {
            rememberLayerBackdrop()
        } else null
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = drawerEnabled && drawerState.isOpen,
            drawerContent = {
                PrimaryNavigationDrawer(selected = selected, onSelect = { page ->
                    onSelect(page)
                    scope.launch { drawerState.close() }
                }, onClose = { scope.launch { drawerState.close() } })
            }
        ) {
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
                        PrimarySideNavigation(selected = selected, onSelect = onSelect,
                            canExpand = canExpandSidebar)
                    }
                    Surface(
                        color = Color.Transparent,
                        modifier = Modifier.weight(1f).fillMaxSize().uiV2BackLayer(backPreview),
                        content = { content(openNavigation) }
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
        BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }
    }
}
