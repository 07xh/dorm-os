package io.github.x07xh.xiaoxu.data

import java.io.IOException
import java.util.concurrent.CancellationException

/**
 * Boundary for an authorized, read-only upstream implementation.
 *
 * No HTTP endpoint, login, credential exchange, response decoding, or
 * Android accessibility access is implemented here. A future adapter MUST
 * obtain all three subject fingerprints from the same legitimately authorized
 * upstream identity, not from user input, a HAR replay, or an unverified token.
 */
interface CampusRunReadOnlySource {
    suspend fun authorizedSubjectFingerprint(): String
    suspend fun readSemester(): CampusRunReadSummary
    suspend fun readHistoryPage(cursor: String?): CampusRunReadPage
}

/** A SHA-256 fingerprint of the authenticated upstream subject, not an ID. */
data class CampusRunReadSummary(
    val subjectFingerprint: String,
    val termLabel: String?,
    val targetMeters: Long?,
    val effectiveMeters: Long?,
    val rawMeters: Long?,
    val dashboardAccumulatedMeters: Long? = null,
    val weeklyMeters: Long? = null,
    val totalRunCount: Int? = null,
    val effectiveRunCount: Int? = null
)

data class CampusRunReadPage(
    val subjectFingerprint: String,
    val records: List<CampusRunRecord>,
    val nextCursor: String?,
    /** May be true only when the authorized source confirms the last page. */
    val isLastPage: Boolean
)

/** Expected adapter errors; never attach credentials or response bodies. */
class CampusRunSourceException(val failure: CampusRunFailure) : Exception()

/**
 * Read-only refresh: identity -> semester -> optional bounded history -> verify.
 * A partial or failed read must not overwrite the last accepted snapshot.
 *
 * A newly bound account always clears the old cache first. Page/record limits
 * retain a partial history without falsely claiming full pagination.
 */
class CampusRunReadOnlyQuery(
    private val source: CampusRunReadOnlySource,
    private val gate: CampusRunSnapshotGate = CampusRunSnapshotGate(),
    private val maxPages: Int = 20,
    private val maxRecords: Int = 1_000
) {
    init {
        require(maxPages in 1..100)
        require(maxRecords in 1..1_000)
    }

    private var boundSubject: String? = null

    fun state(nowEpochMillis: Long): CampusRunViewState = gate.state(nowEpochMillis)

    fun clear() {
        boundSubject = null
        gate.clear()
    }

    /**
     * Fast summary refresh for the main campus-service panel: does not query
     * the history endpoint and never treats missing history as complete.
     */
    suspend fun refreshSummary(
        expectedSubjectFingerprint: String,
        nowEpochMillis: Long
    ): CampusRunViewState =
        refreshInternal(expectedSubjectFingerprint, nowEpochMillis, withHistory = false)

    /** Expensive history query, invoked on demand from a detail screen. */
    suspend fun refresh(
        expectedSubjectFingerprint: String,
        nowEpochMillis: Long
    ): CampusRunViewState =
        refreshInternal(expectedSubjectFingerprint, nowEpochMillis, withHistory = true)

    private suspend fun refreshInternal(
        expectedSubjectFingerprint: String,
        nowEpochMillis: Long,
        withHistory: Boolean
    ): CampusRunViewState {
        require(nowEpochMillis > 0)
        if (!SUBJECT_FINGERPRINT.matches(expectedSubjectFingerprint)) {
            clear()
            return gate.refreshFailed(CampusRunFailure.NOT_AUTHORIZED, nowEpochMillis)
        }
        if (boundSubject != expectedSubjectFingerprint) {
            gate.clear()
            boundSubject = expectedSubjectFingerprint
        }
        return try {
            if (source.authorizedSubjectFingerprint() != expectedSubjectFingerprint) {
                gate.refreshFailed(CampusRunFailure.NOT_AUTHORIZED, nowEpochMillis)
            } else {
                val summary = source.readSemester()
                if (summary.subjectFingerprint != expectedSubjectFingerprint) {
                    gate.refreshFailed(CampusRunFailure.NOT_AUTHORIZED, nowEpochMillis)
                } else {
                    val records = ArrayList<CampusRunRecord>()
                    val visitedCursors = mutableSetOf<String>()
                    var cursor: String? = null
                    var complete = false
                    if (withHistory) for (index in 0 until maxPages) {
                        val page = source.readHistoryPage(cursor)
                        if (page.subjectFingerprint != expectedSubjectFingerprint) {
                            return gate.refreshFailed(CampusRunFailure.NOT_AUTHORIZED,
                                nowEpochMillis)
                        }
                        // Reject malformed pagination before publishing any result.
                        if (page.isLastPage != (page.nextCursor == null)) {
                            throw IllegalArgumentException("invalid_page_termination")
                        }
                        if (page.records.size > maxRecords - records.size) {
                            records.addAll(page.records.take(maxRecords - records.size))
                            break
                        }
                        records.addAll(page.records)
                        if (page.isLastPage) {
                            complete = true
                            break
                        }
                        val next = page.nextCursor!!
                        require(next.length in 1..512 &&
                            next.none { it.isISOControl() } &&
                            visitedCursors.add(next)) { "invalid_or_repeated_cursor" }
                        cursor = next
                        if (records.size == maxRecords) break
                    }
                    // An account may be switched or revoked while pages load.
                    // Never publish a mixed-account snapshot.
                    if (source.authorizedSubjectFingerprint() !=
                        expectedSubjectFingerprint) {
                        return gate.refreshFailed(CampusRunFailure.NOT_AUTHORIZED,
                            nowEpochMillis)
                    }
                    gate.accept(CampusRunSnapshot(
                        provenance = CampusRunProvenance.TRUSTED_READONLY,
                        ownerMatched = true,
                        capturedAtEpochMillis = nowEpochMillis,
                        termLabel = summary.termLabel,
                        targetMeters = summary.targetMeters,
                        effectiveMeters = summary.effectiveMeters,
                        rawMeters = summary.rawMeters,
                        historyComplete = complete,
                        records = records,
                        dashboardAccumulatedMeters = summary.dashboardAccumulatedMeters,
                        weeklyMeters = summary.weeklyMeters,
                        totalRunCount = summary.totalRunCount,
                        effectiveRunCount = summary.effectiveRunCount
                    ), nowEpochMillis)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: CampusRunSourceException) {
            gate.refreshFailed(failure.failure, nowEpochMillis)
        } catch (_: SecurityException) {
            gate.refreshFailed(CampusRunFailure.NOT_AUTHORIZED, nowEpochMillis)
        } catch (_: IOException) {
            gate.refreshFailed(CampusRunFailure.NETWORK, nowEpochMillis)
        } catch (_: IllegalArgumentException) {
            gate.refreshFailed(CampusRunFailure.INVALID_DATA, nowEpochMillis)
        } catch (_: Exception) {
            gate.refreshFailed(CampusRunFailure.SOURCE_UNAVAILABLE, nowEpochMillis)
        }
    }

    private companion object {
        val SUBJECT_FINGERPRINT = Regex("^[a-f0-9]{64}$")
    }
}
