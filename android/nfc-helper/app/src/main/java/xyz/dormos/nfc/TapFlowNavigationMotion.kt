package xyz.dormos.nfc

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight

/**
 * Short, low-cost timing inspired by the supplied interaction recording.
 * This is a visual approximation, not measured original APK parameters:
 * no spring tail,
 * overshoot, blur, glass layer, or gesture-driven page movement.
 */
internal const val TAPFLOW_PAGE_TRANSITION_MS = 210
internal const val TAPFLOW_BUTTON_TRANSITION_MS = 185
internal const val TAPFLOW_BOTTOM_NAV_HEIGHT_DP = 64
private const val TAPFLOW_PAGE_FADE_IN_MS = 155
private const val TAPFLOW_PAGE_FADE_OUT_MS = 120

/**
 * The main shell owns tab changes. SaveableStateHolder keeps each tab's local
 * rememberSaveable state when AnimatedContent disposes the outgoing page.
 */
@Composable
internal fun TapFlowMainTransition(
    currentTab: Int,
    modifier: Modifier,
    stateHolder: SaveableStateHolder,
    content: @Composable (Int) -> Unit
) {
    AnimatedContent(
        targetState = currentTab,
        modifier = modifier.clipToBounds(),
        contentAlignment = Alignment.TopStart,
        transitionSpec = { tapFlowScreenTransition() },
        contentKey = { it },
        label = "TapFlow.MainTransition"
    ) { tab ->
        stateHolder.SaveableStateProvider("tapflow-tab-$tab") {
            content(tab)
        }
    }
}

/**
 * Separate screen-motion policy, analogous to the reference app's
 * ScreenTransition layer. A forward tab change enters from the right; a
 * backward change enters from the left. The fixed bottom bar is outside this
 * transition and never moves with page content.
 */
internal fun AnimatedContentTransitionScope<Int>.tapFlowScreenTransition(): ContentTransform {
    val direction = tabTransitionDirection(initialState, targetState)
    val enter = slideInHorizontally(
        initialOffsetX = { width -> (width / 9) * direction },
        animationSpec = tween(
            durationMillis = TAPFLOW_PAGE_TRANSITION_MS,
            easing = FastOutSlowInEasing
        )
    ) + fadeIn(
        animationSpec = tween(
            durationMillis = TAPFLOW_PAGE_FADE_IN_MS,
            easing = LinearOutSlowInEasing
        )
    )
    val exit = slideOutHorizontally(
        targetOffsetX = { width -> -(width / 16) * direction },
        animationSpec = tween(
            durationMillis = TAPFLOW_PAGE_TRANSITION_MS,
            easing = FastOutLinearInEasing
        )
    ) + fadeOut(
        animationSpec = tween(
            durationMillis = TAPFLOW_PAGE_FADE_OUT_MS,
            easing = FastOutLinearInEasing
        )
    )
    return enter togetherWith exit
}

private data class TapFlowNavEntry(
    val tabId: Int,
    val icon: Int,
    val label: String
)

private val tapFlowNavEntries = listOf(
    TapFlowNavEntry(0, 0, "首页"),
    TapFlowNavEntry(2, 1, "场景"),
    TapFlowNavEntry(1, 2, "工具")
)

/**
 * A fixed Material navigation surface with one moving selection capsule.
 * Its position is animated in the placement phase, so each animation frame
 * does not recompose the complete navigation row.
 */
@Composable
internal fun TapFlowBottomNavigation(
    currentTab: Int,
    palette: V2Palette,
    onSelect: (Int) -> Unit
) {
    val selectedIndex = tapFlowNavEntries.indexOfFirst { it.tabId == currentTab }
        .coerceAtLeast(0)

    // Avoid Material NavigationBar's extra insets/elevation and the oversized
    // rectangular press-state overlay. A single plain background owns the bar.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.surface)
            .navigationBarsPadding()
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(TAPFLOW_BOTTOM_NAV_HEIGHT_DP.dp)
                .clipToBounds()
        ) {
            val itemWidth = maxWidth / tapFlowNavEntries.size
            val indicatorWidth = 54.dp
            val targetOffset = itemWidth * selectedIndex +
                (itemWidth - indicatorWidth) / 2
            val indicatorOffset by animateDpAsState(
                targetValue = targetOffset,
                animationSpec = tween(
                    durationMillis = TAPFLOW_BUTTON_TRANSITION_MS,
                    easing = FastOutSlowInEasing
                ),
                label = "TapFlow.ButtonTransition.indicator"
            )

            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = indicatorOffset.roundToPx(),
                            y = 5.dp.roundToPx()
                        )
                    }
                    .size(width = indicatorWidth, height = 29.dp)
                    .clip(RoundedCornerShape(50))
                    .background(palette.accentLow)
            )

            Row(Modifier.fillMaxSize()) {
                tapFlowNavEntries.forEach { entry ->
                    TapFlowButtonTransition(
                        entry = entry,
                        selected = currentTab == entry.tabId,
                        palette = palette,
                        onClick = { onSelect(entry.tabId) }
                    )
                }
            }
        }
    }
}

/**
 * ButtonTransition layer: only icon/label tint changes here. The shared
 * capsule is animated once by the parent, avoiding three competing indicators.
 */
@Composable
private fun RowScope.TapFlowButtonTransition(
    entry: TapFlowNavEntry,
    selected: Boolean,
    palette: V2Palette,
    onClick: () -> Unit
) {
    val tint by animateColorAsState(
        targetValue = if (selected) palette.blue else palette.muted,
        animationSpec = tween(
            durationMillis = 130,
            easing = FastOutSlowInEasing
        ),
        label = "TapFlow.ButtonTransition.color-${entry.tabId}"
    )

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .padding(horizontal = 5.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(16.dp))
            // Selection is already communicated by the capsule and tint.
            // Suppress the full-cell rectangular ripple seen in the screenshot.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick
            )
            .semantics { this.selected = selected },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp),
            contentAlignment = Alignment.Center
        ) {
            V2NavIcon(which = entry.icon, color = tint)
        }
        Spacer(Modifier.height(1.dp))
        Text(
            text = entry.label,
            color = tint,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
