package io.github.x07xh.xiaoxu.ui

// SPDX-License-Identifier: GPL-3.0-only
// Source: znjhahaha/zhengfang-apk @ bb3b80a6d64e4c2057c0544bb417696ceaa2a9ed, app/src/test/java/com/tyust/course/ui/system/glass/LiquidPickerLayerPolicyTest.kt
// Original authors and contributors. Package adaptation only.

import org.junit.Assert.assertEquals
import org.junit.Test

class LiquidPickerLayerPolicyTest {
    @Test
    fun headerLensRemainsStableWhileBodyMorphsAndSeparates() {
        val collapsed = LiquidPickerLayerPolicy.resolve(
            bodyActive = false,
            bodyExtendsBeyondHeader = false,
            bodySeparated = false,
            bodyAlpha = 0f,
            interactionProgress = 0f
        )
        val merging = LiquidPickerLayerPolicy.resolve(
            bodyActive = true,
            bodyExtendsBeyondHeader = true,
            bodySeparated = false,
            bodyAlpha = 1f,
            interactionProgress = 1f
        )
        val separated = LiquidPickerLayerPolicy.resolve(
            bodyActive = true,
            bodyExtendsBeyondHeader = true,
            bodySeparated = true,
            bodyAlpha = 1f,
            interactionProgress = 0.5f
        )

        listOf(collapsed, merging, separated).forEach { state ->
            assertEquals(1f, state.headerLensAlpha, 0f)
            assertEquals(0f, state.perimeterInteractionProgress, 0f)
        }
        assertEquals(0f, collapsed.mergedBodyLensAlpha, 0f)
        assertEquals(0f, collapsed.bridgeOverlayAlpha, 0f)
        assertEquals(1f, merging.mergedBodyLensAlpha, 0f)
        assertEquals(1f, merging.bridgeOverlayAlpha, 0f)
        assertEquals(0f, merging.separatedBodyLensAlpha, 0f)
        assertEquals(0f, separated.mergedBodyLensAlpha, 0f)
        assertEquals(0f, separated.bridgeOverlayAlpha, 0f)
        assertEquals(1f, separated.separatedBodyLensAlpha, 0f)
    }

    @Test
    fun bodyInsideHeaderAlwaysResolvesToHeaderOnly() {
        val state = LiquidPickerLayerPolicy.resolve(
            bodyActive = true,
            bodyExtendsBeyondHeader = false,
            bodySeparated = false,
            bodyAlpha = 1f,
            interactionProgress = 0f
        )

        assertEquals(LiquidPickerLayerPolicy.Layers.HeaderOnly, state)
        assertEquals(0f, state.bridgeOverlayAlpha, 0f)
    }

    @Test
    fun contourContributionUsesActualBodyBottom() {
        assertEquals(
            false,
            LiquidPickerLayerPolicy.bodyExtendsBeyondHeader(
                bodyActive = true,
                actualBodyBottom = 50.4f,
                headerBottom = 50f,
                threshold = 0.5f
            )
        )
        assertEquals(
            true,
            LiquidPickerLayerPolicy.bodyExtendsBeyondHeader(
                bodyActive = true,
                actualBodyBottom = 50.6f,
                headerBottom = 50f,
                threshold = 0.5f
            )
        )
    }
}
