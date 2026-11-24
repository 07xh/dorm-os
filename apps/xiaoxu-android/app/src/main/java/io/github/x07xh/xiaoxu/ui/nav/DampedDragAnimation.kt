// Based on Kyant0/AndroidLiquidGlass catalog components, Apache-2.0.
// Modified for SleepDown-Schedule.
// Ported to Xiaoxu (校序) for Issue #287; V2 uses the position spring's own velocity and a scoped animation lifecycle.
// Upstream: https://github.com/xiaomanjun233/SleepDown-Schedule
//           app/src/main/java/com/kyant/backdrop/catalog/utils/DampedDragAnimation.kt
// License: Apache-2.0 (original) + SleepDown source-visible license 1.1 (modifications).
package io.github.x07xh.xiaoxu.ui.nav

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

class DampedDragAnimation(
    animationScope: CoroutineScope,
    val initialValue: Float,
    val valueRange: ClosedRange<Float>,
    val visibilityThreshold: Float,
    val initialScale: Float,
    val pressedScale: Float,
    val onDragStarted: DampedDragAnimation.(position: Offset) -> Unit,
    val onDragStopped: DampedDragAnimation.() -> Unit,
    val onDrag: DampedDragAnimation.(size: IntSize, dragAmount: Offset) -> Unit,
    private val reducedMotion: Boolean = false,
) {

    private val valueAnimationSpec =
        spring(1f, 1000f, visibilityThreshold)
    private val pressProgressAnimationSpec =
        spring(1f, 1000f, 0.001f)
    private val scaleXAnimationSpec =
        spring(0.6f, 250f, 0.001f)
    private val scaleYAnimationSpec =
        spring(0.7f, 250f, 0.001f)

    private val valueAnimation =
        Animatable(initialValue, visibilityThreshold)
    private val pressProgressAnimation =
        Animatable(0f, 0.001f)
    private val scaleXAnimation =
        Animatable(initialScale, 0.001f)
    private val scaleYAnimation =
        Animatable(initialScale, 0.001f)

    private val lifecycleJob = SupervisorJob(animationScope.coroutineContext[Job])
    private val scope = CoroutineScope(animationScope.coroutineContext + lifecycleJob)

    private val directValue = mutableFloatStateOf(initialValue)
    private var releaseJob: Job? = null
    private var valueJob: Job? = null
    private var pressJob: Job? = null

    val value: Float get() = if (reducedMotion) directValue.floatValue else valueAnimation.value
    private val span: Float get() = valueRange.endInclusive - valueRange.start
    val progress: Float get() = if (span > 0f) (value - valueRange.start) / span else 0f
    val targetValue: Float get() = directValue.floatValue
    val pressProgress: Float get() = pressProgressAnimation.value
    val scaleX: Float get() = scaleXAnimation.value
    val scaleY: Float get() = scaleYAnimation.value
    // Animatable's velocity is in index units/second. No per-frame tracking jobs.
    val velocity: Float get() = if (reducedMotion) 0f else valueAnimation.velocity
    val normalizedVelocity: Float get() = if (span > 0f) velocity / span else 0f

    fun dispose() { lifecycleJob.cancel() }

    val modifier: Modifier = Modifier.pointerInput(this) {
        inspectDragGestures(
            onDragStart = { down ->
                onDragStarted(down.position)
                press()
            },
            onDragEnd = {
                onDragStopped()
                release()
            },
            onDragCancel = {
                onDragStopped()
                release()
            }
        ) { change, dragAmount ->
            onDrag(size, dragAmount)
        }
    }

    fun press() {
        if (reducedMotion) return
        releaseJob?.cancel()
        pressJob?.cancel()
        pressJob = scope.launch {
            launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(pressedScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(pressedScale, scaleYAnimationSpec) }
        }
    }

    fun release() {
        if (reducedMotion) return
        releaseJob?.cancel()
        releaseJob = scope.launch {
            awaitFrame()
            if (value != targetValue) {
                val threshold = (valueRange.endInclusive - valueRange.start) * 0.025f
                snapshotFlow { abs(valueAnimation.value - targetValue) }
                    .filter { it <= threshold }
                    .first()
            }
            pressJob?.cancel()
            launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(initialScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(initialScale, scaleYAnimationSpec) }
        }
    }

    fun updateValue(value: Float) {
        val targetValue = value.coerceIn(valueRange)
        // Publish the requested index synchronously so consecutive MOVE events
        // cannot calculate their delta from an earlier coroutine's target.
        directValue.floatValue = targetValue
        if (reducedMotion) return
        valueJob?.cancel()
        valueJob = scope.launch {
            valueAnimation.animateTo(targetValue, valueAnimationSpec)
        }
    }

    fun animateToValue(value: Float) {
        if (reducedMotion) { updateValue(value); return }
        press()
        updateValue(value)
        release()
    }
}
