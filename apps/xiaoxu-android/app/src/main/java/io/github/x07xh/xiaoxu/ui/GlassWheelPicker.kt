package io.github.x07xh.xiaoxu.ui

// SPDX-License-Identifier: GPL-3.0-only
// Source: znjhahaha/zhengfang-apk @ bb3b80a6d64e4c2057c0544bb417696ceaa2a9ed, GlassWheelPicker.kt
// Original authors and contributors. Adapted to the shared local dialog host.
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.roundToInt


@Composable
fun GlassWheelColumn(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemHeight: Dp = 40.dp,
    visibleCount: Int = 5
) {
    require(visibleCount % 2 == 1) { "visibleCount 应为奇数" }
    val reduced = rememberGlassAccessibilityMode().reduceMotion
    val density = LocalDensity.current
    val itemHeightPx = with(density) { itemHeight.toPx() }
    val padCount = visibleCount / 2
    val state = rememberLazyListState(
        initialFirstVisibleItemIndex = selectedIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
    )
    val flingBehavior = rememberSnapFlingBehavior(state)
    val scope = rememberCoroutineScope()

    // 吸附中心行索引（滚动中实时更新）
    val centerIndex by remember(state, items.size, itemHeightPx) {
        derivedStateOf {
            val offsetRows = (state.firstVisibleItemScrollOffset / itemHeightPx).roundToInt()
            (state.firstVisibleItemIndex + offsetRows).coerceIn(0, (items.size - 1).coerceAtLeast(0))
        }
    }
    LaunchedEffect(centerIndex) {
        if (centerIndex != selectedIndex) onSelect(centerIndex)
    }

    Box(
        modifier = modifier.height(itemHeight * visibleCount),
        contentAlignment = Alignment.Center
    ) {
        LazyColumn(
            state = state,
            flingBehavior = flingBehavior,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = itemHeight * padCount)
        ) {
            items(items.size, key = { it }) { index ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight)
                        .graphicsLayer {
                            val layoutInfo = state.layoutInfo
                            val info = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                            if (info != null) {
                                val viewportCenter =
                                    (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
                                val itemCenter = info.offset + info.size / 2f
                                val distRows = (itemCenter - viewportCenter) / info.size
                                rotationX = if (reduced) 0f else -distRows * 16f
                                alpha = (1f - 0.24f * abs(distRows)).coerceIn(0.2f, 1f)
                                val scale = if (reduced) 1f else 1f - 0.055f * abs(distRows)
                                scaleX = scale
                                scaleY = scale
                            }
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            scope.launch { if (reduced) state.scrollToItem(index) else state.animateScrollToItem(index) }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val isCenter = index == centerIndex
                    Text(
                        text = items[index],
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isCenter) FontWeight.SemiBold else FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
        // 上下渐隐罩，增强滚筒纵深
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight * padCount)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.30f),
                            Color.Transparent
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight * padCount)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.30f)
                        )
                    )
                )
        )
    }
}


@Composable
fun GlassTimePickerDialog(
    initialTime: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    title: String = "选择时间",
    minuteStep: Int = 5,
    preserveInitial: Boolean = false
) {
    val step = minuteStep.coerceIn(1, 30)
    val hourLabels = remember { (0..23).map { "%02d".format(it) } }
    val minuteValues = remember(step, initialTime, preserveInitial) {
        val values = (0 until 60 step step).toList()
        if (preserveInitial) (values + parseMinute(initialTime, 0)).distinct().sorted() else values
    }
    val minuteLabels = remember(minuteValues) { minuteValues.map { "%02d".format(it) } }

    var hour by remember { mutableIntStateOf(parseHour(initialTime, 8)) }
    var minute by remember {
        mutableIntStateOf(nearestMinuteIndex(parseMinute(initialTime, 0), minuteValues))
    }

    AppDialog(usePortal = true, 
        onDismissRequest = onDismiss,
        title = { Text(title) },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        confirmButton = { TextButton(onClick = { onConfirm("%02d:%02d".format(hour, minuteValues[minute])) }) { Text("确定") } },
        text = {
        Box(modifier = Modifier.fillMaxWidth()) {
            // 中心行 systemFill 胶囊指示条
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF787880).copy(alpha = 0.12f))
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassWheelColumn(
                    items = hourLabels,
                    selectedIndex = hour,
                    onSelect = { hour = it },
                    modifier = Modifier.weight(1f),
                    visibleCount = 3
                )
                Text(
                    text = ":",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                GlassWheelColumn(
                    items = minuteLabels,
                    selectedIndex = minute,
                    onSelect = { minute = it },
                    modifier = Modifier.weight(1f),
                    visibleCount = 3
                )
            }
        }
    })
}

private fun parseHour(value: String, fallback: Int): Int =    value.substringBefore(':').trim().toIntOrNull()?.coerceIn(0, 23) ?: fallback

private fun parseMinute(value: String, fallback: Int): Int =
    value.substringAfter(':', "").trim().toIntOrNull()?.coerceIn(0, 59) ?: fallback

/** 存档里的分钟可能不在步进点上（例如 08:47），取最近的一格而不是丢弃。 */
private fun nearestMinuteIndex(minute: Int, values: List<Int>): Int {
    var best = 0
    values.forEachIndexed { index, value ->
        if (abs(value - minute) < abs(values[best] - minute)) best = index
    }
    return best
}


@Composable
fun GlassDatePickerDialog(
    onConfirm: (java.time.LocalDate) -> Unit,
    onDismiss: () -> Unit,
    initialDate: java.time.LocalDate = java.time.LocalDate.now(),
    title: String = "选择日期",
    preserveInitial: Boolean = false
) {
    val initialCal = remember(initialDate) {
        Calendar.getInstance().apply { clear(); set(initialDate.year, initialDate.monthValue - 1, initialDate.dayOfMonth) }
    }
    val thisYear = remember { Calendar.getInstance().get(Calendar.YEAR) }
    val years = remember(thisYear, initialDate, preserveInitial) {
        val initialYear = initialCal.get(Calendar.YEAR)
        val first = if (preserveInitial) minOf(thisYear - 2, initialYear) else thisYear - 2
        val last = if (preserveInitial) maxOf(thisYear + 1, initialYear) else thisYear + 1
        (first..last).toList()
    }

    var yearIndex by remember {
        mutableIntStateOf(years.indexOf(initialCal.get(Calendar.YEAR)).coerceAtLeast(0))
    }
    var monthIndex by remember {
        mutableIntStateOf(initialCal.get(Calendar.MONTH))
    }
    var dayIndex by remember {
        mutableIntStateOf(initialCal.get(Calendar.DAY_OF_MONTH) - 1)
    }

    val daysInMonth = remember(yearIndex, monthIndex) {
        Calendar.getInstance().apply {
            clear()
            set(years[yearIndex], monthIndex, 1)
        }.getActualMaximum(Calendar.DAY_OF_MONTH)
    }
    if (dayIndex > daysInMonth - 1) dayIndex = daysInMonth - 1

    AppDialog(usePortal = true, onDismissRequest = onDismiss,
        title = { Text(title) },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        confirmButton = { TextButton(onClick = { onConfirm(java.time.LocalDate.of(years[yearIndex], monthIndex + 1, dayIndex + 1)) }) { Text("确定") } },
        text = {
        Box(modifier = Modifier.fillMaxWidth()) {
            // 中心行 systemFill 胶囊指示条
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF787880).copy(alpha = 0.12f))
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassWheelColumn(
                    items = years.map { "${it}年" },
                    selectedIndex = yearIndex,
                    onSelect = { yearIndex = it },
                    modifier = Modifier.weight(1.1f)
                )
                GlassWheelColumn(
                    items = (1..12).map { "${it}月" },
                    selectedIndex = monthIndex,
                    onSelect = { monthIndex = it },
                    modifier = Modifier.weight(0.9f)
                )
                Box(modifier = Modifier.weight(0.9f)) {
                    key(daysInMonth) {
                        GlassWheelColumn(
                            items = (1..daysInMonth).map { "${it}日" },
                            selectedIndex = dayIndex.coerceIn(0, daysInMonth - 1),
                            onSelect = { dayIndex = it },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    })
}

@Composable
fun GlassTimeRangePickerDialog(
    initialStart: String,
    initialEnd: String,
    onConfirm: (start: String, end: String) -> Unit,
    onDismiss: () -> Unit,
    title: String = "编辑节次时间",
    minuteStep: Int = 5,
    preserveInitial: Boolean = false,
    onClear: (() -> Unit)? = null
) {
    val hourLabels = remember { (0..23).map { "%02d".format(it) } }
    val step = minuteStep.coerceIn(1, 30)
    val minuteValues = remember(step, initialStart, initialEnd, preserveInitial) {
        val values = (0 until 60 step step).toList()
        if (preserveInitial) (values + parseMinute(initialStart, 0) + parseMinute(initialEnd, 45)).distinct().sorted() else values
    }
    val minuteLabels = remember(minuteValues) { minuteValues.map { "%02d".format(it) } }

    var startHour by remember { mutableIntStateOf(parseHour(initialStart, 8)) }
    var startMinute by remember {
        mutableIntStateOf(nearestMinuteIndex(parseMinute(initialStart, 0), minuteValues))
    }
    var endHour by remember { mutableIntStateOf(parseHour(initialEnd, 8)) }
    var endMinute by remember {
        mutableIntStateOf(nearestMinuteIndex(parseMinute(initialEnd, 45), minuteValues))
    }

    AppDialog(usePortal = true, onDismissRequest = onDismiss,
        title = { Text(title) },
        dismissButton = { Row { if (onClear != null) TextButton(onClick = onClear) { Text("清除") }; TextButton(onClick = onDismiss) { Text("取消") } } },
        confirmButton = { TextButton(onClick = { onConfirm("%02d:%02d".format(startHour, minuteValues[startMinute]), "%02d:%02d".format(endHour, minuteValues[endMinute])) }) { Text("确定") } },
        text = {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            listOf(
                Triple("开始", startHour to startMinute, true),
                Triple("结束", endHour to endMinute, false)
            ).forEach { (label, value, isStart) ->
                Box(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
                        // 中心行 systemFill 胶囊指示条
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .align(Alignment.Center)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF787880).copy(alpha = 0.12f))
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GlassWheelColumn(
                                items = hourLabels,
                                selectedIndex = value.first,
                                onSelect = { if (isStart) startHour = it else endHour = it },
                                modifier = Modifier.weight(1f),
                                visibleCount = 3
                            )
                            Text(
                                text = ":",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            GlassWheelColumn(
                                items = minuteLabels,
                                selectedIndex = value.second,
                                onSelect = { if (isStart) startMinute = it else endMinute = it },
                                modifier = Modifier.weight(1f),
                                visibleCount = 3
                            )
                        }
                    }
                }
            }
        }
    })
}

@Composable
fun GlassOptionWheelDialog(
    options: List<String>,
    selectedIndex: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
    title: String = "请选择"
) {
    if (options.isEmpty()) return
    var index by remember {
        mutableIntStateOf(selectedIndex.coerceIn(0, options.lastIndex))
    }

    AppDialog(usePortal = true, onDismissRequest = onDismiss,
        title = { Text(title) },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        confirmButton = { TextButton(onClick = { onConfirm(index) }) { Text("确定") } },
        text = {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF787880).copy(alpha = 0.12f))
            )
            GlassWheelColumn(
                items = options,
                selectedIndex = index,
                onSelect = { index = it },
                modifier = Modifier.fillMaxWidth()
            )
        }
    })
}
