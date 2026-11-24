package io.github.x07xh.xiaoxu.data

import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A future officially authorized binding supplies the same owner and room
 * scope for its entire native session. No credentials are stored here.
 */
internal data class CampusUtilitiesSyncBinding(
    val scope: String,
    val room: CampusUtilityRoom
) {
    init { require(Regex("^[a-f0-9]{64}$").matches(scope)) }
}

/** Implementations must bind snapshots to scope, encrypt personal data,
 * preserve unreadable ciphertext, and atomically persist the attempt before
 * network I/O. A month change must not reset the attempt budget.
 */
internal interface CampusUtilitiesSyncStore {
    fun load(scope: String, month: YearMonth): CampusUtilityMonthResult?
    fun reserveIfDue(scope: String, now: Long, interval: Long): Boolean
    fun save(scope: String, result: CampusUtilityMonthResult)
    fun clear(scope: String)
}

internal sealed interface CampusUtilitiesSyncState {
    data object Disabled : CampusUtilitiesSyncState
    data object NotAuthorized : CampusUtilitiesSyncState
    data object StorageUnavailable : CampusUtilitiesSyncState
    data class Cached(val result: CampusUtilityMonthResult?) : CampusUtilitiesSyncState
    data class Current(val result: CampusUtilityMonthResult, val persisted: Boolean = true) : CampusUtilitiesSyncState
    data class Failed(val cached: CampusUtilityMonthResult?,
        val reason: CampusUtilitiesFailure) : CampusUtilitiesSyncState
}

/**
 * One electricity read transaction. A foreground action or a future
 * WorkManager job can call this same entry point; no worker is registered
 * until a verified native school authorization and encrypted store exist.
 */
internal class CampusUtilitiesSyncCoordinator(
    private val currentBinding: suspend () -> CampusUtilitiesSyncBinding?,
    private val client: CampusUtilitiesReadOnlyClient,
    private val store: CampusUtilitiesSyncStore
) {
    suspend fun refreshIfDue(
        enabled: Boolean,
        month: YearMonth,
        now: Long,
        intervalMillis: Long = DEFAULT_INTERVAL_MILLIS
    ): CampusUtilitiesSyncState = withContext(Dispatchers.IO) { PROCESS_LOCK.withLock {
        if (!enabled) return@withLock CampusUtilitiesSyncState.Disabled
        require(now > 0 && intervalMillis >= DEFAULT_INTERVAL_MILLIS)
        val binding = currentBinding()
            ?: return@withLock CampusUtilitiesSyncState.NotAuthorized
        val cached = try {
            store.load(binding.scope, month)
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) {
            return@withLock CampusUtilitiesSyncState.StorageUnavailable
        }
        if (currentBinding() != binding) {
            return@withLock CampusUtilitiesSyncState.NotAuthorized
        }
        val reserved = try {
            store.reserveIfDue(binding.scope, now, intervalMillis)
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) {
            return@withLock CampusUtilitiesSyncState.StorageUnavailable
        }
        if (!reserved) {
            return@withLock CampusUtilitiesSyncState.Cached(cached)
        }
        val result = try {
            client.readMonth(CampusUtilityKind.ELECTRIC, binding.room, month)
        } catch (e: CampusUtilitiesException) {
            if (e.failure == CampusUtilitiesFailure.LOGIN_REQUIRED ||
                e.failure == CampusUtilitiesFailure.ACCESS_DENIED) {
                try { store.clear(binding.scope) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) {
                    return@withLock CampusUtilitiesSyncState.StorageUnavailable
                }
                return@withLock CampusUtilitiesSyncState.NotAuthorized
            }
            return@withLock if (currentBinding() == binding)
                CampusUtilitiesSyncState.Failed(cached, e.failure)
            else CampusUtilitiesSyncState.NotAuthorized
        }
        if (currentBinding() != binding) {
            return@withLock CampusUtilitiesSyncState.NotAuthorized
        }
        // Do not publish an empty or unverified balance as a successful read.
        // This client reads monthly usage, not remaining credit.
        if (result.utility != CampusUtilityKind.ELECTRIC ||
            result.month != month.toString() ||
            (result.totalUsed == null && result.recordCount == null)) {
            return@withLock CampusUtilitiesSyncState.Failed(
                cached, CampusUtilitiesFailure.UNEXPECTED_RESPONSE)
        }
        val minimal = result.copy(accountNum = null)
        try { CampusUtilitiesSnapshotCodec.encode(minimal) }
        catch (_: IllegalArgumentException) {
            return@withLock CampusUtilitiesSyncState.Failed(
                cached, CampusUtilitiesFailure.UNEXPECTED_RESPONSE)
        }
        val persisted = try {
            store.save(binding.scope, minimal)
            true
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { false }
        if (currentBinding() != binding) {
            return@withLock CampusUtilitiesSyncState.NotAuthorized
        }
        CampusUtilitiesSyncState.Current(minimal, persisted)
    } }

    companion object {
        const val DEFAULT_INTERVAL_MILLIS = 6L * 60 * 60 * 1000
        private val PROCESS_LOCK = Mutex()
    }
}
