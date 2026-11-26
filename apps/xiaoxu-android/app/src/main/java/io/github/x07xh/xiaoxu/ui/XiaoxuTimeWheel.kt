package io.github.x07xh.xiaoxu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.tyust.course.ui.system.GlassWheelColumn
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val HOUR_OPTIONS = (0..23).toList()
private val MINUTE_OPTIONS = (0..59).toList()

/** Business time fields backed by the original finite North wheel. */
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
    @Suppress("UNUSED_PARAMETER") cyclic: Boolean = true
) {
    require(options.isNotEmpty()) { "Number wheel requires at least one option" }
    GlassWheelColumn(
        items = options.map { it.toString().padStart(2, '0') },
        selectedIndex = options.indexOf(selected).coerceAtLeast(0),
        onSelect = { index -> options.getOrNull(index)?.let(onSelected) },
        modifier = modifier.semantics { contentDescription = label }
    )
}
