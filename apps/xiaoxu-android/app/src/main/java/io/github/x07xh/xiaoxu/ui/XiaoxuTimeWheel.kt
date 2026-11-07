package io.github.x07xh.xiaoxu.ui

import android.os.Build
import android.view.ViewGroup
import android.widget.NumberPicker
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

private val HOUR_OPTIONS = (0..23).toList()
private val MINUTE_OPTIONS = (0..59).toList()

/**
 * Smooth, native number-wheel time selection, matching the numeric SleepDown
 * reference rather than Material3's circular clock face. NumberPicker supplies
 * mature fling/scroll/accessibility behavior without custom physics or timers.
 * This component only changes an unsaved edit draft, never semester storage.
 */
@Composable
internal fun XiaoxuTimeWheel(
    hour: Int,
    minute: Int,
    onHourChanged: (Int) -> Unit,
    onMinuteChanged: (Int) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        XiaoxuNumberWheel(
            options = HOUR_OPTIONS,
            selected = hour,
            label = "小时",
            onSelected = onHourChanged
        )
        Text(":", style = MaterialTheme.typography.headlineLarge, color = XiaoxuPalette.Ink)
        XiaoxuNumberWheel(
            options = MINUTE_OPTIONS,
            selected = minute,
            label = "分钟",
            onSelected = onMinuteChanged
        )
    }
}

/** One native wheel for time, semester weeks, and periods/day. No duplicate scroll physics. */
@Composable
internal fun XiaoxuNumberWheel(
    options: List<Int>,
    selected: Int,
    label: String,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier.width(118.dp),
    cyclic: Boolean = true
) {
    require(options.isNotEmpty()) { "Number wheel requires at least one option" }
    val currentOnChange by rememberUpdatedState(onSelected)
    val currentOptions by rememberUpdatedState(options)
    AndroidView(
        modifier = modifier.height(210.dp),
        factory = { context ->
            NumberPicker(context).apply {
                minValue = 0
                maxValue = options.lastIndex
                wrapSelectorWheel = cyclic && options.size > 2
                setFormatter { index -> options.getOrNull(index)
                    ?.toString()?.padStart(2, '0').orEmpty() }
                value = options.indexOf(selected).takeIf { it >= 0 } ?: 0
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                contentDescription = label
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setTextColor(XiaoxuPalette.Ink.toArgb())
                    setTextSize(29.sp.value * context.resources.displayMetrics.scaledDensity)
                }
                setOnValueChangedListener { _, _, position ->
                    currentOptions.getOrNull(position)?.let(currentOnChange)
                }
            }
        },
        update = { picker ->
            val position = options.indexOf(selected).takeIf { it >= 0 } ?: 0
            if (picker.value != position) picker.value = position
        }
    )
}
