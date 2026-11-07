package io.github.x07xh.xiaoxu.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Native page motion, without a new navigation owner or a second screen shell.
 *
 * Source reference: SleepDown-Schedule by xiaomanjun233 (2026), subject to
 * SleepDown Attribution Noncommercial Source-Available License 1.1.
 * HomeSwitchMotion.kt: 360ms, monotone cubic page motion.
 * SettingsNavigationMotion.kt: 500ms Miuix-style damped navigation curve.
 * Port adapts the above motions to Xiaoxu's existing AnimatedContent hosts.
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

/** Ported from SleepDown's SettingsNavigationMotion.kt (adapted to route depth). */
private class MiuixSettingsNavigationEasing(
    response: Float = 0.8f,
    damping: Float = 0.95f
) : Easing {
    private val decay: Float
    private val frequency: Float
    private val phase: Float

    init {
        val omega = 2.0 * PI / response
        val stiffness = omega * omega
        val friction = damping * 4.0 * PI / response
        frequency = (sqrt(4.0 * stiffness - friction * friction) / 2.0).toFloat()
        decay = (-friction / 2.0).toFloat()
        phase = decay / frequency
    }

    override fun transform(fraction: Float): Float {
        val time = fraction.toDouble()
        val attenuation = exp(decay * time)
        return (attenuation * (-cos(frequency * time) + phase * sin(frequency * time)) + 1.0).toFloat()
    }
}

private val SettingsPageSlide = tween<IntOffset>(
    durationMillis = XiaoxuMotion.SettingsDetailMs, easing = MiuixSettingsNavigationEasing()
)

private fun settingsDepth(route: String): Int = when (route) {
    "home" -> 0
    "licenses" -> 2
    else -> 1
}

/** Root -> detail pushes from right; back returns to the actual original side. */
internal fun xiaoxuSettingsRouteTransition(
    fromRoute: String,
    toRoute: String
): ContentTransform {
    val direction = settingsDepth(toRoute).compareTo(settingsDepth(fromRoute))
    return when {
        direction > 0 -> slideInHorizontally(SettingsPageSlide) { width -> width } togetherWith
            slideOutHorizontally(SettingsPageSlide) { width -> -width / 4 }
        direction < 0 -> slideInHorizontally(SettingsPageSlide) { width -> -width / 4 } togetherWith
            slideOutHorizontally(SettingsPageSlide) { width -> width }
        else -> EnterTransition.None togetherWith ExitTransition.None
    }
}
