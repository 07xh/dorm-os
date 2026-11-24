package io.github.x07xh.xiaoxu.ui.v2

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import io.github.x07xh.xiaoxu.ui.XiaoxuPalette
import io.github.x07xh.xiaoxu.ui.XiaoxuSegmentOption

/**
 * Single moving selection surface; no competing per-cell background animations.
 * Selected state is entirely controlled by the caller. No hidden selection fallback.
 */
@Composable
internal fun UiV2SegmentedControl(
    options: List<XiaoxuSegmentOption>,
    selectedKey: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    fillWidth: Boolean = true,
    compact: Boolean = false
) {
    if (options.isEmpty()) return
    val index = options.indexOfFirst { it.key == selectedKey }
    val pill = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .clip(pill)
            .background(UiV2Theme.SegmentTrack)
            .padding(3.dp)
            .uiV2SelectionThumb(index, options.size, onDragSelected = { target ->
                options.getOrNull(target)?.let { if (it.key != selectedKey) onSelected(it.key) }
            })
            .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEach { option ->
            key(option.key) {
                val selected = selectedKey == option.key
                Box(
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        .clip(pill)
                        .selectable(
                            selected = selected, role = Role.Tab,
                            onClick = { if (!selected) onSelected(option.key) }
                        )
                        .padding(horizontal = if (compact) 4.dp else 6.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (selected) XiaoxuPalette.BrandDeep else XiaoxuPalette.Ink2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** Shared draw-only selected layer for equal-width controls. Apply after track padding.
 * A horizontal gesture previews selection; only release commits the nearest index.
 * The authoritative index still belongs to the caller, including after cancellation.
 */
@Composable
internal fun Modifier.uiV2SelectionThumb(
    index: Int, count: Int, radius: Dp = 999.dp,
    onDragSelected: ((Int) -> Unit)? = null
): Modifier {
    val mode = LocalUiV2Motion.current
    val thumb = remember(count) { Animatable(index.coerceAtLeast(0).toFloat()) }
    val drag = remember(count) { mutableFloatStateOf(Float.NaN) }
    val settle = remember(count) { mutableIntStateOf(0) }
    val callback = rememberUpdatedState(onDragSelected)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    LaunchedEffect(index, count, settle.intValue, mode) {
        val preview = drag.floatValue
        if (preview.isFinite()) {
            thumb.snapTo(preview)
            drag.floatValue = Float.NaN
        }
        thumb.animateTo(index.coerceAtLeast(0).toFloat(),
            animationSpec = UiV2Motion.spec(mode, UiV2Motion.StandardMs))
    }
    val gesture = if (count > 1 && index in 0 until count && onDragSelected != null) {
        Modifier.pointerInput(index, count, rtl) {
            detectHorizontalDragGestures(
                onDragStart = {
                    drag.floatValue = thumb.value.coerceIn(0f, (count - 1).toFloat())
                },
                onHorizontalDrag = { change, amount ->
                    change.consume()
                    drag.floatValue = UiV2SegmentDragPolicy.move(
                        drag.floatValue, amount, size.width.toFloat(), count, rtl
                    )
                },
                onDragCancel = { settle.intValue++ },
                onDragEnd = {
                    val target = UiV2SegmentDragPolicy.nearest(drag.floatValue, count)
                    if (target != null && target != index) callback.value?.invoke(target)
                    settle.intValue++
                }
            )
        }
    } else Modifier
    return this.then(gesture).drawBehind {
        if (index in 0 until count && size.width > 0f) {
            val width = size.width / count
            val preview = drag.floatValue
            val position = (if (preview.isFinite()) preview else thumb.value)
                .coerceIn(0f, (count - 1).toFloat())
            val visualIndex = if (layoutDirection == LayoutDirection.Rtl) count - 1 - position else position
            drawRoundRect(
                color = UiV2Theme.SegmentThumb,
                topLeft = Offset(visualIndex * width, 0f),
                size = Size(width, size.height),
                cornerRadius = CornerRadius(radius.toPx().coerceAtMost(size.height / 2f))
            )
        }
    }
}
