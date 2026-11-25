package io.github.x07xh.xiaoxu.ui

// SPDX-License-Identifier: GPL-3.0-only
// Source: znjhahaha/zhengfang-apk @ bb3b80a6d64e4c2057c0544bb417696ceaa2a9ed, LiquidSelectionComponents.kt.
// Original authors and contributors. Reuse fallback geometry, drag state and selection.
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun LiquidSegmentedControl(options: List<String>, selectedIndex: Int,
    onSelect: (Int) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    gestures: Boolean = true, height: Dp = 48.dp) {
    if (options.isEmpty()) return
    val optionCount = options.size
    val clampedSelectedIndex = selectedIndex.coerceIn(0, options.lastIndex)
    val colors = MaterialTheme.colorScheme
    val accent = colors.primary
    val accessibility = rememberGlassAccessibilityMode()
    val animationScope = rememberCoroutineScope()
    val latestSelectedIndex by rememberUpdatedState(clampedSelectedIndex)
    val latestOnSelect by rememberUpdatedState(onSelect)
    val haptics = LocalHapticFeedback.current
    val pressedScale = GlassRecipe.SegIndicatorPressedScale
    val indicatorShape = RoundedCornerShape(percent = 50)
    BoxWithConstraints(modifier.fillMaxWidth().height(height)) {
        val density = LocalDensity.current
        val compact = height <= 36.dp
        val horizontalPadding = if (compact) 3.dp else 5.dp
        val verticalPadding = if (compact) 3.dp else 5.dp
        val horizontalPaddingPx = with(density) { horizontalPadding.toPx() }
        val segmentWidthPx = ((constraints.maxWidth - horizontalPaddingPx * 2f) / optionCount).coerceAtLeast(1f)
        val indicatorHeight = (height - verticalPadding * 2).coerceAtLeast(1.dp)
        val dragAnimation = remember(animationScope, optionCount) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = clampedSelectedIndex.toFloat(),
                valueRange = 0f..(optionCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = pressedScale,
                settleAnimationSpec = spring(dampingRatio = .5f, stiffness = 340f),
                releaseScaleAnimationSpec = spring(dampingRatio = .34f, stiffness = 280f),
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


        val segmentLabels: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxSize()
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
                    androidx.compose.ui.graphics.lerp(colors.onSurfaceVariant, accent, selectionAmount)
                } else {
                    // 禁用只降内容对比，轨道与滑块保持实色，避免整块糊成半透明灰
                    colors.onSurface.copy(alpha = 0.38f)
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
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = label, modifier = Modifier.padding(horizontal = if (compact) 6.dp else 10.dp),
                        style = if (compact) {
                            MaterialTheme.typography.labelMedium
                        } else {
                            MaterialTheme.typography.labelLarge
                        },
                        // A partial drag changes paint/optics, not glyph geometry.
                        // Commit font weight once the user actually selects a tab.
                        fontWeight = if (index == clampedSelectedIndex) {
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
        Box(Modifier.fillMaxSize().clip(indicatorShape)
            .background(colors.surfaceContainerHigh)
            .border(0.75.dp, colors.outlineVariant, indicatorShape)
            .drawBehind {
                val (sx, sy) = segIndicatorScale(dragAnimation)
                val left = horizontalPaddingPx + dragAnimation.value * segmentWidthPx
                val top = verticalPadding.toPx()
                val w = segmentWidthPx
                val h = indicatorHeight.toPx()
                scale(sx, sy, Offset(left + w / 2f, top + h / 2f)) {
                    drawRoundRect(colors.surface, Offset(left, top), androidx.compose.ui.geometry.Size(w, h),
                        androidx.compose.ui.geometry.CornerRadius(h / 2f))
                }
            }) { segmentLabels() }
    }
}

private fun segIndicatorScale(anim: DampedDragAnimation): Pair<Float, Float> {
    var sx = anim.scaleX
    var sy = anim.scaleY
    // The position spring already owns physical velocity. A second slow velocity filter
    // erased short taps before their deformation could become visible.
    val stretch = (abs(anim.positionVelocity) / 8f).coerceIn(0f, 1f) * GlassRecipe.SegIndicatorMaxVelocityStretch
    sx *= 1f + stretch
    sy /= 1f + stretch * 0.72f
    // 平移为 0：滑块的 translationX 在**外层** graphicsLayer 上，
    // glassLens 在它内部，已经跟着走了。见 GlassLensTransform。
    return sx to sy
}

