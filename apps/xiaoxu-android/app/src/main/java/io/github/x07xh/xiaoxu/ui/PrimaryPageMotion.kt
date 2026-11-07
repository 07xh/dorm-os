package io.github.x07xh.xiaoxu.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition

/**
 * Restore instantaneous navigation. Main tabs are directly composed, while
 * remaining secondary AnimatedContent hosts receive no enter/exit transition.
 * Never animate heavyweight screens, fade, slide, scale or interpolate size.
 */
private fun immediatePageSwitch(): ContentTransform = ContentTransform(
    targetContentEnter = EnterTransition.None,
    initialContentExit = ExitTransition.None,
    sizeTransform = null
)

internal fun xiaoxuPrimaryPageTransition(
    @Suppress("UNUSED_PARAMETER") forward: Boolean
): ContentTransform = immediatePageSwitch()

internal fun xiaoxuSettingsRouteTransition(
    @Suppress("UNUSED_PARAMETER") fromRoute: String,
    @Suppress("UNUSED_PARAMETER") toRoute: String
): ContentTransform = immediatePageSwitch()
