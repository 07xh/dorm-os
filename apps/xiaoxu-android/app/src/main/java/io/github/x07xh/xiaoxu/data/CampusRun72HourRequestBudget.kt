package io.github.x07xh.xiaoxu.data

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.CancellationException

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

    fun reserveIfDue(subjectFingerprint: String, nowEpochMillis: Long): Boolean =
        synchronized(PROCESS_LOCK) {
            require(Regex("^[a-f0-9]{64}$").matches(subjectFingerprint))
            require(nowEpochMillis > 0L)
            // The lock is shared between budget instances in this process:
            // check + durable commit is one reservation, not two racing reads.
            // This coordinator is deliberately single-process only.
            val previous = store.lastAttemptMillis(subjectFingerprint)
            if (previous != null &&
                (previous >= nowEpochMillis ||
                    nowEpochMillis - previous < minIntervalMillis)) return@synchronized false
            store.saveAttemptMillis(subjectFingerprint, nowEpochMillis)
        }

    companion object {
        const val THREE_DAYS_MILLIS: Long = 72L * 60L * 60L * 1_000L
        private val PROCESS_LOCK = Any()
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
 * login/re-login and no retry on auth error. Summary and history use one bounded read transaction per 72 hours.
 * Either screen gets the same snapshot; a summary visit must not starve history.
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

    private companion object {
        val SUBJECT_FINGERPRINT = Regex("^[a-f0-9]{64}$")
    }

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
    ): CampusRunViewState = refreshIfDue(expectedSubjectFingerprint, nowEpochMillis)

    suspend fun refreshHistoryIfDue(
        expectedSubjectFingerprint: String, nowEpochMillis: Long
    ): CampusRunViewState = refreshIfDue(expectedSubjectFingerprint, nowEpochMillis)

    suspend fun refreshIfDue(
        fingerprint: String, now: Long
    ): CampusRunViewState {
        mutex.lock()
        try {
            // This coordinator is an app-facing entry point. Malformed identity
            // or device time is never an exception-driven read attempt: reject
            // before disk-cache access, checkpoint reservation or network I/O.
            // Hide any earlier in-memory snapshot without deleting encrypted
            // data that might still be recoverable for the rightful owner.
            if (now <= 0L || !SUBJECT_FINGERPRINT.matches(fingerprint)) {
                query.clear()
                currentSubject = null
                return CampusRunViewState.Unavailable(
                    if (now <= 0L) CampusRunFailure.INVALID_DATA
                    else CampusRunFailure.NOT_AUTHORIZED
                )
            }
            // Switching accounts must not reveal in-memory data from a prior
            // source. AEAD in the cache independently enforces that binding.
            if (currentSubject != fingerprint) {
                query.clear()
                currentSubject = fingerprint
            }
            // A transient Keystore failure may have prevented restoration
            // earlier, while the 72-hour budget still prohibits another
            // upstream read. Permit an offline retry on the next foreground
            // visit; do NOT revalidate or promote cached data to Current.
            if (query.state(now) is CampusRunViewState.NotConnected) {
                try {
                    trustedCache?.load(fingerprint, now)?.let {
                        query.restoreTrustedSnapshot(fingerprint, it, now)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Unreadable ciphertext remains on disk for later recovery.
                    query.clear()
                }
            }
            if (!budget.reserveIfDue(fingerprint, now)) return query.state(now)

            // Both entry points refresh the same bounded summary + history snapshot.
            // A failure does not consume a second reservation or publish a partial update.
            val result = query.refresh(fingerprint, now)
            when (result) {
                is CampusRunViewState.Current -> {
                    // The successful official read may still be shown if disk
                    // persistence fails; a subsequent launch must never fake
                    // a stored snapshot.
                    try {
                        trustedCache?.save(fingerprint, result.snapshot, now)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
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
