package io.github.x07xh.xiaoxu.ui

// SPDX-License-Identifier: GPL-3.0-only
// Source: znjhahaha/zhengfang-apk @ bb3b80a6d64e4c2057c0544bb417696ceaa2a9ed, ui/system/SystemUi.kt
// Copyright original authors and contributors; adapted for Xiaoxu.
// Retains the upstream top bar, empty state, section header and list row.
 // Wallpaper bindings use the current Material theme; Shell owns system insets.
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tyust.course.ui.system.LocalAppBackdrop
import com.tyust.course.ui.system.rememberWallpaperRegionState
import com.tyust.course.ui.system.rememberWallpaperRegionAppearance
import com.tyust.course.ui.system.ProvideThemedContent
import com.tyust.course.ui.system.ProvideWallpaperAppearance
import com.tyust.course.ui.system.wallpaperRegion
import com.tyust.course.ui.system.isBackdropSupported
import com.tyust.course.ui.theme.NeuDivider
import com.tyust.course.ui.theme.NeuPrimary
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy

@Composable
fun SystemTopBar(
    title: String,
    subtitle: String? = null,
    collapseFraction: Float = 0f,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    backdrop: Backdrop? = LocalAppBackdrop.current
) {
    val useGlass = backdrop != null && isBackdropSupported()
    val regionState = rememberWallpaperRegionState()
    val appearance = rememberWallpaperRegionAppearance(regionState)
    val customWallpaper = com.tyust.course.manager.AppearanceSettingsManager.mode != com.tyust.course.manager.WallpaperMode.Preset
    val isLightTheme = appearance.usesDarkForeground
    val titleColor = appearance.onSurface

    // iOS 26 分离式头部：展开态大标题直接浮在内容上（无背景），
    // 滚动折叠时标题缩小、浮出细玻璃条。折叠进度由滚动偏移连续驱动、
    // 全程跟手；高刚度临界阻尼弹簧只负责抹平 LazyList 快滚时的跳变。
    val collapse by animateFloatAsState(
        targetValue = collapseFraction.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 1f, stiffness = 900f),
        label = "headerCollapse"
    )
    val surfaceTint = appearance.surface.copy(
        alpha = if (customWallpaper) appearance.surface.alpha else if (isLightTheme) 0.46f else 0.34f
    )
    com.tyust.course.ui.theme.ReportStatusBarSurface(surfaceTint.copy(alpha = surfaceTint.alpha * (if (customWallpaper) 1f else 0.85f + 0.15f * collapse)))

    ProvideWallpaperAppearance(appearance) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wallpaperRegion(regionState)

    ) {
        val showShell = customWallpaper || collapse > 0.01f
        val shellModifier = when {
            useGlass && backdrop != null -> Modifier
                .graphicsLayer { alpha = if (customWallpaper) 1f else collapse }
                // 下缘【齐边】收尾：渐隐抹在模糊结果上只是把"模糊的那一份"按 alpha 混到
                // 清晰的原图上，两份图像叠在一起就是一条重影带（iOS 渐变的是模糊半径，
                // 一次 drawBackdrop 做不到）。玻璃条本来就该有边——那圈默认高光已关掉。
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(0.dp) },
                    effects = {
                        vibrancy()
                        val radius = 10.dp.toPx() * collapse
                        if (radius > 0.5f) blur(radius)
                    },
                    // 库的默认值不是 null：全宽直角矩形的那圈边缘高光在屏幕上只剩
                    // "标题下面一道亮线"。投影同理，而 Offscreen 遮罩也会把它裁掉。
                    highlight = { null },
                    shadow = { null },
                    innerShadow = { null },
                    onDrawSurface = {
                        // 平铺即可：渐隐由 glassEdgeFadeBottom 统一做，
                        // 这里再叠一条渐变会让尾巴衰减得比线性更快。
                        drawRect(surfaceTint)
                    }
                )
            !useGlass && showShell -> Modifier.background(
                Brush.verticalGradient(
                    0f to appearance.solidSurface.copy(alpha = if (customWallpaper) 1f else collapse),
                    0.86f to appearance.solidSurface.copy(alpha = if (customWallpaper) 1f else collapse),
                    1f to Color.Transparent
                )
            )
            else -> Modifier
        }
        Box(modifier = Modifier.fillMaxWidth()) {
            // Shell visibility belongs to the glass only. At rest on a preset
            // wallpaper collapse is zero; applying this layer to the parent also
            // hides the title, subtitle and buttons until the user scrolls.
            Box(Modifier.matchParentSize().then(shellModifier))
            // 展开态：大标题背后铺一层自上而下的软渐变，
            // 内容滚入标题区域时被渐隐吞没而不是直接撞字；随折叠淡出交棒给玻璃条。
            if (collapse < 0.99f && !customWallpaper) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            // 四个色标、尾巴拉长：三色标那版在渐变末端有一处可见的斜率突变，
                            // 在壁纸上读成"标题块的下边界"。
                            Brush.verticalGradient(
                                colors = listOf(
                                    surfaceTint.copy(alpha = surfaceTint.alpha * 0.85f * (1f - collapse)),
                                    surfaceTint.copy(alpha = surfaceTint.alpha * 0.45f * (1f - collapse)),
                                    surfaceTint.copy(alpha = surfaceTint.alpha * 0.12f * (1f - collapse)),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()

                    .padding(
                        start = 20.dp,
                        end = 20.dp,
                        top = lerpDp(10.dp, 6.dp, collapse),
                        bottom = lerpDp(10.dp, 8.dp, collapse)
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                navigationIcon?.invoke()
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = lerpSp(28f, 17f, collapse),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.sp,
                        color = titleColor,
                        maxLines = if (collapse < 0.5f) 2 else 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    if (!subtitle.isNullOrBlank()) {
                        // 折叠时副标题高度收拢并渐隐
                        Box(
                            modifier = Modifier
                                .height(lerpDp(22.dp, 0.dp, collapse))
                                .graphicsLayer { alpha = (1f - collapse * 1.6f).coerceIn(0f, 1f) },
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(NeuPrimary.copy(alpha = 0.65f))
                                )
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = appearance.onSurfaceVariant.copy(alpha = 0.80f),
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    content = actions
                )
            }
        }
    }
    }
}

@Composable
fun SystemEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Inbox,
    action: (@Composable () -> Unit)? = null
) {
    val textAppearance = MaterialTheme.colorScheme

    val isLightTheme = !isSystemInDarkTheme()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Empty and unavailable states retain the same glass treatment as the page controls.
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(
                    if (isLightTheme) Color.White.copy(alpha = 0.50f)
                    else Color.White.copy(alpha = 0.10f)
                )
                .border(
                    0.5.dp,
                    Color.White.copy(alpha = if (isLightTheme) 0.55f else 0.14f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(26.dp),
                tint = textAppearance.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = textAppearance.onSurface,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = textAppearance.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (action != null) {
            Spacer(modifier = Modifier.height(12.dp))
            action()
        }
    }
}

@Composable
fun SystemSectionHeader(
    title: String,
    subtitle: String? = null,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    val textAppearance = MaterialTheme.colorScheme

    Row(
        modifier = Modifier.fillMaxWidth().padding(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = textAppearance.onSurface
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = textAppearance.onSurfaceVariant
                )
            }
        }
        if (!actionLabel.isNullOrBlank() && onActionClick != null) {
            TextButton(onClick = onActionClick) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun SystemListItem(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = onClick != null, onClick = onClick ?: {})
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingIcon != null) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                leadingIcon()
            }
            Spacer(modifier = Modifier.width(12.dp))
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (trailingContent != null) {
            Spacer(modifier = Modifier.width(12.dp))
            trailingContent()
        }
    }
}

@Composable
fun SystemCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    backdrop: Backdrop? = LocalAppBackdrop.current,
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val reduceMotion = rememberGlassAccessibilityMode().reduceMotion
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed && !reduceMotion) 0.985f else 1f,
        animationSpec = MotionSpring.bounce(),
        label = "cardScale"
    )

    val cardShape = RoundedCornerShape(16.dp)

    val baseModifier = modifier
        .fillMaxWidth()
        .scale(pressScale)

    val clickableModifier = if (onClick != null) {
        baseModifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
    } else {
        baseModifier
    }

    // 调用方普遍显式传 colorScheme.surface；把"默认白面"语义映射为半透玻璃面
    // （纯 alpha 混合零采样开销，多彩壁纸自然透出），特殊色卡保持原色。
    val translucent = backgroundColor == MaterialTheme.colorScheme.surface
    val effectiveColor = when {
        !translucent -> backgroundColor
        else -> glassSurfaceColor()
    }
    val effectiveBorder = if (translucent) {
        if (borderColor == MaterialTheme.colorScheme.outlineVariant) glassBorderColor()
        else lerp(glassBorderColor(), borderColor, 0.55f)
    } else {
        borderColor.copy(alpha = 0.15f)
    }

    Surface(
        modifier = clickableModifier,
        shape = cardShape,
        color = effectiveColor,
        contentColor = if (translucent) MaterialTheme.colorScheme.onSurface
            else androidx.compose.material3.contentColorFor(backgroundColor),
        border = BorderStroke(
            width = 0.5.dp,
            brush = Brush.linearGradient(listOf(effectiveBorder, effectiveBorder.copy(alpha = effectiveBorder.alpha * 0.45f), effectiveBorder))
        ),
        // 半透玻璃面必须关 elevation 阴影：RenderNode 阴影会透过半透表面显形为白蒙层
        shadowElevation = when {
            translucent -> 0.dp
            isPressed -> 1.dp
            else -> 2.dp
        },
        tonalElevation = 0.dp
    ) {
        ProvideThemedContent {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
        }
    }
}


@Composable
fun SystemDivider(
    modifier: Modifier = Modifier,
    alpha: Float = 0.5f
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        NeuDivider.copy(alpha = alpha),
                        NeuDivider.copy(alpha = alpha),
                        Color.Transparent
                    )
                )
            )
    )
}
