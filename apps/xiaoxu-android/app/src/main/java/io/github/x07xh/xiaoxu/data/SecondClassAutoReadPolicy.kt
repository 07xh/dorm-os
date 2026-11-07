package io.github.x07xh.xiaoxu.data

object SecondClassAutoReadPolicy {
    fun shouldRead(enabled: Boolean, backgroundAllowed: Boolean, background: Boolean,
        bound: Boolean, interactive: Boolean, now: Long, savedAt: Long, attemptedAt: Long): Boolean =
        enabled && (!background || backgroundAllowed) && bound && !interactive &&
            savedAt > 0 && now >= savedAt && now - savedAt >= 600_000 &&
            (attemptedAt == 0L || now >= attemptedAt && now - attemptedAt >= 60_000)

    fun matchesConfirmedAccount(expected: String?, actual: String?): Boolean =
        expected != null && expected.matches(Regex("^[a-f0-9]{64}$")) && expected == actual
}
