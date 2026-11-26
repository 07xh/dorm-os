package io.github.x07xh.xiaoxu.ui

// SPDX-License-Identifier: GPL-3.0-only
// Upstream: znjhahaha/zhengfang-apk@bb3b80a6d64e4c2057c0544bb417696ceaa2a9ed, app/src/main/java/com/tyust/course/ui/system/LiquidSelectionComponents.kt.
// Original authors and contributors. See upstream license and attribution.

import androidx.compose.ui.platform.testTag

import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedCornerStyle
import com.kyant.shapes.RoundedRectangle
import com.tyust.course.ui.system.*
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 选择器选项只描述数据，不持有展开状态或页面回调。
 */
data class LiquidPickerOption(
    val label: String,
    val enabled: Boolean = true
)

private val PickerHeaderHeight = 50.dp
private val PickerItemHeight = 40.dp
private val PickerItemGap = 3.dp
private val PickerBodyVerticalPadding = 6.dp
private val PickerBodyHorizontalPadding = 10.dp
private val PickerMaxBodyHeight = 240.dp
private val PickerCornerRadius = 20.dp
private val PickerExpandedGap = 12.dp
private val PickerOpeningOverlap = 16.dp
private val PickerClosingOverlap = 24.dp
private val PickerItemEasing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
private const val PickerCircleBezier = 0.5522848f

/** Adds a clockwise rounded-rectangle contour without allocating an intermediate geometry. */
private fun Path.addPickerRoundedContour(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    radius: Float
) {
    val width = (right - left).coerceAtLeast(0f)
    val height = (bottom - top).coerceAtLeast(0f)
    if (width <= 0.5f || height <= 0.5f) return

    val r = radius.coerceIn(0f, minOf(width, height) / 2f)
    val c = r * PickerCircleBezier
    moveTo(left + r, top)
    lineTo(right - r, top)
    cubicTo(right - r + c, top, right, top + r - c, right, top + r)
    lineTo(right, bottom - r)
    cubicTo(right, bottom - r + c, right - r + c, bottom, right - r, bottom)
    lineTo(left + r, bottom)
    cubicTo(left + r - c, bottom, left, bottom - r + c, left, bottom - r)
    lineTo(left, top + r)
    cubicTo(left, top + r - c, left + r - c, top, left + r, top)
    close()
}

/** Smooths sampled implicit-surface stations without inventing the underlying neck geometry. */
private fun Path.addPickerImplicitContour(points: List<Offset>) {
    if (points.size < 3) return
    fun midpoint(first: Offset, second: Offset) = Offset(
        x = (first.x + second.x) / 2f,
        y = (first.y + second.y) / 2f
    )

    val firstMidpoint = midpoint(points.last(), points.first())
    moveTo(firstMidpoint.x, firstMidpoint.y)
    points.forEachIndexed { index, point ->
        val next = points[(index + 1) % points.size]
        val nextMidpoint = midpoint(point, next)
        quadraticTo(point.x, point.y, nextMidpoint.x, nextMidpoint.y)
    }
    close()
}

private fun smoothStep(value: Float): Float {
    val x = value.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

@Stable
private class PickerMotionState(initialPosition: Float) {
    var isSettled by mutableStateOf(true)
        private set
    var travelPosition by mutableFloatStateOf(initialPosition)
        private set
    var travelVelocity by mutableFloatStateOf(0f)
        private set
    var extentPosition by mutableFloatStateOf(initialPosition)
        private set
    var extentVelocity by mutableFloatStateOf(0f)
        private set
    var phaseTimeSeconds by mutableFloatStateOf(0f)
        private set

    suspend fun animateTo(
        expanded: Boolean,
        reducedMotion: Boolean
    ) {
        isSettled = false
        phaseTimeSeconds = 0f
        val target = if (expanded) 1f else 0f
        if (reducedMotion) {
            snapTo(target)
            return
        }
        var state = LiquidPickerMotionPhysics.State(
            travel = LiquidPickerMotionPhysics.Axis(travelPosition, travelVelocity),
            extent = LiquidPickerMotionPhysics.Axis(extentPosition, extentVelocity)
        )
        if (LiquidPickerMotionPhysics.isAtRest(state, expanded)) {
            snapTo(target)
            return
        }

        var previousFrame = withFrameNanos { it }
        while (true) {
            val frame = withFrameNanos { it }
            val deltaSeconds = ((frame - previousFrame) / 1_000_000_000f)
                .coerceIn(0f, 0.1f)
            previousFrame = frame
            phaseTimeSeconds += deltaSeconds
            state = LiquidPickerMotionPhysics.step(
                state = state,
                expanded = expanded,
                deltaSeconds = deltaSeconds
            )
            travelPosition = state.travel.position
            travelVelocity = state.travel.velocity
            extentPosition = state.extent.position
            extentVelocity = state.extent.velocity

            if (LiquidPickerMotionPhysics.isAtRest(state, expanded)) {
                snapTo(target)
                return
            }
        }
    }

    private fun snapTo(target: Float) {
        travelPosition = target
        travelVelocity = 0f
        extentPosition = target
        extentVelocity = 0f
        isSettled = true
    }
}

/**
 * Real rounded-surface lens used under the picker union contour.
 *
 * The AGSL lens only accepts rounded-rectangular shapes, while the temporary SDF bridge is a
 * generic path. Header and body therefore refract the real wallpaper independently; a very thin
 * unified contour above them supplies the transient liquid neck without covering the refraction.
 */
@Composable
private fun PickerLensLayer(
    modifier: Modifier,
    backdrop: Backdrop,
    shape: Shape,
    cornerRadius: Dp,
    motionVelocity: Float,
    pressProgress: Float,
    enabled: Boolean,
    forceBlurFallback: Boolean = false,
    popupSurface: Boolean = false
) {
    val accessibility = rememberGlassAccessibilityMode()
    val isLightTheme = LocalWallpaperAppearanceColors.current.usesDarkForeground
    val hasRealLens = isRuntimeLensEnabled() && !forceBlurFallback
    // API 31/32：自家 ES 2.0 折射。用 App 全局区域 —— 头部/主体采样的
    // `glassBackdrop` 就是 `LocalControlBackdrop`，在 App 根上等于那层壁纸，
    // 与全局底图同源。全屏且静态，展开动画改高度也不会触发重拍。
    //
    // `forceBlurFallback` 那一层要排除：它是 GenericShape 的合并轮廓，
    // 33+ 上也不折射（走 blur），而且自家着色器只有圆角矩形 SDF。
    val lensAnchor = if (forceBlurFallback) null else LocalGlassLensAnchor.current
    val hasLensLook = hasRealLens || lensAnchor != null
    val lensDensity = LocalDensity.current
    val resolvedMaterial = GlassMaterials.resolve(
        role = GlassMaterialRole.Interactive,
        accessibility = accessibility,
        interactionProgress = pressProgress
    )
    val material = if (hasLensLook) {
        resolvedMaterial
    } else {
        // 真的没有折射时（API ≤ 30）才用受控的 blur 顶替。
        // 判据是 hasLensLook：31/32 现在有折射了，再补 blur 就是把它糊掉，
        // 而且底图是无 blur 的原 backdrop，屏幕上多出来的模糊会让折射内容
        // 与周围对不上。
        resolvedMaterial.copy(blurDp = GlassRecipe.PickerBlurDp)
    }
    val motionIntensity = (abs(motionVelocity) / 9f).coerceIn(0f, 1f)
    val enabledScale = if (enabled) 1f else GlassRecipe.ChipDisabledSurfaceScale
    val surfaceAlpha = material.surfaceAlpha * enabledScale *
        if (isLightTheme) 1f else 0.72f
    val customWallpaper =
        com.tyust.course.manager.AppearanceSettingsManager.mode != com.tyust.course.manager.WallpaperMode.Preset
    val wallpaperSurface = LocalWallpaperAppearanceColors.current.surface
    val surfaceColor = if (popupSurface) wallpaperSurface.copy(
        // 菜单行铺在触发器采样范围之外的壁纸上，那里的明暗不受这次色调映射保证。
        alpha = if (customWallpaper) maxOf(wallpaperSurface.alpha, ReadableAnyBackdropAlpha)
        else dropdownSurfaceAlpha(!isLightTheme, accessibility.highContrast)
    ) else if (customWallpaper) wallpaperSurface
    else Color.White.copy(alpha = surfaceAlpha)

    Box(
        modifier = modifier
            // 必须在 drawBackdrop **上游**：它下面那层会把背景原样再画一遍。
            .glassLens(
                lensAnchor,
                optics = { w, h ->
                    glassLensOpticsFrom(
                        material = material,
                        density = lensDensity,
                        // 与下面 resolvePhysicalLens 的 minCornerRadiusPx 同一条算式
                        cornerRadiusPx = with(lensDensity) {
                            minOf(cornerRadius.toPx(), minOf(w, h) / 2f)
                                .coerceAtLeast(0.5f)
                        },
                        minDimensionPx = minOf(w, h).coerceAtLeast(1f),
                        interactionProgress = pressProgress,
                        motionIntensity = motionIntensity,
                        // 与下面同值：静止就是透明玻璃，按压/速度只是加强
                        pressScalesRefraction = false,
                        chromaticAberrationAtRest = false
                    )
                }
            )
            .drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            // Each persistent lens needs its own optical edge. Only the temporary generic
            // bridge omits it; that surface already has the SDF rim below.
            highlight = { if (forceBlurFallback) null else Highlight.Default },
            shadow = { if (forceBlurFallback) null else Shadow(alpha = if (isLightTheme) 0.14f else 0.22f) },
            innerShadow = null,
            effects = {
                val effectiveCornerRadius = minOf(
                    cornerRadius.toPx(),
                    size.minDimension / 2f
                ).coerceAtLeast(0.5f)
                val params = resolvePhysicalLens(
                    scope = this,
                    material = material,
                    minCornerRadiusPx = effectiveCornerRadius,
                    minDimensionPx = size.minDimension.coerceAtLeast(1f),
                    interactionProgress = pressProgress,
                    motionIntensity = motionIntensity,
                    enableBlur = !hasLensLook,
                    allowChromaticAberration = !accessibility.reduceMotion,
                    chromaticAberrationAtRest = false,
                    // The selector is transparent glass even at rest; press/motion only intensify it.
                    pressScalesRefraction = false
                )
                vibrancy()
                if (params.blurPx > 0f) blur(params.blurPx)
                if (params.useLens) {
                    lens(
                        refractionHeight = params.refractionHeightPx,
                        refractionAmount = params.refractionAmountPx,
                        chromaticAberration = params.chromaticAberration
                    )
                } else if (params.fringePx > 0f && lensAnchor == null) {
                    // 只有真的没有折射时才用假色散近似（API ≤ 30）。
                    // 31/32 上折射与七波长色散已由上面的 glassLens 画完。
                    chromaticFringe(params.fringePx)
                }
            },
            onDrawBackdrop = { drawBackdrop ->
                // 31/32 上背景已由 glassLens 以折射方式画过，不能再画一遍
                if (lensAnchor == null) drawBackdrop()
            },
            onDrawSurface = {
                drawRect(surfaceColor)
            }
        )
    )
}

@Composable
fun LiquidPicker(
    options: List<LiquidPickerOption>,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "请选择",
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    backdrop: Backdrop? = LocalControlBackdrop.current,
    maxLabelLines: Int = 1,
    maxSelectedLabelLines: Int = maxLabelLines,
    popupWidth: Dp? = null
) {
    var expanded by remember { mutableStateOf(false) }
    var actionPending by remember { mutableStateOf(false) }
    val latestAction by rememberUpdatedState(onAction)
    var portalBodySpace by remember { mutableStateOf(PickerMaxBodyHeight) }
    var portalOpensUp by remember { mutableStateOf(false) }
    var highlightedRow by remember { mutableStateOf<Int?>(null) }
    val regionState = rememberWallpaperRegionState()
    val appearance = rememberWallpaperRegionAppearance(regionState)
    val validSelectedIndex = selectedIndex?.takeIf { it in options.indices }
    val selectedLabel = validSelectedIndex?.let { options[it].label }
    val haptics = LocalHapticFeedback.current
    val hasAction = !actionLabel.isNullOrBlank() && onAction != null
    val rowCount = options.size + if (hasAction) 1 else 0
    val hasAvailableOption = options.any { it.enabled }
    val canOpen = enabled && (hasAvailableOption || hasAction)
    val accessibility = rememberGlassAccessibilityMode()
    val reduceMotion = accessibility.reduceMotion
    val isLightTheme = appearance.usesDarkForeground
    // Keep the backdrop contract but avoid animated backdrop sampling. Both settled surfaces use
    // the cheap captured-content glass recipe, and the transient bridge is a single cached path.
    val glassBackdrop = backdrop?.takeIf { isBackdropSupported() }
    val backdropToneAvailable = glassBackdrop != null
    val headerInteraction = remember { MutableInteractionSource() }
    val headerPressed by headerInteraction.collectIsPressedAsState()
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    var anchorWidth by remember { mutableStateOf(0.dp) }
    val labelLines = maxLabelLines.coerceIn(1, 3)
    val selectedLabelLines = maxSelectedLabelLines.coerceIn(1, 3)
    val headerHeight = maxOf(PickerHeaderHeight,
        with(density) { MaterialTheme.typography.bodyLarge.lineHeight.toDp() } * selectedLabelLines.toFloat() + 16.dp)
    val itemHeight = maxOf(PickerItemHeight, 48.dp,
        with(density) { MaterialTheme.typography.bodyMedium.lineHeight.toDp() } * labelLines.toFloat() + 16.dp)

    val bodyContentHeight = if (rowCount == 0) {
        0.dp
    } else {
        PickerBodyVerticalPadding * 2f +
            itemHeight * rowCount.toFloat() +
            PickerItemGap * (rowCount - 1).toFloat()
    }
    val bodyHeight = minOf(bodyContentHeight, PickerMaxBodyHeight, portalBodySpace)
    var openingFirstRow by remember { mutableIntStateOf(0) }
    val openingRowCount = kotlin.math.ceil((bodyHeight / (itemHeight + PickerItemGap)).toDouble()).toInt() + 1
    val motion = remember { PickerMotionState(initialPosition = 0f) }
    LaunchedEffect(expanded, reduceMotion) {
        motion.animateTo(
            expanded = expanded,
            reducedMotion = reduceMotion
        )
    }
    val motionProgress = motion.travelPosition
    val motionVelocity = motion.travelVelocity
    val extentProgress = motion.extentPosition
    val extentVelocity = motion.extentVelocity
    val motionTimeSeconds = motion.phaseTimeSeconds
    val headerScale by animateFloatAsState(
        targetValue = if (!reduceMotion && headerPressed && canOpen) 0.985f else 1f,
        animationSpec = MotionSpring.liquidTap(),
        label = "morphPickerHeaderScale"
    )
    val settledProgress = motionProgress.coerceIn(0f, 1f)
    val heightProgress = extentProgress.coerceIn(0f, 1.09f)
    // Keep row composition warm while collapsed. Creating the list and its semantics on the first
    // open frame caused MuMu to miss the small-bud frames and made continuous geometry look like a
    // pop-in. The subtree stays clipped and undrawn until the physical body leaves the header.
    val bodyPrecomposed = rowCount > 0
    val bodyActive = rowCount > 0 && (
        expanded || !motion.isSettled || maxOf(abs(motionProgress), abs(extentProgress)) > 0.005f
    )
    val arrowRotation = 180f * smoothStep(settledProgress)

    // The spring position is also the body's physical travel coordinate. There is no authored
    // detach keyframe: as the rounded boxes move from overlap to a 12dp gap, the SDF neck
    // exists only while the smooth-min field is still one connected zero-level set.
    val bodyTravelProgress = motionProgress.coerceIn(-0.08f, 1.12f)
    val bodyOverlap = if (expanded) PickerOpeningOverlap else PickerClosingOverlap
    val bodyOffset = headerHeight - bodyOverlap +
        (bodyOverlap + PickerExpandedGap) * bodyTravelProgress
    val visualBodyHeight = bodyHeight * heightProgress
    val actualBodyBottom = bodyOffset + visualBodyHeight
    val layoutHeight = maxOf(
        headerHeight,
        actualBodyBottom
    )
    // The surface itself never fades. It begins fully inside the header, grows out as one mass,
    // develops an SDF neck, then separates. Only the final sub-pixel body is hidden.
    val bodyAlpha = if (bodyActive && visualBodyHeight >= 1.dp) 1f else 0f
    val bodyExtendsBeyondHeader = LiquidPickerLayerPolicy.bodyExtendsBeyondHeader(
        bodyActive = bodyActive,
        actualBodyBottom = actualBodyBottom.value,
        headerBottom = headerHeight.value,
        threshold = 0.5f
    )
    val bodySeparated = bodyExtendsBeyondHeader && bodyOffset >= headerHeight + 0.5.dp
    val layerPolicy = LiquidPickerLayerPolicy.resolve(
        bodyActive = bodyActive,
        bodyExtendsBeyondHeader = bodyExtendsBeyondHeader,
        bodySeparated = bodySeparated,
        bodyAlpha = bodyAlpha,
        interactionProgress = if (headerPressed && canOpen) 1f else 0f
    )
    val mergedMotionVelocity = if (abs(extentVelocity) > abs(motionVelocity)) {
        extentVelocity
    } else {
        motionVelocity
    }
    val collisionPenetration = (-motionProgress).coerceIn(0f, 0.13f)
    val collisionScaleX = 1f + collisionPenetration * 0.20f
    val collisionScaleY = 1f - collisionPenetration * 0.32f
    val mergeSmoothnessDp = 9.5f + minOf(
        maxOf(abs(motionVelocity), abs(extentVelocity)) * 0.45f,
        3.5f
    )
    val descriptorPresent = leadingIcon != null || !label.isNullOrBlank()
    val contentEnabled = enabled && (hasAvailableOption || hasAction)
    val primaryContentColor = if (contentEnabled) {
        appearance.onSurface
    } else {
        appearance.onSurface.copy(alpha = 0.38f)
    }
    val secondaryContentColor = if (contentEnabled) {
        appearance.onSurfaceVariant
    } else {
        appearance.onSurface.copy(alpha = 0.30f)
    }
    val highlightColor = if (isLightTheme) {
        Color.White.copy(alpha = if (backdropToneAvailable) 0.42f else 0.34f)
    } else {
        Color.White.copy(alpha = 0.11f)
    }
    val highlightedOffset = animateDpAsState(
        targetValue = highlightedRow
            ?.takeIf { it in 0 until rowCount }
            ?.let { index ->
                PickerBodyVerticalPadding +
                    (itemHeight + PickerItemGap) * index.toFloat()
            }
            ?: PickerBodyVerticalPadding,
        animationSpec = if (reduceMotion) {
            tween(durationMillis = 0)
        } else {
            spring(dampingRatio = 0.82f, stiffness = 500f)
        },
        label = "morphPickerHighlightOffset"
    )
    val highlightedAlpha = if (highlightedRow == null) {
        0f
    } else if (expanded) {
        smoothStep((motionTimeSeconds - 0.34f) / 0.14f)
    } else {
        smoothStep((heightProgress - 0.02f) / 0.12f)
    }
    fun rowRevealProgress(index: Int): Float {
        return pickerRowReveal(index, openingFirstRow, openingRowCount, expanded,
            motion.isSettled, reduceMotion, motionTimeSeconds, settledProgress, heightProgress)
    }

    LaunchedEffect(canOpen) {
        if (!canOpen) {
            expanded = false
            actionPending = false
        }
    }
    LaunchedEffect(expanded, validSelectedIndex, bodyHeight) {
        if (expanded) {
            highlightedRow = validSelectedIndex
            if (validSelectedIndex != null && bodyHeight > 0.dp) {
                withFrameNanos { }
                val rowTop = with(density) {
                    (PickerBodyVerticalPadding +
                        (itemHeight + PickerItemGap) * validSelectedIndex.toFloat())
                        .roundToPx()
                }
                val viewport = with(density) { bodyHeight.roundToPx() }
                val itemHeight = with(density) { itemHeight.roundToPx() }
                val centered = rowTop - (viewport - itemHeight) / 2
                scrollState.scrollTo(centered.coerceIn(0, scrollState.maxValue))
            }
            openingFirstRow = (scrollState.value / with(density) { (itemHeight + PickerItemGap).toPx() }).toInt()
        }
    }
    BackHandler(enabled = expanded) { expanded = false }

    val headerShape = remember { Capsule() }
    val bodyShape = remember {
        RoundedRectangle(
            cornerRadius = PickerCornerRadius,
            style = RoundedCornerStyle.Continuous
        )
    }
    val resolvedLayoutHeight = layoutHeight.coerceAtLeast(48.dp)
    val headerOffset = if (portalOpensUp && bodyActive) resolvedLayoutHeight - headerHeight else 0.dp
    val renderedBodyOffset = if (portalOpensUp && bodyActive) resolvedLayoutHeight - actualBodyBottom else bodyOffset
    val headerTopFraction = headerOffset.value / resolvedLayoutHeight.value
    val headerHeightFraction =
        (headerHeight.value / resolvedLayoutHeight.value).coerceIn(0f, 1f)
    val bodyTopFraction =
        (renderedBodyOffset.value / resolvedLayoutHeight.value).coerceIn(0f, 1f)
    val bodyBottomFraction =
        ((renderedBodyOffset + visualBodyHeight).value / resolvedLayoutHeight.value).coerceIn(0f, 1f)
    val bodyCornerToHeaderRatio = PickerCornerRadius.value / headerHeight.value
    val mergeSmoothnessToHeaderRatio = mergeSmoothnessDp / headerHeight.value
    val surfaceShape = remember(
        headerTopFraction,
        portalOpensUp,
        headerHeightFraction,
        bodyTopFraction,
        bodyBottomFraction,
        bodyCornerToHeaderRatio,
        bodyExtendsBeyondHeader,
        mergeSmoothnessToHeaderRatio
    ) {
        GenericShape { size, _ ->
            val headerHeightPx = (size.height * headerHeightFraction)
                .coerceIn(0f, size.height)
            val headerTopPx = size.height * headerTopFraction
            val bodyTopPx = (size.height * bodyTopFraction)
                .coerceIn(0f, size.height)
            val bodyBottomPx = (size.height * bodyBottomFraction)
                .coerceIn(0f, size.height)
            val bodyCornerPx = headerHeightPx * bodyCornerToHeaderRatio
            val bodyVisible = bodyExtendsBeyondHeader && bodyBottomPx > bodyTopPx + 0.5f
            val implicitContour = if (bodyVisible) {
                val headerBox = RoundedRectMergeGeometry.RoundedBox(
                    left = 0f, top = headerTopPx, right = size.width,
                    bottom = headerTopPx + headerHeightPx, radius = headerHeightPx / 2f
                )
                val bodyBox = RoundedRectMergeGeometry.RoundedBox(
                    left = 0f, top = bodyTopPx, right = size.width,
                    bottom = bodyBottomPx, radius = bodyCornerPx
                )
                RoundedRectMergeGeometry.mergedVerticalOutline(
                    header = if (portalOpensUp) bodyBox else headerBox,
                    body = if (portalOpensUp) headerBox else bodyBox,
                    smoothness = headerHeightPx * mergeSmoothnessToHeaderRatio,
                    stationCount = 44
                )
            } else {
                null
            }

            if (implicitContour != null) {
                addPickerImplicitContour(implicitContour)
            } else {
                addPickerRoundedContour(
                    left = 0f,
                    top = headerTopPx,
                    right = size.width,
                    bottom = headerTopPx + headerHeightPx,
                    radius = headerHeightPx / 2f
                )
                if (bodyVisible) {
                    addPickerRoundedContour(
                        left = 0f,
                        top = bodyTopPx,
                        right = size.width,
                        bottom = bodyBottomPx,
                        radius = bodyCornerPx
                    )
                }
            }
        }
    }
    val surfaceModifier = if (accessibility.highContrast) {
        Modifier
            .clip(surfaceShape)
            .background(appearance.solidSurface)
            .border(1.dp, appearance.border, surfaceShape)
    } else if (glassBackdrop != null) {
        if (layerPolicy.bridgeOverlayAlpha > 0f) {
            // Restore the full SDF neck while the two masses are connected. The layer is absent
            // at both settled endpoints, so its perimeter cannot return as a final thin ring.
            Modifier
                .clip(surfaceShape)
                .background(
                    appearance.surface.copy(
                        alpha = (if (isLightTheme) 0.055f else 0.035f) *
                            layerPolicy.bridgeOverlayAlpha
                    )
                )
                .upstreamPickerGlassRim(
                    shape = surfaceShape,
                    intensity = if (contentEnabled) 1.14f else 0.62f,
                    isLightTheme = isLightTheme,
                    pressProgress = { layerPolicy.perimeterInteractionProgress }
                )
        } else {
            Modifier
        }
    } else {
        Modifier
            .clip(surfaceShape)
            .background(appearance.solidSurface)
            .border(0.75.dp, appearance.border, surfaceShape)
    }

    AnchoredGlassPortal(
        active = bodyActive,
        anchorHeight = headerHeight,
        renderedHeight = resolvedLayoutHeight,
        desiredBodyHeight = minOf(bodyContentHeight, PickerMaxBodyHeight),
        // A second tap can land on the scrim as the action row retracts. Keep the
        // accepted action pending; removing this picker from its page cancels it.
        onDismiss = { expanded = false },
        onClosed = {
            if (actionPending) {
                actionPending = false
                latestAction?.invoke()
            }
        },
        onSpaceAvailable = { space, up ->
            portalBodySpace = with(density) { space.toDp() }
            portalOpensUp = up
        },
        modifier = modifier.fillMaxWidth().onGloballyPositioned {
            anchorWidth = with(density) { it.size.width.toDp() }
        },
        popupWidth = popupWidth?.let { width ->
            val start = anchorWidth.takeIf { it > 0.dp } ?: width
            // Use the existing motion so a compact trigger grows into the full readable menu.
            start + (maxOf(start, width) - start) * smoothStep(settledProgress)
        }
    ) {
    ProvideWallpaperAppearance(appearance) {
    Box(
        modifier = Modifier.fillMaxWidth()
            .height(resolvedLayoutHeight)
    ) {
        // Rounded children use the real lens path (the backdrop library requires a rounded-
        // rectangular shape). While the body is still connected, one generic-outline backdrop
        // layer owns the entire mass so no independent body-top refraction edge can appear.
        if (glassBackdrop != null && !accessibility.highContrast) {
            PickerLensLayer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(resolvedLayoutHeight)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0.5f, 0f)
                        scaleX = headerScale * collisionScaleX
                        scaleY = headerScale * collisionScaleY
                        alpha = layerPolicy.mergedBodyLensAlpha
                    }
                    .drawWithContent {
                        // The persistent header lens owns the complete outer capsule. Restrict the
                        // temporary generic blur to the newly emerged body so its different edge
                        // sampling cannot flash a second ring around the header during hand-off.
                        val clipTop = if (portalOpensUp) 0f else headerHeight.toPx().coerceAtMost(size.height)
                        val clipBottom = if (portalOpensUp) headerOffset.toPx() else size.height
                        clipRect(top = clipTop, bottom = clipBottom) {
                            this@drawWithContent.drawContent()
                        }
                    },
                backdrop = glassBackdrop,
                shape = surfaceShape,
                cornerRadius = PickerCornerRadius,
                motionVelocity = mergedMotionVelocity,
                pressProgress = layerPolicy.perimeterInteractionProgress,
                enabled = contentEnabled,
                // The runtime lens is rounded-rectangular. The transient implicit outline uses a
                // backdrop blur so it remains one sampled glass surface without an internal seam.
                forceBlurFallback = true,
                popupSurface = true
            )
            PickerLensLayer(
                modifier = Modifier
                    .offset(y = headerOffset)
                    .fillMaxWidth()
                    .height(headerHeight)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0.5f, 0f)
                        scaleX = headerScale * collisionScaleX
                        scaleY = headerScale * collisionScaleY
                        alpha = layerPolicy.headerLensAlpha
                    },
                backdrop = glassBackdrop,
                shape = headerShape,
                cornerRadius = headerHeight / 2f,
                motionVelocity = 0f,
                pressProgress = layerPolicy.perimeterInteractionProgress,
                enabled = contentEnabled
            )
            if (bodyPrecomposed) {
                PickerLensLayer(
                    modifier = Modifier
                        .offset(y = renderedBodyOffset)
                        .fillMaxWidth()
                        .height(visualBodyHeight.coerceAtLeast(1.dp))
                        .graphicsLayer {
                            alpha = layerPolicy.separatedBodyLensAlpha
                        },
                    backdrop = glassBackdrop,
                    shape = bodyShape,
                    cornerRadius = PickerCornerRadius,
                    motionVelocity = extentVelocity,
                    pressProgress = layerPolicy.perimeterInteractionProgress,
                    enabled = contentEnabled,
                    popupSurface = true
                )
            }
        }

        // The contour exists only during the connected phase. At both settled endpoints it draws
        // nothing, so the persistent rounded lenses cannot acquire a second perimeter.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 0f)
                    scaleX = headerScale * collisionScaleX
                    scaleY = headerScale * collisionScaleY
                }
                .drawWithContent {
                    if (glassBackdrop != null && !accessibility.highContrast) {
                        val top = if (portalOpensUp) 0f else headerHeight.toPx().coerceAtMost(size.height)
                        val bottom = if (portalOpensUp) headerOffset.toPx() else size.height
                        clipRect(top = top, bottom = bottom) { this@drawWithContent.drawContent() }
                    } else drawContent()
                }
                .then(surfaceModifier)
        )

        Row(
            modifier = Modifier
                .offset(y = headerOffset)
                .fillMaxWidth()
                .height(headerHeight)
                // The menu's changing extent must not change the trigger's tone.
                .wallpaperRegion(regionState)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 0f)
                    scaleX = headerScale * collisionScaleX
                    scaleY = headerScale * collisionScaleY
                }
                .clip(headerShape)
                .clickable(
                    interactionSource = headerInteraction,
                    indication = null,
                    enabled = canOpen && !actionPending,
                    role = Role.Button,
                    onClick = { expanded = !expanded }
                )
                .semantics {
                    contentDescription = label ?: placeholder
                    stateDescription = buildString {
                        append(selectedLabel ?: placeholder)
                        append(if (expanded) "，已展开" else "，已收起")
                    }
                    if (!canOpen) disabled()
                }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (descriptorPresent) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (leadingIcon != null) {
                        Icon(
                            imageVector = leadingIcon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = secondaryContentColor
                        )
                    }
                    if (!label.isNullOrBlank()) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            color = secondaryContentColor,
                            maxLines = 1
                        )
                    }
                }
            }

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = if (descriptorPresent) {
                    Alignment.CenterEnd
                } else {
                    Alignment.CenterStart
                }
            ) {
                AnimatedContent(
                    targetState = selectedLabel ?: placeholder,
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = if (descriptorPresent) {
                        Alignment.CenterEnd
                    } else {
                        Alignment.CenterStart
                    },
                    transitionSpec = {
                        if (reduceMotion) {
                            fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                        } else {
                            (fadeIn(
                                animationSpec = tween(
                                    durationMillis = 160,
                                    delayMillis = 20,
                                    easing = PickerItemEasing
                                )
                            ) + slideInVertically(
                                animationSpec = tween(
                                    durationMillis = 180,
                                    easing = PickerItemEasing
                                ),
                                initialOffsetY = { it / 5 }
                            )) togetherWith fadeOut(tween(durationMillis = 90))
                        }
                    },
                    label = "morphPickerSelectedLabel"
                ) { value ->
                    Text(
                        text = value,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (selectedLabel == null) {
                            FontWeight.Normal
                        } else {
                            FontWeight.SemiBold
                        },
                        color = if (selectedLabel == null) {
                            secondaryContentColor.copy(
                                alpha = secondaryContentColor.alpha * 0.72f
                            )
                        } else {
                            primaryContentColor
                        },
                        textAlign = if (descriptorPresent) TextAlign.End else TextAlign.Start,
                        maxLines = selectedLabelLines,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "收起选项" else "展开选项",
                modifier = Modifier
                    .size(19.dp)
                    .rotate(arrowRotation),
                tint = secondaryContentColor.copy(alpha = 0.82f)
            )
        }

        if (bodyPrecomposed) {
            Box(
                modifier = Modifier
                    .offset(y = renderedBodyOffset)
                    .fillMaxWidth()
                    .height(visualBodyHeight.coerceAtLeast(1.dp))
                    .graphicsLayer {
                        alpha = bodyAlpha
                    }
                    .clip(bodyShape)
                    .padding(horizontal = PickerBodyHorizontalPadding)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(bodyContentHeight)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(itemHeight)
                                .graphicsLayer {
                                    translationY = highlightedOffset.value.toPx()
                                    alpha = highlightedAlpha
                                }
                                .clip(Capsule())
                                .background(highlightColor)
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = PickerBodyVerticalPadding),
                            verticalArrangement = Arrangement.spacedBy(PickerItemGap)
                        ) {
                            options.forEachIndexed { index, option ->
                                MorphingPickerRow(
                                    label = option.label,
                                    height = itemHeight,
                                    maxLines = labelLines,
                                    expanded = expanded,
                                    revealProgress = rowRevealProgress(index),
                                    enabled = option.enabled,
                                    selected = index == validSelectedIndex,
                                    action = false,
                                    onHighlight = { highlightedRow = index },
                                    onClick = {
                                        highlightedRow = index
                                        if (index != validSelectedIndex) haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                        onSelect(index)
                                        expanded = false
                                    }
                                )
                            }

                            if (hasAction) {
                                MorphingPickerRow(
                                    label = actionLabel.orEmpty(),
                                    height = itemHeight,
                                    maxLines = labelLines,
                                    expanded = expanded,
                                    revealProgress = rowRevealProgress(options.size),
                                    enabled = true,
                                    selected = false,
                                    action = true,
                                    onHighlight = { highlightedRow = options.size },
                                    onClick = {
                                        if (!actionPending) {
                                            actionPending = true
                                            expanded = false
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

}
}

@Composable
private fun MorphingPickerRow(
    label: String,
    height: Dp,
    maxLines: Int,
    expanded: Boolean,
    revealProgress: Float,
    enabled: Boolean,
    selected: Boolean,
    action: Boolean,
    onHighlight: () -> Unit,
    onClick: () -> Unit
) {
    val appearance = LocalWallpaperAppearanceColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    LaunchedEffect(pressed, expanded, enabled) {
        if (pressed && expanded && enabled) onHighlight()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer {
                val progress = revealProgress.coerceIn(0f, 1f)
                alpha = progress * if (enabled) 1f else 0.38f
                translationY = (1f - progress) * 6.dp.toPx()
            }
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = expanded && enabled,
                role = if (action) Role.Button else Role.RadioButton,
                onClick = onClick
            )
            .semantics {
                if (!expanded) hideFromAccessibility()
                if (!action) this.selected = selected
            }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected || action) FontWeight.SemiBold else FontWeight.Normal,
            color = if (action) {
                MaterialTheme.colorScheme.primary
            } else {
                appearance.onSurface
            },
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = appearance.onSurface
            )
        }
    }
}


// Original glassRim implementation from GlassChipSurface.kt; shared recipe retained.
private fun Modifier.upstreamPickerGlassRim(
    shape: Shape,
    intensity: Float = 1f,
    isLightTheme: Boolean = true,
    pressProgress: () -> Float = { 0f },
    pointerOffset: () -> Offset = { Offset.Unspecified }
): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val contourWidth = 1.dp.toPx()

    // 内缘：把轮廓整体内缩一圈再描边，光就贴在玻璃内壁上
    val insetPx = 1.1.dp.toPx()
    val innerSize = Size(
        (size.width - insetPx * 2f).coerceAtLeast(0.1f),
        (size.height - insetPx * 2f).coerceAtLeast(0.1f)
    )
    val innerOutline = shape.createOutline(innerSize, layoutDirection, this)
    val innerWidth = 1.2.dp.toPx()

    // 浅色主题下白色内缘线画在白色盘面上等于没画（白叠白），只留一点点；
    // 深色主题反过来，白线才是唯一能看见的光。
    val baseContour = if (isLightTheme) {
        GlassRecipe.ChipContourAlphaLight
    } else {
        GlassRecipe.ChipContourAlphaDark
    }
    val baseInner = if (isLightTheme) {
        GlassRecipe.ChipRimAlphaLight
    } else {
        GlassRecipe.ChipRimAlphaDark
    }

    onDrawWithContent {
        drawContent()
        val p = pressProgress().coerceIn(0f, 1f)

        // 内缘亮线：默认顶部略强，按压时最亮处移向手指
        val innerAlpha = baseInner * intensity * (1f + GlassRecipe.ChipPressedRimBoost * p)
        val pointer = pointerOffset()
        val innerBrush = if (pointer != Offset.Unspecified && p > 0.01f && size.minDimension > 0f) {
            // 用触点为中心的径向渐变去调制边缘线的亮度分布：
            // 靠近手指的那一段更亮。半径取整个控件，保证渐变覆盖全边缘。
            val travel = GlassRecipe.ChipHighlightTravel * p
            val center = Offset(
                size.width / 2f + (pointer.x - size.width / 2f) * travel,
                size.height / 2f + (pointer.y - size.height / 2f) * travel
            )
            Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = innerAlpha * 1.25f),
                    Color.White.copy(alpha = innerAlpha * 0.55f)
                ),
                center = center,
                radius = size.maxDimension * 0.85f
            )
        } else {
            Brush.verticalGradient(
                colorStops = arrayOf(
                    0.00f to Color.White.copy(alpha = innerAlpha),
                    0.50f to Color.White.copy(alpha = innerAlpha * 0.55f),
                    1.00f to Color.White.copy(alpha = innerAlpha * 0.75f)
                )
            )
        }
        translate(insetPx, insetPx) {
            drawOutline(innerOutline, brush = innerBrush, style = Stroke(innerWidth))
        }

        // 外轮廓：均匀一圈，界定形状。按下时收紧变实，像被压出的应力边
        val contourColor = if (isLightTheme) {
            Color.Black.copy(alpha = baseContour * intensity * (1f + 0.5f * p))
        } else {
            Color.White.copy(alpha = baseContour * intensity * (1f + 0.5f * p))
        }
        drawOutline(outline, color = contourColor, style = Stroke(contourWidth))
    }
}

@Composable
internal fun SystemPicker(
    options: List<String>, selectedIndex: Int?, onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier, label: String? = null, placeholder: String = "请选择",
    enabled: Boolean = true, actionLabel: String? = null, onAction: (() -> Unit)? = null,
    maxLabelLines: Int = 1, maxSelectedLabelLines: Int = maxLabelLines
) {
    LiquidPicker(options = options.map(::LiquidPickerOption), selectedIndex = selectedIndex,
        onSelect = onSelect, modifier = modifier, label = label, placeholder = placeholder,
        enabled = enabled, actionLabel = actionLabel, onAction = onAction,
        maxLabelLines = maxLabelLines, maxSelectedLabelLines = maxSelectedLabelLines)
}
