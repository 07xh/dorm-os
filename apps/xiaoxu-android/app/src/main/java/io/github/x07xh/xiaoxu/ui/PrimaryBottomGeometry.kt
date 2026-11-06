package io.github.x07xh.xiaoxu.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Geometry ported from 北化课表 v1.0.83 ScreenMetrics / NavBarMetrics (GPL-3.0).
 * The bar and Scaffold's content reserve MUST read this exact same geometry.
 */
@Immutable
internal data class PrimaryBottomGeometry(
    val trackHeight: Dp,
    val horizontalPadding: Dp,
    val topPadding: Dp,
    val bottomPadding: Dp,
    val indicatorVerticalPadding: Dp
) {
    val contentReserve: Dp get() = trackHeight + topPadding + bottomPadding
}

private fun interpolateDp(start: Dp, end: Dp, fraction: Float): Dp =
    start + (end - start) * fraction.coerceIn(0f, 1f)

@Composable
internal fun rememberPrimaryBottomGeometry(glass: Boolean): PrimaryBottomGeometry {
    if (!glass) {
        return remember { PrimaryBottomGeometry(64.dp, 20.dp, 4.dp, 10.dp, 6.dp) }
    }
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val window = LocalWindowInfo.current.containerSize
    val status = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val width = with(density) { window.width.toDp() }.takeIf { it > 0.dp }
        ?: configuration.screenWidthDp.dp
    val height = with(density) { window.height.toDp() }.takeIf { it > 0.dp }
        ?: configuration.screenHeightDp.dp
    return remember(width, height, status, nav) {
        // Exact continuous adaptation: loose 20:9 phone stays at original sizes.
        val usable = (height - status - nav).coerceAtLeast(0.dp)
        val squeeze = ((720f - usable.value) / 160f).coerceIn(0f, 1f)
        val narrow = ((380f - width.value) / 40f).coerceIn(0f, 1f)
        val track = interpolateDp(72.dp, 62.dp, squeeze)
        val indicator = interpolateDp(56.dp, 48.dp, squeeze)
        val block = interpolateDp(8.dp, 6.dp, squeeze)
        PrimaryBottomGeometry(
            trackHeight = track,
            horizontalPadding = interpolateDp(16.dp, 10.dp, narrow),
            topPadding = block,
            bottomPadding = block,
            indicatorVerticalPadding = (track - indicator) / 2f
        )
    }
}
