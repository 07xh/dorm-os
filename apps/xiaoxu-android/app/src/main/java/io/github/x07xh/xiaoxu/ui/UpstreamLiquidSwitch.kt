package io.github.x07xh.xiaoxu.ui

// SPDX-License-Identifier: GPL-3.0-only
// Source: znjhahaha/zhengfang-apk @ bb3b80a6d64e4c2057c0544bb417696ceaa2a9ed, LiquidComponents.kt.
// Original authors and contributors. Preserve committed preference state for asynchronous callbacks.

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
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
import androidx.compose.ui.unit.Density
import com.tyust.course.ui.system.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs

private const val SWITCH_LENS_TRACK_SCALE_X = 0.75f
private const val SWITCH_LENS_TRACK_SCALE_Y = 0.4f

@Composable
fun LiquidSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    checkedColor: Color = Color.Unspecified,
    backdrop: Backdrop? = LocalControlBackdrop.current
) {
    val isLightTheme = !rememberGlassDarkTheme()
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
    val optical = rememberNavigationMaterialTier() == NavigationMaterialTier.Refractive &&
        !accessibility.highContrast && !accessibility.reduceMotion && enabled
    val glassBackdrop = backdrop?.takeIf { optical && isBackdropSupported() }
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

    val trackBackdrop = rememberLayerBackdrop()
    // 轨道近场与官方 LiquidSlider 滑块同曲线：静止时轨道层纵向塔缩为 0，
    // 滑块是不被轨道颜色污染的纯白实体；按压时轨道层展开到实尺寸，
    // 配合 lens 在滑块内输出折射与色散。
    val scaledTrackBackdrop = rememberBackdrop(trackBackdrop) { drawTrackBackdrop ->
        val progress = dragAnimation.pressProgress
        val scaleX = lerp(2f / 3f, 0.75f, progress)
        val scaleY = lerp(0f, 0.75f, progress)
        scale(scaleX, scaleY) {
            drawTrackBackdrop()
        }
    }
    val thumbBackdrop = if (glassBackdrop != null) {
        rememberCombinedBackdrop(glassBackdrop, scaledTrackBackdrop)
    } else {
        null
    }

    // API31/32：平台没有 AGSL，改用离屏 ES 2.0 做真折射（见 ThumbLens.kt）。
    // 底图 = 环境背景 + 轨道层，与 thumbBackdrop 同源。
    // 锚点挂在外层这个 64×48 的容器上 —— 它不随旋钮移动。
    //
    // ## 轨道必须按 0.75 缩，而且要绕**旋钮中心**缩
    //
    // 这个开关的轨道是**纯色**的。纯色底图折射出来还是同一个纯色 —— 位移一片
    // 平场得到的仍是平场。实测过：底图不缩时按住开关，整个开关区域是均匀的
    // `0fd840`，旋钮彻底消失，屏幕上只剩一圈 highlight（用户报的就是这个）。
    //
    // 33+ 那边之所以有东西可看，全靠 `scale(0.75, 0.75)` 把轨道缩得**比旋钮还矮**
    // （64×28dp 缩成 48×21dp，而旋钮是 40×24dp）：旋钮上下边缘于是采到轨道**外面**
    // 的环境背景，绿/环境那条边才是折射真正在弯的东西。少了这一步，折射就没有
    // 输入。所以这里必须把它烤进底图。
    //
    // 缩放的原点是**旋钮中心**，与库一致 —— 库那层 `scale()` 画在旋钮自己的
    // DrawScope 里，默认绕自身中心。绕锚点中心缩会让旋钮在 checked 那一端探出
    // 轨道右缘 6dp。
    //
    // ## scaleY 烤 0.4，不是按满那一档的 0.75
    //
    // 33+ 的轨道缩放是**动画**：`scaleY = lerp(0f, 0.75f, progress)`（上面
    // scaledTrackBackdrop）。底图只能烤一个固定值，而烤 0.75 实测是错的 ——
    // 用户报「按下旋钮消失」，两台设备同页同控件（1080x2400 @420dpi，开关都在
    // x817-984）逐像素对比：
    //
    //   API35（33+ 真 AGSL）按压中，旋钮上下各有一条**环境色**带：
    //     y1750-1765(16px) 与 y1820-1837(18px)，值约 (149,128,181) 淡紫 = 壁纸
    //   API32（本路径，曾烤 0.75）同一时刻：
    //     旋钮内部**一条带都没有**，整块 (52,199,89) 纯轨道绿 —— 旋钮消失
    //
    // 几何算得通：斜坡宽 5dp = 13.1px，旋钮半高 31.5px。要让斜坡整条都采到轨道
    // 外面，缩后轨道半高必须 ≤ 31.5 - 13.1 = 18.4px，即 scaleY ≤ 0.50。
    // 烤 0.75 时缩后半高 27.6px，旋钮只探出 3.9px —— 斜坡里绝大部分仍是绿，
    // 那 3.9px 被稀释掉，屏幕上就是纯绿。烤 0.4 时探出 16.8px，与 API35 目测的
    // 16/18px 带宽对得上。
    //
    // 曾经这里的注释说「中途那一两百毫秒差异被自己盖住」—— 那句是错的，上面的
    // 逐像素对比就是反例：用户看见的正是按压途中那一档。
    //
    // scaleX 仍是 0.75：横向旋钮半宽 52.5px 本来就在缩后轨道半宽 63px 以内，
    // 两条路都没有横向环境带，这是设计如此（见 GlassLens 的中心轴注记）。
    // 显式命名：DrawScope 里的 `density` 是 Float 成员，写 `density` 靠的是重载
    // 解析恰好挑中外层这个 Density。改个名就不必依赖那个巧合。
    val switchLensDensity = density
    val switchLensAnchor = if (glassBackdrop != null) {
        rememberGlassLensRegion(
            tag = "switch",
            // 轨道色与旋钮位置都跟着 checked 走。用 checked 而不是
            // dragAnimation.value：后者是每帧变化的动画值，写进 keys 就是整个
            // 动画期间每帧重拍。
            checked,
            enabled,
            isLightTheme
        ) { coords ->
            with(glassBackdrop) { drawBackdrop(switchLensDensity, coords, null) }
            // 旋钮中心（锚点坐标系）：CenterStart 对齐 + translationX，
            // 与下面 thumbModifier 的 graphicsLayer 读同一组常量。
            val padding = 2.dp.toPx()
            val thumbCenterX =
                (if (checked) padding + dragWidth else padding) + 40.dp.toPx() / 2f
            scale(
                scaleX = SWITCH_LENS_TRACK_SCALE_X,
                scaleY = SWITCH_LENS_TRACK_SCALE_Y,
                pivot = Offset(thumbCenterX, size.height / 2f)
            ) {
                with(trackBackdrop) { drawBackdrop(switchLensDensity, coords, null) }
            }
        }
    } else {
        null
    }
    // 轨道色是 lerp(灰, 绿, value) 的动画，底图只在 checked 变化时拍了一张 ——
    // 拍到的是动画**起点**的颜色。开关的拨动动画约 300ms，之后补一张。
    LaunchedEffect(switchLensAnchor, checked) {
        if (switchLensAnchor == null) return@LaunchedEffect
        delay(320)
        switchLensAnchor.invalidate()
    }
    val switchLensMaterial = remember {
        // 库：lens(5dp * press, 10dp * press)
        thumbLensMaterial(refractionHeightDp = 5f, refractionAmountDp = 10f)
    }

    Box(
        modifier = modifier
            .width(64.dp)
            .height(48.dp)
            .glassLensAnchor(switchLensAnchor)
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
                .layerBackdrop(trackBackdrop)
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
                    lerp(padding, padding + dragWidth, dragAnimation.value)
                } else {
                    lerp(-padding, -(padding + dragWidth), dragAnimation.value)
                }
            }
            .testTag("liquid-switch-thumb")

        if (thumbBackdrop != null) {
            Box(
                modifier = thumbModifier
                    // 画在 drawBackdrop **之前**：折射结果就是旋钮的背景，
                    // 库那层的 surface / highlight / shadow 仍叠在上面。
                    .glassLens(
                        anchor = switchLensAnchor,
                        optics = { w, h ->
                            thumbLensOptics(
                                material = switchLensMaterial,
                                density = density,
                                // 胶囊：圆角就是短边的一半
                                cornerRadiusPx = minOf(w, h) / 2f,
                                minDimensionPx = minOf(w, h),
                                press = dragAnimation.pressProgress
                            )
                        },
                        // 与下面 layerBlock 读**同一个**函数
                        scale = { _, _ ->
                            thumbLensTransform(
                                anim = dragAnimation,
                                velocityDivisor = 50f,
                                reduceMotion = accessibility.reduceMotion
                            )
                        }
                    )
                    .drawBackdrop(
                        backdrop = thumbBackdrop,
                        shape = { Capsule() },
                        effects = {
                            val progress = dragAnimation.pressProgress
                            if (switchLensAnchor == null) {
                                blur(8.dp.toPx() * (1f - progress))
                                lens(
                                    refractionHeight = 5.dp.toPx() * progress,
                                    refractionAmount = 10.dp.toPx() * progress,
                                    chromaticAberration = true
                                )
                            }
                            // 31/32：折射与色散都已由上面的 glassLens 画完。
                            // 那层 blur 也不能留 —— 它会把自家折射糊掉。
                        },
                        onDrawBackdrop = { drawBackdrop ->
                            // 31/32 上背景已由 glassLens 以折射方式画过，不能再画一遍
                            if (switchLensAnchor == null) drawBackdrop()
                        },
                        highlight = {
                            val progress = dragAnimation.pressProgress
                            Highlight.Ambient.copy(
                                width = Highlight.Ambient.width / 1.5f,
                                blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                                alpha = progress
                            )
                        },
                        shadow = {
                            Shadow(
                                radius = 4.dp,
                                color = Color.Black.copy(alpha = 0.05f)
                            )
                        },
                        innerShadow = {
                            val progress = dragAnimation.pressProgress
                            InnerShadow(
                                radius = 4.dp * progress,
                                alpha = progress
                            )
                        },
                        layerBlock = {
                            // 与上面 glassLens(scale) 读**同一个** thumbLensTransform。
                            // 别在这里重写一份：底栏指示器上"两处各写一遍"已经出过
                            // 一次按下时 rim 环与玻璃错开的问题。
                            val t = thumbLensTransform(
                                anim = dragAnimation,
                                velocityDivisor = 50f,
                                reduceMotion = accessibility.reduceMotion
                            )
                            scaleX = t.scaleX
                            scaleY = t.scaleY
                        },
                        onDrawSurface = {
                            val progress = dragAnimation.pressProgress
                            // 官方同款：静止为不透明纯白旋钮（轨道色不穿透），
                            // 按压时白色退去，露出轨道折射与色散。
                            drawRect(
                                thumbColor.copy(
                                    alpha = androidx.compose.ui.util.lerp(1f, 0f, progress)
                                )
                            )
                        }
                    )
                    .size(40.dp, 24.dp)
            )
        } else {
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
}
