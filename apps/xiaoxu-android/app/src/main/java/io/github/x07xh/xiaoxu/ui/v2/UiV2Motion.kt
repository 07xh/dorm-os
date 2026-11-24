package io.github.x07xh.xiaoxu.ui.v2

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Compose-native motion tokens based on DSH Go AppMotion (MIT-marked).
 * See docs/ui-v2/README.md for provenance. Animation never owns business state.
 */
internal object UiV2Motion {
    const val PressMs = 90
    const val ReleaseMs = 150
    const val QuickMs = 160
    const val PressScale = 0.97f
    val Easing = FastOutSlowInEasing
}

/** Use the existing clickable interaction stream instead of intercepting gestures. */
@Composable
internal fun Modifier.uiV2Press(source: InteractionSource, enabled: Boolean = true): Modifier {
    val pressed by source.collectIsPressedAsState()
    val active = enabled && pressed
    val scale = animateFloatAsState(
        targetValue = if (active) UiV2Motion.PressScale else 1f,
        animationSpec = tween(
            durationMillis = if (active) UiV2Motion.PressMs else UiV2Motion.ReleaseMs,
            easing = UiV2Motion.Easing
        ),
        label = "v2-press"
    )
    // Read the animated value only in the graphics phase, not in composition/layout.
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}
