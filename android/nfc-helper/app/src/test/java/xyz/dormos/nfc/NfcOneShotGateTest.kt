package xyz.dormos.nfc

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class NfcOneShotGateTest {
    @Test fun oneArmCanOnlyBeClaimedOnce() {
        val gate = NfcOneShotGate<String>()
        assertTrue(gate.arm("first"))
        assertEquals("first", gate.claim())
        assertNull(gate.claim())
        assertTrue(gate.isProcessing())
        gate.finish()
        assertFalse(gate.isProcessing())
    }

    @Test fun cancelBeforeClaimPreventsWrite() {
        val gate = NfcOneShotGate<String>()
        assertTrue(gate.arm("draft"))
        assertTrue(gate.cancelPending())
        assertNull(gate.claim())
    }

    @Test fun claimedRequestCannotBeCancelledOrOverwritten() {
        val gate = NfcOneShotGate<String>()
        assertTrue(gate.arm("already-claimed"))
        assertEquals("already-claimed", gate.claim())
        assertFalse(gate.cancelPending())
        assertFalse(gate.arm("unexpected-second-write"))
        gate.finish()
        assertNull(gate.claim())
        assertTrue(gate.arm("new"))
        assertEquals("new", gate.claim())
        gate.finish()
    }

    @Test fun updatingAnUnclaimedDraftKeepsOnlyLatestRequest() {
        val gate = NfcOneShotGate<String>()
        assertTrue(gate.arm("draft A"))
        assertTrue(gate.arm("draft B"))
        assertEquals("draft B", gate.claim())
        gate.finish()
    }

    @Test fun competingScanCallbacksCannotBothClaimSameTagWrite() {
        val gate = NfcOneShotGate<String>()
        gate.arm("one-operation")
        val start = CountDownLatch(1)
        val done = CountDownLatch(12)
        val claimed = AtomicInteger(0)
        repeat(12) {
            Thread {
                try {
                    start.await()
                    if (gate.claim() != null) claimed.incrementAndGet()
                } finally { done.countDown() }
            }.start()
        }
        start.countDown()
        assertTrue("worker threads must finish", done.await(5, TimeUnit.SECONDS))
        assertEquals(1, claimed.get())
        gate.finish()
    }

    @Test fun cancellationRacingWithClaimNeverCreatesTwoOperations() {
        repeat(30) {
            val gate = NfcOneShotGate<String>()
            gate.arm("once")
            val start = CountDownLatch(1)
            val done = CountDownLatch(2)
            val successes = AtomicInteger(0)
            Thread {
                try {
                    start.await()
                    if (gate.claim() != null) successes.incrementAndGet()
                } finally { done.countDown() }
            }.start()
            Thread {
                try {
                    start.await()
                    gate.cancelPending()
                } finally { done.countDown() }
            }.start()
            start.countDown()
            assertTrue(done.await(5, TimeUnit.SECONDS))
            assertTrue(successes.get() in 0..1)
            if (successes.get() == 1) assertTrue(gate.isProcessing())
            gate.finish()
        }
    }
}
