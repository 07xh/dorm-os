// Adapted from znjhahaha/zhengfang-apk v1.0.83 (GPL-3.0); renamed symbols and package only.
package io.github.x07xh.xiaoxu.ui.northglass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Retain the entering icon and at most one outgoing icon during rapid navigation. */
internal fun retiredNavigationIcons(count: Int, previous: Int, next: Int): List<Int> =
    (0 until count).filter { it != previous && it != next }

/** One clock per tab, shared by the visible glyph, optical copy and minimized capsule.
 * Only the current and immediately outgoing icon may keep animating.
 */
@Stable
class NorthNavigationIconPlayback(count: Int, private val scope: CoroutineScope) {
    private val clocks = List(count) { Animatable(1f) }
    private val jobs = arrayOfNulls<Job>(count)
    private val pending = BooleanArray(count)
    // Draw-phase snapshot gate: retiring an icon restores its resting pose
    // immediately without scheduling another coroutine just to reset the clock.
    private val visible = List(count) { mutableStateOf(false) }
    private var selected = -1
    private var reduced = false

    fun phase(index: Int): Float = if (visible[index].value) clocks[index].value else 1f

    fun isRunning(index: Int): Boolean = jobs[index]?.isActive == true

    fun select(index: Int, reduceMotion: Boolean) {
        if (reduced != reduceMotion) {
            reduced = reduceMotion
            if (reduced) clocks.indices.forEach { stop(it) }
        }
        if (selected == index) return
        // Let the immediately outgoing icon finish its current cycle. A third
        // overlapping selection retires older icons instead of accumulating
        // independent 480ms draws in both visible and optical-copy layers.
        if (selected >= 0) pending[selected] = false
        retiredNavigationIcons(clocks.size, selected, index).forEach { stop(it) }
        selected = index
        if (!reduced) replay(index)
    }

    fun replay(index: Int) {
        if (reduced || index != selected) return
        if (isRunning(index)) {
            pending[index] = true
            return
        }
        val clock = clocks[index]
        jobs[index] = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            do {
                pending[index] = false
                // The renderer has identical poses and zero velocity at 0 and 1.
                // Only a completed gesture can enter a new cycle.
                clock.snapTo(0f)
                visible[index].value = true
                clock.animateTo(1f, tween(480, easing = LinearEasing))
            } while (pending[index] && !reduced && index == selected)
            visible[index].value = false
        }
    }

    private fun stop(index: Int) {
        pending[index] = false
        jobs[index]?.cancel()
        jobs[index] = null
        visible[index].value = false
    }

    fun dispose() = clocks.indices.forEach { stop(it) }
}

@Composable
fun rememberNorthNavigationIconPlayback(count: Int, selected: Int, reduced: Boolean): NorthNavigationIconPlayback {
    val scope = rememberCoroutineScope()
    val state = remember(count, scope) { NorthNavigationIconPlayback(count, scope) }
    LaunchedEffect(state, selected, reduced) { state.select(selected, reduced) }
    DisposableEffect(state) { onDispose { state.dispose() } }
    return state
}
