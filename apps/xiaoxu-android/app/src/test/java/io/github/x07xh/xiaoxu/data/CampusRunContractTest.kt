package io.github.x07xh.xiaoxu.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CampusRunContractTest {
    private val now = 1_800_000_000_000L

    private fun sample(
        provenance: CampusRunProvenance = CampusRunProvenance.USER_IMPORT,
        ownerMatched: Boolean = false,
        target: Long? = 10_000L,
        effective: Long? = 2_500L,
        raw: Long? = 3_000L,
        captured: Long = now,
        historyComplete: Boolean = false,
        records: List<CampusRunRecord> = emptyList()
    ) = CampusRunSnapshot(provenance, ownerMatched, captured, null,
        target, effective, raw, historyComplete, records)

    private fun rejects(block: () -> Unit) {
        try {
            block()
            throw AssertionError("invalid campus run snapshot was accepted")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun progressUsesOnlyEffectiveSemesterDistance() {
        val result = validateCampusRunSnapshot(sample(), now)
        assertEquals(7_500L, result.remainingMeters())
        assertEquals(25.0, result.progressPercent()!!, 0.0001)
        assertFalse(result.historyComplete)
    }

    @Test fun unknownAndExcessDistanceAreNotMisreported() {
        val unknown = validateCampusRunSnapshot(
            sample(target = 10_000L, effective = null, raw = 8_000L), now)
        assertNull(unknown.remainingMeters())
        assertNull(unknown.progressPercent())

        val exceeded = validateCampusRunSnapshot(
            sample(target = 10_000L, effective = 12_000L, raw = 13_000L), now)
        assertEquals(0L, exceeded.remainingMeters())
        assertEquals(100.0, exceeded.progressPercent()!!, 0.0001)
    }

    @Test fun trustedClaimRequiresVerifiedOwnerEvidence() {
        rejects { validateCampusRunSnapshot(
            sample(provenance = CampusRunProvenance.TRUSTED_READONLY), now) }
        val trusted = validateCampusRunSnapshot(
            sample(provenance = CampusRunProvenance.TRUSTED_READONLY,
                ownerMatched = true), now)
        assertEquals(CampusRunProvenance.TRUSTED_READONLY, trusted.provenance)
    }

    @Test fun recordsCannotInflateTotalOrPretendFullPagination() {
        val activity = CampusRunRecord("record-1", now - 5_000L,
            rawMeters = 6_000L, effectiveMeters = 4_000L,
            durationSeconds = 1_800L, status = CampusRunRecordStatus.VALID)
        val result = validateCampusRunSnapshot(
            sample(records = listOf(activity), historyComplete = false), now)
        assertEquals(2_500L, result.effectiveMeters)
        assertEquals(1, result.records.size)
        assertFalse(result.historyComplete)
    }

    @Test fun importCannotAssertCompletenessWithoutOfficialPaginationProof() {
        rejects { validateCampusRunSnapshot(
            sample(historyComplete = true), now) }
        val trusted = validateCampusRunSnapshot(sample(
            provenance = CampusRunProvenance.TRUSTED_READONLY,
            ownerMatched = true, historyComplete = true), now)
        assertTrue(trusted.historyComplete)
    }

    @Test fun expiredSourceAuthorizationDiscardsPreviouslyVisiblePersonalData() {
        val gate = CampusRunSnapshotGate()
        gate.accept(sample(), now)
        val state = gate.refreshFailed(
            CampusRunFailure.NOT_AUTHORIZED, now + 1_000L)
        assertTrue(state is CampusRunViewState.Unavailable)
        assertTrue(gate.state(now + 2_000L) is CampusRunViewState.Unavailable)
        gate.clear()
        assertTrue(gate.state(now + 2_000L) is CampusRunViewState.NotConnected)
    }

    @Test fun invalidMetricsDuplicateRecordsAndFutureCaptureFailClosed() {
        rejects { validateCampusRunSnapshot(
            sample(target = 10_000L, effective = 3_100L, raw = 3_000L), now) }
        rejects { validateCampusRunSnapshot(
            sample(captured = now + 10 * 60_000L), now) }
        rejects { validateCampusRunSnapshot(
            sample(target = 0L), now) }
        rejects { validateCampusRunSnapshot(
            sample(target = null, effective = null, raw = null), now) }
        val item = CampusRunRecord("same", now - 100L, 1_000L, 800L, 600L,
            CampusRunRecordStatus.VALID)
        rejects { validateCampusRunSnapshot(
            sample(records = listOf(item, item)), now) }
        rejects { validateCampusRunSnapshot(
            sample(records = listOf(item.copy(effectiveMeters = 1_001L))), now) }
    }

    @Test fun failedRefreshKeepsOldDataButMarksItStale() {
        val gate = CampusRunSnapshotGate()
        assertTrue(gate.state(now) is CampusRunViewState.NotConnected)
        assertTrue(gate.accept(sample(), now) is CampusRunViewState.Current)
        val failure = gate.refreshFailed(CampusRunFailure.NETWORK, now + 1_000L)
        assertTrue(failure is CampusRunViewState.Stale)
        assertEquals(2_500L, (failure as CampusRunViewState.Stale)
            .snapshot.effectiveMeters)
        gate.clear()
        assertTrue(gate.state(now + 1_000L) is CampusRunViewState.NotConnected)
    }

    @Test fun oldSnapshotIsStaleAndFailureWithoutCacheIsNotConnectedFalse() {
        val gate = CampusRunSnapshotGate(maxAgeMillis = 1_000L)
        val error = gate.refreshFailed(CampusRunFailure.NOT_AUTHORIZED, now)
        assertTrue(error is CampusRunViewState.Unavailable)
        assertTrue(gate.accept(sample(captured = now - 2_000L), now)
            is CampusRunViewState.Stale)
        assertTrue(gate.state(now) is CampusRunViewState.Stale)
        gate.clear()
        assertTrue(gate.state(now) is CampusRunViewState.NotConnected)
    }
}
