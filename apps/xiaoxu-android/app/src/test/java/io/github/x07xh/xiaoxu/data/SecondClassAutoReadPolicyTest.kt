package io.github.x07xh.xiaoxu.data

import org.junit.Assert.*
import org.junit.Test

class SecondClassAutoReadPolicyTest {
    private fun due(enabled: Boolean = true, background: Boolean = false,
        backgroundAllowed: Boolean = false, bound: Boolean = true, interactive: Boolean = false,
        now: Long = 2_000_000, saved: Long = 1_000_000, attempted: Long = 0) =
        SecondClassAutoReadPolicy.shouldRead(enabled, backgroundAllowed, background,
            bound, interactive, now, saved, attempted)
    @Test fun priorConfirmedReadAllowsForegroundWithoutAnActivity() {
        assertTrue(due())
        assertFalse(due(bound = false)); assertFalse(due(enabled = false))
        assertFalse(due(interactive = true)); assertFalse(due(saved = 0))
    }
    @Test fun backgroundNeedsIndependentOptIn() {
        assertFalse(due(background = true))
        assertTrue(due(background = true, backgroundAllowed = true))
        assertFalse(due(enabled = false, background = true, backgroundAllowed = true))
    }
    @Test fun savesFailuresAndClockRollbackDoNotCausePollingLoops() {
        assertFalse(due(saved = 1_999_999)); assertFalse(due(attempted = 1_999_999))
        assertFalse(due(saved = 2_000_001)); assertFalse(due(attempted = 2_000_001))
        assertTrue(due(saved = 1_400_000, attempted = 1_940_000))
    }
    @Test fun saveRequiresThePreviouslyConfirmedHash() {
        assertTrue(SecondClassAutoReadPolicy.matchesConfirmedAccount("a".repeat(64), "a".repeat(64)))
        for (pair in listOf(null to null, "a".repeat(64) to null,
            "a".repeat(64) to "b".repeat(64), "x" to "x"))
            assertFalse(SecondClassAutoReadPolicy.matchesConfirmedAccount(pair.first, pair.second))
    }
}
