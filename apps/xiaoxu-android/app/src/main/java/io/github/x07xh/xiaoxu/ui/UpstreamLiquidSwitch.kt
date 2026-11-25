package io.github.x07xh.xiaoxu.ui

// SPDX-License-Identifier: GPL-3.0-only
// Source: znjhahaha/zhengfang-apk @ bb3b80a6d64e4c2057c0544bb417696ceaa2a9ed, LiquidComponents.kt (LiquidSwitch fallback).
// Original authors and contributors. Controlled-state adapter for async permission and storage.
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import com.kyant.shapes.Capsule

@Composable
fun LiquidSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    checkedColor: Color = Color.Unspecified
) {
    val isLightTheme = !isSystemInDarkTheme()
    val activeCheckedColor = when {
        checkedColor.isSpecified -> checkedColor
        isLightTheme -> Color(0xFF34C759)
        else -> Color(0xFF30D158)
    }
    val inactiveTrackColor = if (isLightTheme) {
        Color(0xFF787878).copy(alpha = 0.20f)
    } else {
        Color(0xFF787880).copy(alpha = 0.36f)
    }
    // 禁用态靠"实色降对比"表达，不靠整体降透明度：后者会让轨道色透进 thumb，
    // 把绿轨道与白旋钮糊成一块灰绿。
    val disabledTrackColor = if (isLightTheme) {
        Color(0xFFE3E5E9)
    } else {
        Color(0xFF3A3C41)
    }
    val thumbColor = when {
        enabled -> Color.White
        isLightTheme -> Color(0xFFF2F2F4)
        else -> Color(0xFFB8BABE)
    }
    val accessibility = rememberGlassAccessibilityMode()
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val dragWidth = with(density) { 20.dp.toPx() }
    val animationScope = rememberCoroutineScope()
    val latestChecked by androidx.compose.runtime.rememberUpdatedState(checked)
    val latestOnCheckedChange by androidx.compose.runtime.rememberUpdatedState(onCheckedChange)
    val latestEnabled by androidx.compose.runtime.rememberUpdatedState(enabled)
    val latestDragWidth by androidx.compose.runtime.rememberUpdatedState(dragWidth)
    val latestIsLtr by androidx.compose.runtime.rememberUpdatedState(isLtr)
    var didDrag by remember { mutableStateOf(false) }
    var fraction by remember { mutableFloatStateOf(if (checked) 1f else 0f) }
    val dragAnimation = remember(animationScope) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = fraction,
            valueRange = 0f..1f,
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 1.5f,
            onDragStarted = { didDrag = false; fraction = value.coerceIn(0f, 1f) },
            onDragStopped = {
                if (!latestEnabled) {
                    updateTarget(if (latestChecked) 1f else 0f)
                    return@DampedDragAnimation
                }
                fraction = if (didDrag) {
                    didDrag = false
                    if (targetValue >= 0.5f) 1f else 0f
                } else {
                    if (latestChecked) 0f else 1f
                }
                // release() owns the spring. Snapping the drag value here would
                // erase the entire tap animation before that spring can start.
                val requested = fraction == 1f
                // Permission or storage writes may reject a request. Render the committed value until it changes.
                fraction = if (latestChecked) 1f else 0f
                updateTarget(fraction)
                latestOnCheckedChange(requested)
            },
            onDrag = { _, dragAmount ->
                if (!latestEnabled) return@DampedDragAnimation
                if (!didDrag) didDrag = dragAmount.x != 0f
                val delta = dragAmount.x / latestDragWidth
                fraction = if (latestIsLtr) {
                    (fraction + delta).fastCoerceIn(0f, 1f)
                } else {
                    (fraction - delta).fastCoerceIn(0f, 1f)
                }
                updateValue(fraction)
            },
            onDragCancelled = { didDrag = false; fraction = if (latestChecked) 1f else 0f }
        )
    }

    LaunchedEffect(accessibility.reduceMotion, dragAnimation) {
        dragAnimation.setReducedMotion(accessibility.reduceMotion, if (latestChecked) 1f else 0f)
    }

    // value 只允许一条驱动路径：手势内走 updateValue（直接操纵），
    // 外部 checked 变化走 animateToValue（带按压释放）。两者若并发对同一个
    // Animatable 发起 animateTo，先发起的会被抢占并连锁取消释放动画。
    LaunchedEffect(checked) {
        val target = if (checked) 1f else 0f
        if (target != fraction) {
            fraction = target
            dragAnimation.animateToValue(target)
        }
    }

    Box(
        modifier = modifier
            .width(64.dp)
            .height(48.dp)
            .then(if (enabled) dragAnimation.modifier else Modifier)
            .semantics {
                role = Role.Switch
                stateDescription = if (checked) "已开启" else "已关闭"
                toggleableState = androidx.compose.ui.state.ToggleableState(checked)
                onClick {
                    if (enabled) {
                        latestOnCheckedChange(!latestChecked)
                        true
                    } else {
                        false
                    }
                }
                if (!enabled) disabled()
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .clip(Capsule())
                .drawBehind {
                    drawRect(
                        if (enabled) {
                            lerp(
                                inactiveTrackColor,
                                activeCheckedColor,
                                dragAnimation.value
                            )
                        } else {
                            disabledTrackColor
                        }
                    )
                }
                .size(64.dp, 28.dp)
        )

        val thumbModifier = Modifier
            .graphicsLayer {
                val padding = 2.dp.toPx()
                translationX = if (isLtr) {
                    androidx.compose.ui.util.lerp(padding, padding + dragWidth, dragAnimation.value)
                } else {
                    androidx.compose.ui.util.lerp(-padding, -(padding + dragWidth), dragAnimation.value)
                }
            }
            .testTag("liquid-switch-thumb")

            Box(
                modifier = thumbModifier
                    .graphicsLayer {
                        if (accessibility.reduceMotion) {
                            scaleX = 1f
                            scaleY = 1f
                        } else {
                            scaleX = dragAnimation.scaleX
                            scaleY = dragAnimation.scaleY
                        }
                    }
                    .clip(Capsule())
                    .background(thumbColor)
                    .size(40.dp, 24.dp)
            )
    }
}
