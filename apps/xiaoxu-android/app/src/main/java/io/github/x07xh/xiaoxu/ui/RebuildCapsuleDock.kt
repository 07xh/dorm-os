package io.github.x07xh.xiaoxu.ui

import android.app.ActivityManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.kyant.backdrop.shadow.InnerShadow
import kotlin.math.abs
import android.content.Context
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import android.animation.ValueAnimator
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule
import io.github.x07xh.xiaoxu.MainDestination

internal object RebuildDockMetrics {
    @Composable fun trackHeight() = rememberScreenMetrics().tall(64.dp, 58.dp)
    @Composable fun outerGap() = rememberScreenMetrics().tall(8.dp, 6.dp)
    @Composable fun horizontalGap() = rememberScreenMetrics().wide(16.dp, 10.dp)
    @Composable fun contentReserve() = trackHeight() + outerGap() * 2
}

@Composable
internal fun rememberNavigationMaterialTier(): NavigationMaterialTier {
    val context = LocalContext.current
    return remember(context) {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        navigationMaterialTier(Build.VERSION.SDK_INT, manager?.isLowRamDevice ?: true,
            Build.SUPPORTED_ABIS.toList())
    }
}

// SPDX-License-Identifier: GPL-3.0-only
// Adapted from znjhahaha/zhengfang-apk @ bb3b80a6d64e4c2057c0544bb417696ceaa2a9ed, CapsuleNavigationBar.kt.
// Keep a single visible icon layer and the existing device-tier fallback.
// No API31/32 offscreen GL renderer or additional page snapshot in this port.
@Composable
internal fun RebuildCapsuleDock(
    selected: MainDestination, onSelect: (MainDestination) -> Unit,
    tier: NavigationMaterialTier, backdrop: Backdrop, modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val destinations = MainDestination.entries
    val selectedIndex = destinations.indexOf(selected)
    val accessibility = rememberGlassAccessibilityMode()
    val reduced = accessibility.reduceMotion || !ValueAnimator.areAnimatorsEnabled()
    val optical = tier == NavigationMaterialTier.Refractive && !accessibility.highContrast
    val playback = rememberNavigationIconPlayback(destinations.size, selectedIndex, reduced)
    val haptics = LocalHapticFeedback.current
    val latestSelect by rememberUpdatedState(onSelect)
    val latestIndex by rememberUpdatedState(selectedIndex)
    val scope = rememberCoroutineScope()
    val drag = remember(scope) {
        DampedDragAnimation(scope, selectedIndex.toFloat(), 0f..destinations.lastIndex.toFloat(),
            0.001f, 1f, GlassRecipe.NavPressedScale,
            onDragStarted = {}, onDragStopped = {}, onDrag = { _, _ -> })
    }
    LaunchedEffect(reduced) { drag.setReducedMotion(reduced, selectedIndex.toFloat()) }
    LaunchedEffect(selectedIndex) {
        if (drag.targetValue != selectedIndex.toFloat()) drag.animateToValue(selectedIndex.toFloat())
    }
    fun select(index: Int) {
        if (index != latestIndex) {
            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            latestSelect(destinations[index])
        } else playback.replay(index)
        if (drag.targetValue == index.toFloat()) drag.release() else drag.animateToValue(index.toFloat())
    }
    val shape = RoundedCornerShape(32.dp)
    val dark = isSystemInDarkTheme()
    val trackMaterial = GlassMaterials.resolve(GlassMaterialRole.Navigation, accessibility)
    val indicatorMaterial = GlassMaterials.resolve(GlassMaterialRole.Interactive, accessibility)
    val tint = if (optical) scheme.surface.copy(alpha = if (dark) 0.72f else 0.28f)
        else scheme.surface.copy(alpha = if (tier == NavigationMaterialTier.Opaque || accessibility.highContrast) 1f else 0.94f)
    val track = if (optical) Modifier.drawBackdrop(
        backdrop = backdrop, shape = { Capsule() },
        effects = {
            vibrancy()
            val minDim = size.minDimension.coerceAtLeast(0f)
            val params = resolvePhysicalLens(this, trackMaterial, minDim / 2f, minDim,
                allowChromaticAberration = false)
            if (params.blurPx > 0f) blur(params.blurPx)
            if (params.useLens) lens(params.refractionHeightPx, params.refractionAmountPx, chromaticAberration = false)
        },
        highlight = { Highlight.Default.copy(alpha = 0.16f) },
        shadow = { Shadow(alpha = 0.08f) },
        onDrawSurface = { drawRect(tint) }
    ) else Modifier.clip(shape).background(tint)
    Box(modifier.fillMaxWidth().navigationBarsPadding()
        .padding(horizontal = RebuildDockMetrics.horizontalGap(), vertical = RebuildDockMetrics.outerGap()),
        contentAlignment = Alignment.Center) {
        BoxWithConstraints(Modifier.widthIn(max = 480.dp).fillMaxWidth()
            .height(RebuildDockMetrics.trackHeight()).then(track)
            .border(0.5.dp, scheme.outlineVariant.copy(alpha = if (optical) 0.45f else 1f), shape)
            .padding(4.dp), contentAlignment = Alignment.CenterStart) {
            val tabWidth = maxWidth / destinations.size
            val density = LocalDensity.current
            val tabPx = with(density) { tabWidth.toPx() }.coerceAtLeast(1f)
            val indicator = if (optical) Modifier.drawBackdrop(
                backdrop = backdrop, shape = { Capsule() },
                effects = {
                    val press = drag.pressProgress
                    val minDim = size.minDimension.coerceAtLeast(0f)
                    val params = resolvePhysicalLens(this, indicatorMaterial, minDim / 2f, minDim,
                        interactionProgress = press,
                        motionIntensity = motionIntensityFromVelocity(drag.velocity * tabPx,
                            indicatorMaterial.optics.velocityForFullEffect),
                        enableBlur = false, pressScalesRefraction = true, refractionFloor = 0f)
                    vibrancy()
                    if (params.useLens) lens(params.refractionHeightPx, params.refractionAmountPx,
                        chromaticAberration = params.chromaticAberration)
                },
                highlight = { Highlight.Default.copy(alpha = 0.12f + drag.pressProgress * 0.35f) },
                shadow = { Shadow(alpha = 0.10f + drag.pressProgress * 0.15f) },
                innerShadow = { InnerShadow(radius = 4.dp * drag.pressProgress, alpha = drag.pressProgress * 0.5f) },
                onDrawSurface = {
                    val alpha = if (dark) GlassRecipe.NavSelectedSolidAlphaDark else GlassRecipe.NavSelectedSolidAlpha
                    drawRect(Color.White.copy(alpha = androidx.compose.ui.util.lerp(alpha,
                        GlassRecipe.NavSelectedGlassAlpha, drag.pressProgress)))
                }
            ) else Modifier.clip(shape).background(scheme.primary.copy(alpha = 0.12f))
            Box(Modifier.width(tabWidth).fillMaxHeight()
                .testTag("navigation-selection-indicator")
                .graphicsLayer {
                    translationX = drag.value * tabPx
                    if (optical && !reduced) {
                        val (sx, sy) = indicatorScale(drag)
                        scaleX = sx
                        scaleY = sy
                    }
                }.then(indicator))
            Row(Modifier.fillMaxSize().selectableGroup().testTag("main-navigation")
                .pointerInput(tabPx, reduced) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        down.consume()
                        val startValue = drag.value
                        val committedIndex = latestIndex
                        val startX = down.position.x
                        val touchSlop = viewConfiguration.touchSlop
                        var totalDragX = 0f
                        var dragging = false
                        var completed = false
                        drag.press(down.uptimeMillis)
                        try {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (event.changes.count { it.pressed } > 1 || change.isConsumed) break
                                if (change.changedToUpIgnoreConsumed()) {
                                    completed = true
                                    change.consume()
                                    break
                                }
                                if (!dragging && abs(change.position.y - down.position.y) > touchSlop &&
                                    abs(totalDragX) <= touchSlop) break
                                val delta = change.positionChange()
                                if (delta != Offset.Zero) {
                                    totalDragX += delta.x
                                    if (!dragging && abs(totalDragX) > touchSlop) dragging = true
                                    if (dragging) {
                                        change.consume()
                                        drag.updateValue((startValue + totalDragX / tabPx)
                                            .coerceIn(0f, destinations.lastIndex.toFloat()), change.uptimeMillis)
                                    }
                                }
                            }
                        } finally {
                            val target = if (!completed) committedIndex else if (dragging)
                                kotlin.math.round(drag.targetValue).toInt().coerceIn(destinations.indices)
                            else (startX / tabPx).toInt().coerceIn(destinations.indices)
                            if (completed) select(target) else drag.animateToValue(committedIndex.toFloat())
                        }
                    }
                }, verticalAlignment = Alignment.CenterVertically) {
                destinations.forEachIndexed { index, destination ->
                    val weight = (1f - abs(drag.value - index)).coerceIn(0f, 1f)
                    UpstreamNavTab(destination, selectedIndex == index, scheme.primary,
                        selectionWeight = weight, iconPhase = { playback.phase(index) },
                        pressProgress = drag.pressProgress * weight, onClick = { select(index) })
                }
            }
        }
    }
}

private fun indicatorScale(anim: DampedDragAnimation): Pair<Float, Float> {
    var sx = anim.scaleX
    var sy = anim.scaleY
    // 速度形变系数加大（/6 相对原 /10）
    val velocity = anim.velocity / 6f
    sx /= 1f - (velocity * 0.75f).coerceIn(-0.22f, 0.22f)
    sy *= 1f - (velocity * 0.25f).coerceIn(-0.22f, 0.22f)
    // 平移为 0：指示器的 translationX 挂在**外层** graphicsLayer 上，
    // glassLens 在它内部，已经跟着一起走了。芯片不同，见 GlassLensTransform。
    return sx to sy
}

