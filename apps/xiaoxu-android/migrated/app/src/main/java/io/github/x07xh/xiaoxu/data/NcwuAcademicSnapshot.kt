package io.github.x07xh.xiaoxu.data

import java.time.Instant
import java.time.LocalDate
import org.json.JSONObject

data class NcwuAcademicRow(
    val id: String, val term: String, val title: String,
    val score: String? = null, val credit: Double? = null, val gradePoint: Double? = null,
    val date: String? = null, val startTime: String? = null, val endTime: String? = null,
    val location: String? = null
)
data class NcwuAcademicSnapshot(
    val kind: String, val capturedAt: String, val stale: Boolean,
    val records: List<NcwuAcademicRow>
)

/** Consume only the existing normalized, owner-protected, trusted snapshot.
 * Candidate query messages and imported/synthetic rows cannot enter this model.
 * No assumption is made about the upstream school's unobserved HTML schema.
 */
internal fun parseNcwuAcademicSnapshot(kind: String, json: JSONObject): NcwuAcademicSnapshot {
    require(kind in setOf("grades", "exams") &&
        json.optString("kind") == kind && json.optString("source") == "ncwu" &&
        json.optString("status") in setOf("ready", "stale") &&
        json.optString("provenance") == "trusted_readonly")
    val at = json.getString("lastSuccessAt")
    Instant.parse(at)
    val rows = json.getJSONArray("records")
    require(rows.length() in 0..250)
    val seen = mutableSetOf<Pair<String, String>>()
    val records = (0 until rows.length()).map { i ->
        val row = rows.getJSONObject(i)
        fun optionalText(key: String, max: Int): String? {
            if (!row.has(key) || row.isNull(key)) return null
            val value = row.get(key)
            require(value is String && value.isNotBlank() && value.length <= max)
            return value
        }
        fun optionalNumber(key: String, max: Double): Double? {
            if (!row.has(key) || row.isNull(key)) return null
            val value = row.get(key)
            require(value is Number)
            return value.toDouble().also { require(it.isFinite() && it in 0.0..max) }
        }
        val id = optionalText("externalId", 120) ?: error("missing_id")
        val term = optionalText("term", 40) ?: error("missing_term")
        val title = optionalText("title", 120) ?: error("missing_title")
        require(Regex("^[A-Za-z0-9:_-]{1,120}$").matches(id) && seen.add(term to id))
        if (kind == "grades") {
            NcwuAcademicRow(id, term, title, score = optionalText("score", 40),
                credit = optionalNumber("credit", 30.0), gradePoint = optionalNumber("gradePoint", 10.0))
        } else {
            val date = optionalText("date", 10) ?: error("missing_date")
            require(Regex("^20[0-9]{2}-[0-9]{2}-[0-9]{2}$").matches(date))
            LocalDate.parse(date)
            val start = optionalText("startTime", 5)
            val end = optionalText("endTime", 5)
            val clock = Regex("^(?:[01][0-9]|2[0-3]):[0-5][0-9]$")
            require(listOfNotNull(start, end).all(clock::matches))
            require(start == null || end == null || start <= end)
            NcwuAcademicRow(id, term, title, date = date, startTime = start, endTime = end,
                location = optionalText("location", 120))
        }
    }
    return NcwuAcademicSnapshot(kind, at, json.optString("status") == "stale", records)
}
