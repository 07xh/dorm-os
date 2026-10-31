package xyz.dormos.nfc

import java.util.concurrent.atomic.AtomicInteger

/** Monotonic event gate: suppress NFC intent storms while allowing explicit manual checks. */
class NfcIntentGate(private val quietPeriodMs: Long = 2500L) {
    private var lastAcceptedAt: Long = Long.MIN_VALUE
    @Synchronized
    fun accept(nowElapsedMs: Long): Boolean {
        if (nowElapsedMs < 0L) return false
        if (lastAcceptedAt != Long.MIN_VALUE && nowElapsedMs >= lastAcceptedAt &&
            nowElapsedMs - lastAcceptedAt < quietPeriodMs) return false
        lastAcceptedAt = nowElapsedMs
        return true
    }
}

/**
 * Every waiting-write operation has its own epoch.
 * Cancelling invalidates delayed callbacks; it does not claim to stop a physical IO already underway.
 */
class NfcWriteEpoch {
    private val revision = AtomicInteger()
    fun begin(): Int = revision.incrementAndGet()
    fun invalidate() { revision.incrementAndGet() }
    fun isCurrent(epoch: Int): Boolean = revision.get() == epoch
}
