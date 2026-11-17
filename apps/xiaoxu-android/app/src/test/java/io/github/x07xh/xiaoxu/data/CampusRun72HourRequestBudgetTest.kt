package io.github.x07xh.xiaoxu.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CampusRun72HourRequestBudgetTest {
    private val a = "a".repeat(64)
    private val b = "b".repeat(64)
    private val now = 1_800_000_000_000L

    private class FakeStore : CampusRunRequestCheckpointStore {
        val attempts = mutableMapOf<String, Long>()
        var writable = true
        override fun lastAttemptMillis(subjectFingerprint: String): Long? =
            attempts[subjectFingerprint]
        override fun saveAttemptMillis(
            subjectFingerprint: String, epochMillis: Long
        ): Boolean {
            if (!writable) return false
            attempts[subjectFingerprint] = epochMillis
            return true
        }
    }

    @Test fun oncePer72HoursEvenAfterFailureOrRestart() {
        val store = FakeStore()
        assertTrue(CampusRun72HourRequestBudget(store).reserveIfDue(a, now))
        assertFalse(CampusRun72HourRequestBudget(store).reserveIfDue(a, now + 60_000L))
        assertFalse(CampusRun72HourRequestBudget(store).reserveIfDue(
            a, now + CampusRun72HourRequestBudget.THREE_DAYS_MILLIS - 1L))
        assertTrue(CampusRun72HourRequestBudget(store).reserveIfDue(
            a, now + CampusRun72HourRequestBudget.THREE_DAYS_MILLIS))
    }

    @Test fun accountIsolationAndClockRollbackAreConservative() {
        val store = FakeStore()
        val budget = CampusRun72HourRequestBudget(store)
        assertTrue(budget.reserveIfDue(a, now))
        assertTrue(budget.reserveIfDue(b, now))
        assertFalse(budget.reserveIfDue(a, now - 1L))
        assertFalse(budget.reserveIfDue(b, now + 100_000L))
    }

    @Test fun persistenceFailureMustPreventNetworkCall() {
        val store = FakeStore()
        store.writable = false
        assertFalse(CampusRun72HourRequestBudget(store).reserveIfDue(a, now))
        assertFalse(store.attempts.containsKey(a))
    }
}
