package io.github.x07xh.xiaoxu.ui

// SPDX-License-Identifier: GPL-3.0-only
// Source: znjhahaha/zhengfang-apk @ bb3b80a6d64e4c2057c0544bb417696ceaa2a9ed, ui/system/GlassKit.kt
// Copyright original authors and contributors (upstream Git history).
// Adapted package, imports, theme/wallpaper bindings and animation defaults.
// GPL license included in assets/LICENSE-GPL-3.0.txt.
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tyust.course.ui.system.rememberWallpaperRegionState
import com.tyust.course.ui.system.rememberReadableContentAppearance
import com.tyust.course.ui.system.wallpaperRegion
import com.tyust.course.ui.system.readableWallpaper
import com.tyust.course.ui.system.ProvideThemedContent
import com.tyust.course.ui.theme.NeuPrimary

private val GlassCardShape = RoundedCornerShape(24.dp)
@Composable
private fun glassSurfaceColor(): Color = MaterialTheme.colorScheme.surface
@Composable
private fun glassBorderColor(): Color = MaterialTheme.colorScheme.outlineVariant

/** iOS 设置风格的彩色圆角图标块。 */
@Composable
fun GlassIconChip(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 30.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.27f))
            .background(tint),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size * 0.62f)
        )
    }
}

/** iOS inset grouped 分组容器：圆角玻璃组，行间自带两端淡出分割线。 */
@Composable
fun InsetGroupedSection(
    modifier: Modifier = Modifier,
    header: String? = null,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val region = rememberWallpaperRegionState()
    val wallpaperColors = rememberReadableContentAppearance(region)
    Column(modifier = modifier.fillMaxWidth().wallpaperRegion(region)) {
        if (!header.isNullOrBlank()) {
            Text(
                text = header,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = wallpaperColors.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, bottom = 7.dp)
                    .readableWallpaper(wallpaperColors).padding(horizontal = 6.dp, vertical = 3.dp)
            )
        }
        // The card is tinted by the app theme. Its children must not inherit
        // white wallpaper text when a light card sits on a dark photograph.
        ProvideThemedContent {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(GlassCardShape)
                .background(glassSurfaceColor())
                .border(0.5.dp, glassBorderColor(), GlassCardShape)
                .animateContentSize(animationSpec = MotionSpring.liquidSettle()),
            content = content
        )
        }
        if (!footer.isNullOrBlank()) {
            Text(
                text = footer,
                style = MaterialTheme.typography.bodySmall,
                color = wallpaperColors.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, top = 7.dp)
                    .readableWallpaper(wallpaperColors).padding(horizontal = 6.dp, vertical = 3.dp)
            )
        }
    }
}

/** inset grouped 行：图标 chip + 标题/副标题 + 尾随内容，分割线避开图标区两端淡出。 */
@Composable
fun InsetGroupedRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = NeuPrimary,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    trailing: @Composable (() -> Unit)? = null,
    showDivider: Boolean = true,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(enabled = enabled, onClick = onClick)
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (icon != null) {
                GlassIconChip(icon = icon, tint = iconTint)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (enabled) titleColor else titleColor.copy(alpha = 0.45f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (trailing != null) {
                CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    trailing()
                }
            }
        }
        if (showDivider) {
            val dividerColor = MaterialTheme.colorScheme.outlineVariant
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = if (icon != null) 58.dp else 16.dp)
                    .height(0.5.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                dividerColor.copy(alpha = 0.45f),
                                dividerColor.copy(alpha = 0.45f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }
    }
}

/** 玻璃进度条：半透槽 + 高饱和圆角填充。 */
@Composable
fun GlassProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    height: Dp = 8.dp
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(),
        label = "glassProgress"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(percent = 50))
            .background(
                MaterialTheme.colorScheme.surfaceVariant
            )
    ) {
        if (animated > 0.005f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animated)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(percent = 50))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(tint.copy(alpha = 0.85f), tint)
                        )
                    )
            )
        }
    }
}
