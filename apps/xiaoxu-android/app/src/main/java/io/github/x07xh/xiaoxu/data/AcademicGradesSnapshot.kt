package io.github.x07xh.xiaoxu.data

import org.json.JSONObject
import java.time.Instant

/** Existing server contract; a personal import is not a trusted school result. */
data class AcademicGradeRow(val id: String, val term: String, val title: String,
    val score: String, val credits: Double?, val gradePoint: Double?, val note: String)

data class AcademicGradesSnapshot(val status: String, val provenance: String?,
    val capturedAt: String?, val rows: List<AcademicGradeRow>)

internal fun parseAcademicGradesSnapshot(json: JSONObject): AcademicGradesSnapshot {
    require(json.getString("kind") == "grades" && json.getString("source") == "ncwu")
    val status = json.getString("status")
    require(status in setOf("not_connected", "ready", "stale"))
    val records = json.getJSONArray("records")
    require(records.length() <= 250)
    if (status == "not_connected") {
        require(records.length() == 0 && json.isNull("lastSuccessAt"))
        return AcademicGradesSnapshot(status, null, null, emptyList())
    }
    val source = json.getString("provenance")
    require(source in setOf("trusted_readonly", "user_import"))
    val captured = json.getString("lastSuccessAt").also { Instant.parse(it) }
    fun bounded(row: JSONObject, key: String, max: Int, required: Boolean = false): String {
        val value = if (row.isNull(key)) "" else row.get(key) as? String ?: error("invalid string")
        require(value.length <= max && (!required || value.isNotBlank()))
        return value
    }
    fun number(row: JSONObject, key: String, max: Double): Double? {
        if (row.isNull(key)) return null
        val value = (row.get(key) as? Number)?.toDouble() ?: error("invalid number")
        require(value.isFinite() && value in 0.0..max)
        return value
    }
    val seen = mutableSetOf<Pair<String, String>>()
    val rows = (0 until records.length()).map { i ->
        val row = records.getJSONObject(i)
        val id = bounded(row, "externalId", 120, true)
        require(id.matches(Regex("[A-Za-z0-9:_-]{1,120}")))
        val term = bounded(row, "term", 40, true)
        require(seen.add(id to term))
        AcademicGradeRow(id, term, bounded(row, "title", 120, true),
            bounded(row, "score", 40), number(row, "credit", 30.0),
            number(row, "gradePoint", 10.0), bounded(row, "note", 220))
    }
    return AcademicGradesSnapshot(status, source, captured, rows)
}
