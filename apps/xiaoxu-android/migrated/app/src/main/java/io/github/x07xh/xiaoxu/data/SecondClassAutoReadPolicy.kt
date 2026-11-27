package io.github.x07xh.xiaoxu.data

object SecondClassAutoReadPolicy {
    const val AUTO_SYNC_HOURS = 12L
    const val AUTO_SYNC_INTERVAL_MS = AUTO_SYNC_HOURS * 60 * 60 * 1000
    const val MANUAL_COOLDOWN_MS = 60_000L

    fun requiresReconnect(httpStatus: Int, stage: String = ""): Boolean =
        httpStatus == 401 && stage != "activity_http"

    fun shouldRead(enabled: Boolean, backgroundAllowed: Boolean, background: Boolean,
        bound: Boolean, interactive: Boolean, now: Long, savedAt: Long, attemptedAt: Long,
        userRequested: Boolean = false): Boolean =
        (enabled || userRequested) && (!background || backgroundAllowed) &&
            (!userRequested || !background) && (bound || userRequested) && !interactive &&
            (userRequested || savedAt > 0 && now >= savedAt &&
                now - savedAt >= AUTO_SYNC_INTERVAL_MS) &&
            (attemptedAt == 0L || now >= attemptedAt &&
                now - attemptedAt >= (if (userRequested) MANUAL_COOLDOWN_MS else AUTO_SYNC_INTERVAL_MS))

    fun matchesConfirmedAccount(expected: String?, actual: String?): Boolean =
        expected != null && expected.matches(Regex("^[a-f0-9]{64}$")) && expected == actual
}
