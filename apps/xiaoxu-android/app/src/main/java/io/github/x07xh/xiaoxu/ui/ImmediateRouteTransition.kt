package io.github.x07xh.xiaoxu.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition

/**
 * Sole navigation adapter for secondary pages that still use AnimatedContent.
 * Main tabs already compose only the selected page in MainActivity.
 * Never reintroduce page-level spring, slide, crossfade or size animation.
 */
internal fun xiaoxuSettingsRouteTransition(
    @Suppress("UNUSED_PARAMETER") fromRoute: String,
    @Suppress("UNUSED_PARAMETER") toRoute: String
): ContentTransform = ContentTransform(
    targetContentEnter = EnterTransition.None,
    initialContentExit = ExitTransition.None,
    sizeTransform = null
)
