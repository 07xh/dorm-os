package io.github.x07xh.xiaoxu.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn

/**
 * Keep the outgoing screen fully visible until the incoming screen covers it.
 * A nearly opaque foreground fade avoids the blank frames caused by the old
 * delayed fade-through. No full-screen movement, fade-out, scale, or spring.
 *
 * This is the only shared transition implementation: main tabs, settings,
 * academic child pages and timetable settings retain their existing hosts.
 */
private fun stablePageReveal(durationMillis: Int): ContentTransform = ContentTransform(
    targetContentEnter = fadeIn(
        initialAlpha = 0.94f,
        animationSpec = tween(
            durationMillis = durationMillis.coerceAtMost(140),
            easing = FastOutSlowInEasing
        )
    ),
    initialContentExit = ExitTransition.None,
    targetContentZIndex = 1f,
    sizeTransform = null
)

/** Preserve existing navigation call sites without moving full-page content. */
internal fun xiaoxuPrimaryPageTransition(
    @Suppress("UNUSED_PARAMETER") forward: Boolean
): ContentTransform = stablePageReveal(XiaoxuMotion.PageSwitchMs)

/** Settings and academic child routes reuse exactly the same no-blank reveal. */
internal fun xiaoxuSettingsRouteTransition(
    fromRoute: String,
    toRoute: String
): ContentTransform {
    if (fromRoute == toRoute) {
        return ContentTransform(
            targetContentEnter = EnterTransition.None,
            initialContentExit = ExitTransition.None,
            sizeTransform = null
        )
    }
    return stablePageReveal(XiaoxuMotion.SettingsDetailMs)
}
