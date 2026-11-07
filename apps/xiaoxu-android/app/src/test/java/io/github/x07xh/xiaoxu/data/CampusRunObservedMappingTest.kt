package io.github.x07xh.xiaoxu.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Synthetic decoded fields. They do not claim to reproduce the user's
 * encrypted r/s/v HAR or establish the current upstream JSON schema.
 */
class CampusRunObservedMappingTest {
    private val now = 1_800_000_000_000L
    private val fingerprint = "a".repeat(64)

    private fun rejects(block: () -> Unit) {
        try {
            block()
            throw AssertionError("invalid mapping accepted")
        } catch (_: IllegalArgumentException) {
            // The production adapter fails closed on missing or invalid fields.
        }
    }

    private val meters = CampusRunObservedDistanceUnit.METERS
    private val km = CampusRunObservedDistanceUnit.KILOMETERS

    private val summaryBindings = CampusRunSemesterBindings(
        termKey = "termLabel",
        target = CampusRunDistanceBinding("goalKm", km),
        credited = CampusRunDistanceBinding("creditKm", km),
        total = CampusRunDistanceBinding("totalKm", km),
        weekly = CampusRunDistanceBinding("weeklyKm", km)
    )

    private val historyBindings = CampusRunHistoryBindings(
        recordIdKey = "rrid",
        rawDistance = CampusRunDistanceBinding("totalDis", meters),
        // No verified field establishes 'validDis' as semester credited distance.
        creditedDistance = null,
        durationSecondsKey = "totalTime",
        startedAtKey = "startTime",
        startedAtUnit = CampusRunObservedTimeUnit.EPOCH_MILLIS,
        completeKey = "complete",
        isValidKey = "isValid"
    )

    private fun semesterData(): Map<String, Any?> = mapOf(
        "termLabel" to "Synthetic term",
        "goalKm" to "100",
        "creditKm" to "20.50",
        "totalKm" to "23.80",
        "weeklyKm" to null
    )

    private fun historyRow(id: String = "r1"): Map<String, Any?> = mapOf(
        "rrid" to id,
        "totalDis" to "2350",
        "validDis" to "2100",
        "totalTime" to 910,
        "startTime" to now - 90_000L,
        "complete" to true,
        "isValid" to true,
        "status" to 1
    )

    @Test fun capturedRoutesRetainObservedReadonlyMethodsAndPaths() {
        assertEquals("POST", CampusRunObservedEndpoint.SEMESTER_SUMMARY.method)
        assertEquals("/api/v55/runnings/recordssummary/semester",
            CampusRunObservedEndpoint.SEMESTER_SUMMARY.path)
        assertEquals("POST", CampusRunObservedEndpoint.RUN_RECORDS.method)
        assertEquals("/api/v70230/runnings/records",
            CampusRunObservedEndpoint.RUN_RECORDS.path)
        assertEquals("run.gxapp.iydsj.com", CampusRunObservedEndpoint.RUN_RECORDS.host)
        assertTrue(CampusRunObservedEndpoint.entries.none {
            it.path.contains("save/record") || it.path.contains("upload")
        })
    }

    @Test fun opaqueEnvelopeIsOnlyShapeNotADecryptedBusinessResponse() {
        val cipherB64 = java.util.Base64.getEncoder()
            .encodeToString(ByteArray(16) { 42.toByte() })
        val signatureB64 = java.util.Base64.getEncoder()
            .encodeToString(ByteArray(128) { 7.toByte() })
        val shape = inspectCampusRunOpaqueEnvelope(
            "{\"r\":\"$cipherB64\",\"s\":\"$signatureB64\",\"v\":3}"
        )
        assertEquals(3, shape.responseVersion)
        assertEquals(cipherB64.length, shape.rCharacters)
        assertEquals(signatureB64.length, shape.sCharacters)
        assertEquals(16, shape.rDecodedBytes)
        assertEquals(128, shape.sDecodedBytes)
        assertTrue(shape.rBlockAligned16)
        rejects { inspectCampusRunOpaqueEnvelope("{\"r\":{},\"s\":\"a\",\"v\":1}") }
        rejects { inspectCampusRunOpaqueEnvelope("{\"r\":\"a\",\"s\":\"b\"}") }
        rejects { inspectCampusRunOpaqueEnvelope("{\"r\":\"a\",\"s\":\"b\",\"v\":1,\"data\":[]}") }
        rejects { inspectCampusRunOpaqueEnvelope("not-json") }
    }

    @Test fun semesterMapperRequiresExplicitBindingAndPreservesUnknownValues() {
        val result = mapCampusRunDecodedSemester(semesterData(), fingerprint, summaryBindings)
        assertEquals(100_000L, result.targetMeters)
        assertEquals(20_500L, result.effectiveMeters)
        assertEquals(23_800L, result.rawMeters)
        assertNull(result.weeklyMeters)
        assertNull(result.dashboardAccumulatedMeters)
        assertNull(result.totalRunCount)
        assertEquals("Synthetic term", result.termLabel)
        val noBindings = mapCampusRunDecodedSemester(semesterData(), fingerprint,
            CampusRunSemesterBindings())
        assertNull(noBindings.targetMeters)
        assertNull(noBindings.effectiveMeters)
    }

    @Test fun invalidUnitsOrPrecisionCannotSilentlyTruncateDistance() {
        rejects { mapCampusRunDecodedSemester(
            semesterData() + ("goalKm" to "12.34567"), fingerprint, summaryBindings
        ) }
        rejects { mapCampusRunDecodedSemester(
            semesterData() + ("creditKm" to "not-a-number"), fingerprint, summaryBindings
        ) }
    }

    @Test fun recordMappingDoesNotClaimCreditedMetersFromAnUnverifiedField() {
        val record = mapCampusRunDecodedHistoryRecord(historyRow(), historyBindings)
        assertEquals("r1", record.recordKey)
        assertEquals(2_350L, record.rawMeters)
        assertNull(record.effectiveMeters)
        assertEquals(910L, record.durationSeconds)
        assertEquals(now - 90_000L, record.startedAtEpochMillis)
        assertEquals(CampusRunRecordStatus.VALID, record.status)
        assertEquals(CampusRunRecordKind.UNKNOWN, record.kind)
        assertNull(record.hasAnomaly)
    }

    @Test fun numericStatusAloneCannotProveOfficialCompletion() {
        val record = mapCampusRunDecodedHistoryRecord(
            historyRow() - "complete" - "isValid", historyBindings
        )
        assertEquals(CampusRunRecordStatus.UNKNOWN, record.status)
        val incomplete = mapCampusRunDecodedHistoryRecord(
            historyRow() + ("complete" to false), historyBindings
        )
        assertEquals(CampusRunRecordStatus.INVALID, incomplete.status)
        val contradiction = mapCampusRunDecodedHistoryRecord(
            historyRow() + ("isValid" to false), historyBindings
        )
        assertEquals(CampusRunRecordStatus.INVALID, contradiction.status)
    }

    @Test fun historyListAndOlderRecordListFormsCanBothBeMapped() {
        val row = historyRow()
        val direct = mapCampusRunDecodedHistoryRows(listOf(row), historyBindings)
        val older = mapCampusRunDecodedHistoryRows(mapOf("recordList" to listOf(row)),
            historyBindings)
        assertEquals(direct, older)
        assertEquals(1, direct.size)
        rejects { mapCampusRunDecodedHistoryRows(mapOf<String, Any?>(), historyBindings) }
        rejects { mapCampusRunDecodedHistoryRows(null, historyBindings) }
        rejects { mapCampusRunDecodedHistoryRows(listOf("invalid"), historyBindings) }
        rejects { mapCampusRunDecodedHistoryRows(listOf(mapOf("totalDis" to 100)),
            historyBindings) }
    }

    @Test fun mappedReaderActuallyConnectsToPriorReadOnlyQueryWithSyntheticData() = runBlocking {
        val reader = object : CampusRunAuthorizedDecodedReader {
            override suspend fun currentSubjectFingerprint() = fingerprint
            override suspend fun semester() = CampusRunAuthorizedDecodedSemester(
                fingerprint, semesterData()
            )
            override suspend fun historyPage(cursor: String?) = CampusRunAuthorizedDecodedPage(
                fingerprint, listOf(historyRow()), nextCursor = null, isLastPage = true
            )
        }
        val source = CampusRunObservedMappingSource(reader, summaryBindings, historyBindings)
        val query = CampusRunReadOnlyQuery(source)
        val summary = query.refreshSummary(fingerprint, now)
        assertTrue(summary is CampusRunViewState.Current)
        val first = (summary as CampusRunViewState.Current).snapshot
        assertFalse(first.historyComplete)
        assertTrue(first.records.isEmpty())
        assertEquals(20_500L, first.effectiveMeters)

        val detail = query.refresh(fingerprint, now + 1_000L)
        assertTrue(detail is CampusRunViewState.Current)
        val result = (detail as CampusRunViewState.Current).snapshot
        assertTrue(result.historyComplete)
        assertEquals(1, result.records.size)
        assertEquals(79_500L, result.remainingMeters())
        assertEquals(20_500L, result.effectiveMeters)
    }

    @Test fun decodedReaderWithDifferentSubjectCannotPublishOfficialData() = runBlocking {
        val other = "b".repeat(64)
        val reader = object : CampusRunAuthorizedDecodedReader {
            override suspend fun currentSubjectFingerprint() = fingerprint
            override suspend fun semester() = CampusRunAuthorizedDecodedSemester(
                other, semesterData()
            )
            override suspend fun historyPage(cursor: String?) = CampusRunAuthorizedDecodedPage(
                fingerprint, emptyList<Map<String, Any?>>(), nextCursor = null, isLastPage = true
            )
        }
        val state = CampusRunReadOnlyQuery(
            CampusRunObservedMappingSource(reader, summaryBindings, historyBindings)
        ).refreshSummary(fingerprint, now)
        assertTrue(state is CampusRunViewState.Unavailable)
        assertEquals(CampusRunFailure.NOT_AUTHORIZED,
            (state as CampusRunViewState.Unavailable).reason)
    }
    @Test fun historicalReferenceSemesterFieldsAreOnlyCandidatesNotVerifiedTargets() {
        // Candidate labels from an older external read-only score display;
        // this synthetic fixture is NOT a decoded user response.
        val historical = mapOf<String, Any?>(
            "sname" to "Synthetic old term",
            "semesterDis" to 23_800,
            "semesterValidDis" to 20_000,
            "semesterCount" to 11,
            "semesterValidCount" to 9
        )
        val bindings = CampusRunSemesterBindings(
            termKey = "sname",
            total = CampusRunDistanceBinding("semesterDis", meters),
            credited = CampusRunDistanceBinding("semesterValidDis", meters),
            totalCountKey = "semesterCount",
            creditedCountKey = "semesterValidCount"
        )
        val result = mapCampusRunDecodedSemester(historical, fingerprint, bindings)
        assertEquals(23_800L, result.rawMeters)
        assertEquals(20_000L, result.effectiveMeters)
        assertEquals(11, result.totalRunCount)
        assertEquals(9, result.effectiveRunCount)
        assertNull(result.targetMeters) // No authorized target field was observed.
    }

}
