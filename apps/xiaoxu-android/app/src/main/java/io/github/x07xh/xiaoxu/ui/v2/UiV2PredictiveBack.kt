package io.github.x07xh.xiaoxu.ui.v2

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

internal class UiV2BackPreview {
    val progress = mutableFloatStateOf(0f)
    val fromRight = mutableStateOf(false)
    var settle: Job? = null
}

@Composable
internal fun rememberUiV2BackPreview(routeKey: String): UiV2BackPreview =
    remember(routeKey) { UiV2BackPreview() }

/** Android dispatch owns the commit. A cancelled gesture only restores the layer. */
@Composable
internal fun UiV2PredictiveBack(
    enabled: Boolean,
    preview: UiV2BackPreview,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val mode = rememberUpdatedState(LocalUiV2Motion.current)
    val latestPreview = rememberUpdatedState(preview)
    val latestEnabled = rememberUpdatedState(enabled)
    val latestBack = rememberUpdatedState(onBack)
    PredictiveBackHandler(enabled = enabled) { events ->
        val active = latestPreview.value
        active.settle?.cancel()
        active.progress.floatValue = 0f
        try {
            events.collect { event ->
                active.fromRight.value = event.swipeEdge == BackEventCompat.EDGE_RIGHT
                active.progress.floatValue = if (mode.value == UiV2MotionMode.Reduced) 0f
                    else UiV2BackPolicy.progress(event.progress)
            }
            active.progress.floatValue = 0f
            if (latestEnabled.value && latestPreview.value === active) latestBack.value()
        } catch (cancelled: CancellationException) {
            active.settle = scope.launch {
                animate(
                    initialValue = active.progress.floatValue,
                    targetValue = 0f,
                    animationSpec = UiV2Motion.spec(mode.value, UiV2Motion.QuickMs)
                ) { value, _ -> active.progress.floatValue = value }
            }
            throw cancelled
        }
    }
}

internal fun Modifier.uiV2BackLayer(preview: UiV2BackPreview?): Modifier =
    if (preview == null) this else graphicsLayer {
        val p = preview.progress.floatValue
        scaleX = UiV2BackPolicy.scale(p)
        scaleY = scaleX
        translationX = UiV2BackPolicy.translation(p, size.width, preview.fromRight.value)
    }
