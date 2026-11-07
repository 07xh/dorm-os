package io.github.x07xh.xiaoxu.data

import java.math.BigDecimal
import java.util.concurrent.CancellationException

/**
 * The owner's earlier HAR exposes only r/s/v, not these inner JSON fields.
 * This mapper is deliberately configurable: field names, units and page
 * semantics must be verified against authorized plaintext before enabling a
 * production reader. No field is silently assumed to mean a semester grade.
 */
enum class CampusRunObservedDistanceUnit(val meterMultiplier: Long) {
    METERS(1L), KILOMETERS(1_000L)
}

enum class CampusRunObservedTimeUnit(val millisMultiplier: Long) {
    EPOCH_SECONDS(1_000L), EPOCH_MILLIS(1L)
}

data class CampusRunDistanceBinding(
    val key: String,
    val unit: CampusRunObservedDistanceUnit
)

data class CampusRunSemesterBindings(
    val termKey: String? = null,
    val target: CampusRunDistanceBinding? = null,
    val credited: CampusRunDistanceBinding? = null,
    val total: CampusRunDistanceBinding? = null,
    val dashboard: CampusRunDistanceBinding? = null,
    val weekly: CampusRunDistanceBinding? = null,
    val totalCountKey: String? = null,
    val creditedCountKey: String? = null
)

data class CampusRunHistoryBindings(
    val recordIdKey: String,
    val rawDistance: CampusRunDistanceBinding? = null,
    /**
     * This may be bound ONLY if evidence establishes that this exact field
     * represents credited distance, not merely a measured/valid-distance hint.
     */
    val creditedDistance: CampusRunDistanceBinding? = null,
    val durationSecondsKey: String? = null,
    val startedAtKey: String? = null,
    val startedAtUnit: CampusRunObservedTimeUnit? = null,
    val completeKey: String? = null,
    val isValidKey: String? = null,
    val anomalyKey: String? = null,
    val kind: CampusRunRecordKind = CampusRunRecordKind.UNKNOWN
) {
    init { require((startedAtKey == null) == (startedAtUnit == null)) }
}

private fun observedNumber(input: Any?): BigDecimal? {
    if (input == null) return null
    require(input is Number || input is String) { "invalid_numeric_type" }
    val raw = input.toString()
    require(raw.isNotBlank() && raw.length <= 30) { "invalid_numeric_text" }
    return try {
        BigDecimal(raw)
    } catch (_: NumberFormatException) {
        throw IllegalArgumentException("invalid_numeric_text")
    }
}

private fun observedMeters(
    data: Map<String, Any?>,
    binding: CampusRunDistanceBinding?
): Long? {
    if (binding == null) return null
    val number = observedNumber(data[binding.key]) ?: return null
    val meters = number.multiply(BigDecimal.valueOf(binding.unit.meterMultiplier))
    return try { meters.longValueExact() }
    catch (_: ArithmeticException) { throw IllegalArgumentException("non_integral_meters") }
}

private fun observedLong(data: Map<String, Any?>, key: String?): Long? {
    if (key == null) return null
    return try { observedNumber(data[key])?.longValueExact() }
    catch (_: ArithmeticException) { throw IllegalArgumentException("non_integral_number") }
}

private fun observedInt(data: Map<String, Any?>, key: String?): Int? {
    val number = observedLong(data, key) ?: return null
    require(number in Int.MIN_VALUE..Int.MAX_VALUE) { "invalid_integer" }
    return number.toInt()
}

private fun observedBool(data: Map<String, Any?>, key: String?): Boolean? {
    if (key == null) return null
    val value = data[key] ?: return null
    require(value is Boolean) { "invalid_boolean" }
    return value
}

fun mapCampusRunDecodedSemester(
    fields: Map<String, Any?>,
    subjectFingerprint: String,
    bindings: CampusRunSemesterBindings
): CampusRunReadSummary {
    val term = bindings.termKey?.let { key ->
        val raw = fields[key]
        require(raw == null || raw is String) { "invalid_term_field" }
        raw as? String
    }
    return CampusRunReadSummary(
        subjectFingerprint = subjectFingerprint,
        termLabel = term,
        targetMeters = observedMeters(fields, bindings.target),
        effectiveMeters = observedMeters(fields, bindings.credited),
        rawMeters = observedMeters(fields, bindings.total),
        dashboardAccumulatedMeters = observedMeters(fields, bindings.dashboard),
        weeklyMeters = observedMeters(fields, bindings.weekly),
        totalRunCount = observedInt(fields, bindings.totalCountKey),
        effectiveRunCount = observedInt(fields, bindings.creditedCountKey)
    )
}

/**
 * These are potential field names observed in an external historical research
 * project, NOT confirmed keys inside the owner's r/s/v ciphertext. A record
 * status is VALID only if both explicit "complete" and "isValid" are true;
 * numeric status codes alone are never interpreted as credited achievements.
 */
fun mapCampusRunDecodedHistoryRecord(
    fields: Map<String, Any?>,
    bindings: CampusRunHistoryBindings
): CampusRunRecord {
    val rawId = fields[bindings.recordIdKey] ?: throw IllegalArgumentException("missing_record_id")
    require(rawId is Number || rawId is String) { "invalid_record_id" }
    val recordKey = rawId.toString()
    require(Regex("^[A-Za-z0-9._:-]{1,120}$").matches(recordKey)) {
        "invalid_record_id"
    }
    val complete = observedBool(fields, bindings.completeKey)
    val isValid = observedBool(fields, bindings.isValidKey)
    val status = when {
        complete == false || isValid == false -> CampusRunRecordStatus.INVALID
        complete == true && isValid == true -> CampusRunRecordStatus.VALID
        else -> CampusRunRecordStatus.UNKNOWN
    }
    val start = observedLong(fields, bindings.startedAtKey)?.let {
        Math.multiplyExact(it, bindings.startedAtUnit!!.millisMultiplier)
    }
    return CampusRunRecord(
        recordKey = recordKey,
        startedAtEpochMillis = start,
        rawMeters = observedMeters(fields, bindings.rawDistance),
        effectiveMeters = observedMeters(fields, bindings.creditedDistance),
        durationSeconds = observedLong(fields, bindings.durationSecondsKey),
        status = status,
        kind = bindings.kind,
        hasAnomaly = observedBool(fields, bindings.anomalyKey)
    )
}

/** Accept decoded rows only, never the r/s/v ciphertext or an HTTP-200 flag. */
fun mapCampusRunDecodedHistoryRows(
    payload: Any?,
    bindings: CampusRunHistoryBindings
): List<CampusRunRecord> {
    val list = when (payload) {
        is List<*> -> payload
        is Map<*, *> -> {
            require(payload.containsKey("recordList")) { "missing_record_list" }
            payload["recordList"] as? List<*>
                ?: throw IllegalArgumentException("invalid_record_list")
        }
        else -> throw IllegalArgumentException("invalid_history_payload")
    }
    require(list.size <= 1_000) { "too_many_records" }
    return list.map { item ->
        require(item is Map<*, *>) { "invalid_record_object" }
        val data = mutableMapOf<String, Any?>()
        for ((key, value) in item) {
            require(key is String) { "invalid_record_key_type" }
            data[key] = value
        }
        mapCampusRunDecodedHistoryRecord(data, bindings)
    }
}

data class CampusRunAuthorizedDecodedSemester(
    val subjectFingerprint: String,
    val fields: Map<String, Any?>
)

data class CampusRunAuthorizedDecodedPage(
    val subjectFingerprint: String,
    val recordsPayload: Any?,
    val nextCursor: String?,
    val isLastPage: Boolean
)

/**
 * Only an implementation backed by the user's normally authorized *read*
 * access may supply decoded payloads. This interface neither logs in nor
 * decodes an application's protected r/s/v envelope.
 */
interface CampusRunAuthorizedDecodedReader {
    suspend fun currentSubjectFingerprint(): String
    suspend fun semester(): CampusRunAuthorizedDecodedSemester
    suspend fun historyPage(cursor: String?): CampusRunAuthorizedDecodedPage
}

/** Connects verified decoded fields to the #367 read-only query pipeline. */
class CampusRunObservedMappingSource(
    private val reader: CampusRunAuthorizedDecodedReader,
    private val semesterBindings: CampusRunSemesterBindings,
    private val historyBindings: CampusRunHistoryBindings
) : CampusRunReadOnlySource {
    override suspend fun authorizedSubjectFingerprint(): String =
        reader.currentSubjectFingerprint()

    override suspend fun readSemester(): CampusRunReadSummary {
        val decoded = reader.semester()
        return mapCampusRunDecodedSemester(
            decoded.fields, decoded.subjectFingerprint, semesterBindings
        )
    }

    override suspend fun readHistoryPage(cursor: String?): CampusRunReadPage {
        val decoded = reader.historyPage(cursor)
        return CampusRunReadPage(
            subjectFingerprint = decoded.subjectFingerprint,
            records = mapCampusRunDecodedHistoryRows(
                decoded.recordsPayload, historyBindings
            ),
            nextCursor = decoded.nextCursor,
            isLastPage = decoded.isLastPage
        )
    }
}
