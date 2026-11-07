package io.github.x07xh.xiaoxu.data

import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic upstream fixtures; never use captured HAR, IDs or actual scores. */
class CampusRunReadOnlyQueryTest {
    private val now = 1_800_000_000_000L
    private val alice = "a".repeat(64)
    private val bob = "b".repeat(64)

    private fun summary(subject: String = alice) = CampusRunReadSummary(
        subjectFingerprint = subject,
        termLabel = "2026-2027-1",
        targetMeters = 60_000,
        effectiveMeters = 12_000,
        rawMeters = 15_000,
        dashboardAccumulatedMeters = 30_000,
        weeklyMeters = null,
        totalRunCount = null,
        effectiveRunCount = null
    )

    private fun record(key: String) = CampusRunRecord(
        recordKey = key,
        startedAtEpochMillis = now - 100_000,
        rawMeters = 2_000,
        effectiveMeters = null,
        durationSeconds = 900,
        status = CampusRunRecordStatus.UNKNOWN
    )

    private class FakeSource(
        var subject: String,
        var semester: CampusRunReadSummary,
        var pages: List<CampusRunReadPage>
    ) : CampusRunReadOnlySource {
        var nextPage = 0
        var failing: CampusRunFailure? = null
        override suspend fun authorizedSubjectFingerprint(): String = subject
        override suspend fun readSemester(): CampusRunReadSummary = semester
        override suspend fun readHistoryPage(cursor: String?): CampusRunReadPage {
            failing?.let { throw CampusRunSourceException(it) }
            return pages[nextPage++]
        }
    }

    private fun page(
        items: List<CampusRunRecord> = emptyList(),
        next: String? = null,
        isLast: Boolean = next == null,
        subject: String = alice
    ) = CampusRunReadPage(subject, items, next, isLast)

    private fun source() = FakeSource(alice, summary(), listOf(page()))

    @Test fun successfulReadKeepsSemesterMetricsDistinctFromRunHistory() = runBlocking {
        val src = source()
        src.pages = listOf(page(listOf(record("r1")), next = "cursor2"),
            page(listOf(record("r2"))))
        val state = CampusRunReadOnlyQuery(src).refresh(alice, now)
        assertTrue(state is CampusRunViewState.Current)
        val snapshot = (state as CampusRunViewState.Current).snapshot
        assertEquals(CampusRunProvenance.TRUSTED_READONLY, snapshot.provenance)
        assertTrue(snapshot.ownerMatched)
        assertTrue(snapshot.historyComplete)
        assertEquals(2, snapshot.records.size)
        assertEquals(12_000L, snapshot.effectiveMeters)
        assertEquals(15_000L, snapshot.rawMeters)
        assertEquals(30_000L, snapshot.dashboardAccumulatedMeters)
        assertEquals(80.0, snapshot.remainingMeters()!!.toDouble() / 600.0, 0.001)
        assertNull(snapshot.weeklyMeters)
        assertNull(snapshot.totalRunCount)
    }

    @Test fun pageLimitNeverClaimsCompleteHistory() = runBlocking {
        val src = source()
        src.pages = listOf(page(listOf(record("r1")), next = "cursor2"))
        val state = CampusRunReadOnlyQuery(src, maxPages = 1).refresh(alice, now)
        assertTrue(state is CampusRunViewState.Current)
        val snapshot = (state as CampusRunViewState.Current).snapshot
        assertFalse(snapshot.historyComplete)
        assertEquals(1, snapshot.records.size)
    }

    @Test fun recordLimitNeverClaimsCompleteHistory() = runBlocking {
        val src = source()
        src.pages = listOf(page(listOf(record("r1"), record("r2"), record("r3"))))
        val state = CampusRunReadOnlyQuery(src, maxRecords = 2).refresh(alice, now)
        assertTrue(state is CampusRunViewState.Current)
        val snapshot = (state as CampusRunViewState.Current).snapshot
        assertFalse(snapshot.historyComplete)
        assertEquals(2, snapshot.records.size)
    }

    @Test fun repeatedCursorInvalidatesNewRead() = runBlocking {
        val src = source()
        src.pages = listOf(page(next = "same"), page(next = "same"))
        val state = CampusRunReadOnlyQuery(src).refresh(alice, now)
        assertEquals(CampusRunFailure.INVALID_DATA,
            (state as CampusRunViewState.Unavailable).reason)
    }

    @Test fun contradictoryPageCompletionInvalidatesNewRead() = runBlocking {
        val src = source()
        src.pages = listOf(page(next = "cursor", isLast = true))
        val state = CampusRunReadOnlyQuery(src).refresh(alice, now)
        assertEquals(CampusRunFailure.INVALID_DATA,
            (state as CampusRunViewState.Unavailable).reason)
    }

    @Test fun cannotTrustDifferentAuthenticatedIdentity() = runBlocking {
        val src = source()
        src.subject = bob
        val state = CampusRunReadOnlyQuery(src).refresh(alice, now)
        assertEquals(CampusRunFailure.NOT_AUTHORIZED,
            (state as CampusRunViewState.Unavailable).reason)
    }

    @Test fun summaryAndHistoryMustMatchSameAuthenticatedAccount() = runBlocking {
        val src = source()
        src.semester = summary(bob)
        var state = CampusRunReadOnlyQuery(src).refresh(alice, now)
        assertEquals(CampusRunFailure.NOT_AUTHORIZED,
            (state as CampusRunViewState.Unavailable).reason)
        src.semester = summary()
        src.pages = listOf(page(subject = bob))
        state = CampusRunReadOnlyQuery(src).refresh(alice, now)
        assertEquals(CampusRunFailure.NOT_AUTHORIZED,
            (state as CampusRunViewState.Unavailable).reason)
    }

    @Test fun networkFailureDoesNotOverwriteValidEarlierSnapshot() = runBlocking {
        val src = source()
        val query = CampusRunReadOnlyQuery(src)
        assertTrue(query.refresh(alice, now) is CampusRunViewState.Current)
        src.failing = CampusRunFailure.NETWORK
        val state = query.refresh(alice, now + 10_000L)
        assertTrue(state is CampusRunViewState.Stale)
        assertEquals(CampusRunFailure.NETWORK,
            (state as CampusRunViewState.Stale).reason)
        assertEquals(12_000L, state.snapshot.effectiveMeters)
    }

    @Test fun authorizationRevocationAndAccountSwitchNeverExposePriorAccount() = runBlocking {
        val src = source()
        val query = CampusRunReadOnlyQuery(src)
        assertTrue(query.refresh(alice, now) is CampusRunViewState.Current)
        src.failing = CampusRunFailure.NOT_AUTHORIZED
        assertTrue(query.refresh(alice, now + 1_000L) is CampusRunViewState.Unavailable)
        assertTrue(query.state(now + 2_000L) is CampusRunViewState.Unavailable)
        src.failing = CampusRunFailure.NETWORK
        assertTrue(query.refresh(bob, now + 3_000L) is CampusRunViewState.Unavailable)
        assertTrue(query.state(now + 3_000L) is CampusRunViewState.Unavailable)
        query.clear()
        assertTrue(query.state(now + 4_000L) is CampusRunViewState.NotConnected)
    }

    @Test fun brokenMeasurementDoesNotReplaceOldSnapshot() = runBlocking {
        val src = source()
        val query = CampusRunReadOnlyQuery(src)
        query.refresh(alice, now)
        src.semester = summary().copy(effectiveMeters = 16_000, rawMeters = 15_000)
        val result = query.refresh(alice, now + 1_000L)
        assertTrue(result is CampusRunViewState.Stale)
        assertEquals(CampusRunFailure.INVALID_DATA,
            (result as CampusRunViewState.Stale).reason)
        assertEquals(12_000L, result.snapshot.effectiveMeters)
    }

    @Test fun ioErrorsAreNotReportedAsSuccessfulRefresh() = runBlocking {
        val src = object : CampusRunReadOnlySource {
            override suspend fun authorizedSubjectFingerprint() = alice
            override suspend fun readSemester(): CampusRunReadSummary {
                throw IOException("synthetic network error")
            }
            override suspend fun readHistoryPage(cursor: String?): CampusRunReadPage {
                error("unreachable")
            }
        }
        val state = CampusRunReadOnlyQuery(src).refresh(alice, now)
        assertEquals(CampusRunFailure.NETWORK,
            (state as CampusRunViewState.Unavailable).reason)
    }
}
