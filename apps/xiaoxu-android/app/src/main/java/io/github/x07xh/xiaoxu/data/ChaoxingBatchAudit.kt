package io.github.x07xh.xiaoxu.data

/**
 * This is an in-memory evidence ledger from the user's explicitly started,
 * owner-authorized Chaoxing batch read. It is NOT a local task or a forecast.
 *
 * COUNT_VERIFIED: The trusted endpoint matched its declared course work total.
 * LIST_ONLY: Individual statuses were verified, but full work coverage was not.
 * FAILED: The endpoint or validation failed; do not present stale records as fresh.
 * NOT_CHECKED: Batch stopped before reaching this course.
 */
enum class ChaoxingCourseCoverage { COUNT_VERIFIED, LIST_ONLY, FAILED, NOT_CHECKED }

data class ChaoxingCourseAudit(
    val courseKey: String,
    val courseTitle: String,
    val coverage: ChaoxingCourseCoverage,
    val homework: List<ChaoxingHomework>
) {
    init {
        require(courseKey.isNotBlank() && courseTitle.isNotBlank())
        require(homework.isEmpty() ||
            coverage == ChaoxingCourseCoverage.COUNT_VERIFIED ||
            coverage == ChaoxingCourseCoverage.LIST_ONLY)
        require(homework.all { it.platformVerified })
        require(homework.map { it.workId }.distinct().size == homework.size)
    }
}

/** Keep verified source statuses distinct from an owner's local "done" flag. */
data class ChaoxingAuditSummary(
    val checkedCourses: Int,
    val totalCourses: Int,
    val countVerifiedCourses: Int,
    val listOnlyCourses: Int,
    val failedCourses: Int,
    val notCheckedCourses: Int,
    val verifiedWorkRows: Int,
    val statuses: Map<String, Int>,
    val fullyCoveredAndRefreshed: Boolean
)

fun summarizeChaoxingAudit(
    audit: List<ChaoxingCourseAudit>,
    totalCourses: Int,
    taskLedgerFresh: Boolean
): ChaoxingAuditSummary {
    val countVerified = audit.count { it.coverage == ChaoxingCourseCoverage.COUNT_VERIFIED }
    val listOnly = audit.count { it.coverage == ChaoxingCourseCoverage.LIST_ONLY }
    val failed = audit.count { it.coverage == ChaoxingCourseCoverage.FAILED }
    val unchecked = audit.count { it.coverage == ChaoxingCourseCoverage.NOT_CHECKED }
    val work = audit.flatMap { it.homework }
    val completedCount = countVerified + listOnly
    return ChaoxingAuditSummary(
        checkedCourses = countVerified + listOnly + failed,
        totalCourses = totalCourses,
        countVerifiedCourses = countVerified,
        listOnlyCourses = listOnly,
        failedCourses = failed,
        notCheckedCourses = unchecked,
        verifiedWorkRows = work.size,
        statuses = work.groupingBy { it.statusCode }.eachCount(),
        fullyCoveredAndRefreshed = totalCourses > 0 &&
            audit.size == totalCourses &&
            audit.map { it.courseKey }.distinct().size == totalCourses &&
            countVerified == totalCourses &&
            taskLedgerFresh
    )
}
