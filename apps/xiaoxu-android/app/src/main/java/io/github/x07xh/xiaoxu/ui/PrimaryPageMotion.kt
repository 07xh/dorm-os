package io.github.x07xh.xiaoxu.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith

/**
 * Stationary fade-through for all native page hosts:
 * - five main tabs
 * - My -> settings -> timetable editor
 * - Academic overview -> grades/exams/second class/services
 * - timetable -> timetable settings
 *
 * Old/new screens MUST NEVER translate, spring or scale the entire layout.
 * This is not a duplicate navigation stack: existing AnimatedContent owns
 * the route state, while the bottom dock and parent geometry stay stationary.
 *
 * Reference: the restrained one-page navigation rhythm used by SleepDown.
 * This is an independently adapted alpha-only transition, not a copied port.
 */
private fun stationaryFadeThrough(durationMillis: Int): ContentTransform {
    // Sequential phases avoid translucent text ghosting from a crossfade.
    val exitMillis = (durationMillis * 35 / 100).coerceAtLeast(1)
    val enterMillis = (durationMillis - exitMillis).coerceAtLeast(1)
    return (fadeIn(
        animationSpec = tween(
            durationMillis = enterMillis,
            delayMillis = exitMillis,
            easing = FastOutSlowInEasing
        )
    ) togetherWith fadeOut(
        animationSpec = tween(durationMillis = exitMillis)
    ))
}

/** API preserves forward for existing nav callers; no directional slide is applied. */
internal fun xiaoxuPrimaryPageTransition(
    @Suppress("UNUSED_PARAMETER") forward: Boolean
): ContentTransform = stationaryFadeThrough(XiaoxuMotion.PageSwitchMs)

/** All settings and academic child routes use the same stable animation. */
internal fun xiaoxuSettingsRouteTransition(
    fromRoute: String,
    toRoute: String
): ContentTransform {
    if (fromRoute == toRoute) return EnterTransition.None togetherWith ExitTransition.None
    return stationaryFadeThrough(XiaoxuMotion.SettingsDetailMs)
}
