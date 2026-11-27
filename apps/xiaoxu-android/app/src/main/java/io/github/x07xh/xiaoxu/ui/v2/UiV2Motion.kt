package io.github.x07xh.xiaoxu.ui.v2

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
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
    const val StandardMs = 240
    const val SheetSettleMs = 260
    const val PressScale = 0.97f
    val Easing = FastOutSlowInEasing

    fun spec(mode: UiV2MotionMode, durationMs: Int): AnimationSpec<Float> = when (mode) {
        UiV2MotionMode.Reduced -> snap()
        UiV2MotionMode.Fluid -> spring(dampingRatio = 0.9f, stiffness = 550f, visibilityThreshold = 0.001f)
        UiV2MotionMode.Standard -> tween(durationMs, easing = Easing)
    }
}

/** Use the existing clickable interaction stream instead of intercepting gestures. */
@Composable
internal fun Modifier.uiV2Press(source: InteractionSource, enabled: Boolean = true): Modifier {
    val mode = LocalUiV2Motion.current
    // Disabled and reduced-motion controls need no press collector or animation layer.
    if (!enabled || mode == UiV2MotionMode.Reduced) return this
    val pressed by source.collectIsPressedAsState()
    val active = pressed
    val scale = animateFloatAsState(
        targetValue = if (active) UiV2Motion.PressScale else 1f,
        animationSpec = UiV2Motion.spec(mode, if (active) UiV2Motion.PressMs else UiV2Motion.ReleaseMs),
        label = "v2-press"
    )
    // Read the animated value only in the graphics phase, not in composition/layout.
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}
