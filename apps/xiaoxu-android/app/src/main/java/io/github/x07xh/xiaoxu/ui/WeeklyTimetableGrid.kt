package io.github.x07xh.xiaoxu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.x07xh.xiaoxu.data.VerifiedLesson

/**
 * Actual native seven-day timetable grid, using only the owner's saved lessons.
 *
 * It is rendered for one explicitly selected academic week. Missing week data
 * never creates synthetic lessons, and sections have no guessed clock times.
 * Cells for multi-section lessons repeat the same tinted block; tapping any
 * section opens the existing course detail sheet.
 *
 * [selectedWeekday] / [onSelectWeekday] are optional. When a caller needs the
 * header row to drive a side pane (tablet layout) it passes them: each day
 * header then becomes a 48dp selectable tab with a visible selected state.
 * Callers that leave them null keep the header as a passive column caption, so
 * the phone layout is unchanged. Course rows are never filtered or synthesised
 * here.
 */
@Composable
internal fun WeeklyTimetableGrid(
    lessons: List<VerifiedLesson>,
    selectedWeekday: Int? = null,
    onSelectWeekday: ((Int) -> Unit)? = null,
    onLessonClick: (VerifiedLesson) -> Unit
) {
    val all = remember(lessons) {
        lessons.sortedWith(compareBy({ it.weekday }, { it.startSection }, { it.title }))
    }
    val maxSection = (all.maxOfOrNull { it.endSection } ?: 12).coerceIn(12, 24)
    val days = listOf("一", "二", "三", "四", "五", "六", "日")
    var overlapChoices by remember { mutableStateOf<List<VerifiedLesson>>(emptyList()) }

    // Reuse the existing native weekly-grid model; adapt only the presentation
    // to the phone viewport. All seven days fit when there is sufficient room,
    // while very narrow screens may still pan horizontally.
    val dark = isSystemInDarkTheme()
    val surface = MaterialTheme.colorScheme.surface
    val lowSurface = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f)
    val shades = if (dark) listOf(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer
    ) else listOf(
        Color(0xFFDAEAFF), Color(0xFFDAF5E8), Color(0xFFFFEDD7),
        Color(0xFFF9E1EF), Color(0xFFE7E3FF)
    )
    val inks = if (dark) listOf(
        MaterialTheme.colorScheme.onPrimaryContainer,
        MaterialTheme.colorScheme.onSecondaryContainer,
        MaterialTheme.colorScheme.onTertiaryContainer
    ) else listOf(
        Color(0xFF153F80), Color(0xFF165C43), Color(0xFF855018),
        Color(0xFF883655), Color(0xFF514084)
    )

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact = maxWidth < 620.dp
        val labelWidth = if (compact) 32.dp else 40.dp
        // Seven columns always fit the available phone width; no sideways swipe
        // is required to inspect Saturday/Sunday on a normal handset.
        val dayWidth = if (compact) (maxWidth - labelWidth - 8.dp) / 7 else 88.dp
        val rowHeight = if (compact) 54.dp else 58.dp

        Column(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Row(
                modifier = if (onSelectWeekday == null) Modifier else Modifier.selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Box(
                    Modifier.width(labelWidth).height(48.dp)
                        .background(lowSurface, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("节", style = MaterialTheme.typography.labelMedium)
                }
                days.forEachIndexed { index, day ->
                    val weekday = index + 1
                    val isSelected = selectedWeekday == weekday
                    Box(
                        modifier = Modifier
                            .width(dayWidth)
                            .height(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary else lowSurface
                            )
                            .then(
                                if (onSelectWeekday == null) Modifier
                                else Modifier.selectable(
                                    selected = isSelected,
                                    role = Role.Tab,
                                    onClick = { onSelectWeekday(weekday) }
                                )
                            )
                            .semantics {
                                contentDescription = "周$day" +
                                    (if (isSelected) "，当前选中" else "") +
                                    "，${all.count { it.weekday == weekday }} 个课块" +
                                    (if (onSelectWeekday == null) "" else "，查看当天课程")
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "周" + day,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                Column(Modifier.width(labelWidth)) {
                    for (section in 1..maxSection) {
                        Box(
                            Modifier.fillMaxWidth().height(rowHeight)
                                .padding(1.dp)
                                .background(lowSurface, RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(section.toString(), style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                for (day in 1..7) {
                    Column(Modifier.width(dayWidth)) {
                        var section = 1
                        while (section <= maxSection) {
                            val available = all.filter {
                                it.weekday == day && section in it.startSection..it.endSection
                            }
                            // Merge only consecutive sections with the EXACT
                            // same course set. Split again at overlaps/boundaries
                            // so the drawing never invents a longer class.
                            var span = 1
                            if (available.isNotEmpty()) {
                                while (section + span <= maxSection) {
                                    val probe = section + span
                                    val next = all.filter {
                                        it.weekday == day && probe in it.startSection..it.endSection
                                    }
                                    if (next != available) break
                                    span++
                                }
                            }
                            // Freeze section metadata before the loop advances; a
                            // click/TalkBack callback must describe this exact block.
                            val blockStart = section
                            val first = available.firstOrNull()
                            val shadeIndex = if (first == null) 0 else
                                Math.floorMod(first.title.hashCode(), shades.size)
                            val shade = shades[shadeIndex]
                            val shadeText = inks[shadeIndex]
                            Box(
                                modifier = Modifier.fillMaxWidth().height(rowHeight * span)
                                    .padding(1.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(if (first == null) surface else shade)
                                    .then(if (first == null) Modifier else Modifier
                                        .clickable {
                                            if (available.size == 1) onLessonClick(first)
                                            else overlapChoices = available
                                        }
                                        .semantics {
                                            role = Role.Button
                                            contentDescription = first.title +
                                                "，星期" + days[day - 1] +
                                                "，第" + blockStart + "至" + (blockStart + span - 1) + "节" +
                                                (if (first.location.isBlank()) "" else "，" + first.location) +
                                                (if (available.size > 1) "，同时段共" + available.size + "门课" else "")
                                        })
                                    .padding(horizontal = 4.dp, vertical = 3.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (first != null) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(if (span >= 2) 4.dp else 0.dp)
                                    ) {
                                        if (span >= 2 && first.location.isNotBlank()) {
                                            Text(first.location, color = shadeText.copy(alpha = 0.82f),
                                                style = MaterialTheme.typography.labelSmall,
                                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        }
                                        Text(
                                            text = first.title,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = shadeText,
                                            maxLines = if (span >= 2) 4 else 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (span >= 3 && first.teacher.isNotBlank()) {
                                            Text(first.teacher, color = shadeText.copy(alpha = 0.76f),
                                                style = MaterialTheme.typography.labelSmall,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        if (available.size > 1) {
                                            Text("+${available.size - 1} 门", color = shadeText,
                                                style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                            section += span
                        }
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(4.dp))
    if (overlapChoices.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { overlapChoices = emptyList() },
            title = { Text("同一节次有多项课程") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    overlapChoices.forEach { lesson ->
                        TextButton(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                            onClick = {
                                overlapChoices = emptyList()
                                onLessonClick(lesson)
                            }
                        ) {
                            Text(lesson.title + " · 第${lesson.startSection}–${lesson.endSection}节",
                                maxLines = 2)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { overlapChoices = emptyList() }) { Text("关闭") }
            }
        )
    }
}
