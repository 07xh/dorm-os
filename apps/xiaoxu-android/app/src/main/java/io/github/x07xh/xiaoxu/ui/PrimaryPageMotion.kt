package io.github.x07xh.xiaoxu.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn

/**
 * Stationary fade-through for all native page hosts:
 * - five main tabs
 * - My -> /**
 * No-blank page reveal: unlike sequential fade-through, the outgoing screen
 * never disappears before the next is visible. The incoming full-screen page
 * starts almost opaque, renders ABOVE the old one, and finishes at full alpha.
 *
 * A single static layer is always present: no lateral translation, scaling,
 * delayed fade, bounce, or a transparent gap exposing the shell background.
 * Existing AnimatedContent hosts retain all page state, business data and
 * bottom navigation.
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

nternal fun xiaoxuPrimaryPageTransition(
    @Suppress("UNUSED_PARAMETER") forward: Boolean
): ContentTransform = stablePageReveal(XiaoxuMotion.PageSwitchMs)

/** All settings and academic child routes use the same stable animation. */
internal fun xiaoxuSettingsRouteTransition(
    fromRoute: String,
    toRoute: String
): ContentTransform {
    if (fromRoute == toRoute) return ContentTransform(EnterTransition.None, ExitTransition.None, sizeTransform = null)
    return stablePageReveal(XiaoxuMotion.SettingsDetailMs)
}
