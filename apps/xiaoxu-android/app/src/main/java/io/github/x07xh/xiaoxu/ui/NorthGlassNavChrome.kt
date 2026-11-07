package io.github.x07xh.xiaoxu.ui

import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlin.math.abs

/**
 * Material and physics taken from CourseSelector / 北化课表 v1.0.83,
 * ui/system/CapsuleNavigationBar.kt, GlassMaterial.kt and
 * glass/PhysicalLensSurface.kt (GPL-3.0, znjhahaha/zhengfang-apk).
 *
 * These are solely rendering modifiers. NativeShell owns the selection and the
 * existing FloatingBottomBar owns gestures; neither glass function owns a route.
 *
 * API 33+ uses Kyant's physical backdrop lens. The original API 31/32 offline
 * GL path has not yet been ported, so those versions retain the preexisting dock.
 */
internal fun Modifier.northGlassTrack(
    backdrop: Backdrop,
    pressProgress: () -> Float,
    indicatorExpansion: () -> Float
): Modifier = this.drawBackdrop(
    backdrop = backdrop,
    shape = { Capsule() },
    effects = {
        vibrancy()
        blur(10.dp.toPx()) // Original Navigation material.
        // Original track has an 8dp refractive slope and a 12dp displacement.
        // Clamp to the measured capsule before calling Kyant's lens() shader.
        if (Build.VERSION.SDK_INT >= 33 && size.minDimension > 0f) {
            val radius = size.minDimension / 2f
            val height = 8.dp.toPx().coerceIn(0f, radius)
            val amount = 12.dp.toPx().coerceIn(0f, size.minDimension * 0.40f)
            if (height > 0f && amount > 0f) {
                lens(
                    refractionHeight = height,
                    refractionAmount = amount,
                    chromaticAberration = false
                )
            }
        }
    },
    highlight = { Highlight.Default.copy(alpha = 0.16f) },
    shadow = { Shadow(alpha = 0.08f) },
    layerBlock = {
        val press = pressProgress().coerceIn(0f, 1f)
        val gain = if (size.width > 0f) 16.dp.toPx() / size.width else 0f
        val boost = indicatorExpansion().coerceIn(0f, 0.4f)
        val stretch = (1f + gain * press) * (1f + boost * 0.18f)
        scaleX = stretch
        scaleY = stretch
    },
    onDrawSurface = { drawRect(Color.White.copy(alpha = 0.28f)) }
)

/**
 * Original Interactive material: 12dp slope / 18dp displacement, with
 * press-dependent refraction floor 0 and motion-dependent chromatic fringe.
 * The lens, rim and shadow share the same scale transform to avoid white halos.
 */
internal fun Modifier.northGlassIndicator(
    backdrop: Backdrop,
    pressProgress: () -> Float,
    velocityPx: () -> Float,
    indicatorScale: () -> Pair<Float, Float>
): Modifier = this.drawBackdrop(
    backdrop = backdrop,
    shape = { Capsule() },
    effects = {
        val press = pressProgress().coerceIn(0f, 1f)
        val motion = (abs(velocityPx()) / 900f).coerceIn(0f, 1f)
        vibrancy()
        if (Build.VERSION.SDK_INT >= 33 && size.minDimension > 0f) {
            val base = press // Original v1.0.83 NavIndicatorRefractionFloor = 0.
            val hScale = base * (1f + motion * 0.25f)
            val aScale = base * (1f + motion * 0.35f)
            val h = (12.dp.toPx() * hScale).coerceIn(0f, size.minDimension / 2f)
            val a = (18.dp.toPx() * aScale).coerceIn(0f, size.minDimension * 0.40f)
            if (h > 0f && a > 0f) {
                lens(
                    refractionHeight = h,
                    refractionAmount = a,
                    chromaticAberration = press > 0.05f || motion > 0.05f
                )
            }
        }
    },
    highlight = {
        val p = pressProgress().coerceIn(0f, 1f)
        val (sx, sy) = indicatorScale()
        val compensated = ((sx + sy) / 2f).coerceAtLeast(1f)
        Highlight.Default.copy(
            width = Highlight.Default.width / compensated,
            blurRadius = Highlight.Default.blurRadius / compensated,
            alpha = 0.12f + 0.35f * p
        )
    },
    shadow = {
        val p = pressProgress().coerceIn(0f, 1f)
        Shadow(alpha = 0.10f + 0.15f * p)
    },
    innerShadow = {
        val p = pressProgress().coerceIn(0f, 1f)
        InnerShadow(radius = 4.dp * p, alpha = 0.5f * p)
    },
    layerBlock = {
        val (sx, sy) = indicatorScale()
        scaleX = sx
        scaleY = sy
    },
    onDrawSurface = {
        val press = pressProgress().coerceIn(0f, 1f)
        // Original white neutral tint: never darken the selected glass to gray.
        drawRect(Color.White.copy(alpha = lerp(0.34f, 0.14f, press)))
    }
)
