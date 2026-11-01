package xyz.dormos.nfc

/** Suppresses repeat NFC dispatch storms without timers, polling or background work. */
class NfcIntentGate(private val quietPeriodMs: Long = 2500L) {
    private val lastByProfile = LinkedHashMap<String, Long>()

    init { require(quietPeriodMs >= 0L) }

    @Synchronized
    fun accept(profileKey: String, nowElapsedMs: Long): Boolean {
        if (nowElapsedMs < 0L || profileKey.isBlank() || profileKey.length > 80) return false
        val last = lastByProfile[profileKey]
        if (last != null && nowElapsedMs >= last && nowElapsedMs - last < quietPeriodMs) return false
        if (last != null && nowElapsedMs < last) lastByProfile.clear()
        lastByProfile.remove(profileKey)
        lastByProfile[profileKey] = nowElapsedMs
        while (lastByProfile.size > 32) lastByProfile.remove(lastByProfile.keys.first())
        return true
    }
}
