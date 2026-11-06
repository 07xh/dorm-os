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

    @Test fun ownerScopeRejectsUnverifiedOrMissingCloudIdentity() {
        val binding = "A".repeat(43)
        assertEquals("local", campusRunVisibleScope(false, null))
        assertNull(campusRunVisibleScope(true, null))
        assertNull(campusRunVisibleScope(false, binding))
        assertNull(campusRunVisibleScope(true, "invalid-binding"))
    }

    @Test fun ownerScopeIsStableWithinSessionAndDistinctBetweenOwners() {
        val one = "A".repeat(43)
        val two = "B".repeat(43)
        val a = campusRunVisibleScope(true, one)
        val aAgain = campusRunVisibleScope(true, one)
        val b = campusRunVisibleScope(true, two)
        assertTrue(a!!.matches(Regex("^owner_[a-f0-9]{64}$")))
        assertEquals(a, aAgain)
        assertFalse(a == b)
        assertFalse(a.contains(one))
        assertFalse(a == "local")
    }

    @Test fun manualKmParserUsesExactMetersAndNeverConvertsBlankToZero() {
        assertEquals(12_340L, parseCampusRunKilometers("12.34"))
        assertEquals(5_000L, parseCampusRunKilometers("5"))
        assertEquals(0L, parseCampusRunKilometers("0.00"))
        assertNull(parseCampusRunKilometers("  "))
        rejects { parseCampusRunKilometers("-1") }
        rejects { parseCampusRunKilometers("1.999") }
        rejects { parseCampusRunKilometers("1e6") }
        rejects { parseCampusRunKilometers("1,000") }
    }

    @Test fun manualEntryIsNeverTrustedAndKeepsUnrelatedScopesSeparate() {
        val input = CampusRunManualInput("示例学期", "120.00", "19.25",
            "24.80", "38.50", "")
        val summary = input.toSnapshot(now)
        assertEquals(CampusRunProvenance.USER_IMPORT, summary.provenance)
        assertFalse(summary.ownerMatched)
        assertEquals("示例学期", summary.termLabel)
        assertEquals(120_000L, summary.targetMeters)
        assertEquals(19_250L, summary.effectiveMeters)
        assertEquals(24_800L, summary.rawMeters)
        assertEquals(38_500L, summary.dashboardAccumulatedMeters)
        assertNull(summary.weeklyMeters)
        assertNull(summary.totalRunCount)
        assertFalse(summary.historyComplete)
        assertTrue(summary.records.isEmpty())
    }

    @Test fun incompleteOrInconsistentManualInputFailsWithoutFabricatingZeros() {
        rejects { CampusRunManualInput("", "", "", "", "", "").toSnapshot(now) }
        rejects { CampusRunManualInput("", "0", "1", "2", "", "").toSnapshot(now) }
        rejects { CampusRunManualInput("", "100", "9", "8", "", "").toSnapshot(now) }
        val partial = CampusRunManualInput("", "90", "", "", "", "").toSnapshot(now)
        assertNull(partial.effectiveMeters)
        assertNull(partial.remainingMeters())
        assertNull(partial.progressPercent())
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

    @Test fun screenshotMetricsStayInSeparateScopes() {
        // Fully synthetic fixture: no personal IDs, screenshots, or HAR values.
        val values = validateCampusRunSnapshot(sample(
            target = 120_000L, effective = 15_000L, raw = 24_500L
        ).copy(
            dashboardAccumulatedMeters = 46_000L,
            weeklyMeters = 0L,
            totalRunCount = null,
            effectiveRunCount = null
        ), now)
        assertEquals(105_000L, values.remainingMeters())
        assertEquals(12.5, values.progressPercent()!!, 0.0001)
        assertEquals(24_500L, values.rawMeters)
        assertEquals(46_000L, values.dashboardAccumulatedMeters)
        assertEquals(0L, values.weeklyMeters)
        assertNull(values.totalRunCount) // UI "--次" is unknown, never zero.
        assertNull(values.effectiveRunCount)
    }

    @Test fun validityAndAnomalyRemainIndependentOfDisplayedDistance() {
        val invalid = CampusRunRecord(
            "uncredited-1", now - 500_000L,
            rawMeters = 2_500L, effectiveMeters = null,
            durationSeconds = 980L, status = CampusRunRecordStatus.INVALID,
            kind = CampusRunRecordKind.SCORE_RUN, hasAnomaly = true
        )
        val result = validateCampusRunSnapshot(sample(
            records = listOf(invalid)
        ), now)
        assertEquals(CampusRunRecordStatus.INVALID, result.records[0].status)
        assertEquals(CampusRunRecordKind.SCORE_RUN, result.records[0].kind)
        assertEquals(true, result.records[0].hasAnomaly)
        assertNull(result.records[0].effectiveMeters)
        // Nothing about a distance alone establishes a credited result.
        assertEquals(2_500L, result.effectiveMeters)
    }

    @Test fun separateDashboardMetricsAndOfficialCountsAreValidated() {
        rejects { validateCampusRunSnapshot(
            sample().copy(weeklyMeters = -10L), now) }
        rejects { validateCampusRunSnapshot(
            sample().copy(dashboardAccumulatedMeters = -1L), now) }
        rejects { validateCampusRunSnapshot(
            sample().copy(totalRunCount = -1), now) }
        rejects { validateCampusRunSnapshot(
            sample().copy(totalRunCount = 2, effectiveRunCount = 3), now) }
        val zero = validateCampusRunSnapshot(
            sample().copy(totalRunCount = 0, effectiveRunCount = 0), now)
        assertEquals(0, zero.totalRunCount)
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
