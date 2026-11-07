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
 * Uses SleepDown's single-page navigation approach, adapted to a subtle
 * 220/200ms foreground-only slide without spring rebound or crossfade.
 * Not a byte-for-byte motion port.
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
 * Move only the incoming opaque page by a small amount, never the whole screen.
 * Outgoing content stays fixed and is covered by the new page. This preserves
 * directionality and prevents two heavyweight pages from sliding in opposite
 * directions, which looked like the whole UI was shaking.
 */
internal fun xiaoxuPrimaryPageTransition(forward: Boolean): ContentTransform {
    val direction = if (forward) 1 else -1
    return slideInHorizontally(PrimaryPageSlide) { width ->
        direction * minOf(width / 18, 84)
    } togetherWith ExitTransition.None
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
 * A tiny foreground-only transition: background page and navigation remain
 * stationary on both push and back. Repeated taps should never wobble the UI.
 */
internal fun xiaoxuSettingsRouteTransition(
    fromRoute: String,
    toRoute: String
): ContentTransform {
    val direction = settingsDepth(toRoute).compareTo(settingsDepth(fromRoute))
    return when {
        direction > 0 -> slideInHorizontally(SettingsPageSlide) { width ->
            minOf(width / 20, 72)
        } togetherWith ExitTransition.None
        direction < 0 -> EnterTransition.None togetherWith
            slideOutHorizontally(SettingsPageSlide) { width -> minOf(width / 20, 72) }
        else -> EnterTransition.None togetherWith ExitTransition.None
    }
}
