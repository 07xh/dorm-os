package io.github.x07xh.xiaoxu.data

import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic identities and distances only; no upstream credentials or HTTP. */
class CampusRun72HourReadCoordinatorTest {
    private val alice = "a".repeat(64)
    private val bob = "b".repeat(64)
    private val now = 1_800_000_000_000L

    private class Checkpoints : CampusRunRequestCheckpointStore {
        val attempts = mutableMapOf<String, Long>()
        override fun lastAttemptMillis(subjectFingerprint: String) =
            attempts[subjectFingerprint]
        override fun saveAttemptMillis(subjectFingerprint: String, epochMillis: Long): Boolean {
            attempts[subjectFingerprint] = epochMillis
            return true
        }
    }

    private class MemoryCache : CampusRunTrustedSnapshotCache {
        var subject: String? = null
        var snapshot: CampusRunSnapshot? = null
        var savedCount = 0
        var cleared = false
        var failKeystoreRead = false
        override fun load(subjectFingerprint: String, nowEpochMillis: Long): CampusRunSnapshot? {
            if (failKeystoreRead) throw SecurityException("synthetic Keystore error")
            return if (subject == subjectFingerprint) snapshot else null
        }

        override fun save(
            subjectFingerprint: String, snapshot: CampusRunSnapshot, nowEpochMillis: Long
        ): Boolean {
            require(snapshot.provenance == CampusRunProvenance.TRUSTED_READONLY)
            subject = subjectFingerprint
            this.snapshot = snapshot
            savedCount++
            return true
        }

        override fun clear(): Boolean {
            subject = null
            snapshot = null
            cleared = true
            return true
        }
    }

    private class ReadSource(var subject: String) : CampusRunReadOnlySource {
        var semesterReads = 0
        var historyReads = 0
        var failHistory = false
        var failNetwork = false
        override suspend fun authorizedSubjectFingerprint() = subject
        override suspend fun readSemester(): CampusRunReadSummary {
            semesterReads++
            if (failNetwork) throw IOException("offline synthetic fixture")
            return CampusRunReadSummary(
                subjectFingerprint = subject,
                termLabel = "term",
                targetMeters = 20_000L,
                effectiveMeters = 2_000L,
                rawMeters = 3_000L
            )
        }
        override suspend fun readHistoryPage(cursor: String?): CampusRunReadPage {
            historyReads++
            if (failHistory) throw IOException("offline history fixture")
            return CampusRunReadPage(subject, emptyList(), null, true)
        }
    }

    private fun coordinator(
        source: ReadSource,
        checkpoints: Checkpoints,
        cache: MemoryCache
    ) = CampusRun72HourReadCoordinator(
        CampusRunReadOnlyQuery(source),
        CampusRun72HourRequestBudget(checkpoints),
        cache
    )

    @Test fun invalidSubjectOrClockNeverTouchesCacheBudgetOrNetwork() = runBlocking {
        val checkpoints = Checkpoints()
        val cache = MemoryCache()
        val source = ReadSource(alice)
        val coordinator = coordinator(source, checkpoints, cache)

        val invalid = coordinator.refreshSummaryIfDue("not-a-fingerprint", now)
        assertTrue(invalid is CampusRunViewState.Unavailable)
        assertEquals(CampusRunFailure.NOT_AUTHORIZED,
            (invalid as CampusRunViewState.Unavailable).reason)

        val clock = coordinator.refreshSummaryIfDue(alice, 0L)
        assertTrue(clock is CampusRunViewState.Unavailable)
        assertEquals(CampusRunFailure.INVALID_DATA,
            (clock as CampusRunViewState.Unavailable).reason)

        assertEquals(0, source.semesterReads)
        assertTrue(checkpoints.attempts.isEmpty())
        assertEquals(0, cache.savedCount)
        assertFalse(cache.cleared)

        // After bad input, a valid first read must remain available.
        assertTrue(coordinator.refreshSummaryIfDue(alice, now)
            is CampusRunViewState.Current)
        assertEquals(1, source.semesterReads)

        // A malformed account supplied later must hide, not expose Alice.
        val switched = coordinator.refreshHistoryIfDue("bad", now + 1L)
        assertTrue(switched is CampusRunViewState.Unavailable)
        assertEquals(CampusRunFailure.NOT_AUTHORIZED,
            (switched as CampusRunViewState.Unavailable).reason)
        assertEquals(1, source.semesterReads)
        assertEquals(1, checkpoints.attempts.size)
        assertFalse(cache.cleared)
    }

    @Test fun restartRestoresEncryptedCacheSeamWithoutAnotherRead() = runBlocking {
        val checkpoints = Checkpoints()
        val cache = MemoryCache()
        val source = ReadSource(alice)
        val first = coordinator(source, checkpoints, cache)
        val current = first.refreshSummaryIfDue(alice, now)
        assertTrue(current is CampusRunViewState.Current)
        assertEquals(1, source.semesterReads)
        assertEquals(1, cache.savedCount)

        val restarted = coordinator(source, checkpoints, cache)
        val resumed = restarted.refreshSummaryIfDue(alice, now + 3_600_000L)
        // Encryption proves this is our old snapshot, not that the upstream
        // account remains authorized today: never label it a live Current.
        assertTrue(resumed is CampusRunViewState.Stale)
        assertNull((resumed as CampusRunViewState.Stale).reason)
        assertEquals(now, resumed.snapshot.capturedAtEpochMillis)
        assertEquals(1, source.semesterReads)
        assertEquals(1, cache.savedCount)
    }

    @Test fun transientKeystoreFailureCanRestoreOfflineWithin72Hours() = runBlocking {
        val checkpoints = Checkpoints()
        val cache = MemoryCache()
        val source = ReadSource(alice)
        val original = coordinator(source, checkpoints, cache)
        assertTrue(original.refreshSummaryIfDue(alice, now)
            is CampusRunViewState.Current)
        assertEquals(1, source.semesterReads)

        cache.failKeystoreRead = true
        val restarted = coordinator(source, checkpoints, cache)
        val unavailable = restarted.refreshSummaryIfDue(alice, now + 10_000L)
        assertTrue(unavailable is CampusRunViewState.NotConnected)
        assertEquals(1, source.semesterReads)
        assertFalse(cache.cleared)

        cache.failKeystoreRead = false
        val recovered = restarted.refreshSummaryIfDue(alice, now + 20_000L)
        assertTrue(recovered is CampusRunViewState.Stale)
        assertEquals(now,
            (recovered as CampusRunViewState.Stale).snapshot.capturedAtEpochMillis)
        assertEquals(1, source.semesterReads)
        assertEquals(1, checkpoints.attempts.size)
        assertFalse(cache.cleared)
    }

    @Test fun at72HoursFailedReadLeavesCacheButDoesNotRetry() = runBlocking {
        val checkpoints = Checkpoints()
        val cache = MemoryCache()
        val source = ReadSource(alice)
        val coordinator = coordinator(source, checkpoints, cache)
        coordinator.refreshSummaryIfDue(alice, now)
        source.failNetwork = true
        val due = now + CampusRun72HourRequestBudget.THREE_DAYS_MILLIS
        val failed = coordinator.refreshSummaryIfDue(alice, due)
        assertTrue(failed is CampusRunViewState.Stale)
        assertEquals(CampusRunFailure.NETWORK, (failed as CampusRunViewState.Stale).reason)
        assertEquals(1, cache.savedCount)
        assertEquals(now, cache.snapshot?.capturedAtEpochMillis)
        assertEquals(2, source.semesterReads)

        coordinator.refreshSummaryIfDue(alice, due + 1_000L)
        assertEquals(2, source.semesterReads)
    }

    @Test fun accountSwitchCannotExposePriorAccountAndRevocationClearsCache() = runBlocking {
        val checkpoints = Checkpoints()
        val cache = MemoryCache()
        val source = ReadSource(alice)
        val coordinator = coordinator(source, checkpoints, cache)
        assertTrue(coordinator.refreshSummaryIfDue(alice, now) is CampusRunViewState.Current)
        // The source is still Alice, so a Bob lookup is not authorized.
        val switched = coordinator.refreshSummaryIfDue(bob, now + 1_000L)
        assertTrue(switched is CampusRunViewState.Unavailable)
        assertEquals(CampusRunFailure.NOT_AUTHORIZED,
            (switched as CampusRunViewState.Unavailable).reason)
        assertNull(cache.snapshot)
        assertTrue(cache.cleared)
        assertEquals(1, source.semesterReads)
    }

    @Test fun signingOutClearsPrivateCacheButPreservesReadBudget() = runBlocking {
        val checkpoints = Checkpoints()
        val cache = MemoryCache()
        val source = ReadSource(alice)
        val coordinator = coordinator(source, checkpoints, cache)
        coordinator.refreshSummaryIfDue(alice, now)
        coordinator.clearOnSignOut()
        assertNull(cache.snapshot)
        assertTrue(cache.cleared)
        assertFalse(CampusRun72HourRequestBudget(checkpoints)
            .reserveIfDue(alice, now + 1_000L))
    }
    @Test fun cacheWithinOneHourStillStaysStaleUntilActualAuthorizedRefresh() = runBlocking {
        val checkpoints = Checkpoints()
        val cache = MemoryCache()
        val source = ReadSource(alice)
        val one = coordinator(source, checkpoints, cache)
        assertTrue(one.refreshSummaryIfDue(alice, now) is CampusRunViewState.Current)

        val newProcess = coordinator(source, checkpoints, cache)
        val cached = newProcess.refreshSummaryIfDue(alice, now + 60_000L)
        assertTrue(cached is CampusRunViewState.Stale)
        assertEquals(1, source.semesterReads)
        val after72h = newProcess.refreshSummaryIfDue(
            alice, now + CampusRun72HourRequestBudget.THREE_DAYS_MILLIS
        )
        assertTrue(after72h is CampusRunViewState.Current)
        assertEquals(2, source.semesterReads)
        assertEquals(now + CampusRun72HourRequestBudget.THREE_DAYS_MILLIS,
            (after72h as CampusRunViewState.Current).snapshot.capturedAtEpochMillis)
    }


    @Test fun summaryThenHistoryUsesOneCompleteTransaction() = runBlocking {
        val checkpoints = Checkpoints()
        val cache = MemoryCache()
        val source = ReadSource(alice)
        val coordinator = coordinator(source, checkpoints, cache)
        val summary = coordinator.refreshSummaryIfDue(alice, now) as CampusRunViewState.Current
        assertTrue(summary.snapshot.historyComplete)
        val history = coordinator.refreshHistoryIfDue(alice, now + 1000L)
            as CampusRunViewState.Current
        assertEquals(summary.snapshot, history.snapshot)
        assertEquals(1, source.semesterReads)
        assertEquals(1, source.historyReads)
        assertEquals(1, cache.savedCount)
        assertEquals(1, checkpoints.attempts.size)
    }

    @Test fun historyFailurePreservesPriorCompleteSnapshotAndBudget() = runBlocking {
        val checkpoints = Checkpoints()
        val cache = MemoryCache()
        val source = ReadSource(alice)
        val c = coordinator(source, checkpoints, cache)
        val first = c.refreshIfDue(alice, now) as CampusRunViewState.Current
        source.failHistory = true
        val due = now + CampusRun72HourRequestBudget.THREE_DAYS_MILLIS
        val failed = c.refreshIfDue(alice, due) as CampusRunViewState.Stale
        assertEquals(CampusRunFailure.NETWORK, failed.reason)
        assertEquals(first.snapshot, failed.snapshot)
        assertEquals(first.snapshot, cache.snapshot)
        c.refreshHistoryIfDue(alice, due + 1000L)
        assertEquals(2, source.semesterReads)
        assertEquals(2, source.historyReads)
        assertEquals(1, cache.savedCount)
    }

}
