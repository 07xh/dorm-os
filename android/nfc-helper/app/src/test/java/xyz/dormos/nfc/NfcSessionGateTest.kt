package xyz.dormos.nfc

import org.junit.Assert.*
import org.junit.Test

class NfcSessionGateTest {
    @Test fun duplicateTagIntentsAreSuppressedWithoutTimersOrPolling() {
        val gate=NfcIntentGate(2500)
        assertTrue(gate.accept(10000))
        assertFalse(gate.accept(10000))
        assertFalse(gate.accept(10001))
        assertFalse(gate.accept(12499))
        assertTrue(gate.accept(12500))
        assertTrue(gate.accept(15000))
    }
    @Test fun elapsedClockResetDoesNotPermanentBlockNextTag() {
        val gate=NfcIntentGate(2500)
        assertTrue(gate.accept(9000))
        assertTrue(gate.accept(300))
        assertFalse(gate.accept(301))
        assertFalse(gate.accept(-1))
    }
    @Test fun differentTagsCanBeScannedImmediatelyWithoutCrossTagBlock() {
        val gate = NfcIntentGate(2500)
        assertTrue(gate.accept("v2:tag:one", 10_000))
        assertFalse(gate.accept("v2:tag:one", 10_100))
        assertTrue(gate.accept("v2:tag:two", 10_100))
        assertFalse(gate.accept("v2:tag:two", 10_101))
        assertTrue(gate.accept("v2:tag:one", 12_500))
    }

    @Test fun tagDebounceRemainsBoundedAndRejectsMalformedKeys() {
        val gate = NfcIntentGate(2500)
        assertFalse(gate.accept("", 10))
        assertFalse(gate.accept("x".repeat(81), 10))
        for (i in 0..40) assertTrue(gate.accept("tag-" + i, 10_000L + i))
        assertTrue(gate.accept("tag-0", 10_050)) // Evicted; no unbounded memory.
        assertFalse(gate.accept("tag-0", 10_051))
        assertTrue(gate.accept("tag-0", 20)) // Clock reset cannot permanently block it.
    }

    @Test fun cancellationInvalidatesDelayedNfcWrites() {
        val gate=NfcWriteEpoch()
        val one=gate.begin()
        assertTrue(gate.isCurrent(one))
        gate.invalidate()
        assertFalse(gate.isCurrent(one))
        val two=gate.begin()
        assertFalse(gate.isCurrent(one))
        assertTrue(gate.isCurrent(two))
        gate.invalidate()
        assertFalse(gate.isCurrent(two))
    }
}
