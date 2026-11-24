package io.github.x07xh.xiaoxu.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NcwuAcademicSnapshotTest {
    private fun payload(kind: String, records: JSONArray = JSONArray()) =
        JSONObject().put("kind", kind).put("source", "ncwu")
            .put("status", "ready").put("provenance", "trusted_readonly")
            .put("lastSuccessAt", "2026-11-25T00:00:00Z").put("records", records)

    private fun row() = JSONObject().put("externalId", "synthetic-1")
        .put("term", "2026-2027-1").put("title", "合成测试课程")

    @Test fun gradePreservesZeroAndUnknown() {
        val snapshot = parseNcwuAcademicSnapshot("grades", payload("grades",
            JSONArray().put(row().put("score", "0").put("credit", 0))))
        assertEquals("0", snapshot.records.single().score)
        assertEquals(0.0, snapshot.records.single().credit!!, 0.0)
        assertNull(snapshot.records.single().gradePoint)
    }

    @Test fun rejectsCandidateAndImportedData() {
        assertTrue(runCatching { parseNcwuAcademicSnapshot("grades",
            payload("grades").put("provenance", "user_import")) }.isFailure)
        assertTrue(runCatching { parseNcwuAcademicSnapshot("exams",
            JSONObject().put("kind", "exams").put("verified", false)) }.isFailure)
    }

    @Test fun examRequiresDateAndValidTime() {
        val exam = row().put("date", "2026-12-20").put("startTime", "09:00")
            .put("endTime", "11:00").put("location", "合成考场")
        assertEquals("09:00", parseNcwuAcademicSnapshot("exams",
            payload("exams", JSONArray().put(exam))).records.single().startTime)
        assertTrue(runCatching { parseNcwuAcademicSnapshot("exams",
            payload("exams", JSONArray().put(row()))) }.isFailure)
        assertTrue(runCatching { parseNcwuAcademicSnapshot("exams",
            payload("exams", JSONArray().put(exam.put("date", "2026-02-30")))) }.isFailure)
    }

    @Test fun duplicateRowsRejectedAndTrustedEmptyAccepted() {
        assertTrue(runCatching { parseNcwuAcademicSnapshot("grades",
            payload("grades", JSONArray().put(row()).put(row()))) }.isFailure)
        assertTrue(parseNcwuAcademicSnapshot("grades", payload("grades")).records.isEmpty())
    }
}
