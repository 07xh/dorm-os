package io.github.x07xh.xiaoxu.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset

/**
 * Native page motion, without a new navigation owner or a second screen shell.
 *
 * Source reference: SleepDown-Schedule by xiaomanjun233 (2026), subject to
 * SleepDown Attribution Noncommercial Source-Available License 1.1.
 * Uses SleepDown's single-page navigation approach, adapted to Xiaoxu's
 * lighter 280/320ms, non-overshooting timings. Not a byte-for-byte motion port.
 * The same AnimatedContent hosts are retained.
 * https://github.com/xiaomanjun233/SleepDown-Schedule
 *
 * The dock, page business state, and backdrop ownership are unchanged.
 */

private val PrimaryPageSlide = tween<IntOffset>(
    durationMillis = XiaoxuMotion.PageSwitchMs,
    easing = CubicBezierEasing(0.22f, 0.72f, 0.20f, 1f)
)

/**
 * Full-width, opaque translations keep the outgoing and incoming pages from
 * producing the text ghosting of the old crossfade. The single page layer moves
 * as one group, avoiding per-card animation layers on heavy task/timetable pages.
 * Rapid reversals are handled by AnimatedContent's existing transition state.
 */
internal fun xiaoxuPrimaryPageTransition(forward: Boolean): ContentTransform {
    val direction = if (forward) 1 else -1
    return slideInHorizontally(PrimaryPageSlide) { width -> direction * width } togetherWith
        slideOutHorizontally(PrimaryPageSlide) { width -> -direction * width }
}

/**
 * Monotonic easing avoids perceptible spring tails when returning rapidly.
 * Unlike a damped overshoot, the screen lands exactly at its final position.
 */
private val SettingsPageSlide = tween<IntOffset>(
    durationMillis = XiaoxuMotion.SettingsDetailMs,
    easing = CubicBezierEasing(0.22f, 0.74f, 0.20f, 1f)
)

private fun settingsDepth(route: String): Int = when (route) {
    "home" -> 0
    "licenses" -> 2
    else -> 1
}

/**
 * Small, short, non-bouncing navigation. Only one page moves at a time.
 * Screen-width pushes caused excessive travel on phone and tablet.
 */
internal fun xiaoxuSettingsRouteTransition(
    fromRoute: String,
    toRoute: String
): ContentTransform {
    val direction = settingsDepth(toRoute).compareTo(settingsDepth(fromRoute))
    return when {
        direction > 0 -> slideInHorizontally(SettingsPageSlide) { width -> width / 6 } togetherWith
            ExitTransition.None
        direction < 0 -> EnterTransition.None togetherWith
            slideOutHorizontally(SettingsPageSlide) { width -> width / 6 }
        else -> EnterTransition.None togetherWith ExitTransition.None
    }
}
