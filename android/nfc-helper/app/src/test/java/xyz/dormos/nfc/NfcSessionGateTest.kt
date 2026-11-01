package xyz.dormos.nfc

import org.junit.Assert.*
import org.junit.Test

class NfcSessionGateTest {
    @Test fun duplicateTagIntentsAreSuppressedWithoutTimersOrPolling() {
        val gate = NfcIntentGate(2500)
        assertTrue(gate.accept("tag-one", 10000))
        assertFalse(gate.accept("tag-one", 10001))
        assertFalse(gate.accept("tag-one", 12499))
        assertTrue(gate.accept("tag-one", 12500))
    }

    @Test fun differentTagsCanBeScannedImmediately() {
        val gate = NfcIntentGate(2500)
        assertTrue(gate.accept("tag-one", 10000))
        assertTrue(gate.accept("tag-two", 10001))
        assertFalse(gate.accept("tag-two", 10002))
    }

    @Test fun elapsedClockResetDoesNotPermanentlyBlockNextTag() {
        val gate = NfcIntentGate(2500)
        assertTrue(gate.accept("tag-one", 9000))
        assertTrue(gate.accept("tag-one", 300))
        assertFalse(gate.accept("tag-one", 301))
        assertFalse(gate.accept("tag-one", -1))
    }
}
