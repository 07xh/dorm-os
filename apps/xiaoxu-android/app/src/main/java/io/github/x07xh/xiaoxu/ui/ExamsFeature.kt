package io.github.x07xh.xiaoxu.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import io.github.x07xh.xiaoxu.data.AcademicExamRow

@Composable
internal fun ColumnScope.ExamsFeature(data: AppWorkspaceState, actions: AppPageActions) {
    val cloud = data.cloud
    val snapshot = cloud.exams.takeIf { cloud.examsOwner == cloud.ownerId }
    AcademicSnapshotList<AcademicExamRow>(
        title = "考试", owner = cloud.ownerId.takeIf { cloud.authenticated },
        snapshot = snapshot?.let { AcademicListSnapshot(it.status, it.provenance, it.capturedAt, it.rows) },
        busy = cloud.examsBusy, error = cloud.examsError,
        onConnect = { actions.navigate(AppRoute.CONNECTIONS) }, onRead = actions.readExams,
        termOf = { it.term }, keyOf = { it.id },
        sort = { rows -> rows.sortedWith(compareBy({ it.date }, { it.startTime }, { it.id })) }
    ) { row ->
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.item)) {
            ExamItemRow(ExamItemUi(courseName = row.title,
                examTime = listOf(row.date, row.startTime, row.endTime).filter { it.isNotBlank() }.joinToString(" "),
                location = row.location, seatNumber = "", examName = "考试",
                teacher = row.teacher, sourceId = row.id))
            if (row.note.isNotBlank()) AppNotice(row.note)
        }
    }
}
