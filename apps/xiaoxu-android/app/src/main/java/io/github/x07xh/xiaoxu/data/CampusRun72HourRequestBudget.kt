package io.github.x07xh.xiaoxu.data

import android.content.Context
import kotlinx.coroutines.sync.Mutex

/**
 * Local request budget for the owner's already-authorized campus-running reads.
 * One reservation covers an entire summary/history refresh, NOT one HTTP request.
 *
 * Reserve BEFORE making any network call, so network errors or process restarts
 * cannot cause immediate re-queries. This never logs in, refreshes credentials,
 * creates devices or schedules background jobs.
 */
interface CampusRunRequestCheckpointStore {
    fun lastAttemptMillis(subjectFingerprint: String): Long?
    /** Persist atomically; false means no upstream call is permitted. */
    fun saveAttemptMillis(subjectFingerprint: String, epochMillis: Long): Boolean
}

class CampusRun72HourRequestBudget(
    private val store: CampusRunRequestCheckpointStore,
    private val minIntervalMillis: Long = THREE_DAYS_MILLIS
) {
    init { require(minIntervalMillis >= THREE_DAYS_MILLIS) }

    @Synchronized
    fun reserveIfDue(subjectFingerprint: String, nowEpochMillis: Long): Boolean {
        require(Regex("^[a-f0-9]{64}$").matches(subjectFingerprint))
        require(nowEpochMillis > 0L)
        val previous = store.lastAttemptMillis(subjectFingerprint)
        // A backwards clock or future checkpoint must NOT silently bypass the budget.
        if (previous != null &&
            (previous >= nowEpochMillis ||
                nowEpochMillis - previous < minIntervalMillis)) return false
        return store.saveAttemptMillis(subjectFingerprint, nowEpochMillis)
    }

    companion object {
        const val THREE_DAYS_MILLIS: Long = 72L * 60L * 60L * 1_000L
    }
}

/** Only a fingerprint and the last attempted read timestamp, never credentials. */
class AndroidCampusRunRequestCheckpointStore(context: Context) :
    CampusRunRequestCheckpointStore {
    private val prefs = context.applicationContext.getSharedPreferences(
        "xiaoxu_campus_run_read_budget_v1", Context.MODE_PRIVATE
    )

    override fun lastAttemptMillis(subjectFingerprint: String): Long? =
        if (!prefs.contains("attempt_$subjectFingerprint")) null
        else try {
            prefs.getLong("attempt_$subjectFingerprint", Long.MAX_VALUE)
        } catch (_: ClassCastException) {
            Long.MAX_VALUE // damaged checkpoint: fail closed
        }

    override fun saveAttemptMillis(
        subjectFingerprint: String, epochMillis: Long
    ): Boolean = prefs.edit().putLong("attempt_$subjectFingerprint", epochMillis).commit()
}

/**
 * Opt-in entry point for a future approved, truly connected read-only adapter.
 * Call on app foreground/navigation only. No background polling, no automatic
 * login/re-login and no retry on auth error. Both summary and history share
 * the SAME 72-hour budget to prevent a detail screen from bypassing the cap.
 *
 * Note: production must also restore the owner's trusted cached snapshot on
 * app restart; the existing Query gate itself holds only an in-memory snapshot.
 */
class CampusRun72HourReadCoordinator(
    private val query: CampusRunReadOnlyQuery,
    private val budget: CampusRun72HourRequestBudget,
    /** Optional, already owner-scoped Android Keystore cache. */
    private val trustedCache: CampusRunTrustedSnapshotCache? = null
) {
    private val mutex = Mutex()
    private var currentSubject: String? = null

    suspend fun clearOnSignOut() {
        mutex.lock()
        try {
            query.clear()
            currentSubject = null
            // Do not keep personally identified official running history after
            // 校序 owner sign-out, even while the read budget persists.
            trustedCache?.clear()
        } finally {
            mutex.unlock()
        }
    }

    suspend fun refreshSummaryIfDue(
        expectedSubjectFingerprint: String, nowEpochMillis: Long
    ): CampusRunViewState = refreshIfDue(expectedSubjectFingerprint, nowEpochMillis, false)

    suspend fun refreshHistoryIfDue(
        expectedSubjectFingerprint: String, nowEpochMillis: Long
    ): CampusRunViewState = refreshIfDue(expectedSubjectFingerprint, nowEpochMillis, true)

    private suspend fun refreshIfDue(
        fingerprint: String, now: Long, history: Boolean
    ): CampusRunViewState {
        mutex.lock()
        try {
            // Switching accounts must not reveal in-memory data from a prior
            // source. AEAD in the cache independently enforces that binding.
            if (currentSubject != fingerprint) {
                query.clear()
                currentSubject = fingerprint
                try {
                    trustedCache?.load(fingerprint, now)?.let {
                        query.restoreTrustedSnapshot(fingerprint, it, now)
                    }
                } catch (_: Exception) {
                    // A broken or inaccessible cache is never authorization.
                    query.clear()
                }
            }
            if (!budget.reserveIfDue(fingerprint, now)) return query.state(now)

            val result = if (history) query.refresh(fingerprint, now)
                else query.refreshSummary(fingerprint, now)
            when (result) {
                is CampusRunViewState.Current -> {
                    // The successful official read may still be shown if disk
                    // persistence fails; a subsequent launch must never fake
                    // a stored snapshot.
                    try {
                        trustedCache?.save(fingerprint, result.snapshot, now)
                    } catch (_: Exception) {
                        // No secret data is logged. The live result is valid.
                    }
                }
                is CampusRunViewState.Unavailable -> {
                    if (result.reason == CampusRunFailure.NOT_AUTHORIZED) {
                        trustedCache?.clear()
                    }
                }
                else -> Unit // A failed read must not replace a good cache.
            }
            return result
        } finally {
            mutex.unlock()
        }
    }
}
