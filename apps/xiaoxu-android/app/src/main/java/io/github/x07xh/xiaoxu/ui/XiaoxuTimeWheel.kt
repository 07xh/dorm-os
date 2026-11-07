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
        NativeTimeNumberWheel(
            value = hour,
            maxValue = 23,
            label = "小时",
            onValueChanged = onHourChanged
        )
        Text(":", style = MaterialTheme.typography.headlineLarge, color = XiaoxuPalette.Ink)
        NativeTimeNumberWheel(
            value = minute,
            maxValue = 59,
            label = "分钟",
            onValueChanged = onMinuteChanged
        )
    }
}

@Composable
private fun NativeTimeNumberWheel(
    value: Int,
    maxValue: Int,
    label: String,
    onValueChanged: (Int) -> Unit
) {
    val currentOnChange by rememberUpdatedState(onValueChanged)
    AndroidView(
        modifier = Modifier.width(118.dp).height(210.dp),
        factory = { context ->
            NumberPicker(context).apply {
                minValue = 0
                this.maxValue = maxValue
                wrapSelectorWheel = true
                setFormatter { number -> number.toString().padStart(2, '0') }
                this.value = value.coerceIn(0, maxValue)
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                contentDescription = label
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setTextColor(XiaoxuPalette.Ink.toArgb())
                    setTextSize(29.sp.value * context.resources.displayMetrics.scaledDensity)
                }
                setOnValueChangedListener { _, _, selected ->
                    currentOnChange(selected)
                }
            }
        },
        update = { picker ->
            val expected = value.coerceIn(0, maxValue)
            if (picker.value != expected) picker.value = expected
        }
    )
}
