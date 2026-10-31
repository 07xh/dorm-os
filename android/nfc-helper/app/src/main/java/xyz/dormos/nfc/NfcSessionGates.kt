package xyz.dormos.nfc

import java.util.concurrent.atomic.AtomicInteger

/**
 * Suppresses repeat-dispatch storms for one NFC profile, while allowing a second
 * distinct sticker immediately. Uses elapsedRealtime, never a background timer.
 * Tracks at most 32 recently scanned profile IDs in memory.
 */
class NfcIntentGate(private val quietPeriodMs: Long = 2500L) {
    private val lastByProfile = LinkedHashMap<String, Long>()
    init { require(quietPeriodMs >= 0L) }

    /** Compatibility entry for older tests and actions. */
    fun accept(nowElapsedMs: Long): Boolean = accept("legacy", nowElapsedMs)

    @Synchronized
    fun accept(profileKey: String, nowElapsedMs: Long): Boolean {
        if (nowElapsedMs < 0L || profileKey.isBlank() || profileKey.length > 80) return false
        val last = lastByProfile[profileKey]
        // Elapsed clock can reset after reboot; do not trap the profile forever.
        if (last != null && nowElapsedMs >= last && nowElapsedMs - last < quietPeriodMs) return false
        if (last != null && nowElapsedMs < last) lastByProfile.clear()
        lastByProfile.remove(profileKey)
        lastByProfile[profileKey] = nowElapsedMs
        while (lastByProfile.size > 32) {
            lastByProfile.remove(lastByProfile.keys.first())
        }
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
