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

/** Bounded, non-sensitive reasons shown only for an unsuccessful official read. */
enum class ChaoxingReadIssue(val label: String) {
    NETWORK("请求中断，检查网络后重试"),
    AUTHORIZATION("官方授权需要重新确认"),
    LIST_INCOMPLETE("官网作业总数与已读取数量不符，需检查筛选或分页"),
    PAGE_UNAVAILABLE("本课程作业页面暂时无法核验"),
    STORAGE("云端保存或回执未能核验，旧记录不变"),
    UPSTREAM("学校接口暂时不可用"),
    INVALID_RESPONSE("官方返回内容未通过校验，未更新作业状态")
}

/** Never place HTTP response bodies, upstream URLs or exception messages in UI state. */
fun classifyChaoxingReadIssue(error: String, status: Int): ChaoxingReadIssue =
    when (error) {
        "session_expired", "authorization_failed", "interactive_login_required" ->
            ChaoxingReadIssue.AUTHORIZATION
        "work_list_incomplete" -> ChaoxingReadIssue.LIST_INCOMPLETE
        "work_not_available" -> ChaoxingReadIssue.PAGE_UNAVAILABLE
        "database_unavailable", "connector_not_configured" -> ChaoxingReadIssue.STORAGE
        "upstream_unavailable", "school_login_unavailable", "not_configured",
        "courses_unverified" -> ChaoxingReadIssue.UPSTREAM
        else -> when {
            status == 401 || status == 409 -> ChaoxingReadIssue.AUTHORIZATION
            status >= 500 -> ChaoxingReadIssue.UPSTREAM
            else -> ChaoxingReadIssue.INVALID_RESPONSE
        }
    }

data class ChaoxingCourseAudit(
    val courseKey: String,
    val courseTitle: String,
    val coverage: ChaoxingCourseCoverage,
    val homework: List<ChaoxingHomework>,
    val failure: ChaoxingReadIssue? = null
) {
    init {
        require(courseKey.isNotBlank() && courseTitle.isNotBlank())
        require(homework.isEmpty() ||
            coverage == ChaoxingCourseCoverage.COUNT_VERIFIED ||
            coverage == ChaoxingCourseCoverage.LIST_ONLY)
        require(failure == null || coverage == ChaoxingCourseCoverage.FAILED)
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
