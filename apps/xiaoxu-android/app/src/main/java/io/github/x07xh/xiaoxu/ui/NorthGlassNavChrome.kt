package io.github.x07xh.xiaoxu.ui

import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.util.lerp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule

/**
 * Glass drawing extracted and adapted from 北化课表 (CourseSelector) v1.0.83,
 * ui/system/CapsuleNavigationBar.kt. Upstream: znjhahaha/zhengfang-apk (GPL-3.0).
 * This is intentionally a presentation-only adapter: no routes, data or school sessions.
 *
 * Full original optics include an offscreen GL implementation for API31/32, custom
 * physical lens and icon motion. For now the real Kyant backdrop path is enabled
 * on API33+ only; the existing reliable Compose path is retained elsewhere.
 */
internal fun Modifier.northGlassTrack(backdrop: Backdrop): Modifier =
    this.drawBackdrop(
        backdrop = backdrop,
        shape = { Capsule() },
        effects = {
            vibrancy()
            blur(10.dp.toPx())
        },
        highlight = { Highlight.Default.copy(alpha = 0.16f) },
        shadow = { Shadow(alpha = 0.08f) },
        onDrawSurface = { drawRect(Color.White.copy(alpha = 0.28f)) }
    )

/** At rest the original indicator is a clear lens; pressing increases its rim. */
internal fun Modifier.northGlassIndicator(
    backdrop: Backdrop,
    pressProgress: () -> Float
): Modifier = this.drawBackdrop(
    backdrop = backdrop,
    shape = { Capsule() },
    effects = {
        val press = pressProgress().coerceIn(0f, 1f)
        vibrancy()
        if (Build.VERSION.SDK_INT >= 33 && press > 0.001f) {
            lens(
                refractionHeight = 10.dp.toPx() * press,
                refractionAmount = 14.dp.toPx() * press,
                chromaticAberration = true
            )
        }
    },
    highlight = {
        val p = pressProgress().coerceIn(0f, 1f)
        Highlight.Default.copy(alpha = 0.12f + 0.35f * p)
    },
    shadow = {
        val p = pressProgress().coerceIn(0f, 1f)
        Shadow(alpha = 0.10f + 0.15f * p)
    },
    innerShadow = {
        val p = pressProgress().coerceIn(0f, 1f)
        InnerShadow(radius = 4.dp * p, alpha = p * 0.5f)
    },
    onDrawSurface = {
        val press = pressProgress().coerceIn(0f, 1f)
        drawRect(Color.White.copy(alpha = lerp(0.34f, 0.14f, press)))
    }
)
