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
