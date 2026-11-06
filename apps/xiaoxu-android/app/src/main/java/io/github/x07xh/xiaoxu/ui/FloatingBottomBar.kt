package io.github.x07xh.xiaoxu.ui

import android.os.Build
import com.kyant.backdrop.Backdrop
import io.github.x07xh.xiaoxu.ui.northglass.NorthAppSymbolSpec
import io.github.x07xh.xiaoxu.ui.northglass.NorthPhosphorNavigationIcon
import io.github.x07xh.xiaoxu.ui.northglass.rememberNorthNavigationIconPlayback

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import io.github.x07xh.xiaoxu.ui.nav.DampedDragAnimation
import io.github.x07xh.xiaoxu.ui.nav.inspectDragGestures
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 悬浮胶囊底部导航（Issue #287 / PR #328）。
 *
 * 动画驱动直接复用 SleepDown-Schedule 的 [DampedDragAnimation] 与
 * `inspectDragGestures`（见 `ui/nav/`，Apache-2.0 原件 + SleepDown 1.1 修改，
 * 署名见 `THIRD_PARTY_NOTICES.md`）。这两个文件零项目内部依赖，可独立抽取。
 *
 * **未**复用其 `LiquidBottomTabs`：该实现依赖 `glass` 模块 41 个文件 / 6559 行
 * 与 `com.kyant.backdrop` 液态玻璃库，无法独立抽取，强行移植等于把整套玻璃
 * 基础设施搬进校序，与「避免新一轮架构重构」直接冲突。本组件只用 Compose
 * 原生绘制复刻同样的**交互**（悬浮、滑动、压感），不复制其**渲染**。
 *
 * ## 手势语义（PR #328 修复，不可回退）
 *
 * 1. **轻点与拖拽分离**：手指位移累积超过系统 `touchSlop` 才认定为拖拽；
 *    未超阈值时父级不消费事件，`Modifier.selectable` 正常收到 up 并触发点击。
 * 2. **拖拽态消费事件**：越过阈值后逐帧 `consume()`，子项收不到 up，
 *    不会出现「拖到第 3 格又额外触发一次原格点击」的双重选中。
 * 3. **取消不等于选中**：`onDragCancel` 只把指示器回弹到当前选中项，
 *    **绝不**改变选中状态（旧实现 `onDragStop` 与 `onDragCancel` 共用，
 *    系统中断手势会误触发选中）。
 * 4. **外部选中同步不用 `animateToValue`**：那个方法内部会 `press()`/`release()`，
 *    快速连点会打断上一个 `release()`，导致指示器永久停在按下放大态。
 *    这里改用 `updateValue()`，只驱动位移，不碰压感。
 *
 * ## 重组控制
 *
 * - 指示器位移走 `Modifier.offset { }`（lambda 版）——只在 layout 阶段读值；
 * - 压感走 `graphicsLayer { }`（lambda 版）——只在 draw 阶段读值；
 * - 拖拽瞬时状态放在普通 holder（[DragGestureState]），不进 Snapshot；
 * - 每个 cell 独立 `MutableInteractionSource`，按压只重组该 cell。
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
    val badge: Int? = null,
    val northSymbol: NorthAppSymbolSpec? = null
)

private val BarHeight: Dp = 64.dp
private val BarHorizontalInset: Dp = 20.dp
private val BarBottomInset: Dp = 10.dp
private val IndicatorInset: Dp = 6.dp
private val BarShape = RoundedCornerShape(percent = 50)
private val IndicatorShape = RoundedCornerShape(percent = 50)
private const val PressedScale = 1.06f
private const val ColorAnimMs = 180

/**
 * 拖拽过程的瞬时状态。
 *
 * 故意**不**放进 `mutableStateOf`：它只在手势回调与 `LaunchedEffect` 里读写，
 * 没有任何 Composable 需要因为 `moved` 变化而重组。放进 Snapshot 只会让
 * 每一次 `onDrag` 都产生一次状态写入与失效检查。
 */
private class DragGestureState {
    var moved: Boolean = false
    var net: Float = 0f

    fun reset() {
        moved = false
        net = 0f
    }
}

/**
 * @param items 导航项，顺序即展示顺序；请在调用方用 `remember` 固定，避免每帧新建。
 * @param selectedIndex 当前选中下标；越界会被夹到合法范围。
 * @param onSelect 点击回调，参数为目标下标。
 * @param applyNavigationBarInset 是否自行处理系统手势条安全区（默认是）。
 *        `Scaffold` 的 bottomBar 不会自动加 inset，保留默认即可；若外层已经
 *        用 `consumeWindowInsets` 处理过，传 `false` 避免双重留白。
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
    applyNavigationBarInset: Boolean = true,
    draggable: Boolean = true,
    backdrop: Backdrop? = null
) {
    if (items.isEmpty()) return
    val count = items.size
    val safeIndex = selectedIndex.coerceIn(0, count - 1)
    // Original v1.0.83 glass geometry only on supported real lens platforms.
    val useNorthGlass = backdrop != null && Build.VERSION.SDK_INT >= 33
    val iconPlayback = if (useNorthGlass) rememberNorthNavigationIconPlayback(count, safeIndex, false) else null
    val visualHeight = if (useNorthGlass) 72.dp else BarHeight
    val horizontalInset = if (useNorthGlass) 16.dp else BarHorizontalInset
    val bottomInset = if (useNorthGlass) 8.dp else BarBottomInset

    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val touchSlop = LocalViewConfiguration.current.touchSlop

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (applyNavigationBarInset) {
                    Modifier.windowInsetsPadding(
                        WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
                    )
                } else Modifier
            )
            .padding(
                start = horizontalInset,
                end = horizontalInset,
                top = if (useNorthGlass) 8.dp else 4.dp,
                bottom = bottomInset
            ),
        contentAlignment = Alignment.Center
    ) {
        val barWidthPx = constraints.maxWidth.toFloat()
        val cellWidthPx = barWidthPx / count
        val cellWidthDp = with(LocalDensity.current) { cellWidthPx.toDp() }

        val scope = rememberCoroutineScope()
        val dragState = remember(count) { DragGestureState() }

        // 驱动只负责「索引浮点值 + 压感」，手势事件由下方 pointerInput 精确分发。
        val drag = remember(count) {
            DampedDragAnimation(
                animationScope = scope,
                initialValue = safeIndex.toFloat(),
                valueRange = 0f..(count - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = PressedScale,
                onDragStarted = {},
                onDragStopped = {},
                onDrag = { _, _ -> }
            )
        }

        // 手势回调只捕获一次，用 rememberUpdatedState 保证始终读到最新值。
        val currentOnSelect by rememberUpdatedState(onSelect)
        val currentIndex by rememberUpdatedState(safeIndex)
        val currentRtl by rememberUpdatedState(isRtl)
        val currentSlop by rememberUpdatedState(touchSlop)
        val currentCellWidth by rememberUpdatedState(cellWidthPx)

        // ⚠️ 手势必须挂在胶囊本身，不能挂在同级覆盖层：
        // Compose 的 hitTest 在 z-order 上遇到第一个命中的兄弟就停止，
        // 胶囊会把上层事件全挡住，同级覆盖层永远收不到 MOVE。
        val gestureModifier = if (draggable) {
            Modifier.pointerInput(drag, count) {
                inspectDragGestures(
                    onDragStart = {
                        dragState.reset()
                        drag.press()
                    },
                    onDragEnd = { change ->
                        if (dragState.moved) {
                            // 拖拽已接管本手势：吞掉 up，避免子项再触发一次点击。
                            change.consume()
                            val target = drag.targetValue
                                .fastRoundToInt()
                                .fastCoerceIn(0, count - 1)
                            drag.updateValue(target.toFloat())
                            currentOnSelect(target)
                        } else {
                            // 未越过 slop：只是手指抖动，指示器回弹即可。
                            drag.updateValue(currentIndex.toFloat())
                        }
                        drag.release()
                        dragState.reset()
                    },
                    onDragCancel = {
                        // 系统中断（来电、手势返回、多指介入）：只回弹，不改选中。
                        drag.updateValue(currentIndex.toFloat())
                        drag.release()
                        dragState.reset()
                    }
                ) { change, dragAmount ->
                    // RTL 下索引 0 在最右侧，物理右移等于索引递减。
                    val dx = if (currentRtl) -dragAmount.x else dragAmount.x
                    if (!dragState.moved) {
                        dragState.net += dx
                        if (abs(dragState.net) > currentSlop) dragState.moved = true
                    }
                    if (dragState.moved) {
                        change.consume()
                        drag.updateValue(
                            (drag.targetValue + dx / currentCellWidth)
                                .fastCoerceIn(0f, (count - 1).toFloat())
                        )
                    }
                }
            }
        } else {
            Modifier
        }

        // 外部选中变化（点击、返回、状态恢复、进程重建）时让指示器跟上。
        // 用 updateValue 而不是 animateToValue：后者会 press()/release()，
        // 快速连点时会打断上一个 release，压感永久停在放大态。
        LaunchedEffect(safeIndex, count) {
            if (!dragState.moved) drag.updateValue(safeIndex.toFloat())
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(visualHeight)
                .then(
                    if (useNorthGlass && backdrop != null) Modifier.northGlassTrack(backdrop)
                    else Modifier.shadow(elevation = 12.dp, shape = BarShape, clip = false)
                        .clip(BarShape).background(containerColor)
                )
                .then(gestureModifier)
        ) {
            // 指示器：offset lambda 只在 layout 阶段读 drag.value，位移不触发重组。
            Box(
                Modifier
                    .offset {
                        // 不要在这里手动镜像 RTL：Modifier.offset{} 内部用
                        // placeable.placeRelative()，RTL 下会自动把 x 翻成
                        // parentWidth - x - width。手动再翻一次就成了双重镜像
                        // （实测：RTL 选中「课表」时指示器会跑到「任务」上）。
                        // 索引值 * 格宽 是方向无关的逻辑位置，交给 placeRelative 处理。
                        IntOffset((drag.value * cellWidthPx).roundToInt(), 0)
                    }
                    .width(cellWidthDp)
                    .fillMaxHeight()
                    .padding(IndicatorInset)
                    .then(
                        if (useNorthGlass && backdrop != null) {
                            Modifier.northGlassIndicator(backdrop) { drag.pressProgress }
                        } else Modifier.clip(IndicatorShape).background(indicatorColor)
                    )
                    .graphicsLayer {
                        // 压感时指示器轻微横向撑开，呼应 SleepDown 的 pressProgress 用法。
                        val p = drag.pressProgress
                        scaleX = 1f + 0.04f * p
                        scaleY = 1f + 0.02f * p
                    }
            )

            Row(
                Modifier
                    .fillMaxSize()
                    .selectableGroup(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, item ->
                    FloatingBarCell(
                        item = item,
                        selected = index == safeIndex,
                        showLabel = showLabels,
                        useNorthGlass = useNorthGlass,
                        iconPhase = { iconPlayback?.phase(index) ?: 1f },
                        selectedContentColor = selectedContentColor,
                        unselectedContentColor = unselectedContentColor,
                        modifier = Modifier.width(cellWidthDp),
                        onClick = {
                            if (index == safeIndex) iconPlayback?.replay(index)
                            onSelect(index)
                        }
                    )
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
    useNorthGlass: Boolean,
    iconPhase: () -> Float,
    selectedContentColor: Color,
    unselectedContentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    // 每 cell 独立交互源：按压只让本 cell 重组，不牵动整条 dock。
    val interactionSource = remember { MutableInteractionSource() }
    val contentColor by animateColorAsState(
        targetValue = if (selected) selectedContentColor else unselectedContentColor,
        animationSpec = tween(ColorAnimMs),
        label = "barCellColor"
    )

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(IndicatorShape)
            // selectable 而不是 clickable：只有它会把「已选中」写进无障碍节点，
            // TalkBack 才会读「课表，已选中，标签页」。Role.Tab + 父级
            // selectableGroup() 让整条 dock 被识别为一组标签页。
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = onClick
            )
            // mergeDescendants：否则 Text 会自成一个可聚焦节点，
            // TalkBack 会把「标签页，已选中」和「今日」读成两个焦点。
            // 合并后整格只有一个焦点，朗读「今日，已选中，标签页」。
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            if (useNorthGlass && item.northSymbol != null) {
                // Original BUCT vector paths and replay motion, not approximate Material icons.
                NorthPhosphorNavigationIcon(
                    spec = item.northSymbol,
                    selection = if (selected) 1f else 0f,
                    phase = iconPhase,
                    tint = contentColor,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier
                        .size(22.dp)
                        .graphicsLayer {
                            val scale = if (selected) PressedScale else 1f
                            scaleX = scale
                            scaleY = scale
                        }
                )
            }
            if (item.badge != null && item.badge > 0) {
                BadgeDot(count = item.badge)
            }
        }
        if (showLabel) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = item.label,
                fontSize = if (useNorthGlass) 12.sp else 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = contentColor,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
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
