package io.github.x07xh.xiaoxu.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.x07xh.xiaoxu.data.NativeSchoolRecord
import java.text.DateFormat
import java.util.Date

/** Existing owner's local course catalog only. No inference from homework/plan
 * status, no invented chapter count and no duplicate school connection. */
internal data class StudyCoursePreview(val id: String, val title: String, val capturedAt: Long)

internal fun latestStudyCoursePreviews(records: List<NativeSchoolRecord>): List<StudyCoursePreview>? {
    val binding = records.singleOrNull {
        it.provider == "chaoxing" && it.kind == "account_binding" &&
            it.fingerprint == "current" && it.sourceHost == "i.chaoxing.com" &&
            it.capturedAt > 0 && it.details.matches(Regex("[a-f0-9]{64}"))
    } ?: return null
    return records.asSequence().filter {
        it.provider == "chaoxing" && it.kind == "course" &&
            it.sourceHost == "mooc2-ans.chaoxing.com" && it.details == "课程列表" &&
            it.capturedAt == binding.capturedAt && it.fingerprint.matches(Regex("[a-f0-9]{64}")) &&
            it.title.isNotBlank()
    }.distinctBy { it.fingerprint }.sortedWith(compareBy({ it.title }, { it.fingerprint }))
        .map { StudyCoursePreview(it.fingerprint, it.title, it.capturedAt) }.toList()
}

@Composable
internal fun ColumnScope.StudyProgressFeature(data: AppWorkspaceState, actions: AppPageActions) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = { actions.connectLocal("chaoxing") }) { Text("读取学习通") }
    }
    AppSnapshotContent(data.nativeRecords) { records ->
        val courses = remember(records) { latestStudyCoursePreviews(records) }
        if (courses == null || courses.isEmpty()) {
            SystemEmptyState("尚无本次读取的课程名单", "请连接学习通读取本人记录；不能据此推断学校没有课程。")
        } else {
            AppNotice("已读取课程名单 · " + DateFormat.getDateTimeInstance().format(Date(courses.first().capturedAt)))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.item)) {
                item(key = "progress-source") {
                    AppNotice("现有只读来源尚未提供课程学习百分比。作业与学习计划完成状态不等于课程进度。")
                }
                items(courses, key = { it.id }) { course ->
                    InsetGroupedSection {
                        InsetGroupedRow(title = course.title, subtitle = "课程进度未读取",
                            showDivider = false)
                    }
                }
            }
        }
    }
}
