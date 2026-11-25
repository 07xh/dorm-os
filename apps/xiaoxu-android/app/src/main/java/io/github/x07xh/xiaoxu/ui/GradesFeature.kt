package io.github.x07xh.xiaoxu.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import io.github.x07xh.xiaoxu.data.AcademicGradeRow

@Composable
internal fun ColumnScope.GradesFeature(data: AppWorkspaceState, actions: AppPageActions) {
    val cloud = data.cloud
    val snapshot = cloud.grades.takeIf { cloud.gradesOwner == cloud.ownerId }
    AcademicSnapshotList<AcademicGradeRow>(
        title = "成绩", owner = cloud.ownerId.takeIf { cloud.authenticated },
        snapshot = snapshot?.let { AcademicListSnapshot(it.status, it.provenance, it.capturedAt, it.rows) },
        busy = cloud.gradesBusy, error = cloud.gradesError,
        onConnect = { actions.navigate(AppRoute.CONNECTIONS) }, onRead = actions.readGrades,
        termOf = { it.term }, keyOf = { it.id }
    ) { row ->
        GradeItemRow(GradeItemUi(courseName = row.title,
            grade = row.score.ifBlank { "未记录" }, credits = row.credits?.toString().orEmpty(),
            gpa = row.gradePoint?.toString().orEmpty(), courseType = "",
            term = row.term, jxbId = row.id, detail = row.note))
    }
}
