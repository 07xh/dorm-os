package io.github.x07xh.xiaoxu.data

import java.math.BigDecimal
import org.json.JSONObject

/**
 * Offline import of a local, owner-supplied JSON result produced by the
 * previously reviewed ydsj_toolkit/json_client.py "score" or "all" command.
 *
 * This is NOT an authorized live connector. Nothing in this file logs in,
 * makes HTTP requests, reads another app's credentials, or trusts import flags.
 * The owner must select the distance unit observed in their own output.
 */
enum class CampusRunImportUnit(val multiplier: Long) {
    METERS(1L),
    KILOMETERS(1_000L)
}

fun parseCampusRunToolkitJson(
    text: String,
    unit: CampusRunImportUnit,
    capturedAtEpochMillis: Long
): CampusRunSnapshot {
    require(text.length in 2..256_000) { "invalid_import_size" }
    require(capturedAtEpochMillis > 0) { "invalid_import_timestamp" }

    val root = JSONObject(text)
    // command: "all" -> { score: {semester_completed: ...}, ... }
    // command: "score" -> { semester_completed: ... }
    val score = when (val nested = root.opt("score")) {
        null, JSONObject.NULL -> root
        is JSONObject -> nested
        else -> throw IllegalArgumentException("invalid_score_section")
    }
    val completed = score.opt("semester_completed") as? JSONObject
        ?: throw IllegalArgumentException("missing_semester_completed")
    // HTTP 200 and a protected r/s/v envelope alone do not mean valid data.
    require(completed.opt("error") is Number &&
        (completed.opt("error") as Number).toInt() == 10_000 &&
        (completed.opt("error") as Number).toDouble() == 10_000.0
    ) { "unverified_score_response" }

    val data = completed.opt("data") as? JSONObject
        ?: throw IllegalArgumentException("missing_semester_data")
    require(data.length() <= 150) { "invalid_semester_field_count" }

    fun meters(name: String): Long? {
        val raw = data.opt(name)
        if (raw == null || raw === JSONObject.NULL) return null
        require(raw is Number || raw is String) { "invalid_distance_type" }
        val string = raw.toString()
        require(string.matches(Regex("^(0|[1-9][0-9]{0,8})(\\.[0-9]{1,4})?$"))) {
            "invalid_distance_text"
        }
        val value = try {
            BigDecimal(string)
                .multiply(BigDecimal.valueOf(unit.multiplier))
                .longValueExact()
        } catch (_: ArithmeticException) {
            throw IllegalArgumentException("distance_not_whole_meters")
        }
        require(value in 0..10_000_000) { "distance_out_of_range" }
        return value
    }

    val term = when (val value = data.opt("sname")) {
        null, JSONObject.NULL -> null
        is String -> value.trim().takeIf { it.isNotEmpty() }
        else -> throw IllegalArgumentException("invalid_semester_name")
    }
    val snapshot = CampusRunSnapshot(
        provenance = CampusRunProvenance.USER_IMPORT,
        ownerMatched = false,
        capturedAtEpochMillis = capturedAtEpochMillis,
        termLabel = term,
        targetMeters = null, // No verified goal field in this archived format.
        effectiveMeters = meters("semesterValidDis"),
        rawMeters = meters("semesterDis"),
        historyComplete = false, // A score response cannot prove pagination.
        records = emptyList()   // Do not invent history or status from counts.
    )
    return validateCampusRunSnapshot(snapshot, capturedAtEpochMillis)
}
