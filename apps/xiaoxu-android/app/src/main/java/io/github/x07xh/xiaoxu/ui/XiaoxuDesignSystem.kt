package io.github.x07xh.xiaoxu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 校序统一 UI 设计系统 v1（Issue #268 第一阶段）。
 *
 * 只描述"看起来怎样"：颜色、圆角、字级、间距、状态语义。
 * 不接触任何数据判定——云端状态由 CloudClient 负责，本机课表仅标注待核对，
 * 本文件只负责把结论画出来，绝不自行推断教学周、倒计时或"下一节课"。
 */

// ---------------------------------------------------------------------------
// Token
// ---------------------------------------------------------------------------

object XiaoxuPalette {
    /** 主色 */
    val Brand = Color(0xFF2F6BFF)

    /** 深蓝：Hero 渐变末端、按压态 */
    val BrandDeep = Color(0xFF1B47C8)

    /** 主色浅底：选中态、徽标底 */
    val BrandSoft = Color(0xFFEAF1FF)

    /** 页面背景 */
    val Bg = Color(0xFFF4F7FD)

    /** 卡片 */
    val Surface = Color(0xFFFFFFFF)

    /** 次级面（分组底、未核验底） */
    val SurfaceAlt = Color(0xFFF1F4FA)

    /** 主文字 */
    val Ink = Color(0xFF16233A)

    /** 次要文字 */
    val Ink2 = Color(0xFF6B7A99)

    /** 占位、未选中图标 */
    val Ink3 = Color(0xFF9AA7BE)

    /** 描边、分隔线 */
    val Line = Color(0xFFE9EEF8)

    val Success = Color(0xFF16A34A)
    val SuccessSoft = Color(0xFFE9F9EF)
    val Warning = Color(0xFFB45309)
    val WarningSoft = Color(0xFFFFF4E3)
    val Danger = Color(0xFFD84343)
    val DangerSoft = Color(0xFFFFEDED)
    val Neutral = Color(0xFF5B6B85)
    val NeutralSoft = Color(0xFFF1F4FA)
}

/** 圆角：18dp 基础卡，22dp 重要卡（Hero / 抽屉顶角）。 */
object XiaoxuRadius {
    val card: Dp = 18.dp
    val hero: Dp = 22.dp
    val tile: Dp = 14.dp
    val pill: Dp = 999.dp
}

/** 间距：页面 18dp，区块 14dp，卡内 16dp，元素 8dp。 */
object XiaoxuSpacing {
    val page: Dp = 18.dp
    val section: Dp = 14.dp
    val card: Dp = 16.dp
    val gap: Dp = 8.dp
}

/** 课程六色：背景 / 前景 / 左侧强调条。按课程标识稳定取模，不随列表顺序变化。 */
data class XiaoxuCourseColor(val background: Color, val foreground: Color, val accent: Color)

val XiaoxuCourseColors: List<XiaoxuCourseColor> = listOf(
    XiaoxuCourseColor(Color(0xFFEAF1FF), Color(0xFF2159D8), Color(0xFF2F6BFF)),
    XiaoxuCourseColor(Color(0xFFFFF4E3), Color(0xFFB45309), Color(0xFFF59E0B)),
    XiaoxuCourseColor(Color(0xFFE7F8F5), Color(0xFF0F766E), Color(0xFF14B8A6)),
    XiaoxuCourseColor(Color(0xFFFFEDED), Color(0xFFD84343), Color(0xFFEF5350)),
    XiaoxuCourseColor(Color(0xFFE9F9EF), Color(0xFF15803D), Color(0xFF22C55E)),
    XiaoxuCourseColor(Color(0xFFF1EDFF), Color(0xFF5B3FD1), Color(0xFF7C5CFC))
)

/** 同一门课在任何列表里颜色一致：只依赖传入的稳定 key（课程 id 优先）。 */
fun xiaoxuCourseColor(key: String): XiaoxuCourseColor {
    val slots = XiaoxuCourseColors
    val hash = if (key.isBlank()) 0 else key.hashCode()
    val index = ((hash % slots.size) + slots.size) % slots.size
    return slots[index]
}

/** 状态语义：一处定义底色与字色，所有徽标 / 标签 / 空态共用。 */
enum class XiaoxuTone { Brand, Success, Warning, Danger, Neutral }

val XiaoxuTone.containerColor: Color
    get() = when (this) {
        XiaoxuTone.Brand -> XiaoxuPalette.BrandSoft
        XiaoxuTone.Success -> XiaoxuPalette.SuccessSoft
        XiaoxuTone.Warning -> XiaoxuPalette.WarningSoft
        XiaoxuTone.Danger -> XiaoxuPalette.DangerSoft
        XiaoxuTone.Neutral -> XiaoxuPalette.NeutralSoft
    }

val XiaoxuTone.contentColor: Color
    get() = when (this) {
        XiaoxuTone.Brand -> XiaoxuPalette.BrandDeep
        XiaoxuTone.Success -> XiaoxuPalette.Success
        XiaoxuTone.Warning -> XiaoxuPalette.Warning
        XiaoxuTone.Danger -> XiaoxuPalette.Danger
        XiaoxuTone.Neutral -> XiaoxuPalette.Neutral
    }

private val XiaoxuColorScheme = lightColorScheme(
    primary = XiaoxuPalette.Brand,
    onPrimary = Color.White,
    primaryContainer = XiaoxuPalette.BrandSoft,
    onPrimaryContainer = XiaoxuPalette.BrandDeep,
    secondary = XiaoxuPalette.Neutral,
    onSecondary = Color.White,
    secondaryContainer = XiaoxuPalette.NeutralSoft,
    onSecondaryContainer = XiaoxuPalette.Ink,
    tertiary = XiaoxuPalette.Success,
    onTertiary = Color.White,
    tertiaryContainer = XiaoxuPalette.SuccessSoft,
    onTertiaryContainer = XiaoxuPalette.Success,
    background = XiaoxuPalette.Bg,
    onBackground = XiaoxuPalette.Ink,
    surface = XiaoxuPalette.Surface,
    onSurface = XiaoxuPalette.Ink,
    surfaceVariant = XiaoxuPalette.SurfaceAlt,
    onSurfaceVariant = XiaoxuPalette.Ink2,
    outline = Color(0xFFD8E1F2),
    outlineVariant = XiaoxuPalette.Line,
    error = XiaoxuPalette.Danger,
    onError = Color.White,
    errorContainer = XiaoxuPalette.DangerSoft,
    onErrorContainer = XiaoxuPalette.Danger,
    surfaceTint = XiaoxuPalette.Brand
)

private val XiaoxuTypography = Typography(
    headlineMedium = TextStyle(
        fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold,
        letterSpacing = (-0.4).sp
    ),
    headlineSmall = TextStyle(
        fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold,
        letterSpacing = (-0.2).sp
    ),
    titleLarge = TextStyle(fontSize = 19.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 13.5.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 11.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 15.sp)
)

private val XiaoxuShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(XiaoxuRadius.card),
    large = RoundedCornerShape(XiaoxuRadius.hero),
    extraLarge = RoundedCornerShape(28.dp)
)

/** 全 App 唯一主题入口：颜色 / 字级 / 圆角一次装好，页面不再自带配色。 */
@Composable
fun XiaoxuTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = XiaoxuColorScheme,
        typography = XiaoxuTypography,
        shapes = XiaoxuShapes,
        content = content
    )
}

// ---------------------------------------------------------------------------
// 组件
// ---------------------------------------------------------------------------

/**
 * 通用圆角卡：白底 + 1dp 描边 + 18dp 圆角。
 * 传 onClick 才可点，触控高度强制 ≥48dp；不传就是纯展示容器。
 */
@Composable
fun XiaoxuCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(XiaoxuRadius.card),
    container: Color = XiaoxuPalette.Surface,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = XiaoxuSpacing.card,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(1.dp, shape, clip = false)
            .clip(shape)
            .background(container)
            .border(1.dp, XiaoxuPalette.Line, shape)
            .then(
                if (onClick != null) {
                    Modifier.heightIn(min = 48.dp).clickable(onClick = onClick)
                } else Modifier
            )
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(XiaoxuSpacing.gap),
        content = content
    )
}

/**
 * 蓝色渐变主卡。
 *
 * tone = Brand 用于有真实数据的"今日课程"；tone = Neutral 用于未连接 / 未核验 / 加载中，
 * 让"没拿到数据"在视觉上一眼可辨，不做成蓝卡假装有内容。
 */
@Composable
fun XiaoxuHero(
    kicker: String,
    title: String,
    supporting: String,
    modifier: Modifier = Modifier,
    tone: XiaoxuTone = XiaoxuTone.Brand,
    badge: String? = null,
    meta: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(XiaoxuRadius.hero)
    val colors = if (tone == XiaoxuTone.Neutral) {
        listOf(Color(0xFF3A4A64), Color(0xFF22303F))
    } else {
        listOf(XiaoxuPalette.Brand, XiaoxuPalette.BrandDeep)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(6.dp, shape, clip = false)
            .clip(shape)
            .background(Brush.linearGradient(colors, start = Offset.Zero, end = Offset(1080f, 900f)))
            .drawBehind {
                drawCircle(
                    color = Color.White.copy(alpha = 0.08f),
                    radius = size.minDimension * 0.62f,
                    center = Offset(size.width * 0.98f, -size.height * 0.20f)
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.05f),
                    radius = size.minDimension * 0.44f,
                    center = Offset(size.width * 0.70f, size.height * 1.10f)
                )
            }
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = kicker,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (badge != null) {
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(XiaoxuRadius.pill))
                        .background(Color.White.copy(alpha = 0.18f))
                        .padding(horizontal = 9.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        maxLines = 1
                    )
                }
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = supporting,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.86f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (meta != null) {
            Text(
                text = meta,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.66f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(XiaoxuRadius.pill))
                    .background(Color.White)
                    .clickable(onClick = onAction)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (tone == XiaoxuTone.Neutral) XiaoxuPalette.Ink else XiaoxuPalette.BrandDeep,
                    maxLines = 1
                )
            }
        }
    }
}

/** 统计卡：一枚数字 + 一句口径说明。数字必须来自真实记录，口径写在 caption 里。 */
@Composable
fun MetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    tone: XiaoxuTone = XiaoxuTone.Brand,
    onClick: (() -> Unit)? = null
) {
    XiaoxuCard(modifier = modifier, onClick = onClick, contentPadding = 14.dp) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = tone.contentColor,
            maxLines = 1
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = XiaoxuPalette.Ink,
            maxLines = 1
        )
        if (caption != null) {
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = XiaoxuPalette.Ink3,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 课程色块：底色按课程标识稳定取色，左侧 4dp 强调条。 */
@Composable
fun CourseTile(
    courseKey: String,
    title: String,
    meta: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    onClick: (() -> Unit)? = null,
    emphasise: Boolean = false
) {
    val palette = xiaoxuCourseColor(courseKey)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(XiaoxuRadius.tile))
            .background(palette.background)
            .then(
                if (onClick != null) {
                    Modifier.heightIn(min = 48.dp).clickable(onClick = onClick)
                } else Modifier
            )
            .padding(vertical = 10.dp, horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(4.dp)
                .height(if (emphasise) 34.dp else 28.dp)
                .clip(RoundedCornerShape(XiaoxuRadius.pill))
                .background(palette.accent)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = palette.foreground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (emphasise) FontWeight.SemiBold else FontWeight.Medium
            )
            Text(
                text = meta,
                style = MaterialTheme.typography.labelSmall,
                color = palette.foreground.copy(alpha = 0.74f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelMedium,
                color = palette.foreground,
                maxLines = 1
            )
        }
    }
}

/** 分区标题：读屏按 heading 朗读；右侧操作区只在真有去向时出现。 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = XiaoxuPalette.Ink,
                modifier = Modifier.semantics { heading() }
            )
            if (caption != null) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelSmall,
                    color = XiaoxuPalette.Ink3
                )
            }
        }
        if (actionLabel != null && onAction != null) {
            Box(
                Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(XiaoxuRadius.pill))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = XiaoxuPalette.Brand,
                    maxLines = 1
                )
            }
        }
    }
}

/** 来源徽标：只回答"从哪来"，不表达任何核验结论。 */
@Composable
fun SourceBadge(text: String, tone: XiaoxuTone = XiaoxuTone.Neutral) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(tone.containerColor)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = tone.contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 状态标签：带圆点，用于截止状态、连接状态等结论性标签。 */
@Composable
fun StatusChip(text: String, tone: XiaoxuTone = XiaoxuTone.Neutral) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(XiaoxuRadius.pill))
            .background(tone.containerColor)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(tone.contentColor))
        Spacer(Modifier.width(5.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = tone.contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 统一底部抽屉：22dp 顶角，内容可滚动，标题与正文同一套字级。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XiaoxuSheet(
    onDismiss: () -> Unit,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = XiaoxuPalette.Surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = XiaoxuPalette.Ink
                )
            }
            content()
        }
    }
}

/**
 * 实际调用的统一空态容器。加载、失败和未核验状态由各真实数据页面按来源状态处理；
 * 不保留没有使用方的演示性状态包装组件。
 */
@Composable
private fun XiaoxuStatePanel(
    icon: ImageVector,
    title: String,
    message: String,
    tone: XiaoxuTone,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    XiaoxuCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(tone.containerColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tone.contentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = XiaoxuPalette.Ink
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = XiaoxuPalette.Ink2
                )
            }
        }
        if (actionLabel != null && onAction != null) {
            Box(
                Modifier
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(XiaoxuRadius.pill))
                    .background(tone.containerColor)
                    .clickable(onClick = onAction)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = tone.contentColor
                )
            }
        }
    }
}


@Composable
fun XiaoxuEmpty(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    XiaoxuStatePanel(
        icon = Icons.Outlined.Inbox, title = title, message = message,
        tone = XiaoxuTone.Neutral, modifier = modifier,
        actionLabel = actionLabel, onAction = onAction
    )
}


/** 统一顶栏：品牌标记 + 页面名 + 刷新。不用 TopAppBar，避免五个页面各调一次主题。 */
@Composable
fun XiaoxuTopBar(
    title: String,
    subtitle: String? = null,
    refreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    onMessages: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(XiaoxuPalette.Bg)
            // 边到边模式下由顶栏自己吃掉状态栏高度，页面内容不再各自处理。
            .statusBarsPadding()
            .padding(start = 18.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.linearGradient(
                        listOf(XiaoxuPalette.Brand, XiaoxuPalette.BrandDeep),
                        start = Offset.Zero, end = Offset(90f, 90f)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "序",
                style = MaterialTheme.typography.titleSmall,
                color = Color.White
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = XiaoxuPalette.Ink,
                maxLines = 1
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = XiaoxuPalette.Ink3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (onMessages != null) {
            IconButton(onClick = onMessages) {
                Icon(
                    imageVector = Icons.Outlined.NotificationsNone,
                    contentDescription = "打开消息中心",
                    tint = XiaoxuPalette.Ink2
                )
            }
        }
        if (onRefresh != null) {
            IconButton(onClick = onRefresh, enabled = !refreshing) {
                if (refreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = XiaoxuPalette.Brand
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "刷新云端与官方通知",
                        tint = XiaoxuPalette.Ink2
                    )
                }
            }
        }
    }
}

/** 快捷入口格子：只放已接入的真实页面，不做"未接入"灰按钮。 */
@Composable
fun QuickEntry(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: XiaoxuTone = XiaoxuTone.Brand
) {
    Column(
        modifier = modifier
            .heightIn(min = 76.dp)
            .clip(RoundedCornerShape(XiaoxuRadius.card))
            .background(XiaoxuPalette.Surface)
            .border(1.dp, XiaoxuPalette.Line, RoundedCornerShape(XiaoxuRadius.card))
            .clickable(onClick = onClick, role = Role.Button)
            .padding(vertical = 10.dp, horizontal = 4.dp)
            .semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(tone.containerColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tone.contentColor,
                modifier = Modifier.size(17.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = XiaoxuPalette.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
