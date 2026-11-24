package io.github.x07xh.xiaoxu.ui.v2

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Handle-scoped drag avoids competing with the content's list and input gestures. */
internal class UiV2SheetDragState {
    var offset by mutableFloatStateOf(0f)
    var height by mutableFloatStateOf(0f)
}

@Composable
internal fun rememberUiV2SheetDragState(): UiV2SheetDragState =
    remember { UiV2SheetDragState() }

internal fun Modifier.uiV2SheetMotion(state: UiV2SheetDragState): Modifier =
    onSizeChanged { state.height = it.height.toFloat() }
        .graphicsLayer { translationY = state.offset }

@Composable
internal fun UiV2SheetHandle(
    state: UiV2SheetDragState,
    enabled: Boolean,
    onDismiss: () -> Unit,
    color: Color
) {
    val scope = rememberCoroutineScope()
    val mode = rememberUpdatedState(LocalUiV2Motion.current)
    val currentDismiss by rememberUpdatedState(onDismiss)
    val currentEnabled by rememberUpdatedState(enabled)
    val flingPx = with(LocalDensity.current) { UiV2SheetPolicy.FlingDpPerSecond.dp.toPx() }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val drag = rememberDraggableState { delta ->
        if (currentEnabled) state.offset = (state.offset + delta).coerceIn(0f, state.height.coerceAtLeast(0f))
    }
    androidx.compose.runtime.LaunchedEffect(enabled) {
        if (!enabled) {
            settleJob?.cancel()
            state.offset = 0f
        }
    }
    if (enabled) {
        Box(
            Modifier.fillMaxWidth().height(48.dp)
                .draggable(
                    state = drag,
                    orientation = Orientation.Vertical,
                    onDragStarted = { settleJob?.cancel() },
                    onDragStopped = { velocity ->
                        if (UiV2SheetPolicy.shouldDismiss(
                                currentEnabled, state.offset, state.height, velocity, flingPx
                            )
                        ) {
                            state.offset = 0f
                            currentDismiss()
                        } else {
                            settleJob?.cancel()
                            settleJob = scope.launch {
                                animate(
                                    initialValue = state.offset, targetValue = 0f,
                                    animationSpec = UiV2Motion.spec(mode.value, UiV2Motion.SheetSettleMs)
                                ) { value, _ -> state.offset = value.coerceAtLeast(0f) }
                            }
                        }
                    }
                )
                .semantics { contentDescription = "下拉或点击关闭" }
                .clickable(role = Role.Button, onClick = { if (currentEnabled) currentDismiss() }),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.width(UiV2SheetPolicy.HandleWidthDp.dp)
                .height(UiV2SheetPolicy.HandleHeightDp.dp)
                .clip(RoundedCornerShape(50)).background(color))
        }
    }
}
