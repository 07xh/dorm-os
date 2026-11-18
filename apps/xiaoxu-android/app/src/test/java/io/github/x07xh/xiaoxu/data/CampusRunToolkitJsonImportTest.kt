package io.github.x07xh.xiaoxu.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** No personal data: synthetic JSON matching the archived CLI result shape. */
class CampusRunToolkitJsonImportTest {
    private val now = 1_800_000_000_000L

    @Test fun importsOnlyCompletedSemesterFromAllJsonAndPreservesProvenance() {
        val json = """
            {
              "score": {
                "semester_completed": {
                  "error": 10000,
                  "data": {
                    "sname": "2026-autumn", "semesterDis": 12.45,
                    "semesterValidDis": 9.38,
                    "semesterCount": 5, "semesterValidCount": 3
                  }
                },
                "semester_info": {"error": 10000, "data": {"targetDistance": 40}}
              },
              "rank": {"data": [{"uid": "someone-else", "order": 1}]},
              "history": {"error": 10000, "data": [{"rrid": "sample"}]},
              "token": "must-not-be-copied"
            }
        """.trimIndent()
        val result = parseCampusRunToolkitJson(json, CampusRunImportUnit.KILOMETERS, now)
        assertEquals(CampusRunProvenance.USER_IMPORT, result.provenance)
        assertFalse(result.ownerMatched)
        assertEquals(12_450L, result.rawMeters)
        assertEquals(9_380L, result.effectiveMeters)
        assertNull(result.targetMeters)
        assertEquals("2026-autumn", result.termLabel)
        assertTrue(result.records.isEmpty())
        assertFalse(result.historyComplete)
        assertEquals(5, result.totalRunCount)
        assertEquals(3, result.effectiveRunCount)
        assertEquals(now, result.capturedAtEpochMillis)
    }

    @Test fun meterUnitDoesNotImplicitlyAssumeKilometers() {
        val json = """{"semester_completed":{"error":10000,"data":{
          "semesterDis":12345,"semesterValidDis":10003}}}"""
        val meters = parseCampusRunToolkitJson(json, CampusRunImportUnit.METERS, now)
        assertEquals(12_345L, meters.rawMeters)
        assertEquals(10_003L, meters.effectiveMeters)
        assertNull(meters.totalRunCount)
        assertNull(meters.effectiveRunCount)
    }

    @Test fun malformedOrNonSuccessResponsesNeverBecomeImportedProgress() {
        val fixtures = listOf(
            """{"semester_completed":{"error":401,"data":{"semesterDis":99}}}""",
            """{"semester_completed":{"error":10000,"r":"x","s":"x","v":1}}""",
            """{"semester_completed":{"error":10000,"data":{"semesterDis":20,
                "semesterValidDis":25}}}""",
            """{"semester_completed":{"error":10000,"data":{"semesterDis":-1}}}""",
            """{"semester_completed":{"error":10000,"data":{"semesterDis":1.0001}}}""",
            """{"semester_completed":{"error":10000,"data":{"semesterDis":true}}}""",
            """{"semester_completed":{"error":10000,"data":{"sname":"no metrics"}}}""",
            """{"score":{"semester_completed":{"error":10000,"data":{"semesterDis":1.25,
                "semesterValidDis":3.25}}},"verifiedFieldSemantics":true}"""
        )
        for (json in fixtures) {
            try {
                parseCampusRunToolkitJson(json, CampusRunImportUnit.METERS, now)
                throw AssertionError("invalid JSON accepted: $json")
            } catch (_: IllegalArgumentException) {
                // Strict local import rejects bad status, overcredit and speculation.
            }
        }
    }

    @Test fun countsAreLiteralFieldsNotSynthesizedFromHistory() {
        val json = """{"semester_completed":{"error":10000,"data":{
            "semesterCount":"7", "semesterValidCount":4,
            "semesterDis":8000,"semesterValidDis":7000}}}"""
        val snapshot = parseCampusRunToolkitJson(json, CampusRunImportUnit.METERS, now)
        assertEquals(7, snapshot.totalRunCount)
        assertEquals(4, snapshot.effectiveRunCount)
        assertTrue(snapshot.records.isEmpty())
        assertFalse(snapshot.historyComplete)
    }

    @Test fun invalidAndContradictoryRunCountsFailClosed() {
        val scoreFields = listOf(
            """"semesterCount":3,"semesterValidCount":4""",
            """"semesterCount":-1""",
            """"semesterCount":1.5""",
            """"semesterCount":true""",
            """"semesterCount":1000001""",
            """"semesterCount":"a lot""""
        )
        for (fields in scoreFields) {
            val json = """{"semester_completed":{"error":10000,
                "data":{"semesterDis":100,"semesterValidDis":50,$fields}}}"""
            try {
                parseCampusRunToolkitJson(json, CampusRunImportUnit.METERS, now)
                throw AssertionError("invalid count was accepted")
            } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun sanitizedExportRequiresExactDeclaredMeters() {
        val sanitized = """{"schema":"xiaoxu/campus-run-toolkit-safe-v1",
            "unit":"m","semester_completed":{"error":10000,"data":{
                "semesterDis":12345,"semesterValidDis":10003,
                "semesterCount":5,"semesterValidCount":3}}}"""
        val imported = parseCampusRunToolkitJson(sanitized, CampusRunImportUnit.METERS, now)
        assertEquals(12_345L, imported.rawMeters)
        assertEquals(10_003L, imported.effectiveMeters)
        assertEquals(5, imported.totalRunCount)
        assertEquals(3, imported.effectiveRunCount)

        try {
            parseCampusRunToolkitJson(sanitized, CampusRunImportUnit.KILOMETERS, now)
            throw AssertionError("declared meters must reject selected kilometers")
        } catch (_: IllegalArgumentException) { }

        try {
            parseCampusRunToolkitJson(sanitized.replace(
                "xiaoxu/campus-run-toolkit-safe-v1", "unknown/schema"),
                CampusRunImportUnit.METERS, now)
            throw AssertionError("unsupported schema must be rejected")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun rejectsOversizedAndExcessPrecisionWithoutRounding() {
        val unsupported = """{"semester_completed":{"error":10000,"data":{
            "semesterDis":1.23456}}}"""
        try {
            parseCampusRunToolkitJson(unsupported, CampusRunImportUnit.KILOMETERS, now)
            throw AssertionError("precision must be explicit")
        } catch (_: IllegalArgumentException) { }

        try {
            parseCampusRunToolkitJson(" ".repeat(256_001), CampusRunImportUnit.METERS, now)
            throw AssertionError("oversized document accepted")
        } catch (_: IllegalArgumentException) { }
    }
}
