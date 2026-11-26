package io.github.x07xh.xiaoxu.ui

// SPDX-License-Identifier: GPL-3.0-only
// Source: znjhahaha/zhengfang-apk @ bb3b80a6d64e4c2057c0544bb417696ceaa2a9ed, ui/system/LiquidSelectionComponents.kt.
// Original authors and contributors. Full segmented implementation; only package and call compatibility adapted.

import androidx.compose.ui.platform.testTag

import androidx.compose.ui.draw.drawBehind
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

@Composable
internal fun LiquidSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** Keep selection taps but let the caller own drags (the indicator still follows `selectedIndex`). */
    gestures: Boolean = true,
    height: Dp = 52.dp,
    backdrop: Backdrop? = LocalControlBackdrop.current,
    edgePadding: Dp? = null,
    verticalInset: Dp? = null,
    restingRefraction: Float? = null,
    showTrack: Boolean = true,
    refractLabels: Boolean = true,
    stableLabelRefraction: Boolean = false,
    labelContent: (@Composable (index: Int, selection: Float, color: Color) -> Unit)? = null
) {
    if (options.isEmpty()) return

    val optionCount = options.size
    val stableLabels = remember(options) { SegmentLabelSource(options.size) }
    var stableCoordinates by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
    val clampedSelectedIndex = selectedIndex.coerceIn(0, optionCount - 1)
    val glassBackdrop = backdrop?.takeIf { isBackdropSupported() }
    val useGlass = glassBackdrop != null
    // API 33+ uses a runtime lens; API 31/32 refracts the background asynchronously.
    val hasRealLens = isRuntimeLensEnabled()
    // Cards and dialogs keep the theme; a free-standing control follows the wallpaper under it.
    // Labels, track and indicator must all read the same palette, or one of them inverts.
    val palette = localReadableAppearance()
    val isLightTheme = palette.usesDarkForeground
    val accent = readableAccent(palette)
    val readableBacking = !LocalThemedContent.current &&
        com.tyust.course.manager.AppearanceSettingsManager.mode != com.tyust.course.manager.WallpaperMode.Preset
    val trackShape = RoundedCornerShape(percent = 50)
    val indicatorShape = RoundedCornerShape(percent = 50)
    // Animated date labels stay live above the lens. Other controls keep labels
    // in a small optical overlay, independent of the more expensive page background.
    val segmentLabelsSnapshot = remember { GlassLensContentSnapshot() }
    val segmentsBackdrop = if (refractLabels) rememberLayerBackdrop(onDraw = { segmentLabelsSnapshot.draw(this) }) else null
    val indicatorDrawState = remember { GlassLensDrawState() }
    val animationScope = rememberCoroutineScope()
    val accessibility = rememberGlassAccessibilityMode()
    val trackMaterial = GlassMaterials.resolve(
        role = GlassMaterialRole.Navigation,
        accessibility = accessibility,
        interactionProgress = 0f
    )
    val indicatorMaterial = GlassMaterials.resolve(
        role = GlassMaterialRole.Interactive,
        accessibility = accessibility,
        interactionProgress = 0f
    )
    // Reduced motion is resolved by the retained animation state, so re-enabling it
    // restores the normal spring without rebuilding the current drag position.
    val pressedScale = GlassRecipe.SegIndicatorPressedScale
    val latestSelectedIndex by rememberUpdatedState(clampedSelectedIndex)
    val latestOnSelect by rememberUpdatedState(onSelect)
    val haptics = LocalHapticFeedback.current

    // 折射锚点在 BoxWithConstraints 内部创建（要用到 constraints 算出的尺寸），
    // 但必须挂在这一层 —— 所以用一个 State 把它带出来，下一帧生效。
    // 差一帧无所谓：底图本来就是"上一帧的结果"（见 GlassLens.kt 的说明）。
    var segLensAnchor by remember { mutableStateOf<GlassLensAnchor?>(null) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer {
                clip = false
            }
            .glassLensAnchor(segLensAnchor)
            .onGloballyPositioned { stableCoordinates = it; stableLabels.resize(it.size) }
    ) {
        val density = LocalDensity.current
        val compact = height <= 36.dp
        val horizontalPadding = edgePadding ?: if (compact) 3.dp else 5.dp
        val verticalPadding = verticalInset ?: if (compact) 3.dp else 5.dp
        val refractionFloor = restingRefraction?.coerceIn(0f, 1f) ?: if (compact) 0.30f else 0.42f
        val horizontalPaddingPx = with(density) { horizontalPadding.toPx() }
        val segmentWidthPx =
            ((constraints.maxWidth - horizontalPaddingPx * 2f) / optionCount)
                .coerceAtLeast(1f)
        val segmentWidth = with(density) { segmentWidthPx.toDp() }
        val indicatorWidth = segmentWidth
        // key 里【不放 segmentWidthPx】：这个动画的状态是"段序号"空间的
        // （valueRange = 0..count-1，速度也按序号算），宽度只在手势里做 px→序号换算，
        // 那处 pointerInput 自己带 key。写进来的后果是顶栏折叠时宽度每帧在变，
        // 于是动画对象每帧重建一次、滑块状态被反复丢掉。
        val dragAnimation = remember(animationScope, optionCount) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = clampedSelectedIndex.toFloat(),
                valueRange = 0f..(optionCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = pressedScale,
                settleAnimationSpec = MotionSpring.segmentedSettle(),
                releaseScaleAnimationSpec = MotionSpring.segmentedRelease(),
                pressScaleAnimationSpec = spring(dampingRatio = 0.64f, stiffness = 680f),
                onDragStarted = {},
                onDragStopped = {},
                onDrag = { _, _ -> }
            )
        }

        LaunchedEffect(accessibility.reduceMotion, dragAnimation) {
            dragAnimation.setReducedMotion(accessibility.reduceMotion, latestSelectedIndex.toFloat())
        }

        val requestSelection: (Int) -> Unit = { requestedIndex ->
            if (enabled) {
                val targetIndex = requestedIndex.coerceIn(0, optionCount - 1)
                if (targetIndex != latestSelectedIndex) haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                latestOnSelect(targetIndex)
                dragAnimation.animateToValue(targetIndex.toFloat())
                animationScope.launch {
                    withFrameNanos { }
                    val acceptedIndex = latestSelectedIndex.coerceIn(0, optionCount - 1)
                    if (acceptedIndex != targetIndex) {
                        dragAnimation.animateToValue(acceptedIndex.toFloat())
                    }
                }
            }
        }

        LaunchedEffect(clampedSelectedIndex, dragAnimation) {
            if (abs(dragAnimation.targetValue - clampedSelectedIndex) > 0.001f) {
                dragAnimation.animateToValue(clampedSelectedIndex.toFloat())
            }
        }

        val recipeTrackColor = if (hasRealLens) {
            if (isLightTheme) {
                Color.White.copy(alpha = GlassRecipe.SegTrackSurfaceAlphaLight)
            } else {
                Color.Black.copy(alpha = GlassRecipe.SegTrackSurfaceAlphaDark)
            }
        } else {
            if (isLightTheme) {
                Color(GlassRecipe.NavLegacyTrackSurfaceLight)
                    .copy(alpha = GlassRecipe.NavLegacyTrackSurfaceAlpha)
            } else {
                Color.Black.copy(alpha = GlassRecipe.NavLegacyTrackSurfaceAlpha)
            }
        }
        // A photo can put black and white under the same label; a 10–16 % tint separates
        // neither from it. Custom wallpapers get at least the tone-mapped backing.
        val trackBackgroundColor = if (readableBacking) {
            palette.surface.copy(alpha = maxOf(palette.surface.alpha, recipeTrackColor.alpha))
        } else {
            recipeTrackColor
        }
        val trackBorderColor = palette.onSurface.copy(
            alpha = when {
                compact && useGlass -> 0.07f
                useGlass -> 0.10f
                else -> 0.14f
            }
        )
        val indicatorHeight = (height - verticalPadding * 2).coerceAtLeast(1.dp)
        val materialBackdrop = if (!refractLabels && glassBackdrop != null)
            SegmentMaterialSource(glassBackdrop, trackBackgroundColor)
        else glassBackdrop
        val stableOptics = stableLabelRefraction && labelContent == null && useGlass && !accessibility.highContrast
        val indicatorBackdrop = if (materialBackdrop != null && stableOptics) {
            // Like the course tabs, disperse the composited material + tinted glyphs.
            // Dispersing a transparent glyph texture separately mixes empty RGB with
            // glyph coverage and creates dark/primary-colour fragments at the rim.
            SegmentRefractionSource(materialBackdrop, stableLabels,
                if (enabled) accent else palette.onSurface.copy(alpha = .38f))
        } else if (materialBackdrop != null && segmentsBackdrop != null) {
            rememberCombinedBackdrop(materialBackdrop, segmentsBackdrop)
        } else {
            materialBackdrop
        }

        fun textFrameReady(): Boolean {
            stableLabels.revision // Redraw the mask after fonts/layout change.
            // Both the optical frame and its synchronous backdrop fallback now
            // contain tinted glyphs. Leaving ordinary text inside the fallback
            // would paint the original colour over it a second time.
            return stableOptics && stableLabels.ready
        }
        val textMask: androidx.compose.ui.graphics.drawscope.ContentDrawScope.() -> Unit = {
            val contentScope = this
            if (!textFrameReady()) drawContent() else {
                val t = segIndicatorScale(dragAnimation)
                val left = horizontalPaddingPx + dragAnimation.value * segmentWidthPx
                val top = verticalPadding.toPx()
                val w = segmentWidthPx; val h = indicatorHeight.toPx()
                val rect = androidx.compose.ui.geometry.Rect(left + (w - w*t.scaleX)/2f, top + (h - h*t.scaleY)/2f,
                    left + (w + w*t.scaleX)/2f, top + (h + h*t.scaleY)/2f)
                val mask = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(rect,
                    androidx.compose.ui.geometry.CornerRadius(h*t.scaleX/2f, h*t.scaleY/2f))) }
                clipPath(mask, androidx.compose.ui.graphics.ClipOp.Difference) { contentScope.drawContent() }
            }
        }

        // API 31/32：平台没有 AGSL，改用离屏 ES 2.0 做真折射（见 GlassLens.kt）。
        // 结构与 CapsuleNavigationBar 一致：锚点挂在**不随滑块移动**的外层，
        // 底图上传一次，滑块滑动时只改采样窗口。
        // Cache material and immutable glyphs separately, then composite them BEFORE
        // a single optical pass, matching the course tabs on both platform paths.
        val lensDensity = LocalDensity.current
        val lensAnchor = if (glassBackdrop != null) {
            // 标签带上选项文字：屏幕上同时有多个分段控件，且尺寸可能相同
            // （登录页的「密码登录/Cookie登录」与课程页的「可选/已选」都是 381x126），
            // 只按尺寸命名会互相覆盖。
            rememberGlassLensRegion("seg-" + options.joinToString("_"), isLightTheme, refractLabels, glassBackdrop,
                stableOptics, if (stableOptics) accent else null, enabled,
                freshness = LocalPageGlassFreshness.current,
                rasterizeOverlayOnCpu = true,
                overlaySource = when {
                    stableOptics -> ({ _ -> stableLabels.draw(this,
                        if (enabled) accent else palette.onSurface.copy(alpha = .38f)) })
                    refractLabels -> ({ coords -> segmentLabelsSnapshot.draw(this, coords) })
                    else -> null
                }) { coords ->
                with(requireNotNull(materialBackdrop)) { drawBackdrop(lensDensity, coords, null) }
            }
        } else {
            null
        }
        // 把锚点交给外层的 glassLensAnchor（它需要外层那块不动的坐标）
        SideEffect {
            lensAnchor?.requireCurrentSource = !refractLabels
            lensAnchor?.retainCompatibleBackground = stableOptics
            segLensAnchor = lensAnchor
        }
        // The region refreshes both after selection and after the underlying page settles.

        // One visible label row owns gestures and semantics on every API level.
        val segmentLabels: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent(textMask)
                .selectableGroup()
                .padding(horizontal = horizontalPadding, vertical = verticalPadding)
                .pointerInput(enabled, gestures, optionCount, segmentWidthPx) {
                    if (!enabled || !gestures) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        down.consume()
                        val startValue = dragAnimation.value
                        val touchSlop = viewConfiguration.touchSlop
                        val tappedIndex =
                            (down.position.x / segmentWidthPx)
                                .toInt()
                                .coerceIn(0, optionCount - 1)
                        var totalDragX = 0f
                        var dragging = false
                        var completed = false
                        var pointerId = down.id

                        dragAnimation.press(down.uptimeMillis)

                        try {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == pointerId }
                                    ?: break
                                if (event.changes.count { it.pressed } > 1) {
                                    event.changes.forEach { it.consume() }
                                    break
                                }
                                if (change.isConsumed) break
                                if (change.changedToUpIgnoreConsumed()) {
                                    completed = true
                                    change.consume()
                                    break
                                }
                                if (!dragging && abs(change.position.y - down.position.y) > touchSlop && abs(totalDragX) <= touchSlop) break

                                val dragAmount = change.positionChange()
                                if (dragAmount != Offset.Zero) {
                                    totalDragX += dragAmount.x
                                    if (!dragging && abs(totalDragX) > touchSlop) {
                                        dragging = true
                                    }
                                    if (dragging) {
                                        change.consume()
                                        dragAnimation.updateValue(
                                            (startValue + totalDragX / segmentWidthPx)
                                                .coerceIn(0f, (optionCount - 1).toFloat()),
                                            change.uptimeMillis
                                        )
                                    }
                                }
                                pointerId = change.id
                            }
                        } finally {
                            when {
                                !completed -> dragAnimation.animateToValue(latestSelectedIndex.toFloat())
                                dragging -> requestSelection(dragAnimation.targetValue.roundToInt())
                                else -> requestSelection(tappedIndex)
                            }
                        }
                    }
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            options.forEachIndexed { index, label ->
                val selectionAmount =
                    (1f - abs(dragAnimation.value - index.toFloat())).coerceIn(0f, 1f)
                val textColor = if (enabled) {
                    // 分段栏嵌在页面里，只靠字重差提示太弱：选中直接走主色，
                    // 未选中压到低对比，两端拉开后"当前在哪一段"一眼可见。
                    lerpColor(palette.onSurfaceVariant, accent, selectionAmount)
                } else {
                    // 禁用只降内容对比，轨道与滑块保持实色，避免整块糊成半透明灰
                    palette.onSurface.copy(alpha = 0.38f)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(indicatorShape)
                        .focusable(enabled)
                        .onKeyEvent { event ->
                            if (enabled && event.type == KeyEventType.KeyUp && event.key == Key.Enter) {
                                requestSelection(index)
                                true
                            } else {
                                false
                            }
                        }
                        .selectable(
                            selected = index == clampedSelectedIndex,
                            enabled = enabled,
                            role = Role.Tab,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { requestSelection(index) }
                        )
                        .then(if (labelContent != null) Modifier.semantics { contentDescription = label } else Modifier),
                    contentAlignment = Alignment.Center
                ) {
                    var glyphLayout by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
                    var glyphCoordinates by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
                    fun updateGlyph() {
                        val parent = stableCoordinates; val child = glyphCoordinates; val layout = glyphLayout
                        if (stableOptics && parent?.isAttached == true && child?.isAttached == true && layout != null) {
                            val before = stableLabels.revision
                            stableLabels.update(index, layout, parent.localPositionOf(child))
                            if (before != stableLabels.revision) {
                                lensAnchor?.invalidateOverlay()
                            }
                        }
                    }
                    SideEffect { updateGlyph() }
                    if (labelContent != null) labelContent(index, selectionAmount, textColor)
                    else Text(
                        text = label,
                        modifier = Modifier.padding(horizontal = if (compact) 6.dp else 10.dp)
                            .onGloballyPositioned { glyphCoordinates = it; updateGlyph() },
                        onTextLayout = { glyphLayout = it; updateGlyph() },
                        style = if (compact) {
                            MaterialTheme.typography.labelMedium
                        } else {
                            MaterialTheme.typography.labelLarge
                        },
                        // A partial drag changes paint/optics, not glyph geometry.
                        // Commit font weight once the user actually selects a tab.
                        fontWeight = if (if (stableOptics) index == clampedSelectedIndex else selectionAmount >= 0.55f) {
                            FontWeight.ExtraBold
                        } else {
                            FontWeight.SemiBold
                        },
                        color = textColor,
                        maxLines = 1,
                        softWrap = false,
                        overflow = if (compact) TextOverflow.Clip else TextOverflow.Ellipsis
                    )
                }
            }
        }
        }

        val trackTransform: GraphicsLayerScope.() -> Unit = {
            val maxGain = 16.dp.toPx()
            val pressScale = lerp(1f, 1f + maxGain / size.width, dragAnimation.pressProgress)
            val indicatorBoost = ((dragAnimation.scaleX + dragAnimation.scaleY) / 2f - 1f)
                .coerceIn(0f, 0.4f)
            val scale = pressScale * (1f + indicatorBoost * 0.18f)
            scaleX = scale
            scaleY = scale
        }
        val fallbackIndicatorColor = palette.solidSurface
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (!showTrack && glassBackdrop != null) {
                        Modifier
                    } else if (glassBackdrop != null) {
                        Modifier.drawBackdrop(
                            backdrop = glassBackdrop,
                            shape = { Capsule() },
                            effects = {
                                vibrancy()
                                if (hasRealLens) {
                                    val params = resolvePhysicalLens(
                                        scope = this,
                                        material = trackMaterial,
                                        minCornerRadiusPx = size.minDimension / 2f,
                                        minDimensionPx = size.minDimension,
                                        interactionProgress = 0f,
                                        enableBlur = true,
                                        allowChromaticAberration = false,
                                        pressScalesRefraction = false
                                    )
                                    if (params.blurPx > 0f) blur(params.blurPx)
                                    else blur(8.dp.toPx())
                                    if (params.useLens) {
                                        lens(
                                            refractionHeight = params.refractionHeightPx,
                                            refractionAmount = params.refractionAmountPx,
                                            chromaticAberration = false
                                        )
                                    }
                                } else {
                                    blur(GlassRecipe.NavLegacyTrackBlurDp.dp.toPx())
                                }
                            },
                            highlight = {
                                if (hasRealLens) Highlight.Default.copy(alpha = 0.16f) else null
                            },
                            shadow = { if (hasRealLens) Shadow(alpha = 0.08f) else null },
                            layerBlock = trackTransform,
                            onDrawSurface = { drawRect(trackBackgroundColor) }
                        )
                    } else {
                        (if (showTrack) Modifier
                            .clip(trackShape)
                            .background(trackBackgroundColor)
                            .border(0.75.dp, trackBorderColor, trackShape) else Modifier)
                            .drawBehind {
                                val transform = segIndicatorScale(dragAnimation)
                                val left = horizontalPaddingPx + dragAnimation.value * segmentWidthPx
                                val top = verticalPadding.toPx()
                                val w = segmentWidthPx
                                val h = indicatorHeight.toPx()
                                scale(transform.scaleX, transform.scaleY, Offset(left + w / 2f, top + h / 2f)) {
                                    drawRoundRect(fallbackIndicatorColor, Offset(left, top),
                                        androidx.compose.ui.geometry.Size(w, h),
                                        androidx.compose.ui.geometry.CornerRadius(h / 2f))
                                    if (!refractLabels) drawRoundRect(
                                        palette.onSurface.copy(alpha = if (accessibility.highContrast) .65f else .18f),
                                        Offset(left, top), androidx.compose.ui.geometry.Size(w, h),
                                        androidx.compose.ui.geometry.CornerRadius(h / 2f),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(.75.dp.toPx()))
                                }
                            }
                    }
                )
        ) {
            if (refractLabels || glassBackdrop == null) segmentLabels()
        }

        val indicatorBaseModifier = Modifier
            .width(indicatorWidth)
            .height(indicatorHeight)
            .align(Alignment.CenterStart)
            .graphicsLayer {
                translationX = horizontalPaddingPx + dragAnimation.value * segmentWidthPx
                clip = false
            }

        if (glassBackdrop != null && indicatorBackdrop != null) {
            if (segmentsBackdrop != null) {
            // 专供滑块折射采样的隐藏层：染成主色后，透镜里浮出的就是饱和蓝字，
            // 滑块表面因此可以做到几乎透明，不必靠白色填充去制造存在感。
            val labelTint = ColorFilter.tint(if (enabled) accent else palette.onSurface.copy(alpha = 0.38f))
            Row(
                modifier = Modifier
                    .clearAndSetSemantics { }
                    .layerBackdrop(segmentsBackdrop)
                    .align(Alignment.CenterStart)
                    .height(indicatorHeight)
                    .fillMaxWidth()
                    .onGloballyPositioned { segmentLabelsSnapshot.coordinates = it }
                    .drawWithContent {
                        // Async readback must own the glyph commands, not mutable child layers
                        // that page transitions can discard before the capture is rasterized.
                        segmentLabelsSnapshot.record(this, labelTint)
                        lensAnchor?.invalidateOverlay()
                    }
                    .padding(horizontal = horizontalPadding),
                verticalAlignment = Alignment.CenterVertically
            ) {
                options.forEachIndexed { index, label ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (labelContent != null) labelContent(index, 1f, accent)
                        else Text(
                            text = label,
                            modifier = Modifier.padding(
                                horizontal = if (compact) 6.dp else 10.dp
                            ),
                            style = if (compact) {
                                MaterialTheme.typography.labelMedium
                            } else {
                                MaterialTheme.typography.labelLarge
                            },
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = if (compact) TextOverflow.Clip else TextOverflow.Ellipsis
                        )
                    }
                }
            }
            }

            Box(
                modifier = indicatorBaseModifier
                    .testTag("segment-background-lens")
                    // API31/32 真折射：画在 drawBackdrop 之前，所以它提供背景，
                    // 库那层的 surface / highlight / shadow 仍叠在上面。
                    .glassLens(
                        anchor = lensAnchor,
                        // lambda 而非现成值：pressProgress / velocity 是 snapshot
                        // state，组合期读会让整个控件按压时每帧重组。
                        // w/h 是**实测**尺寸，由 glassLens 在 draw 里给。
                        optics = { w, h ->
                            val minDim = minOf(w, h)
                            glassLensOpticsFrom(
                                material = indicatorMaterial,
                                density = density,
                                cornerRadiusPx = minDim / 2f,
                                minDimensionPx = minDim,
                                interactionProgress = dragAnimation.pressProgress,
                                motionIntensity = motionIntensityFromVelocity(
                                    velocityX = dragAnimation.velocity * segmentWidthPx,
                                    fullEffectVelocity =
                                        indicatorMaterial.optics.velocityForFullEffect
                                ),
                                pressScalesRefraction = true,
                                // Dense multi-line labels can opt out of resting distortion.
                                // Both rendering paths retain the same press/motion optics.
                                refractionFloor = refractionFloor,
                                chromaticAberrationAtRest = refractionFloor > 0f
                            )
                        },
                        // 与下面 layerBlock **同一份**形变。不传的话按下时库的
                        // highlight 环按放大后的轮廓画，而折射还是原尺寸，
                        // 屏幕上是一圈白环浮在玻璃外面。
                        scale = { _, _ -> segIndicatorScale(dragAnimation) },
                        drawState = indicatorDrawState
                    )
                    .drawBackdrop(
                    backdrop = indicatorBackdrop,
                    shape = { Capsule() },
                    effects = {
                        val press = dragAnimation.pressProgress
                        vibrancy()
                        if (hasRealLens) {
                            val motion = motionIntensityFromVelocity(
                                velocityX = dragAnimation.velocity * segmentWidthPx,
                                fullEffectVelocity = indicatorMaterial.optics.velocityForFullEffect
                            )
                            val params = resolvePhysicalLens(
                                scope = this,
                                material = indicatorMaterial,
                                minCornerRadiusPx = size.minDimension / 2f,
                                minDimensionPx = size.minDimension,
                                interactionProgress = press,
                                motionIntensity = motion,
                                enableBlur = false,
                                allowChromaticAberration = true,
                                chromaticAberrationAtRest = refractionFloor > 0f,
                                pressScalesRefraction = true,
                                refractionFloor = refractionFloor
                            )
                            if (params.useLens) {
                                lens(
                                    refractionHeight = params.refractionHeightPx,
                                    refractionAmount = params.refractionAmountPx,
                                    chromaticAberration = params.chromaticAberration
                                )
                            }
                        } else if (lensAnchor == null) {
                            // 既无 AGSL 也无离屏折射（API ≤ 30）：只剩 RGB 分离近似。
                            lens(
                                refractionHeight = 10.dp.toPx() * (0.42f + press * 0.58f),
                                refractionAmount = 14.dp.toPx() * (0.42f + press * 0.58f),
                                chromaticAberration = true
                            )
                            val legacyMotion = motionIntensityFromVelocity(
                                velocityX = dragAnimation.velocity * segmentWidthPx,
                                fullEffectVelocity = indicatorMaterial.optics.velocityForFullEffect
                            )
                            chromaticFringe(
                                (0.55f + press * 1.8f + legacyMotion * 1.2f)
                                    .coerceIn(0f, 2.2f).dp.toPx()
                            )
                        }
                        // lensAnchor != null（API31/32）：折射与七波长色散都已由
                        // glassLens 在这之前画完。这里**不能**再叠 chromaticFringe ——
                        // 那是两套不同的边缘着色互相污染，屏幕上就是那圈蓝紫边。
                    },
                    highlight = {
                        val progress = dragAnimation.pressProgress
                        val scaleComp = (
                            (dragAnimation.scaleX + dragAnimation.scaleY) / 2f
                            ).coerceAtLeast(1f)
                        // 有真折射时用同一份轮廓光配方（含 blurRadius 补偿）：
                        // 参照图里滑块边缘有一圈明显的亮边，离屏折射这条路原本走的是
                        // 下面那条没有 blurRadius 的老路，边缘偏硬。
                        if (hasRealLens || lensAnchor != null) {
                            Highlight.Default.copy(
                                width = Highlight.Default.width / scaleComp,
                                blurRadius = Highlight.Default.blurRadius / scaleComp,
                                alpha = GlassRecipe.SegSelectedRimAlpha + progress * 0.22f
                            )
                        } else {
                            Highlight.Default.copy(
                                width = Highlight.Default.width / scaleComp,
                                alpha = GlassRecipe.SegSelectedRimAlpha + progress * 0.22f
                            )
                        }
                    },
                    shadow = {
                        val progress = dragAnimation.pressProgress
                        if (hasRealLens) {
                            // 静止就带明显投影：选中感来自"浮起"，不是给滑块上色
                            Shadow(alpha = GlassRecipe.SegSelectedShadowAlpha + progress * 0.12f)
                        } else {
                            Shadow(alpha = GlassRecipe.SegSelectedShadowAlpha + progress * 0.30f)
                        }
                    },
                    innerShadow = {
                        val progress = dragAnimation.pressProgress
                        InnerShadow(
                            radius = 4.dp * progress,
                            alpha = progress * 0.5f
                        )
                    },
                    layerBlock = {
                        // 与上面 glassLens 的 scale 读同一个函数，两者不可能再脱钩
                        val segT = segIndicatorScale(dragAnimation)
                        val sx = segT.scaleX
                        val sy = segT.scaleY
                        scaleX = sx
                        scaleY = sy
                    },
                    onDrawBackdrop = { drawBackdrop ->
                        // API31/32 上背景已由 glassLens 以折射方式画过，不能再画一遍
                        if (lensAnchor == null || !indicatorDrawState.hasBackground) drawBackdrop()
                    },
                    onDrawSurface = {
                        val press = dragAnimation.pressProgress
                        // 有真折射（AGSL 或离屏 GL）就走同一份**提亮**填充。
                        //
                        // 下面那条 `Black×0.1` 的老路是"没有折射时靠压暗制造选中感"
                        // 留下的。现在 31/32 也有真折射了，继续叠黑的后果是：折射把
                        // 锐利壁纸带进来、再被这层黑压一遍，滑块整体发灰发褐，与粉色
                        // 轨道明显割裂（已在设备上截图确认）。
                        // 已在 API 35 上截库同一个控件对照：它是白色 0.18 的提亮，
                        // 滑块比轨道更亮更粉，而不是更暗。
                        if (stableLabelRefraction) {
                            drawRect((if (isLightTheme) Color.White else Color.Black).copy(alpha = .08f + press * .04f))
                        } else if (readableBacking) {
                            // The lens shows the wallpaper without the track's backing, so the
                            // selected label needs the same tone-mapped backing of its own.
                            val restAlpha = if (isLightTheme) {
                                GlassRecipe.SegSelectedSurfaceAlphaLight
                            } else {
                                GlassRecipe.SegSelectedSurfaceAlphaDark
                            }
                            drawRect(palette.surface.copy(alpha = maxOf(palette.surface.alpha, restAlpha)))
                        } else if (hasRealLens || lensAnchor != null) {
                            val solidColor = if (isLightTheme) {
                                Color(GlassRecipe.NavSelectedSolidColorLight)
                            } else {
                                Color(GlassRecipe.NavSelectedSolidColorDark)
                            }
                            val restAlpha = if (isLightTheme) {
                                GlassRecipe.SegSelectedSurfaceAlphaLight
                            } else {
                                GlassRecipe.SegSelectedSurfaceAlphaDark
                            }
                            val fillAlpha = lerp(
                                restAlpha,
                                GlassRecipe.NavSelectedGlassAlpha,
                                press
                            )
                            if (fillAlpha > 0f) {
                                drawRect(solidColor.copy(alpha = fillAlpha))
                            }
                        } else {
                            drawRect(Color.Black.copy(0.1f), alpha = 1f - press)
                            drawRect(Color.Black.copy(alpha = 0.03f * press))
                        }
                    }
                )
            )
        }

        // Child graphics layers animate without rerecording an ancestor Picture.
        // Keep changing dates in the foreground so a frozen glyph cannot cover them.
        if (!refractLabels && glassBackdrop != null) segmentLabels()
    }
}

/**
 * 分段控件滑块的按压 + 速度形变。
 *
 * 单独抽出来是因为它有**两个**消费者：库那层 `drawBackdrop(layerBlock)`，
 * 以及 API31/32 的 `glassLens(scale)`。两边必须逐帧一致 —— 不一致时按下会看到
 * 一圈白环（库画的 highlight）浮在玻璃（自家折射）外面。
 */
private fun segIndicatorScale(anim: DampedDragAnimation): GlassLensTransform {
    var sx = anim.scaleX
    var sy = anim.scaleY
    // The position spring already owns physical velocity. A second slow velocity filter
    // erased short taps before their deformation could become visible.
    val stretch = (abs(anim.positionVelocity) / 8f).coerceIn(0f, 1f) * GlassRecipe.SegIndicatorMaxVelocityStretch
    sx *= 1f + stretch
    sy /= 1f + stretch * 0.72f
    // 平移为 0：滑块的 translationX 在**外层** graphicsLayer 上，
    // glassLens 在它内部，已经跟着走了。见 GlassLensTransform。
    return GlassLensTransform(scaleX = sx, scaleY = sy)
}
