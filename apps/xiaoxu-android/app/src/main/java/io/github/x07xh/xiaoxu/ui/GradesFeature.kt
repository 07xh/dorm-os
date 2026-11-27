package io.github.x07xh.xiaoxu.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch

@Composable
internal fun AcademicRecordsFeature(data: AppWorkspaceState, actions: AppPageActions, initialTab: Int, onBack: () -> Unit) {
    val cloud = data.cloud
    val owner = cloud.ownerId.takeIf { cloud.authenticated }
    val scope = rememberCoroutineScope()
    key(owner, initialTab) {
        var tab by rememberSaveable { mutableStateOf(initialTab) }
        var gradeTerm by rememberSaveable { mutableStateOf("") }
        var examTerm by rememberSaveable { mutableStateOf("") }
        val gradeSnapshot = cloud.grades.takeIf { owner != null && cloud.gradesOwner == owner }
        val examSnapshot = cloud.exams.takeIf { owner != null && cloud.examsOwner == owner }
        val gradeRows = gradeSnapshot?.takeUnless { it.status == "not_connected" }?.rows.orEmpty()
        val examRows = examSnapshot?.takeUnless { it.status == "not_connected" }?.rows.orEmpty()
        val gradeTerms = remember(gradeRows) { gradeRows.map { it.term }.filter { it.isNotBlank() }.distinct().sortedDescending() }
        val examTerms = remember(examRows) { examRows.map { it.term }.filter { it.isNotBlank() }.distinct().sortedDescending() }
        val currentTerm = if (tab == 2) examTerm.takeIf { it in examTerms }.orEmpty()
            else gradeTerm.takeIf { it in gradeTerms }.orEmpty()
        val grades = remember(gradeRows) { gradeRows.map { row ->
            GradeItemUi(courseName = row.title, grade = row.score.ifBlank { "未记录" },
                credits = row.credits?.toString().orEmpty(), gpa = row.gradePoint?.toString().orEmpty(),
                courseType = "", term = row.term, jxbId = row.id, detail = row.note)
        } }
        val exams = remember(examRows, examTerm) { examRows.filter { examTerm !in examTerms || it.term == examTerm }
            .sortedWith(compareBy({ it.date }, { it.startTime }, { it.id })).map { row ->
                ExamItemUi(courseName = row.title, examTime = listOf(row.date, row.startTime, row.endTime)
                    .filter { it.isNotBlank() }.joinToString(" "), location = row.location, seatNumber = "",
                    examName = "考试", teacher = row.teacher, sourceId = row.id,
                    recordId = row.term + ":" + row.id)
            } }
        fun snapshotLabel(status: String?, provenance: String?, capturedAt: String?): String = when {
            owner == null -> "未连接云端账户"
            status == null || status == "not_connected" -> "尚无已保存快照"
            else -> listOfNotNull(if (provenance == "trusted_readonly") "教务可信只读快照" else "个人导入快照",
                "非实时", if (status == "stale") "已过期" else null, capturedAt).joinToString(" · ")
        }
        val gradeLabel = snapshotLabel(gradeSnapshot?.status, gradeSnapshot?.provenance, gradeSnapshot?.capturedAt)
        val examLabel = snapshotLabel(examSnapshot?.status, examSnapshot?.provenance, examSnapshot?.capturedAt)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = AppSpacing.workspaceMaxWidth).fillMaxSize()) {
                GradesScreen(currentTab = tab, onTabChange = { tab = it },
                    semesterGrades = grades.filter { currentTerm.isBlank() || it.term == currentTerm },
                    semesters = listOf("") + if (tab == 2) examTerms else gradeTerms, currentSemester = currentTerm,
                    onSemesterChange = { if (tab == 2) examTerm = it else gradeTerm = it },
                    semesterIsLoading = cloud.gradesBusy, overallGrades = grades,
                    overallStats = remember(grades) { snapshotStats(grades) },
                    overallIsLoading = cloud.gradesBusy, examList = exams, examIsLoading = cloud.examsBusy,
                    onRefresh = { if (owner == null) actions.navigate(AppRoute.ACCOUNT) else scope.launch {
                        if (tab == 2) actions.readExams() else actions.readGrades()
                    } },
                    onBack = onBack, syncStatus = if (tab == 2) examLabel else gradeLabel,
                    semesterError = cloud.gradesError.orEmpty(), overallError = cloud.gradesError.orEmpty(),
                    examError = cloud.examsError.orEmpty(), onConnect = { actions.navigate(AppRoute.CONNECTIONS) })
            }
        }
    }
}

internal fun recordedCredits(grades: List<GradeItemUi>): String {
    if (grades.isEmpty() || grades.any { it.credits.toDoubleOrNull()?.isFinite() != true }) return "—"
    return java.lang.String.format(java.util.Locale.ROOT, "%.1f", grades.sumOf { it.credits.toDouble() })
}

internal fun semesterAverageGpa(grades: List<GradeItemUi>): String {
    if (grades.isEmpty() || grades.any { it.credits.toDoubleOrNull()?.let { c -> c.isFinite() && c > 0 } != true ||
            it.gpa.toDoubleOrNull()?.let { g -> g.isFinite() && g >= 0 } != true }) return "—"
    val credits = grades.sumOf { it.credits.toDouble() }
    return java.lang.String.format(java.util.Locale.ROOT, "%.2f", grades.sumOf { it.credits.toDouble() * it.gpa.toDouble() } / credits)
}


private fun snapshotStats(grades: List<GradeItemUi>): OverallStatsUi {
    val distribution = grades.mapNotNull { row ->
        val score = row.grade.trim().toDoubleOrNull()?.takeIf { it.isFinite() }
        when {
            row.grade == "优秀" || score != null && score >= 90 -> "优"
            row.grade == "良好" || score != null && score >= 80 -> "良"
            row.grade == "中等" || score != null && score >= 70 -> "中"
            row.grade == "及格" || score != null && score >= 60 -> "及"
            else -> null
        }
    }.groupingBy { it }.eachCount()
    return OverallStatsUi(semesterAverageGpa(grades), recordedCredits(grades), grades.size,
        distribution["优"] ?: 0, distribution["良"] ?: 0, distribution["中"] ?: 0, distribution["及"] ?: 0)
}
