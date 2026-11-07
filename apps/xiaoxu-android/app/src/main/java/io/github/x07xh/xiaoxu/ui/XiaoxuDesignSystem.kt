package io.github.x07xh.xiaoxu.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import android.view.WindowManager
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

    /** 全页面轻蓝紫底色，不在每个页面独立叠加渐变卡片。 */
    val PageGradient = Brush.linearGradient(
        0.00f to Color(0xFFF2F8FF),
        0.22f to Color(0xFFE4EEFF),
        0.44f to Color(0xFFF2ECFF),
        0.68f to Color(0xFFE7F4F4),
        0.86f to Color(0xFFF5EEFC),
        1.00f to Color(0xFFFAF8FF)
    )

    /** 卡片与底栏共享的轻玻璃配色：不采样背景，低成本透明合成。 */
    val GlassCard = Color.White.copy(alpha = 0.85f)
    val GlassBorder = Color.White.copy(alpha = 0.74f)
    val GlassTrack = Color.White.copy(alpha = 0.48f)
    val GlassIndicator = Color.White.copy(alpha = 0.60f)
    val SheetSurface = Color(0xFFF9FAFF)
    /** Local highlight gradients imitate fine surface reflections without runtime blur. */
    val GlassSheen = Brush.verticalGradient(
        listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.015f))
    )
    val SheetSheen = Brush.verticalGradient(
        listOf(Color(0xFFFCFDFF), Color(0xFFF5F7FF), Color(0xFFFAF8FD))
    )

    /** Touch feedback is pale blue, never a default dark rectangular ripple. */
    val PressTint = Color(0xFF4D86D9).copy(alpha = 0.12f)
    /** One low-contrast dim strength for modal surfaces, including system-bar regions. */
    val ModalScrim = Color.Black.copy(alpha = 0.12f)

    /** 不改变课程颜色，统一其他页面的基础材质。 */
    val Surface = Color(0xFFFFFFFF)

    /** 次级面（分组底、未核验底） */
    val SurfaceAlt = Color(0xFFF1F4FA)

    /** 主文字 */
    val Ink = Color(0xFF34445F)

    /** 次要文字 */
    val Ink2 = Color(0xFF52627B)

    /** 占位、未选中图标 */
    val Ink3 = Color(0xFF66758C)

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

/** 参考北化的大圆角几何，全 App 同源，不为各页面另建 CardShape。 */
object XiaoxuRadius {
    val card: Dp = 24.dp
    val hero: Dp = 28.dp
    val tile: Dp = 16.dp
    val pill: Dp = 999.dp
}

/** 间距：页面 18dp，区块 14dp，卡内 16dp，元素 8dp。 */
object XiaoxuSpacing {
    val page: Dp = 18.dp
    val section: Dp = 14.dp
    val card: Dp = 16.dp
    val gap: Dp = 8.dp

    /** 北化式紧凑列表统一起始高度；长标题仍由内容自行撑开。 */
    val listRowMin: Dp = 76.dp
    val glassStroke: Dp = 0.5.dp
}

/** Local selection and feedback only; full-screen route switches are immediate. */
internal object XiaoxuMotion {
    const val SelectionMs = 180
    const val FeedbackMs = 120
}

/** 所有一级页面与二级内容页共用同一内容宽度，不再各自声明 860dp。 */
internal object XiaoxuPageLayout {
    val ContentMaxWidth: Dp = 860.dp
    val StateMaxWidth: Dp = 560.dp
}

/** 窄屏铺满，平板居中限宽；仅收敛布局规则，不修改业务或现有配色。 */
internal fun Modifier.xiaoxuPageWidth(
    max: Dp = XiaoxuPageLayout.ContentMaxWidth
): Modifier = this.widthIn(max = max).fillMaxWidth()

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
        fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.4).sp
    ),
    headlineSmall = TextStyle(
        fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp
    ),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.5.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.5.sp, lineHeight = 16.sp)
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
        content = {
            // Default Compose clickable/selectable rows receive the same bounded
            // low-contrast ripple; individually clipped shapes retain their outline.
            CompositionLocalProvider(
                LocalIndication provides ripple(bounded = true, color = XiaoxuPalette.PressTint)
            ) { content() }
        }
    )
}

/** Keep dialog dimming uniform across content and system-bar regions. */
@Composable
internal fun XiaoxuDialogDim() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        val oldDim = window?.attributes?.dimAmount
        window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window?.setDimAmount(0.12f)
        onDispose { if (oldDim != null) window?.setDimAmount(oldDim) }
    }
}

/** Reusable clipped press surface for toolbar icons (no dark Material ripple). */
@Composable
internal fun XiaoxuSoftIconButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Transparent,
    content: @Composable () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    Box(
        modifier = modifier.size(48.dp).clip(CircleShape).background(backgroundColor)
            .clickable(
                enabled = enabled, role = Role.Button, interactionSource = source,
                indication = ripple(bounded = true, color = XiaoxuPalette.PressTint),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) { content() }
}

// ---------------------------------------------------------------------------
// 组件
// ---------------------------------------------------------------------------

/**
 * 通用柔和玻璃卡：与设置分组共享 24dp 圆角和 0.5dp 轻描边，不重复叠灰影。
 * 传 onClick 才可点，触控高度强制 ≥48dp；不传就是纯展示容器。
 */
@Composable
fun XiaoxuCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(XiaoxuRadius.card),
    container: Color = XiaoxuPalette.GlassCard,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = XiaoxuSpacing.card,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(container)
            .then(if (container == XiaoxuPalette.GlassCard)
                Modifier.background(XiaoxuPalette.GlassSheen) else Modifier)
            .border(
                if (container == XiaoxuPalette.GlassCard) XiaoxuSpacing.glassStroke else 0.75.dp,
                if (container == XiaoxuPalette.GlassCard) XiaoxuPalette.GlassBorder
                else XiaoxuPalette.Line,
                shape
            )
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

/** High-contrast Material3 switch for light translucent settings surfaces. */
@Composable
fun XiaoxuSettingsSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = XiaoxuPalette.Brand,
            checkedBorderColor = XiaoxuPalette.Brand,
            uncheckedThumbColor = Color(0xFF526781),
            uncheckedTrackColor = Color(0xFFE8EEF7),
            uncheckedBorderColor = Color(0xFFA6B6CD),
            disabledCheckedThumbColor = Color.White,
            disabledCheckedTrackColor = Color(0xFF8BAEF2),
            disabledCheckedBorderColor = Color(0xFF8BAEF2),
            disabledUncheckedThumbColor = Color(0xFF8492A7),
            disabledUncheckedTrackColor = Color(0xFFE3E9F2),
            disabledUncheckedBorderColor = Color(0xFFB2BFD1)
        )
    )
}

/** 同一个分段选择器供课表设置、二课、成绩与任务筛选使用；不持有业务状态。 */
data class XiaoxuSegmentOption(val key: String, val label: String)

@Composable
fun XiaoxuSegmentedControl(
    options: List<XiaoxuSegmentOption>,
    selectedKey: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    fillWidth: Boolean = true,
    compact: Boolean = false
) {
    if (options.isEmpty()) return
    val track = RoundedCornerShape(999.dp)
    Row(
        modifier = modifier.then(if (fillWidth) Modifier.fillMaxWidth() else Modifier).clip(track)
            .background(if (compact) XiaoxuPalette.SurfaceAlt else XiaoxuPalette.GlassTrack)
            .border(XiaoxuSpacing.glassStroke,
                if (compact) Color(0xFFD6E0F0) else XiaoxuPalette.GlassBorder, track)
            .padding(if (compact) 3.dp else 4.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEach { option ->
            val selected = selectedKey == option.key
            val itemShape = RoundedCornerShape(999.dp)
            val itemColor = animateColorAsState(
                targetValue = if (selected) {
                    if (compact) XiaoxuPalette.BrandSoft else XiaoxuPalette.GlassIndicator
                } else Color.Transparent,
                animationSpec = tween(XiaoxuMotion.SelectionMs), label = "xiaoxu-segment-bg"
            ).value
            val inkColor = animateColorAsState(
                targetValue = if (selected) XiaoxuPalette.BrandDeep else XiaoxuPalette.Ink2,
                animationSpec = tween(XiaoxuMotion.SelectionMs), label = "xiaoxu-segment-text"
            ).value
            Box(
                modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                    .clip(itemShape).background(itemColor)
                    // The track owns the outline: avoid a second dark rim
                    // around ordinary selected tabs while retaining compact styling.
                    .then(if (selected && compact) Modifier.border(
                        XiaoxuSpacing.glassStroke, Color(0xFFB8CFF9), itemShape
                    ) else Modifier)
                    .selectable(selected = selected, role = Role.Tab,
                        onClick = { if (!selected) onSelected(option.key) }),
                contentAlignment = Alignment.Center
            ) {
                Text(option.label, style = MaterialTheme.typography.titleSmall,
                    color = inkColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * 设置列表与教务连续分组唯一共享的玻璃外壳。
 * 不建立业务状态，不接管行高或点击行为；课表设置保持原来的弹出位置与文字。
 */
@Composable
internal fun XiaoxuGroupSurface(
    modifier: Modifier = Modifier,
    verticalPadding: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(XiaoxuRadius.card)
    Column(
        modifier = modifier.fillMaxWidth().clip(shape)
            .background(XiaoxuPalette.GlassCard)
            .background(XiaoxuPalette.GlassSheen)
            .border(XiaoxuSpacing.glassStroke, XiaoxuPalette.GlassBorder, shape)
            .padding(vertical = verticalPadding),
        content = content
    )
}

/** 连续分组设置：只管理展示与布局，业务回调仍属于对应页面。 */
@Composable
fun XiaoxuGroupedSection(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!title.isNullOrBlank()) {
            Text(title, style = MaterialTheme.typography.labelMedium,
                color = XiaoxuPalette.Ink2, modifier = Modifier.padding(start = 14.dp))
        }
        XiaoxuGroupSurface(verticalPadding = 4.dp, content = content)
    }
}

/**
 * 北化式玻璃详情行：点击只打开独立 Dialog，不在列表中插入任何扩展内容。
 * 保持原签名，课程与第二课堂现有真实数据及 saveable 展开状态无需重写。
 */
@Composable
fun XiaoxuExpandableGlassRow(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(XiaoxuMotion.SelectionMs),
        label = "glass-row-chevron"
    )
    XiaoxuCard(modifier = modifier, contentPadding = 0.dp) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .heightIn(min = XiaoxuSpacing.listRowMin)
                .clickable(role = Role.Button, onClickLabel = "查看$title",
                    onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title,
                    style = MaterialTheme.typography.titleMedium,
                    color = XiaoxuPalette.Ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = XiaoxuPalette.Ink2,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                }
            }
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = "查看详情",
                tint = XiaoxuPalette.Ink2,
                modifier = Modifier.size(20.dp).rotate(rotation)
            )
        }
    }
    if (expanded) {
        XiaoxuGlassDialog(title = title, onDismiss = onToggle, content = content)
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

/**
 * 北化参考的独立玻璃浮层：详情不再参与页面 Column/LazyColumn 测量，
 * 不会把其余设置、任务和课程卡片向下顶开。
 *
 * 保留原有数据 Composable，浮层只负责布局和返回/点击外部关闭。
 * 内容在内部滚动，避免长许可文本溢出小屏；不在浮层套二层灰色 Card。
 */
@Composable
fun XiaoxuGlassDialog(
    title: String,
    onDismiss: () -> Unit,
    dismissLabel: String = "完成",
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(32.dp)
    val maxDialogHeight = LocalConfiguration.current.screenHeightDp.dp * 0.82f
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        XiaoxuDialogDim()
        Column(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .widthIn(max = 560.dp)
                .heightIn(max = maxDialogHeight)
                .clip(shape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFFE5F3FF),
                            Color(0xFFF5F0FF),
                            Color(0xFFE7F8F6),
                            Color(0xFFF9F2FC)
                        )
                    )
                )
                .border(0.5.dp, Color.White.copy(alpha = 0.82f), shape)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = XiaoxuPalette.Ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().semantics { heading() }
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content
            )
            XiaoxuDialogPrimaryButton(onClick = onDismiss) {
                Text(dismissLabel)
            }
        }
    }
}

/**
 * 唯一的弹窗主按钮外观：原文链接与完成操作共用 Material3
 * 胶囊按钮的尺寸、圆角、配色和触控反馈。
 */
@Composable
internal fun XiaoxuDialogPrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 50.dp),
        shape = RoundedCornerShape(999.dp),
        content = content
    )
}

/**
 * 兼容原任务详情和公告详情调用：保留函数签名，
 * 展现统一改为参考北化课表的居中玻璃浮层（非底部抽屉）。
 */
@Composable
fun XiaoxuSheet(
    onDismiss: () -> Unit,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    XiaoxuGlassDialog(
        title = title ?: "详情",
        onDismiss = onDismiss,
        content = content
    )
}

/** 实际使用的统一空态；其他状态由真实数据页面按官方来源处理。 */
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



/** 统一页内工具图标：只有主要操作有淡色底，避免每页单独叠四套按钮卡片。 */
@Composable
internal fun XiaoxuToolbarAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    emphasized: Boolean = false
) {
    XiaoxuSoftIconButton(
        modifier = Modifier.size(42.dp),
        backgroundColor = if (emphasized) XiaoxuPalette.BrandSoft else Color.Transparent,
        enabled = enabled,
        onClick = onClick
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(20.dp),
            tint = when {
                !enabled -> XiaoxuPalette.Ink3
                emphasized -> XiaoxuPalette.BrandDeep
                else -> XiaoxuPalette.Ink2
            }
        )
    }
}

/** 统一顶栏：品牌标记 + 页面名 + 刷新。不用 TopAppBar，避免五个页面各调一次主题。 */
@Composable
fun XiaoxuTopBar(
    title: String,
    subtitle: String? = null,
    refreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    onMessages: (() -> Unit)? = null,
    applyStatusBarInset: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            // A topBar-hosted header owns status inset; a page-hosted header is
            // already positioned below Scaffold's contentWindowInsets.
            .then(if (applyStatusBarInset) Modifier.statusBarsPadding() else Modifier)
            .padding(start = XiaoxuSpacing.page, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
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
            XiaoxuSoftIconButton(onClick = onMessages) {
                Icon(
                    imageVector = Icons.Outlined.NotificationsNone,
                    contentDescription = "打开消息中心",
                    tint = XiaoxuPalette.Ink2
                )
            }
        }
        if (onRefresh != null) {
            XiaoxuSoftIconButton(onClick = onRefresh, enabled = !refreshing) {
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
            .heightIn(min = 88.dp)
            .clip(RoundedCornerShape(XiaoxuRadius.card))
            .background(XiaoxuPalette.GlassCard)
            .border(XiaoxuSpacing.glassStroke, XiaoxuPalette.GlassBorder, RoundedCornerShape(XiaoxuRadius.card))
            .clickable(onClick = onClick, role = Role.Button)
            .padding(vertical = 10.dp, horizontal = 4.dp)
            .semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(tone.containerColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tone.contentColor,
                modifier = Modifier.size(21.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = XiaoxuPalette.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
