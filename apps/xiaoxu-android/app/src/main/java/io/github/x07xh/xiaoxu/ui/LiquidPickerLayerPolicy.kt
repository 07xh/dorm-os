package io.github.x07xh.xiaoxu.ui

// SPDX-License-Identifier: GPL-3.0-only
// Upstream: znjhahaha/zhengfang-apk@bb3b80a6d64e4c2057c0544bb417696ceaa2a9ed, app/src/main/java/com/tyust/course/ui/system/glass/LiquidPickerLayerPolicy.kt.
// Original authors and contributors. See upstream license and attribution.

/** Keeps the picker's outer optical boundary stable while the body changes topology. */
internal object LiquidPickerLayerPolicy {
    enum class Layers(
        val headerLensAlpha: Float,
        val mergedBodyLensAlpha: Float,
        val separatedBodyLensAlpha: Float,
        val bridgeOverlayAlpha: Float,
        val perimeterInteractionProgress: Float
    ) {
        HeaderOnly(1f, 0f, 0f, 0f, 0f),
        Merging(1f, 1f, 0f, 1f, 0f),
        Separated(1f, 0f, 1f, 0f, 0f)
    }

    fun resolve(
        bodyActive: Boolean,
        bodyExtendsBeyondHeader: Boolean,
        bodySeparated: Boolean,
        bodyAlpha: Float,
        @Suppress("UNUSED_PARAMETER") interactionProgress: Float
    ): Layers {
        if (!bodyActive || !bodyExtendsBeyondHeader || bodyAlpha <= 0f) return Layers.HeaderOnly
        return if (bodySeparated) Layers.Separated else Layers.Merging
    }

    fun bodyExtendsBeyondHeader(
        bodyActive: Boolean,
        actualBodyBottom: Float,
        headerBottom: Float,
        threshold: Float
    ): Boolean = bodyActive && actualBodyBottom > headerBottom + threshold
}
