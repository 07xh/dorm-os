package io.github.x07xh.xiaoxu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.x07xh.xiaoxu.data.VerifiedLesson

/**
 * Settings-owned timetable import confirmation. Only displays locally parsed meetings.
 * The existing SAF picker, parser, and commit path stay in MainActivity.
 */
@Composable
internal fun TimetableImportPreviewSheet(
    fileName: String,
    lessons: List<VerifiedLesson>,
    oldCount: Int,
    hasSemesterSettings: Boolean,
    saving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val decreased = oldCount > 0 && lessons.size < oldCount
    var confirmedReduced by remember(fileName, oldCount, lessons) { mutableStateOf(false) }
    val sorted = remember(lessons) {
        lessons.sortedWith(compareBy({ it.weekday }, { it.startSection }, { it.title }))
    }

    XiaoxuSheet(
        title = if (decreased) "核对课表变更" else "导入课表",
        onDismiss = { if (!saving) onDismiss() }
    ) {
        XiaoxuCard {
            Text(
                text = fileName,
                style = MaterialTheme.typography.bodyMedium,
                color = XiaoxuPalette.Ink2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = lessons.size.toString() + " 个课块",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = XiaoxuPalette.Ink
            )
            Text(
                text = if (oldCount == 0) "将保存为本机课表"
                    else "原有 " + oldCount + " 个课块 · 保存后替换",
                style = MaterialTheme.typography.bodySmall,
                color = XiaoxuPalette.Ink2
            )
            Text(
                text = if (hasSemesterSettings) "包含学期与节次设置，将一并恢复"
                    else "原有学期及节次设置保持不变",
                style = MaterialTheme.typography.bodySmall,
                color = XiaoxuPalette.Ink2
            )
        }

        if (decreased) {
            XiaoxuCard(container = XiaoxuPalette.WarningSoft) {
                Text(
                    text = if (confirmedReduced) "请确认已核对课程，再替换原课表"
                        else "新文件课块更少，请核对是否遗漏课程",
                    style = MaterialTheme.typography.bodyMedium,
                    color = XiaoxuPalette.Warning
                )
            }
        }
        Text(
            "课程预览",
            style = MaterialTheme.typography.titleMedium,
            color = XiaoxuPalette.Ink,
            fontWeight = FontWeight.SemiBold
        )
        // Bound the list independently so 25+ meetings never turn into a screen-sized text dialog.
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(sorted) { index, lesson ->
                XiaoxuCard(contentPadding = 12.dp) {
                    Text(
                        (index + 1).toString() + ". " + lesson.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = XiaoxuPalette.Ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    val weekday = listOf("一", "二", "三", "四", "五", "六", "日")
                        .getOrElse(lesson.weekday - 1) { "?" }
                    Text(
                        "周" + weekday + " · 第 " + lesson.startSection + "–" +
                            lesson.endSection + " 节 · " + formatWeeks(lesson.weeks),
                        style = MaterialTheme.typography.bodySmall,
                        color = XiaoxuPalette.Ink2
                    )
                    val place = lesson.location.ifBlank { "地点未提供" }
                    val teacher = lesson.teacher.ifBlank { "教师未提供" }
                    Text(
                        place + " · " + teacher,
                        style = MaterialTheme.typography.bodySmall,
                        color = XiaoxuPalette.Ink3,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Text(
            "仅保存到本机，不代表教务系统实时同步。",
            style = MaterialTheme.typography.bodySmall,
            color = XiaoxuPalette.Ink3
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !saving,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
            ) { Text("保留旧课表") }
            Button(
                onClick = {
                    if (decreased && !confirmedReduced) confirmedReduced = true
                    else onConfirm()
                },
                enabled = !saving,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
            ) {
                Text(
                    if (saving) "正在保存"
                    else if (decreased && !confirmedReduced) "继续核对"
                    else if (decreased) "已核对，替换"
                    else "确认保存"
                )
            }
        }
    }
}
