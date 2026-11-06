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

/**
 * App record labels; a displayed "达标" does not independently prove that a
 * specific number of meters was credited to the semester assessment.
 */
enum class CampusRunRecordStatus { VALID, INVALID, PENDING, UNKNOWN }
enum class CampusRunRecordKind { SCORE_RUN, FREE_RUN, AI_EXERCISE, INDOOR_EXERCISE, UNKNOWN }

data class CampusRunRecord(
    val recordKey: String,
    val startedAtEpochMillis: Long?,
    val rawMeters: Long?,
    val effectiveMeters: Long?,
    val durationSeconds: Long?,
    val status: CampusRunRecordStatus,
    val kind: CampusRunRecordKind = CampusRunRecordKind.UNKNOWN,
    /** A separately displayed "存在异常" flag, not another validity state. */
    val hasAnomaly: Boolean? = null
)

data class CampusRunSnapshot(
    val provenance: CampusRunProvenance,
    val ownerMatched: Boolean,
    /** Time this particular read/import succeeded, not a promise of live status. */
    val capturedAtEpochMillis: Long,
    /** May be null when no authoritative term was observed. */
    val termLabel: String?,
    /** Source-displayed semester target; must not be guessed from term history. */
    val targetMeters: Long?,
    /** "计入成绩" semester meters, distinct from all other distance metrics. */
    val effectiveMeters: Long?,
    /** "学期累计总里程", including distances not credited to the grade. */
    val rawMeters: Long?,
    /** True only if the source explicitly confirmed complete pagination. */
    val historyComplete: Boolean,
    val records: List<CampusRunRecord>,
    /** Home dashboard "累计跑量": time scope unverified, NOT semester rawMeters. */
    val dashboardAccumulatedMeters: Long? = null,
    /** Dashboard "本周跑量": a separate window from the semester fields. */
    val weeklyMeters: Long? = null,
    /** "—次" on the official page means null, not zero. */
    val totalRunCount: Int? = null,
    val effectiveRunCount: Int? = null
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

/**
 * User reads these values from their own official app and explicitly enters
 * them in 校序. These are USER_IMPORT snapshots, never official synchronization.
 *
 * Decimal kilometers accept up to two digits after the decimal point. Blank
 * means unknown; no implicit zero. This function has no Android dependencies.
 */
data class CampusRunManualInput(
    val termLabel: String,
    val targetKm: String,
    val creditedKm: String,
    val semesterTotalKm: String,
    val dashboardTotalKm: String,
    val weeklyKm: String
)

fun parseCampusRunKilometers(raw: String): Long? {
    val value = raw.trim()
    if (value.isEmpty()) return null
    require(Regex("^(0|[1-9][0-9]{0,5})(\\.[0-9]{1,2})?$")
        .matches(value)) { "invalid_distance" }
    return java.math.BigDecimal(value)
        .multiply(java.math.BigDecimal(1_000))
        .longValueExact()
}

fun CampusRunManualInput.toSnapshot(nowEpochMillis: Long): CampusRunSnapshot =
    validateCampusRunSnapshot(CampusRunSnapshot(
        provenance = CampusRunProvenance.USER_IMPORT,
        ownerMatched = false,
        capturedAtEpochMillis = nowEpochMillis,
        termLabel = termLabel.trim().ifEmpty { null },
        targetMeters = parseCampusRunKilometers(targetKm),
        effectiveMeters = parseCampusRunKilometers(creditedKm),
        rawMeters = parseCampusRunKilometers(semesterTotalKm),
        historyComplete = false,
        records = emptyList(),
        dashboardAccumulatedMeters = parseCampusRunKilometers(dashboardTotalKm),
        weeklyMeters = parseCampusRunKilometers(weeklyKm)
    ), nowEpochMillis)

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
    require(snapshot.provenance == CampusRunProvenance.TRUSTED_READONLY ||
        !snapshot.historyComplete) { "import_cannot_prove_full_history" }
    require(snapshot.termLabel == null ||
        (snapshot.termLabel.isNotBlank() &&
            snapshot.termLabel.length <= 80 &&
            snapshot.termLabel.none { it == '\n' || it == '\r' })) { "invalid_term" }

    fun validDistance(meters: Long?, max: Long): Boolean =
        meters == null || meters in 0..max
    require(snapshot.targetMeters == null ||
        snapshot.targetMeters in 1..MAX_TOTAL_METERS) { "invalid_target" }
    require(validDistance(snapshot.effectiveMeters, MAX_TOTAL_METERS) &&
        validDistance(snapshot.rawMeters, MAX_TOTAL_METERS) &&
        validDistance(snapshot.dashboardAccumulatedMeters, MAX_TOTAL_METERS) &&
        validDistance(snapshot.weeklyMeters, MAX_TOTAL_METERS)) { "invalid_total" }
    require(snapshot.totalRunCount == null ||
        snapshot.totalRunCount in 0..1_000_000) { "invalid_run_count" }
    require(snapshot.effectiveRunCount == null ||
        snapshot.effectiveRunCount in 0..1_000_000) { "invalid_credit_count" }
    require(snapshot.totalRunCount == null || snapshot.effectiveRunCount == null ||
        snapshot.effectiveRunCount <= snapshot.totalRunCount) { "inconsistent_counts" }
    require(snapshot.effectiveMeters == null || snapshot.rawMeters == null ||
        snapshot.effectiveMeters <= snapshot.rawMeters) { "inconsistent_totals" }
    require(snapshot.targetMeters != null || snapshot.effectiveMeters != null ||
        snapshot.rawMeters != null || snapshot.dashboardAccumulatedMeters != null ||
        snapshot.weeklyMeters != null || snapshot.totalRunCount != null ||
        snapshot.effectiveRunCount != null || snapshot.records.isNotEmpty()) {
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
        // Revoked/expired authorization is not an ordinary network failure:
        // do not continue exposing the previous owner's personal snapshot.
        if (reason == CampusRunFailure.NOT_AUTHORIZED) previous = null
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
