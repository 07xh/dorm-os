package io.github.x07xh.xiaoxu.ui

// SPDX-License-Identifier: GPL-3.0-only
// Source: znjhahaha/zhengfang-apk @ bb3b80a6d64e4c2057c0544bb417696ceaa2a9ed, ui/screen/ScheduleScreen.kt
// Copyright upstream authors and contributors. Port of actual timetable layout,
// overlap grouping, course card rendering, text measurement and week display.
// Adaptations: package, shared theme, existing dialog/list and icon APIs.

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import kotlin.math.roundToInt

private val SchedulePeriodHeight = 84.dp

data class ScheduleCourseUi(
    val name: String,
    val teacher: String,
    val location: String,
    val day: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val weeks: String,
    val color: Color,
    val isCustom: Boolean = false,
    val customId: String = "",
    val sourceId: String = "",
    val id: String = if (isCustom) "custom:$customId" else ScheduleIdentity.network(
        sourceId, name, teacher, day, startPeriod, endPeriod, weeks, location
    ),
    val hasConflict: Boolean = false,
    val isCurrent: Boolean = false,
    val isNext: Boolean = false,
    val details: List<ScheduleDetail> = emptyList(),
    val occurrenceDate: String? = null,
    val originalOccurrenceDate: String? = null
) {
    fun record() = ScheduleCourseRecord(id, name, teacher, location, day, startPeriod, endPeriod, weeks, isCustom, details)
}

data class PeriodTimeUi(val period: Int, val startTime: String, val endTime: String)

private fun courseNameStyle(base: TextStyle, duration: Int): TextStyle = base.copy(
    fontWeight = FontWeight.Bold,
    fontSize = when (duration) { 1 -> 10.5.sp; 2 -> 11.sp; else -> 11.5.sp },
    lineHeight = when (duration) { 1 -> 12.sp; 2 -> 12.5.sp; else -> 13.sp },
    letterSpacing = (-0.2).sp
)

private fun courseLocationStyle(base: TextStyle, duration: Int): TextStyle = base.copy(
    fontWeight = FontWeight.Normal,
    fontSize = if (duration <= 2) 9.sp else 9.5.sp,
    lineHeight = if (duration <= 2) 10.5.sp else 11.sp
)

private class GridScrollAnchor {
    var period = 0f
    var top = 0f
    var maximum = 0
    var offset = 0
}

@Composable
internal fun rememberFullCoursePeriodHeight(courses: List<ScheduleCourseUi>, columnWidth: Int, minimum: Dp): Dp {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer(cacheSize = 128)
    val base = androidx.compose.material3.LocalTextStyle.current.merge(MaterialTheme.typography.labelSmall)
    return remember(courses, columnWidth, minimum, density, measurer, base) {
        with(density) {
            val textWidth = (columnWidth - 2 * 1.dp.roundToPx() - 5.dp.roundToPx() - 2.dp.roundToPx() - 2).coerceAtLeast(1)
            var height = minimum.toPx()
            courses.forEach { course ->
                val duration = (course.endPeriod - course.startPeriod + 1).coerceAtLeast(1)
                val name = measurer.measure(course.name, courseNameStyle(base, duration), constraints = Constraints(maxWidth = textWidth)).size.height
                val metadata = courseLocationStyle(base, duration)
                val teacher = measurer.measure(course.teacher.ifBlank { "教师待定" }, metadata, constraints = Constraints(maxWidth = textWidth)).size.height
                val room = measurer.measure("教室\nA1208", metadata).size.height
                // Keep the optional status lane allocated, including on minute ticks.
                val required = name + teacher + room + 25.dp.toPx()
                height = maxOf(height, kotlin.math.ceil(required / duration))
            }
            kotlin.math.ceil(height).toDp()
        }
    }
}

private fun ScheduleCourseUi.hasCardStatus() = hasConflict || isCurrent || isNext ||
    !ScheduleWeeks.parse(weeks).valid

@Composable
internal fun TimetableBackground(
    periodCount: Int,
    periodHeight: Dp,
    dayCount: Int = 7
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // 去斑马纹：透明列 + 极淡分隔线，壁纸从网格间透出
        Row(modifier = Modifier.fillMaxSize()) {
            repeat(dayCount) { dayIndex ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    if (dayIndex < dayCount - 1) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .width(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f))
                        )
                    }
                }
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            repeat(periodCount) { rowIndex ->
                Box(
                    modifier = Modifier
                        .height(periodHeight)
                        .fillMaxWidth()
                ) {
                    if (rowIndex < periodCount - 1) {
                        HorizontalDivider(
                            modifier = Modifier.align(Alignment.BottomCenter),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f),
                            thickness = 0.5.dp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TimetableLayout(
    courses: List<ScheduleCourseUi>,
    periodCount: Int = 12,
    periodHeight: Dp = SchedulePeriodHeight,
    modifier: Modifier = Modifier,
    dayCount: Int = 7,
    /** ISO days in display order; column 0 is the first day of the teaching week. */
    dayOrder: List<Int> = (1..7).toList(),
    periodTimes: List<PeriodTimeUi> = emptyList(),
    onCourseClick: (ScheduleCourseUi) -> Unit,
    onCourseLongClick: (ScheduleCourseUi) -> Unit = {}
) {
    val entries = remember(courses, dayOrder) { timetableEntries(courses.filter { it.day in dayOrder }) }
    var selectedGroup by remember { mutableStateOf<List<ScheduleCourseUi>?>(null) }
    selectedGroup?.let { group ->
        AlertDialog(
            onDismissRequest = { selectedGroup = null },
            title = { Text("重叠课程") },
            text = {
                Column(Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                    group.forEach { course ->
                        SystemListItem(title = course.name, subtitle = "${course.teacher} · ${course.location}",
                            onClick = { selectedGroup = null; onCourseClick(course) })
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selectedGroup = null }) { Text("关闭") } }
        )
    }
    Layout(
        modifier = modifier.fillMaxSize(),
        content = {
            entries.forEach { entry ->
                androidx.compose.runtime.key(entry.display.id + entry.display.occurrenceDate.orEmpty() + entry.display.startPeriod) { CourseCard(course = entry.display, onClick = {
                    if (entry.courses.size == 1) onCourseClick(entry.courses.first()) else selectedGroup = entry.courses
                }, onLongClick = {
                    if (entry.courses.size == 1) onCourseLongClick(entry.courses.first()) else selectedGroup = entry.courses
                }) }
            }
        }
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val columnWidth = width / dayCount.toFloat()
        val cardInset = 1.dp.roundToPx()
        val pxPerPeriod = periodHeight.toPx()

        val placeables = measurables.mapIndexed { index, measurable ->
            val course = entries[index].display
            val duration = course.endPeriod - course.startPeriod + 1
            val height = (duration * pxPerPeriod).roundToInt()
            val cardWidth = (columnWidth.roundToInt() - cardInset * 2).coerceAtLeast(1)
            val cardHeight = (height - cardInset * 2).coerceAtLeast(1)

            measurable.measure(
                constraints.copy(
                    minWidth = cardWidth,
                    maxWidth = cardWidth,
                    minHeight = cardHeight,
                    maxHeight = cardHeight
                )
            )
        }

        layout(width, (periodCount * pxPerPeriod).roundToInt()) {
            placeables.forEachIndexed { index, placeable ->
                val course = entries[index].display
                val dayIndex = dayOrder.indexOf(course.day).coerceIn(0, dayCount - 1)
                val startPeriodIndex = (course.startPeriod - 1).coerceIn(0, periodCount - 1)

                val x = (dayIndex * columnWidth).roundToInt() + cardInset
                val y = (startPeriodIndex * pxPerPeriod).roundToInt() + cardInset

                placeable.place(x, y)
            }
        }
    }
}

private data class TimetableEntry(val display: ScheduleCourseUi, val courses: List<ScheduleCourseUi>)

private fun timetableEntries(courses: List<ScheduleCourseUi>): List<TimetableEntry> {
    val byId = courses.associateBy { it.id }
    return scheduleOverlapGroups(courses.map { it.record() }).map { group ->
        val originals = group.courses.map { byId.getValue(it.id) }
        val first = originals.first()
        val display = if (originals.size == 1) first else first.copy(
            id = "overlap:" + ScheduleIdentity.digest(originals.joinToString { it.id }),
            name = first.name, location = "${originals.size} 门重叠 · 点按查看",
            startPeriod = group.start, endPeriod = group.end, hasConflict = true,
            isCurrent = originals.any { it.isCurrent })
        TimetableEntry(display, originals)
    }
}

@Composable
fun CourseCard(course: ScheduleCourseUi, onLongClick: () -> Unit = {}, onClick: () -> Unit) {
    val darkCard = isSystemInDarkTheme()
    val focusRequester = remember { FocusRequester() }
    val focusRegistry = LocalScheduleFocus.current
    var cardBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val unknownWeeks = remember(course.weeks) { !ScheduleWeeks.parse(course.weeks).valid }
    DisposableEffect(course.id, focusRegistry) {
        focusRegistry?.register(course.id, focusRequester)
        onDispose { focusRegistry?.remove(course.id, focusRequester) }
    }
    val duration = (course.endPeriod - course.startPeriod + 1).coerceAtLeast(1)
    // 彩色半透玻璃 tile：加深填充保证壁纸上可读，边缘细亮线似透镜
    val containerColor = scheduleCardColor(course.color, if (course.isCurrent) 0.20f else 0.10f)
    val accentColor = course.color.copy(alpha = 0.85f)

    val displayLocation = ScheduleLocation.compact(course.location)

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && android.animation.ValueAnimator.areAnimatorsEnabled()) 0.97f else 1f,
        animationSpec = androidx.compose.animation.core.tween(140),
        label = "courseCardScale"
    )

    Surface(
        modifier = Modifier
            .testTag("schedule-course-${course.id}")
            .focusRequester(focusRequester)
            .onGloballyPositioned {
                cardBounds = it.boundsInWindow()
                focusRegistry?.place(course.id, it.boundsInWindow())
            }
            .semantics {
                stateDescription = listOfNotNull(if (course.hasConflict) "时间冲突" else null,
                    if (course.isCurrent) "正在上课" else null, if (course.isCustom) "自定义课程" else null,
                    if (unknownWeeks) "周次待核对" else null).joinToString("，")
            }
            .scale(scale)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onLongClickLabel = "课程快捷操作",
                onLongClick = onLongClick,
                onClick = {
                    // A course can also exist in the pager's adjacent week. The clicked copy
                    // owns the opening origin and the focus returned after dismissal.
                    focusRegistry?.register(course.id, focusRequester)
                    cardBounds?.let { focusRegistry?.place(course.id, it) }
                    onClick()
                }
            ),
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = scheduleCardBorder(course.color, course.isCurrent || course.isNext)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 顶部玻璃高光渐变：模拟光源照射的反射
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(with(LocalDensity.current) { (duration * 20).dp })
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = if (darkCard) 0.08f else 0.35f),
                                Color.White.copy(alpha = if (darkCard) 0.02f else 0.05f),
                                Color.Transparent
                            )
                        )
                    )
            )
            // 左侧彩色指示条：课程颜色标识
            Box(
                modifier = Modifier
                    .width((3f + (1f - scale) * 50f).dp)
                    .fillMaxHeight()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                accentColor,
                                accentColor.copy(alpha = 0.3f)
                            )
                        )
                    )
            )
            // 内容区域
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 5.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = course.name,
                    modifier = Modifier.fillMaxWidth().testTag("schedule-name-${course.id}"),
                    style = courseNameStyle(MaterialTheme.typography.labelSmall, duration),
                    color = MaterialTheme.colorScheme.onSurface,
                    softWrap = true,
                )

                Text(course.teacher.ifBlank { "教师待定" },
                    Modifier.fillMaxWidth().testTag("schedule-teacher-${course.id}"),
                    style = courseLocationStyle(MaterialTheme.typography.labelSmall, duration),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, softWrap = true)

                if (displayLocation.isNotBlank()) {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
                    val baseStyle = courseLocationStyle(MaterialTheme.typography.labelSmall, duration)
                    val room = ScheduleLocation.room(course.location).orEmpty()
                    val density = LocalDensity.current
                    // Leave a pixel rounding margin, and remeasure after scaling: modern
                    // Android font scaling is nonlinear, so a width ratio alone can wrap the room.
                    val availableWidth = (constraints.maxWidth - 2).coerceAtLeast(1).toFloat()
                    val locationStyle = remember(room, baseStyle, availableWidth, density) {
                        val roomWidth = measurer.measure(room, baseStyle, softWrap = false).size.width
                        var fitted = baseStyle.copy(fontSize = baseStyle.fontSize * minOf(1f, availableWidth / roomWidth.coerceAtLeast(1)))
                        repeat(8) {
                            if (measurer.measure(room, fitted, softWrap = false).size.width <= availableWidth) return@remember fitted
                            fitted = fitted.copy(fontSize = fitted.fontSize * 0.92f)
                        }
                        fitted
                    }
                    val label = remember(displayLocation, availableWidth, locationStyle, density) {
                        ScheduleLocation.fit(course.location, availableWidth, 2) {
                            measurer.measure(it, locationStyle, softWrap = false).size.width.toFloat()
                        }
                    }
                    Text(
                        text = label,
                        modifier = Modifier.fillMaxWidth().testTag("schedule-location-${course.id}"),
                        style = locationStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = true
                    )
                    }
                }
                // A status symbol must not reserve a column beside every line of a long address.
                if (duration > 1 && course.hasCardStatus()) Icon(
                    imageVector = if (course.hasConflict || unknownWeeks) Icons.Outlined.Warning else Icons.Outlined.Schedule,
                    contentDescription = when { unknownWeeks -> "周次待核对"; course.isCurrent -> "正在上课"; course.isNext -> "下一节"; else -> "重叠课程" },
                    modifier = Modifier.size(10.dp), tint = if (unknownWeeks) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        }
    }
}

internal fun isInWeek(weeks: String?, week: Int): Boolean {
    return ScheduleWeeks.parse(weeks).visibleIn(week)
}
