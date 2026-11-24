package io.github.x07xh.xiaoxu.data

import org.junit.Assert.*
import org.junit.Test

class CampusUtilitiesSnapshotCodecTest {
    private val sample = CampusUtilityMonthResult(
        CampusUtilityKind.ELECTRIC, "2026-11", null, "12.50", 3, "v2", 100_000_000L)

    @Test fun roundTripPreservesExactMonthlyUsageAndTimestamp() {
        assertEquals(sample, CampusUtilitiesSnapshotCodec.decode(
            CampusUtilitiesSnapshotCodec.encode(sample)))
    }

    @Test fun unknownValuesRemainUnknown() {
        for (value in listOf(sample.copy(totalUsed = null), sample.copy(recordCount = null))) {
            assertEquals(value, CampusUtilitiesSnapshotCodec.decode(
                CampusUtilitiesSnapshotCodec.encode(value)))
        }
    }

    @Test fun invalidOrSensitiveSnapshotsAreRejected() {
        val invalid = listOf(
            sample.copy(accountNum = "private-account"),
            sample.copy(utility = CampusUtilityKind.WATER),
            sample.copy(totalUsed = null, recordCount = null),
            sample.copy(totalUsed = "-1"),
            sample.copy(totalUsed = "NaN"),
            sample.copy(recordCount = -1),
            sample.copy(fetchedAtMillis = 0),
            sample.copy(detailVersion = "unknown")
        )
        for (value in invalid) {
            try {
                CampusUtilitiesSnapshotCodec.encode(value)
                fail("Expected invalid snapshot to be rejected")
            } catch (_: IllegalArgumentException) {}
        }
    }

    @Test fun corruptSchemaCannotBePresentedAsOfficialUsage() {
        try {
            CampusUtilitiesSnapshotCodec.decode("""{"schema":"other"}""".toByteArray())
            fail("Expected corrupt schema rejection")
        } catch (_: IllegalArgumentException) {}
    }
}
