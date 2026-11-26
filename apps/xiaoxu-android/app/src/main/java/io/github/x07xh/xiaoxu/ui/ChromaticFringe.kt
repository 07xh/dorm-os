package io.github.x07xh.xiaoxu.ui

// SPDX-License-Identifier: GPL-3.0-only
// Upstream: znjhahaha/zhengfang-apk@bb3b80a6d64e4c2057c0544bb417696ceaa2a9ed, app/src/main/java/com/tyust/course/ui/system/glass/ChromaticFringe.kt.
// Original authors and contributors. See upstream license and attribution.

import android.graphics.BlendMode
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.os.Build
import androidx.compose.ui.graphics.asComposeRenderEffect
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.effects.effect

/**
 * API 31/32 的色散近似：无 RuntimeShader 时用 RenderEffect 链做 RGB 通道分离。
 * 红通道左移、蓝通道右移、绿通道原位，PLUS 混合还原中心色彩，
 * 边缘呈现红/蓝彩边，观感接近 API 33+ lens 的 chromaticAberration。
 * 在 blur 之后调用，彩边会被模糊自然柔化。
 */
fun BackdropEffectScope.chromaticFringe(offsetPx: Float) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    if (offsetPx < 0.1f) return

    val rOnly = ColorMatrixColorFilter(
        ColorMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    )
    val gOnly = ColorMatrixColorFilter(
        ColorMatrix(
            floatArrayOf(
                0f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    )
    val bOnly = ColorMatrixColorFilter(
        ColorMatrix(
            floatArrayOf(
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    )

    val red = RenderEffect.createColorFilterEffect(
        rOnly,
        RenderEffect.createOffsetEffect(-offsetPx, 0f)
    )
    val green = RenderEffect.createColorFilterEffect(gOnly)
    val blue = RenderEffect.createColorFilterEffect(
        bOnly,
        RenderEffect.createOffsetEffect(offsetPx, 0f)
    )

    val combined = RenderEffect.createBlendModeEffect(
        RenderEffect.createBlendModeEffect(red, green, BlendMode.PLUS),
        blue,
        BlendMode.PLUS
    )
    effect(combined.asComposeRenderEffect())
}
