package io.github.x07xh.xiaoxu.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import com.tyust.course.ui.system.LiquidSwitch
import com.tyust.course.ui.system.SystemSectionHeader
import com.tyust.course.ui.system.SystemStatusBadge
import com.tyust.course.ui.system.SystemTone
import com.tyust.course.ui.system.SystemEmptyState
import com.tyust.course.ui.system.SystemSecondaryButton
import com.tyust.course.ui.theme.MotionSpecs
import com.tyust.course.ui.system.SystemSegmentedControl
import com.tyust.course.ui.system.SystemCompactSegmentedControl
import com.tyust.course.ui.system.SystemDialog
import com.tyust.course.ui.system.SystemPrimaryButton
import com.tyust.course.ui.system.LiquidButton
import com.tyust.course.ui.system.LiquidButtonStyle
import com.tyust.course.ui.theme.CourseSelectorTheme
import com.tyust.course.ui.system.SystemTopBar
import com.tyust.course.ui.system.SystemCard
import androidx.compose.foundation.layout.PaddingValues
import com.tyust.course.ui.system.SystemIconButton
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.statusBars
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


    /** 卡片与底栏共享的轻玻璃配色：不采样背景，低成本透明合成。 */
    val GlassCard = Color.White.copy(alpha = 0.95f)
    val GlassBorder = Color.White.copy(alpha = 0.74f)
    val GlassTrack = Color.White.copy(alpha = 0.48f)
    val SheetSurface = Color(0xFFF9FAFF)
    /** 北化式材质分工：仅悬浮控件带高光，普通内容卡仍使用稳定浅色底。 */
    /** 单条白色柔性边缘高光；不再叠加第二层深灰 Card 描边。 */

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

/** B2 hierarchy: outer groups soften the boundary; inner rows stay visually quieter.
 * Keep navigation-dock geometry independent of these content-only tokens.
 */
object XiaoxuRadius {
    val group: Dp = 20.dp
    val card: Dp = 18.dp
    val list: Dp = 14.dp
    val tile: Dp = 12.dp
    val hero: Dp = 28.dp
    val modal: Dp = 28.dp
    val pill: Dp = 999.dp
}

/** 间距：页面 18dp，区块 14dp，卡内 16dp，元素 8dp。 */
object XiaoxuSpacing {
    val page: Dp = 18.dp
    val section: Dp = 14.dp
    val card: Dp = 16.dp
    val gap: Dp = 8.dp

    /** 北化式紧凑列表统一起始高度；长标题仍由内容自行撑开。 */
    val listRowMin: Dp = 68.dp
    val glassStroke: Dp = 0.5.dp
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

/** Standalone activity compatibility entry; the upstream theme is the only Material theme. */
@Composable
fun XiaoxuTheme(content: @Composable () -> Unit) {
    CourseSelectorTheme(darkTheme = false, content = content)
}

/** Keep dialog dimming uniform across content and system-bar regions. */
@Composable
internal fun XiaoxuDialogDim(dimAmount: Float = 0.12f) {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        val oldDim = window?.attributes?.dimAmount
        window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window?.setDimAmount(dimAmount)
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
    glassChrome: Boolean = false,
    content: @Composable () -> Unit
) {
    LiquidButton(
        onClick = onClick,
        modifier = modifier.size(48.dp),
        enabled = enabled,
        shape = CircleShape,
        style = when {
            backgroundColor != Color.Transparent -> LiquidButtonStyle.SolidTinted
            glassChrome -> LiquidButtonStyle.Surface
            else -> LiquidButtonStyle.SolidSurface
        },
        tint = if (backgroundColor != Color.Transparent) backgroundColor else Color.Unspecified,
        horizontalPadding = 0.dp,
        content = { content() }
    )
}

// ---------------------------------------------------------------------------
// 组件
// ---------------------------------------------------------------------------

/**
 * 普通内容卡使用18dp；设置外分组20dp、连续行14dp，不重复叠灰影。
 * 传 onClick 才可点，触控高度强制 ≥48dp；不传就是纯展示容器。
 */
@Composable
fun XiaoxuCard(
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") shape: Shape = RoundedCornerShape(XiaoxuRadius.card),
    container: Color = XiaoxuPalette.GlassCard,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = XiaoxuSpacing.card,
    content: @Composable ColumnScope.() -> Unit
) {
    SystemCard(
        modifier = modifier,
        backgroundColor = if (container == XiaoxuPalette.GlassCard) MaterialTheme.colorScheme.surface else container,
        onClick = onClick,
        contentPadding = PaddingValues(contentPadding),
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
    LiquidSwitch(checked = checked, onCheckedChange = onCheckedChange,
        modifier = modifier, enabled = enabled)
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
    val labels = options.map { it.label }
    val selectedIndex = options.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)
    val layout = modifier.then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
    val select: (Int) -> Unit = { index ->
        options.getOrNull(index)?.let { if (it.key != selectedKey) onSelected(it.key) }
    }
    if (compact) {
        SystemCompactSegmentedControl(labels, selectedIndex, select, modifier = layout)
    } else {
        SystemSegmentedControl(labels, selectedIndex, select, modifier = layout)
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
    SystemCard(modifier = modifier,
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = verticalPadding),
        content = content)
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
        animationSpec = MotionSpecs.tabTransition(),
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
    Box(modifier.fillMaxWidth().semantics { heading() }) {
        SystemSectionHeader(title = title, subtitle = caption, actionLabel = actionLabel,
            onActionClick = onAction)
    }
}

private fun XiaoxuTone.toSystemTone(): SystemTone = when (this) {
    XiaoxuTone.Brand -> SystemTone.Info
    XiaoxuTone.Success -> SystemTone.Success
    XiaoxuTone.Warning -> SystemTone.Warning
    XiaoxuTone.Danger -> SystemTone.Danger
    XiaoxuTone.Neutral -> SystemTone.Neutral
}

/** 来源徽标：只回答"从哪来"，不表达任何核验结论。 */
@Composable
fun SourceBadge(text: String, tone: XiaoxuTone = XiaoxuTone.Neutral) {
    SystemStatusBadge(text = text, tone = tone.toSystemTone())
}

/** 状态标签：带圆点，用于截止状态、连接状态等结论性标签。 */
@Composable
fun StatusChip(text: String, tone: XiaoxuTone = XiaoxuTone.Neutral) {
    SystemStatusBadge(text = text, tone = tone.toSystemTone())
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
    showDismissButton: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    SystemDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(title, style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() }, textAlign = TextAlign.Center)
        },
        confirmButton = if (showDismissButton) {
            { SystemPrimaryButton(dismissLabel, onDismiss, modifier = Modifier.fillMaxWidth()) }
        } else null
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
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
    LiquidButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        style = LiquidButtonStyle.SolidTinted,
        tint = MaterialTheme.colorScheme.primary,
        minHeight = 52.dp,
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
fun XiaoxuEmpty(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    SystemEmptyState(title = title, message = message, modifier = modifier,
        icon = Icons.Outlined.Inbox,
        action = if (actionLabel != null && onAction != null) {
            { SystemSecondaryButton(actionLabel, onAction) }
        } else null)
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
    SystemIconButton(
        icon = icon,
        contentDescription = label,
        onClick = onClick,
        enabled = enabled,
        tint = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    )
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
    Box(Modifier.then(if (!applyStatusBarInset) Modifier.consumeWindowInsets(WindowInsets.statusBars) else Modifier)) {
        SystemTopBar(title = title, subtitle = subtitle, actions = {
            if (onMessages != null) {
                SystemIconButton(Icons.Outlined.NotificationsNone, "打开消息中心", onMessages)
            }
            if (onRefresh != null) {
                if (refreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    SystemIconButton(Icons.Outlined.Refresh, "刷新云端与官方通知", onRefresh)
                }
            }
        })
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
            .clip(RoundedCornerShape(XiaoxuRadius.tile))
            .background(XiaoxuPalette.GlassCard)
            .border(XiaoxuSpacing.glassStroke, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(XiaoxuRadius.tile))
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
