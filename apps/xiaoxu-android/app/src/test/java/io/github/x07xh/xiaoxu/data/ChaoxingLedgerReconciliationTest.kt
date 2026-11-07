package io.github.x07xh.xiaoxu.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChaoxingLedgerReconciliationTest {
    private fun work(id: String, status: String) =
        ChaoxingHomework(id, "实验报告", status, true, status)
    private fun course(key: String, title: String, coverage: ChaoxingCourseCoverage,
        vararg rows: ChaoxingHomework) =
        ChaoxingCourseAudit(key, title, coverage, rows.toList())
    private fun task(id: String, status: String?, sourceId: String?,
        sourceCourse: String? = null, provider: String = "chaoxing") =
        CloudTask(id, "实验报告", "todo", "platform", provider, null,
            sourceExternalId = sourceId, officialStatus = status,
            sourceCourse = sourceCourse)

    @Test fun perCourseReconciliationRequiresExactIdAndMatchingStatus() {
        val audit = listOf(
            course("101:201:301", "Python", ChaoxingCourseCoverage.COUNT_VERIFIED,
                work("1", "submitted"), work("2", "todo")),
            course("102:202:302", "工程制图", ChaoxingCourseCoverage.LIST_ONLY,
                work("3", "graded")),
            course("103:203:303", "高数", ChaoxingCourseCoverage.FAILED)
        )
        val cloud = listOf(
            task("official-1", "submitted", "work:101:201:1", "Python"),
            task("official-2", "todo", "work:101:201:2", "Python"),
            task("official-3", "todo", "work:102:202:3", "工程制图"),
            task("old-1", null, null, "Python"),
            task("old-2", null, null, "Python"),
            task("old-3", null, null, null),
            task("other", "graded", "work:101:201:1", provider = "ncwu")
        )
        val report = reconcileChaoxingLedger(audit, cloud, ledgerFresh = true)
        val python = report.courses.getValue("101:201:301")
        assertEquals(2, python.listed)
        assertEquals(2, python.stored)
        assertEquals(0, python.missing)
        assertEquals(2, python.possibleLegacy)
        assertEquals(1, python.statuses["submitted"])
        val drawing = report.courses.getValue("102:202:302")
        assertEquals(1, drawing.listed)
        assertEquals(0, drawing.stored)
        assertEquals(1, drawing.missing)
        assertEquals(3, report.possibleLegacyTotal)
        assertEquals(1, report.unmatchedLegacy)
        assertEquals(1, report.missingFromLedger)
        assertNull(report.courses.getValue("103:203:303").missing)
    }

    @Test fun outdatedOrDuplicateSourceEvidenceDoesNotCountAsReconciled() {
        val audit = listOf(course("10:20:30", "程序设计",
            ChaoxingCourseCoverage.COUNT_VERIFIED, work("1", "submitted")))
        val mismatched = listOf(task("x", "todo", "work:10:20:1"))
        assertEquals(1,
            reconcileChaoxingLedger(audit, mismatched, true).missingFromLedger)
        val duplicated = listOf(
            task("x", "submitted", "work:10:20:1"),
            task("y", "submitted", "work:10:20:1")
        )
        assertEquals(1,
            reconcileChaoxingLedger(audit, duplicated, true).missingFromLedger)
    }

    @Test fun unknownCourseTotalAndUnverifiedLegacyLabelsCannotProveCoverage() {
        val audit = listOf(
            course("10:20:30", "同名课", ChaoxingCourseCoverage.LIST_ONLY,
                work("1", "submitted")),
            course("11:21:31", "同名课", ChaoxingCourseCoverage.NOT_CHECKED)
        )
        val cloud = listOf(task("old", null, null, "同名课"),
            task("official", "submitted", "work:10:20:1", "同名课"))
        val fresh = reconcileChaoxingLedger(audit, cloud, true)
        assertEquals(1, fresh.unmatchedLegacy)
        assertEquals(1, fresh.possibleLegacyTotal)
        assertEquals(0, fresh.courses.getValue("10:20:30").possibleLegacy)
        val stale = reconcileChaoxingLedger(audit, cloud, false)
        assertNull(stale.missingFromLedger)
        assertNull(stale.possibleLegacyTotal)
        assertNull(stale.courses.getValue("10:20:30").stored)
        assertNull(stale.courses.getValue("10:20:30").possibleLegacy)
    }

    @Test fun completeCourseTotalsCannotHideMissingOrConflictingStoredStatuses() {
        val audits = listOf(course("10:20:30", "程序设计",
            ChaoxingCourseCoverage.COUNT_VERIFIED,
            work("1", "submitted"), work("2", "graded")))
        val summary = summarizeChaoxingAudit(audits, totalCourses = 1, taskLedgerFresh = true)
        val complete = reconcileChaoxingLedger(audits, listOf(
            task("one", "submitted", "work:10:20:1"),
            task("two", "graded", "work:10:20:2")
        ), true)
        assertEquals(true, chaoxingBatchEvidenceReconciled(summary, complete))
        val missing = reconcileChaoxingLedger(audits, listOf(
            task("one", "submitted", "work:10:20:1"),
            task("two", "todo", "work:10:20:2")
        ), true)
        assertEquals(1, missing.missingFromLedger)
        assertEquals(false, chaoxingBatchEvidenceReconciled(summary, missing))
        assertEquals(false, chaoxingBatchEvidenceReconciled(summary,
            reconcileChaoxingLedger(audits, emptyList(), false)))
    }
}
