package io.github.x07xh.xiaoxu.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChaoxingBatchAuditTest {
    private fun work(id: String, status: String) =
        ChaoxingHomework(id, "作业$id", status, true, status)
    private fun course(key: String, coverage: ChaoxingCourseCoverage,
        rows: List<ChaoxingHomework> = emptyList()) =
        ChaoxingCourseAudit(key, "课程$key", coverage, rows)

    @Test fun completeNeedsAllCourseTotalsAndFreshTaskLedger() {
        val audits = listOf(
            course("101:1:1", ChaoxingCourseCoverage.COUNT_VERIFIED,
                listOf(work("1", "todo"), work("2", "submitted"))),
            course("102:1:2", ChaoxingCourseCoverage.COUNT_VERIFIED,
                listOf(work("3", "graded"), work("4", "returned"), work("5", "unknown")))
        )
        val report = summarizeChaoxingAudit(audits, 2, taskLedgerFresh = true)
        assertTrue(report.fullyCoveredAndRefreshed)
        assertEquals(2, report.countVerifiedCourses)
        assertEquals(5, report.verifiedWorkRows)
        assertEquals(1, report.statuses["todo"])
        assertEquals(1, report.statuses["submitted"])
        assertEquals(1, report.statuses["graded"])
        assertEquals(1, report.statuses["returned"])
        assertEquals(1, report.statuses["unknown"])
        assertFalse(summarizeChaoxingAudit(audits, 2, false).fullyCoveredAndRefreshed)
    }

    @Test fun noTotalMeansIndividualStatusesButNotCompleteCoverage() {
        val report = summarizeChaoxingAudit(
            listOf(course("101", ChaoxingCourseCoverage.LIST_ONLY,
                listOf(work("1", "submitted")))),
            totalCourses = 1, taskLedgerFresh = true
        )
        assertEquals(1, report.checkedCourses)
        assertEquals(1, report.verifiedWorkRows)
        assertEquals(1, report.listOnlyCourses)
        assertFalse(report.fullyCoveredAndRefreshed)
    }

    @Test fun failingAndUnvisitedCoursesNeverCountAsVerifiedEmpty() {
        val report = summarizeChaoxingAudit(listOf(
            course("a", ChaoxingCourseCoverage.COUNT_VERIFIED),
            course("b", ChaoxingCourseCoverage.FAILED),
            course("c", ChaoxingCourseCoverage.NOT_CHECKED)
        ), totalCourses = 3, taskLedgerFresh = true)
        assertEquals(2, report.checkedCourses)
        assertEquals(1, report.countVerifiedCourses)
        assertEquals(1, report.failedCourses)
        assertEquals(1, report.notCheckedCourses)
        assertEquals(0, report.verifiedWorkRows)
        assertFalse(report.fullyCoveredAndRefreshed)
    }

    @Test fun duplicateCourseKeysCannotPretendTotalCoverage() {
        val report = summarizeChaoxingAudit(listOf(
            course("a", ChaoxingCourseCoverage.COUNT_VERIFIED),
            course("a", ChaoxingCourseCoverage.COUNT_VERIFIED)
        ), totalCourses = 2, taskLedgerFresh = true)
        assertFalse(report.fullyCoveredAndRefreshed)
    }

    @Test fun uncheckedSourceIsRejectedBeforeEnteredInLedger() {
        try {
            ChaoxingCourseAudit("a", "课程", ChaoxingCourseCoverage.FAILED,
                listOf(work("1", "todo")))
            throw AssertionError("failed course must not contain official rows")
        } catch (_: IllegalArgumentException) { }
        try {
            ChaoxingCourseAudit("a", "课程", ChaoxingCourseCoverage.COUNT_VERIFIED,
                listOf(ChaoxingHomework("2", "未知", "submitted", false, "")))
            throw AssertionError("unverified school row must not enter audit")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun viewFilterFindsCompletedAcrossAllCoursesWithoutGuessingMissingRows() {
        val audits = listOf(
            course("101", ChaoxingCourseCoverage.COUNT_VERIFIED,
                listOf(work("1", "todo"), work("2", "submitted"))),
            course("102", ChaoxingCourseCoverage.LIST_ONLY,
                listOf(work("3", "graded"), work("4", "returned"), work("5", "unknown"))),
            course("103", ChaoxingCourseCoverage.FAILED)
        )
        assertEquals(listOf("2", "3"), selectChaoxingHomeworkRows(audits, "completed")
            .map { it.second.workId })
        assertEquals(listOf("1", "4"), selectChaoxingHomeworkRows(audits, "actionable")
            .map { it.second.workId })
        assertEquals(listOf("5"), selectChaoxingHomeworkRows(audits, "unknown")
            .map { it.second.workId })
        assertEquals(5, selectChaoxingHomeworkRows(audits, "all").size)
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            selectChaoxingHomeworkRows(audits, "invented")
        }
    }
}
