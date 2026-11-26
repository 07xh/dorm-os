package io.github.x07xh.xiaoxu.ui

// SPDX-License-Identifier: GPL-3.0-only
// Source: znjhahaha/zhengfang-apk @ bb3b80a6d64e4c2057c0544bb417696ceaa2a9ed, app/src/main/java/com/tyust/course/ui/system/LiquidComponents.kt.
// Original authors and contributors; package and existing host/business contracts adapted.

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
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
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
import com.tyust.course.ui.theme.IOSDisabledFillDark
import com.tyust.course.ui.theme.IOSDisabledFillLight
import com.tyust.course.ui.theme.IOSFillDark
import com.tyust.course.ui.theme.IOSFillLight
import com.tyust.course.ui.system.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs

enum class LiquidButtonStyle {
    Transparent,
    Surface,
    Tinted,

    /** 实色填充（不透明），用于玻璃弹窗等容器之上，避免玻璃叠玻璃发糊。 */
    SolidSurface,
    SolidTinted
}

/**
 * 按钮的静止折射下限。两条路（33+ 的 `resolvePhysicalLens` 与 31/32 的
 * `glassLensOpticsFrom`）读**同一个**值，理由见 CapsuleNavigationBar 里同名常量的注释：
 * 两处各写一个字面量就会漂移，而且漂移只在真机上看得出来。
 */
private const val ButtonRefractionFloor = 0.62f

/**
 * 按钮玻璃层的形变。**唯一算式**，两个消费者：
 * 库那层 `drawBackdrop(layerBlock)` 与 API31/32 的 `glassLens(scale)`。
 *
 * 与芯片的 `chipGlassTransform` 同构但不同参（按钮的挤压只看 travel、
 * 不含速度项），所以没有合并 —— 合并会让两者的手感被迫统一。
 */
private fun Density.buttonGlassTransform(
    optics: InteractiveOptics,
    heightPx: Float
): GlassLensTransform {
    val progress = optics.pressProgress
    val swell = if (heightPx > 0f) {
        lerp(1f, 1f + GlassRecipe.ChipPressSwellDp.dp.toPx() / heightPx, progress)
    } else {
        1f
    }
    // 有界跟手，理由同 liquidChip：拿 size.minDimension 当上限
    // 会让玻璃层整体滑出按钮自己的插槽。
    val travelPx = GlassRecipe.ChipDragTravelDp.dp.toPx()
    val travel = optics.dragTravel(travelPx)
    // 等体积挤压，不是两轴同时放大
    val stretch = GlassRecipe.ChipDragStretch * (abs(travel.x) / travelPx).coerceIn(0f, 1f)
    val squash = GlassRecipe.ChipDragStretch * (abs(travel.y) / travelPx).coerceIn(0f, 1f)
    return GlassLensTransform(
        scaleX = swell * (1f + stretch - squash),
        scaleY = swell * (1f + squash - stretch),
        translationX = travel.x,
        translationY = travel.y
    )
}

@Composable
fun LiquidButton(
    onClick: () -> Unit,
    backdrop: Backdrop? = LocalControlBackdrop.current,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isInteractive: Boolean = true,
    style: LiquidButtonStyle = LiquidButtonStyle.Surface,
    shape: androidx.compose.ui.graphics.Shape = Capsule(),
    tint: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    minHeight: Dp = 48.dp,
    horizontalPadding: Dp = 16.dp,
    content: @Composable RowScope.() -> Unit
) {
    val isSolid = style == LiquidButtonStyle.SolidSurface || style == LiquidButtonStyle.SolidTinted
    val glassBackdrop = backdrop?.takeIf { isBackdropSupported() && !isSolid }
    val isLightTheme = !rememberGlassDarkTheme()
    val wallpaperColors = LocalWallpaperAppearanceColors.current
    val activeTint = if (tint.isSpecified) tint else MaterialTheme.colorScheme.primary
    val activeContentColor = when {
        contentColor.isSpecified -> contentColor
        style == LiquidButtonStyle.Tinted || style == LiquidButtonStyle.SolidTinted -> {
            if (activeTint == MaterialTheme.colorScheme.primary) MaterialTheme.colorScheme.onPrimary
            else if (activeTint == MaterialTheme.colorScheme.error) MaterialTheme.colorScheme.onError
            else if (activeTint.luminance() > 0.4f) Color(0xFF171B22) else Color.White
        }
        style == LiquidButtonStyle.SolidSurface -> MaterialTheme.colorScheme.onSurface
        else -> wallpaperColors.onSurface
    }
    val resolvedContentColor = if (enabled) {
        activeContentColor
    } else {
        (if (isSolid) MaterialTheme.colorScheme.onSurfaceVariant else wallpaperColors.onSurfaceVariant).copy(alpha = 0.62f)
    }
    val disabledSurfaceColor = wallpaperColors.surface.copy(
        alpha = GlassRecipe.ActionDisabledSurfaceAlpha
    )
    val interactionSource = remember { MutableInteractionSource() }
    val accessibility = rememberGlassAccessibilityMode()
    // 两个分支共用同一交互源：玻璃分支用它驱动 layerBlock 的光学形变，
    // 实色分支用它驱动压扁回弹。interactionSource 保留但只负责灰罩亮度——
    // 几何与光学归 optics，亮度归 indication，两者不再各自播一条动画。
    val optics = rememberInteractiveOptics()
    val allowInteraction = isInteractive && enabled && !accessibility.reduceMotion
    // 玻璃分支的按压由 layerBlock 的光学形变表达；实色分支交给全局 iOS 按压（回缩 + 灰罩）。
    // 玻璃分支不能用 indication：drawBackdrop 不裁剪内容，灰罩会溢出成方块。
    val contentRow: @Composable (Modifier, Indication?) -> Unit = { baseModifier, pressIndication ->
        Row(
            baseModifier
                .clickable(
                    interactionSource = interactionSource,
                    indication = pressIndication,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick
                )
                .defaultMinSize(minHeight = minHeight)
                .padding(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompositionLocalProvider(LocalContentColor provides resolvedContentColor) {
                content()
            }
        }
    }

    if (glassBackdrop != null) {
        // API31/32 的离屏折射锚点，由不动的祖先下发（见 GlassLensRegion.kt）。
        val lensAnchor = LocalGlassLensAnchor.current
        val buttonDensity = LocalDensity.current
        val buttonMaterial = GlassMaterials.resolve(
            role = GlassMaterialRole.Interactive,
            accessibility = accessibility
        )
        val glassModifier = modifier
            // 画在 drawBackdrop **之前**：折射结果是背景，库那层的 surface 叠在上面。
            .glassLens(
                anchor = lensAnchor,
                optics = { w, h ->
                    glassLensOpticsFrom(
                        material = buttonMaterial,
                        density = buttonDensity,
                        cornerRadiusPx = lensCornerRadiusPx(shape, w, h, buttonDensity),
                        minDimensionPx = minOf(w, h),
                        interactionProgress = optics.opticalProgress,
                        motionIntensity = optics.motionIntensity(
                            buttonMaterial.optics.velocityForFullEffect
                        ),
                        pressScalesRefraction = true,
                        // 与下面 resolvePhysicalLens 同值
                        refractionFloor = ButtonRefractionFloor,
                        chromaticAberrationAtRest = false
                    )
                },
                // 与下面 layerBlock 同一份形变。这里的 layerBlock 自己写了一份
                // 跟手/挤压算式（与芯片的 chipGlassTransform 同构但不同源），
                // 所以这边照它复现；两处一起改。
                scale = if (allowInteraction) {
                    { _, h ->
                        with(buttonDensity) {
                            buttonGlassTransform(optics, h)
                        }
                    }
                } else {
                    null
                }
            )
            .drawBackdrop(
                backdrop = glassBackdrop,
                shape = { shape },
                // 与 liquidChip 同源的光学管线：参数从 GlassMaterials 解析而不是写死，
                // 否则 reduceMotion 折射减半、highContrast 提亮、API31/32 退化色散
                // 这三条策略要在每个组件里各抄一遍，迟早抄漏。
                effects = {
                    vibrancy()
                    val material = GlassMaterials.resolve(
                        role = GlassMaterialRole.Interactive,
                        accessibility = accessibility,
                        interactionProgress = optics.opticalProgress
                    )
                    val params = resolvePhysicalLens(
                        scope = this,
                        material = material,
                        minCornerRadiusPx = size.minDimension / 2f,
                        minDimensionPx = size.minDimension,
                        interactionProgress = optics.opticalProgress,
                        motionIntensity = optics.motionIntensity(
                            material.optics.velocityForFullEffect
                        ),
                        enableBlur = false,
                        allowChromaticAberration = allowInteraction,
                        chromaticAberrationAtRest = false,
                        pressScalesRefraction = true,
                        refractionFloor = ButtonRefractionFloor
                    )
                    // 顺序必须 blur → lens，两者在这里互斥所以不会踩到
                    if (params.useLens) {
                        lens(
                            params.refractionHeightPx,
                            params.refractionAmountPx,
                            params.chromaticAberration
                        )
                    } else if (lensAnchor == null) {
                        // Interactive 档在 API33+ 刻意不带 blur（靠折射与色散）；
                        // **真的没有折射时**（API ≤ 30）才由调用侧补 blur 撑住质感。
                        //
                        // 31/32 现在有离屏折射了，这层 blur 必须撤：它会把自家折射
                        // 糊掉，而且底图是无 blur 的原 backdrop，屏幕上这一层多出来
                        // 的模糊会让折射内容与周围对不上。
                        blur(6.dp.toPx())
                        if (params.fringePx > 0f) chromaticFringe(params.fringePx)
                    }
                },
                onDrawBackdrop = { drawBackdrop ->
                    // 31/32 上背景已由 glassLens 折射过，不能再画一遍
                    if (lensAnchor == null) drawBackdrop()
                },
                layerBlock = if (allowInteraction) {
                    {
                        // 与上面 glassLens(scale) 读**同一个** buttonGlassTransform。
                        // 别在这里重写一份：底栏指示器上"两处各写一遍"已经出过一次
                        // 按下时 rim 环与玻璃错开的问题。
                        val t = buttonGlassTransform(optics, size.height)
                        translationX = t.translationX
                        translationY = t.translationY
                        scaleX = t.scaleX
                        scaleY = t.scaleY
                    }
                } else {
                    null
                },
                onDrawSurface = {
                    when {
                        !enabled -> drawRect(disabledSurfaceColor)
                        // 不用 BlendMode.Hue：那样只贡献色相，明度会沿用身后折射的环境亮度，
                        // 在壁纸上形成一条随位置游走的伪高光。这里直接实色覆盖。
                        style == LiquidButtonStyle.Tinted ->
                            drawRect(activeTint.copy(alpha = GlassRecipe.ActionTintAlpha))
                        style == LiquidButtonStyle.Surface -> drawRect(wallpaperColors.surface)
                        else -> Unit
                    }
                }
            )
            .then(if (allowInteraction) optics.gestureModifier else Modifier)
        contentRow(glassModifier, null)
    } else {
        // iOS 实色路径：填充完全不透明，不参与折射，因此不会出现随环境游走的高光。
        val fallbackColor = when {
            !enabled -> if (isLightTheme) IOSDisabledFillLight else IOSDisabledFillDark
            style == LiquidButtonStyle.SolidTinted -> activeTint
            style == LiquidButtonStyle.SolidSurface -> MaterialTheme.colorScheme.surfaceContainerHigh
            style == LiquidButtonStyle.Tinted -> activeTint.copy(alpha = GlassRecipe.ActionTintAlpha)
            style == LiquidButtonStyle.Surface -> wallpaperColors.solidSurface.copy(alpha = 0.94f)
            else -> Color.Transparent
        }
        val fallbackModifier = modifier
            // 回缩必须在 clip/background 之前：这样底色随内容一起缩，
            // 而不是只缩到文字。灰罩由 clickable 处的 GlassPressIndication 叠加。
            .graphicsLayer {
                if (!allowInteraction) return@graphicsLayer
                applyPressSquash(progress = optics.pressProgress, depth = 0.06f)
            }
            .clip(shape)
            .background(fallbackColor)
            .then(
                if (isSolid) {
                    Modifier
                } else {
                    Modifier.border(
                        width = 0.5.dp,
                        color = wallpaperColors.border.copy(alpha = 0.46f),
                        shape = shape
                    )
                }
            )
            // 实色分支也要挂手势：optics 的唯一驱动源是它，
            // 漏挂会让上面那层压扁永远停在 1.0。
            .then(if (allowInteraction) optics.gestureModifier else Modifier)
        contentRow(fallbackModifier, LocalIndication.current)
    }
}

// Primary, secondary and destructive button entry points from upstream SystemUi.kt.
@Composable
fun SystemPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    // iOS filled button：不透明实色胶囊。按压回缩与灰罩由全局 GlassPressIndication 统一提供。
    LiquidButton(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        enabled = enabled,
        style = LiquidButtonStyle.SolidTinted,
        tint = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = Capsule()
    ) {
        if (leadingIcon != null) leadingIcon()
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun SystemSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    // iOS gray-fill：不透明胶囊，保证任何底色上都清晰。填充跟主题（surfaceContainerHigh），
    // 文字就必须也跟主题：取壁纸前景色的话，深色主题配浅色壁纸时是深灰底上的深色字。
    LiquidButton(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        enabled = enabled,
        style = LiquidButtonStyle.SolidSurface,
        shape = Capsule()
    ) {
        if (leadingIcon != null) leadingIcon()
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun SystemDestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    LiquidButton(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        enabled = enabled,
        style = LiquidButtonStyle.SolidTinted,
        tint = MaterialTheme.colorScheme.error,
        contentColor = MaterialTheme.colorScheme.onError,
        shape = Capsule()
    ) {
        if (leadingIcon != null) leadingIcon()
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}
