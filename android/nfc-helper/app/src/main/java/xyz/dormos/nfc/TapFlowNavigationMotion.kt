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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight

/**
 * Motion timings measured from the supplied White Autumn Rain 2.1 interaction
 * recording. These are deliberately short, fixed transitions: no spring tail,
 * overshoot, blur, glass layer, or gesture-driven page movement.
 */
internal const val TAPFLOW_PAGE_TRANSITION_MS = 280
internal const val TAPFLOW_BUTTON_TRANSITION_MS = 250
private const val TAPFLOW_PAGE_FADE_IN_MS = 180
private const val TAPFLOW_PAGE_FADE_OUT_MS = 145

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
        modifier = modifier,
        contentAlignment = Alignment.TopStart,
        transitionSpec = { tapFlowScreenTransition() },
        contentKey = { it },
        label = "TapFlow.MainTransition"
    ) { tab ->
        stateHolder.SaveableStateProvider("tapflow-tab-$tab") {
            key(tab) {
                content(tab)
            }
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
        initialOffsetX = { width -> width * direction },
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
        targetOffsetX = { width -> -width * direction },
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

    NavigationBar(
        containerColor = palette.surface,
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
        ) {
            val itemWidth = maxWidth / tapFlowNavEntries.size
            val indicatorWidth = 42.dp
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
                    .size(width = indicatorWidth, height = 28.dp)
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
            durationMillis = 170,
            easing = FastOutSlowInEasing
        ),
        label = "TapFlow.ButtonTransition.color-${entry.tabId}"
    )

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(top = 4.dp),
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
        Spacer(Modifier.height(2.dp))
        Text(
            text = entry.label,
            color = tint,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
