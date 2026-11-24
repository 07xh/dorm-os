package io.github.x07xh.xiaoxu.ui.v2

import androidx.compose.animation.core.animateFloatAsState
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
    val thumb = animateFloatAsState(
        targetValue = index.coerceAtLeast(0).toFloat(),
        animationSpec = tween(UiV2Motion.StandardMs, easing = UiV2Motion.Easing),
        label = "v2-segment-thumb"
    )
    val pill = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .clip(pill)
            .background(UiV2Theme.SegmentTrack)
            .padding(3.dp)
            .drawBehind {
                if (index >= 0 && size.width > 0f) {
                    val width = size.width / options.size
                    val position = thumb.value.coerceIn(0f, (options.size - 1).toFloat())
                    val visualIndex = if (layoutDirection == LayoutDirection.Rtl) {
                        options.size - 1 - position
                    } else position
                    drawRoundRect(
                        color = UiV2Theme.SegmentThumb,
                        topLeft = Offset(visualIndex * width, 0f),
                        size = Size(width, size.height),
                        cornerRadius = CornerRadius(size.height / 2f)
                    )
                }
            }
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
