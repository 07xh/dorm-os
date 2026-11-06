package io.github.x07xh.xiaoxu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import io.github.x07xh.xiaoxu.ui.nav.DampedDragAnimation
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 悬浮胶囊底部导航（Issue #287）。
 *
 * 动画驱动直接复用 SleepDown-Schedule 的 [DampedDragAnimation]
 * （见 `ui/nav/DampedDragAnimation.kt`，Apache-2.0 原件 + SleepDown 修改，已保留署名）。
 * 该文件零项目内部依赖，可独立抽取——这是本次唯一满足"直接复用"条件的部分。
 *
 * **未**复用其 `LiquidBottomTabs`：该实现依赖 `glass` 模块 41 个文件 / 6559 行
 * 与 `com.kyant.backdrop` 第三方液态玻璃库，无法独立抽取，强行移植等于把整套
 * 玻璃基础设施搬进校序，与"避免新一轮架构重构"直接冲突。本组件只用 Compose
 * 原生绘制复刻同样的**交互**（悬浮、滑动、压感），不复制其**渲染**。
 *
 * 三个热点做读写分离，避免无效重组：
 * 1. 指示器位移走 `Modifier.offset { }`（lambda 版本）——只在 layout 阶段读值；
 * 2. 压感缩放走 `graphicsLayer { }`（lambda 版本）——只在 draw 阶段读值；
 * 3. 每个 cell 独立 `MutableInteractionSource`，按压只重组该 cell。
 *
 * 本文件不修改 MainActivity、不持有页面状态：选中项由调用方传入，组件只回调
 * [onSelect]，接入后页面状态保存行为与现在完全一致。
 */

/** 单个导航项。 */
@Immutable
data class FloatingBarItem(
    val label: String,
    val icon: ImageVector,
    /** 角标数字，null 或 ≤0 表示不显示。 */
    val badge: Int? = null
)

private val BarHeight: Dp = 64.dp
private val BarHorizontalInset: Dp = 20.dp
private val BarBottomInset: Dp = 12.dp
private val IndicatorInset: Dp = 6.dp
private val BarShape = RoundedCornerShape(percent = 50)
private val IndicatorShape = RoundedCornerShape(percent = 50)
private const val PressedScale = 1.06f

/**
 * @param items 导航项，顺序即展示顺序；请在调用方用 `remember` 固定，避免每帧新建。
 * @param selectedIndex 当前选中下标；越界会被夹到合法范围。
 * @param onSelect 点击回调，参数为目标下标。
 * @param draggable 是否允许在导航条上横向拖拽切换（默认开启，与 SleepDown 一致）。
 */
@Composable
fun FloatingBottomBar(
    items: List<FloatingBarItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = XiaoxuPalette.Surface,
    indicatorColor: Color = XiaoxuPalette.BrandSoft,
    unselectedContentColor: Color = XiaoxuPalette.Ink3,
    selectedContentColor: Color = XiaoxuPalette.BrandDeep,
    showLabels: Boolean = true,
    draggable: Boolean = true
) {
    if (items.isEmpty()) return
    val count = items.size
    val safeIndex = selectedIndex.coerceIn(0, count - 1)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = BarHorizontalInset, vertical = BarBottomInset),
        contentAlignment = Alignment.Center
    ) {
        val density = LocalDensity.current
        val cellWidthPx = constraints.maxWidth.toFloat() / count
        val cellWidthDp = with(density) { cellWidthPx.toDp() }

        val scope = rememberCoroutineScope()
        var dragging by remember(count) { mutableStateOf(false) }
        // 只有真正拖动过才在抬手时改选中项；轻点交给 cell 的 clickable，
        // 否则父级手势会把"轻点第 3 个 tab"变成"落回当前指示器位置"。
        var dragMoved by remember(count) { mutableStateOf(false) }
        val currentOnSelect by rememberUpdatedState(onSelect)
        val currentCellWidth by rememberUpdatedState(cellWidthPx)

        val drag = remember(scope, count) {
            DampedDragAnimation(
                animationScope = scope,
                initialValue = safeIndex.toFloat(),
                valueRange = 0f..(count - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = PressedScale,
                onDragStarted = {
                    dragging = true
                    dragMoved = false
                },
                onDragStopped = {
                    if (dragMoved) {
                        val target = targetValue.fastRoundToInt().fastCoerceIn(0, count - 1)
                        animateToValue(target.toFloat())
                        currentOnSelect(target)
                    }
                    dragging = false
                    dragMoved = false
                },
                onDrag = { _, dragAmount ->
                    if (abs(dragAmount.x) > 0.5f) dragMoved = true
                    if (dragMoved) {
                        updateValue(
                            (targetValue + dragAmount.x / currentCellWidth)
                                .fastCoerceIn(0f, (count - 1).toFloat())
                        )
                    }
                }
            )
        }

        // 外部选中变化（点击其它入口 / 状态恢复）时让指示器跟上；拖拽中不打断手势。
        LaunchedEffect(safeIndex, dragging) {
            if (!dragging && abs(drag.targetValue - safeIndex.toFloat()) > 0.01f) {
                drag.animateToValue(safeIndex.toFloat())
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(BarHeight)
                .shadow(elevation = 12.dp, shape = BarShape, clip = false)
                .clip(BarShape)
                .background(containerColor)
                .then(if (draggable) drag.modifier else Modifier)
        ) {
            // 指示器：offset lambda 只在 layout 阶段读 drag.value，位移不触发重组。
            Box(
                Modifier
                    .offset { IntOffset((drag.value * cellWidthPx).roundToInt(), 0) }
                    .width(cellWidthDp)
                    .fillMaxHeight()
                    .padding(IndicatorInset)
                    .clip(IndicatorShape)
                    .background(indicatorColor)
                    .graphicsLayer {
                        // 压感时指示器轻微横向撑开，呼应 SleepDown 的 pressProgress 用法。
                        val p = drag.pressProgress
                        scaleX = 1f + 0.04f * p
                        scaleY = 1f + 0.02f * p
                    }
            )

            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                items.forEachIndexed { index, item ->
                    key(item.label) {
                        FloatingBarCell(
                            item = item,
                            selected = index == safeIndex,
                            showLabel = showLabels,
                            selectedContentColor = selectedContentColor,
                            unselectedContentColor = unselectedContentColor,
                            modifier = Modifier.width(cellWidthDp),
                            onClick = { onSelect(index) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingBarCell(
    item: FloatingBarItem,
    selected: Boolean,
    showLabel: Boolean,
    selectedContentColor: Color,
    unselectedContentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    // 每 cell 独立交互源：按压只让本 cell 重组，不牵动整条 dock。
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val contentColor = if (selected) selectedContentColor else unselectedContentColor

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(IndicatorShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = contentColor,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer {
                        // draw 阶段读 pressed，跳过重组。
                        val s = if (pressed) PressedScale else 1f
                        scaleX = s
                        scaleY = s
                    }
            )
            if (item.badge != null && item.badge > 0) {
                BadgeDot(count = item.badge)
            }
        }
        if (showLabel) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = item.label,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = contentColor,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun BadgeDot(count: Int) {
    Box(
        Modifier
            .offset(x = 6.dp, y = (-4).dp)
            .height(14.dp)
            .width(if (count > 9) 18.dp else 14.dp)
            .clip(CircleShape)
            .background(XiaoxuPalette.Danger),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            fontSize = 8.sp,
            color = Color.White,
            maxLines = 1
        )
    }
}

/**
 * 接入示例（本轮**不**改 MainActivity，交给整合方）：
 *
 * ```kotlin
 * var current by rememberSaveable { mutableStateOf(Page.HOME) }   // 现有状态不动
 *
 * val barItems = remember {
 *     PrimaryTabs.map { FloatingBarItem(it.label, it.symbol) }
 * }
 *
 * Scaffold(bottomBar = {
 *     FloatingBottomBar(
 *         items = barItems,
 *         selectedIndex = PrimaryTabs.indexOf(current).coerceAtLeast(0),
 *         onSelect = { current = PrimaryTabs[it] },
 *         modifier = Modifier.navigationBarsPadding()
 *     )
 * }) { ... }
 * ```
 *
 * `Page`、`rememberSaveable`、`SaveableStateHolder` 全部无需改动。
 */
