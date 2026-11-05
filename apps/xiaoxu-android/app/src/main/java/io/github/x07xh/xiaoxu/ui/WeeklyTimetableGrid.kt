package io.github.x07xh.xiaoxu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
 */
@Composable
internal fun WeeklyTimetableGrid(
    lessons: List<VerifiedLesson>,
    onLessonClick: (VerifiedLesson) -> Unit
) {
    val all = remember(lessons) {
        lessons.sortedWith(compareBy({ it.weekday }, { it.startSection }, { it.title }))
    }
    val maxSection = (all.maxOfOrNull { it.endSection } ?: 10).coerceIn(10, 24)
    val days = listOf("一", "二", "三", "四", "五", "六", "日")
    val dayWidth = 88.dp
    val labelWidth = 40.dp
    val rowHeight = 52.dp
    var overlapChoices by remember { mutableStateOf<List<VerifiedLesson>>(emptyList()) }
    val surface = MaterialTheme.colorScheme.surface
    val lowSurface = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
    val primary = MaterialTheme.colorScheme.primaryContainer
    val secondary = MaterialTheme.colorScheme.secondaryContainer
    val tertiary = MaterialTheme.colorScheme.tertiaryContainer
    val primaryText = MaterialTheme.colorScheme.onPrimaryContainer
    val secondaryText = MaterialTheme.colorScheme.onSecondaryContainer
    val tertiaryText = MaterialTheme.colorScheme.onTertiaryContainer

    Column(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
            Box(
                Modifier.width(labelWidth).height(40.dp)
                    .background(lowSurface, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("节", style = MaterialTheme.typography.labelMedium)
            }
            days.forEach { day ->
                Box(
                    Modifier.width(dayWidth).height(40.dp)
                        .background(lowSurface, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("周" + day, style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold)
                }
            }
        }
        for (section in 1..maxSection) {
            Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                Box(
                    Modifier.width(labelWidth).height(rowHeight)
                        .background(lowSurface, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(section.toString(), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                for (day in 1..7) {
                    val available = all.filter {
                        it.weekday == day && section in it.startSection..it.endSection
                    }
                    val first = available.firstOrNull()
                    val toneIndex = if (first == null) 0 else
                        Math.floorMod(first.title.hashCode(), 3)
                    val shade: Color = when (toneIndex) {
                        0 -> primary
                        1 -> secondary
                        else -> tertiary
                    }
                    val shadeText: Color = when (toneIndex) {
                        0 -> primaryText
                        1 -> secondaryText
                        else -> tertiaryText
                    }
                    Box(
                        modifier = Modifier.width(dayWidth).height(rowHeight)
                            .clip(RoundedCornerShape(7.dp))
                            .background(if (first == null) surface else shade)
                            .then(
                                if (first == null) Modifier
                                else Modifier.clickable {
                                    if (available.size == 1) onLessonClick(first)
                                    else overlapChoices = available
                                }
                            )
                            .padding(horizontal = 5.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (first != null) {
                            Text(
                                text = when {
                                    available.size > 1 -> "${available.size} 门课，点击核对"
                                    section == first.startSection -> first.title
                                    else -> "·"
                                },
                                maxLines = if (section == first.startSection) 2 else 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall,
                                color = shadeText,
                                fontWeight = if (section == first.startSection)
                                    FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(6.dp))
    Text(
        text = "每行是一个节次，色块表示本周有课；跨节课程连续显示。横向滑动查看周六、周日，点击课程查看详情。",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
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
                TextButton(onClick = { overlapChoices = emptyList() }) {
                    Text("关闭")
                }
            }
        )
    }
}
