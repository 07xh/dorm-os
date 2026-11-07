package io.github.x07xh.xiaoxu.data

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Python #372 JSON -> Kotlin #368 reader -> existing #367 query.
 *
 * These values are synthetic, NOT an authorized school account response.
 */
class CampusRunBridgeJsonReaderTest {
    private val subject = "e".repeat(64)
    private val now = 1_800_000_000_000L
    private val bindings = CampusRunSemesterBindings(
        termKey = "summary.sname",
        total = CampusRunDistanceBinding(
            "summary.semesterDis", CampusRunObservedDistanceUnit.METERS),
        credited = CampusRunDistanceBinding(
            "summary.semesterValidDis", CampusRunObservedDistanceUnit.METERS),
        target = CampusRunDistanceBinding(
            "info.targetDistance", CampusRunObservedDistanceUnit.METERS)
    )
    private val historyBindings = CampusRunHistoryBindings(
        recordIdKey = "rrid",
        rawDistance = CampusRunDistanceBinding(
            "totalDis", CampusRunObservedDistanceUnit.METERS),
        durationSecondsKey = "totalTime",
        completeKey = "complete",
        isValidKey = "isValid"
    )

    private fun subjectJson(): String = JSONObject()
        .put("bridgeSchemaVersion", 1)
        .put("bridgeKind", "subject")
        .put("subjectFingerprint", subject).toString()

    private fun summaryJson(verified: Boolean): String = JSONObject()
        .put("bridgeSchemaVersion", 1)
        .put("bridgeKind", "semester")
        .put("subjectFingerprint", subject)
        .put("verifiedFieldSemantics", verified)
        .put("fields", JSONObject()
            .put("summary.sname", "Synthetic term")
            .put("summary.semesterDis", 13_000)
            .put("summary.semesterValidDis", 11_000)
            .put("info.targetDistance", 20_000)).toString()

    private fun historyJson(
        verified: Boolean = true,
        last: Any = true,
        paginationVerified: Boolean = true
    ): String = JSONObject()
        .put("bridgeSchemaVersion", 1)
        .put("bridgeKind", "history")
        .put("subjectFingerprint", subject)
        .put("verifiedFieldSemantics", verified)
        .put("paginationVerified", paginationVerified)
        .put("isLastPage", last)
        .put("nextCursor", JSONObject.NULL)
        .put("recordsPayload", JSONArray().put(JSONObject()
            .put("rrid", 17)
            .put("totalDis", 2_100)
            .put("totalTime", 700)
            .put("complete", true)
            .put("isValid", true))).toString()

    private fun reader(
        summary: String = summaryJson(true),
        history: String = historyJson()
    ) = CampusRunBridgeJsonReader(
        fetchSubject = { subjectJson() },
        fetchSemester = { summary },
        fetchHistoryPage = { history }
    )

    @Test fun candidateUpstreamPayloadMustNotBecomeTrustedOfficialGrade() = runBlocking {
        val query = CampusRunReadOnlyQuery(CampusRunObservedMappingSource(
            reader(summary = summaryJson(false)), bindings, historyBindings
        ))
        val value = query.refreshSummary(subject, now)
        assertTrue(value is CampusRunViewState.Unavailable)
        assertEquals(CampusRunFailure.INVALID_DATA,
            (value as CampusRunViewState.Unavailable).reason)
    }

    @Test fun verifiedSyntheticPayloadConnectsAcrossBothExistingQueryLayers() = runBlocking {
        val query = CampusRunReadOnlyQuery(CampusRunObservedMappingSource(
            reader(), bindings, historyBindings
        ))
        val current = query.refresh(subject, now)
        assertTrue(current is CampusRunViewState.Current)
        val snapshot = (current as CampusRunViewState.Current).snapshot
        assertEquals(20_000L, snapshot.targetMeters)
        assertEquals(11_000L, snapshot.effectiveMeters)
        assertEquals(13_000L, snapshot.rawMeters)
        assertEquals(9_000L, snapshot.remainingMeters())
        assertEquals(1, snapshot.records.size)
        assertEquals(2_100L, snapshot.records[0].rawMeters)
        assertEquals(CampusRunRecordStatus.VALID, snapshot.records[0].status)
        assertTrue(snapshot.historyComplete)
        assertEquals(CampusRunProvenance.TRUSTED_READONLY, snapshot.provenance)
    }

    @Test fun unknownHistoryEndMustNotBePublishedAsComplete() = runBlocking {
        val source = CampusRunObservedMappingSource(
            reader(history = historyJson(last = JSONObject.NULL)),
            bindings, historyBindings
        )
        val query = CampusRunReadOnlyQuery(source)
        val state = query.refresh(subject, now)
        assertTrue(state is CampusRunViewState.Unavailable)
        assertEquals(CampusRunFailure.INVALID_DATA,
            (state as CampusRunViewState.Unavailable).reason)
    }

    @Test fun unknownHistorySemanticsIsRejectedEvenIfPaginationKnown() = runBlocking {
        val query = CampusRunReadOnlyQuery(CampusRunObservedMappingSource(
            reader(history = historyJson(verified = false)), bindings, historyBindings
        ))
        val state = query.refresh(subject, now)
        assertTrue(state is CampusRunViewState.Unavailable)
    }

    @Test fun invalidSchemaOrIdentityCannotFeedVerifiedSource() = runBlocking {
        val readerVersion = CampusRunBridgeJsonReader(
            fetchSubject = {
                subjectJson().replace("\"bridgeSchemaVersion\":1",
                    "\"bridgeSchemaVersion\":99")
            },
            fetchSemester = { summaryJson(true) },
            fetchHistoryPage = { historyJson() }
        )
        val state = CampusRunReadOnlyQuery(CampusRunObservedMappingSource(
            readerVersion, bindings, historyBindings
        )).refreshSummary(subject, now)
        assertTrue(state is CampusRunViewState.Unavailable)
        assertEquals(CampusRunFailure.INVALID_DATA,
            (state as CampusRunViewState.Unavailable).reason)
    }

    @Test fun summaryOnlyDoesNotCallHistoryEndpoint() = runBlocking {
        var historyReads = 0
        val reader = CampusRunBridgeJsonReader(
            fetchSubject = { subjectJson() },
            fetchSemester = { summaryJson(true) },
            fetchHistoryPage = { historyReads++; historyJson() }
        )
        val state = CampusRunReadOnlyQuery(CampusRunObservedMappingSource(
            reader, bindings, historyBindings
        )).refreshSummary(subject, now)
        assertTrue(state is CampusRunViewState.Current)
        assertFalse((state as CampusRunViewState.Current).snapshot.historyComplete)
        assertEquals(0, historyReads)
    }

    @Test fun unrecognizedNestedFieldsAreRejectedBeforeSemanticMapping() = runBlocking {
        val bad = JSONObject(summaryJson(true))
        bad.getJSONObject("fields").put("secret", JSONObject().put("token", "unsafe"))
        val query = CampusRunReadOnlyQuery(CampusRunObservedMappingSource(
            reader(summary = bad.toString()), bindings, historyBindings
        ))
        val result = query.refreshSummary(subject, now)
        assertTrue(result is CampusRunViewState.Unavailable)
    }
}
