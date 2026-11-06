package io.github.x07xh.xiaoxu.data

/**
 * Personal campus-running data only. These types do not call the upstream
 * service, collect credentials, generate tracks, or submit an exercise result.
 *
 * All distances are integer METERS. Unknown fields stay null, never zero.
 */
enum class CampusRunProvenance {
    /** Proven by an allowed upstream read AND an owner-identity check. */
    TRUSTED_READONLY,
    /** Supplied by the owner; never represented as verified live school data. */
    USER_IMPORT
}

enum class CampusRunRecordStatus { VALID, INVALID, PENDING, UNKNOWN }

data class CampusRunRecord(
    val recordKey: String,
    val startedAtEpochMillis: Long?,
    val rawMeters: Long?,
    val effectiveMeters: Long?,
    val durationSeconds: Long?,
    val status: CampusRunRecordStatus
)

data class CampusRunSnapshot(
    val provenance: CampusRunProvenance,
    val ownerMatched: Boolean,
    /** Time this particular read/import succeeded, not a promise of live status. */
    val capturedAtEpochMillis: Long,
    /** May be null when no authoritative term was observed. */
    val termLabel: String?,
    val targetMeters: Long?,
    val effectiveMeters: Long?,
    val rawMeters: Long?,
    /** True only if the source explicitly confirmed complete pagination. */
    val historyComplete: Boolean,
    val records: List<CampusRunRecord>
) {
    /** Never sum a potentially partial history list into semester totals. */
    fun remainingMeters(): Long? =
        if (targetMeters == null || effectiveMeters == null) null
        else (targetMeters - effectiveMeters).coerceAtLeast(0)

    fun progressPercent(): Double? =
        if (targetMeters == null || effectiveMeters == null || targetMeters <= 0) null
        else (100.0 * effectiveMeters.toDouble() / targetMeters.toDouble())
            .coerceIn(0.0, 100.0)
}

private const val MAX_TOTAL_METERS = 10_000_000L
private const val MAX_SINGLE_RUN_METERS = 200_000L
private const val MAX_RECORDS = 1_000
private const val MAX_CLOCK_DRIFT_MILLIS = 5 * 60_000L
private val RECORD_KEY_FORMAT = Regex("^[A-Za-z0-9._:-]{1,120}$")

/**
 * Validate before any future Room/cloud write or UI state update.
 * A successful parsing operation alone MUST NOT set ownerMatched=true;
 * only the actual authorized source adapter may establish that evidence.
 */
fun validateCampusRunSnapshot(
    snapshot: CampusRunSnapshot,
    nowEpochMillis: Long
): CampusRunSnapshot {
    require(nowEpochMillis > 0)
    require(snapshot.capturedAtEpochMillis > 0 &&
        snapshot.capturedAtEpochMillis <= nowEpochMillis + MAX_CLOCK_DRIFT_MILLIS) {
        "invalid_capture_time"
    }
    require(snapshot.provenance != CampusRunProvenance.TRUSTED_READONLY ||
        snapshot.ownerMatched) { "trusted_source_requires_owner_match" }
    require(snapshot.termLabel == null ||
        (snapshot.termLabel.isNotBlank() &&
            snapshot.termLabel.length <= 80 &&
            snapshot.termLabel.none { it == '\n' || it == '\r' })) { "invalid_term" }

    fun validDistance(meters: Long?, max: Long): Boolean =
        meters == null || meters in 0..max
    require(snapshot.targetMeters == null ||
        snapshot.targetMeters in 1..MAX_TOTAL_METERS) { "invalid_target" }
    require(validDistance(snapshot.effectiveMeters, MAX_TOTAL_METERS) &&
        validDistance(snapshot.rawMeters, MAX_TOTAL_METERS)) { "invalid_total" }
    require(snapshot.effectiveMeters == null || snapshot.rawMeters == null ||
        snapshot.effectiveMeters <= snapshot.rawMeters) { "inconsistent_totals" }
    require(snapshot.targetMeters != null || snapshot.effectiveMeters != null ||
        snapshot.rawMeters != null || snapshot.records.isNotEmpty()) {
        "no_observed_data"
    }
    require(snapshot.records.size <= MAX_RECORDS) { "too_many_records" }
    val keys = mutableSetOf<String>()
    for (record in snapshot.records) {
        require(RECORD_KEY_FORMAT.matches(record.recordKey) &&
            keys.add(record.recordKey)) { "invalid_or_duplicate_record_key" }
        require(record.startedAtEpochMillis == null ||
            (record.startedAtEpochMillis > 0 &&
                record.startedAtEpochMillis <= snapshot.capturedAtEpochMillis +
                    MAX_CLOCK_DRIFT_MILLIS)) { "invalid_record_time" }
        require(validDistance(record.rawMeters, MAX_SINGLE_RUN_METERS) &&
            validDistance(record.effectiveMeters, MAX_SINGLE_RUN_METERS) &&
            (record.durationSeconds == null || record.durationSeconds in 0..172_800)) {
            "invalid_record_measurement"
        }
        require(record.rawMeters == null || record.effectiveMeters == null ||
            record.effectiveMeters <= record.rawMeters) { "inconsistent_record_distance" }
    }
    return snapshot
}

enum class CampusRunFailure {
    NOT_AUTHORIZED, NETWORK, SOURCE_UNAVAILABLE, INVALID_DATA
}

/**
 * A local-only presentation state. Adapter integration must call clear() when
 * the 校序 owner signs out or the authorized source is revoked.
 */
sealed class CampusRunViewState {
    data object NotConnected : CampusRunViewState()
    data class Current(val snapshot: CampusRunSnapshot) : CampusRunViewState()
    data class Stale(val snapshot: CampusRunSnapshot, val reason: CampusRunFailure?) :
        CampusRunViewState()
    data class Unavailable(val reason: CampusRunFailure) : CampusRunViewState()
}

class CampusRunSnapshotGate(
    private val maxAgeMillis: Long = 36L * 60L * 60L * 1_000L
) {
    init { require(maxAgeMillis > 0) }

    private var previous: CampusRunSnapshot? = null
    private var lastFailure: CampusRunFailure? = null

    fun accept(snapshot: CampusRunSnapshot, nowEpochMillis: Long): CampusRunViewState {
        val verified = validateCampusRunSnapshot(snapshot, nowEpochMillis)
        previous = verified
        lastFailure = null
        return state(nowEpochMillis)
    }

    fun refreshFailed(reason: CampusRunFailure, nowEpochMillis: Long): CampusRunViewState {
        lastFailure = reason
        return state(nowEpochMillis)
    }

    fun state(nowEpochMillis: Long): CampusRunViewState {
        require(nowEpochMillis > 0)
        val value = previous ?: return lastFailure?.let(CampusRunViewState::Unavailable)
            ?: CampusRunViewState.NotConnected
        val stale = lastFailure != null ||
            nowEpochMillis < value.capturedAtEpochMillis ||
            nowEpochMillis - value.capturedAtEpochMillis > maxAgeMillis
        return if (stale) CampusRunViewState.Stale(value, lastFailure)
        else CampusRunViewState.Current(value)
    }

    fun clear() {
        previous = null
        lastFailure = null
    }
}
