package io.github.x07xh.xiaoxu.data

import org.json.JSONObject
import java.time.Instant

/** Existing server contract; a personal import is not a trusted school result. */
data class AcademicGradeRow(val id: String, val term: String, val title: String,
    val score: String, val credits: Double?, val gradePoint: Double?, val note: String)

data class AcademicGradesSnapshot(val status: String, val provenance: String?,
    val capturedAt: String?, val rows: List<AcademicGradeRow>)

data class AcademicExamRow(val id: String, val term: String, val title: String,
    val date: String, val startTime: String, val endTime: String, val location: String,
    val teacher: String, val note: String)
data class AcademicExamsSnapshot(val status: String, val provenance: String?,
    val capturedAt: String?, val rows: List<AcademicExamRow>)

private data class AcademicSnapshotEnvelope(val status: String, val provenance: String?,
    val capturedAt: String?, val rows: org.json.JSONArray)
private fun academicEnvelope(json: JSONObject, kind: String): AcademicSnapshotEnvelope {
    require(kind in setOf("grades", "exams"))
    require(json.getString("kind") == kind && json.getString("source") == "ncwu")
    val status = json.getString("status")
    require(status in setOf("not_connected", "ready", "stale"))
    val records = json.getJSONArray("records")
    require(records.length() <= 250)
    if (status == "not_connected") {
        require(records.length() == 0 && json.isNull("lastSuccessAt"))
        return AcademicSnapshotEnvelope(status, null, null, records)
    }
    val source = json.getString("provenance")
    require(source in setOf("trusted_readonly", "user_import"))
    val captured = json.getString("lastSuccessAt").also { Instant.parse(it) }
    return AcademicSnapshotEnvelope(status, source, captured, records)
}
private fun bounded(row: JSONObject, key: String, max: Int, required: Boolean = false): String {
    val value = if (row.isNull(key)) "" else row.get(key) as? String ?: error("invalid string")
    require(value.length <= max && (!required || value.isNotBlank()))
    return value
}
private fun number(row: JSONObject, key: String, max: Double): Double? {
    if (row.isNull(key)) return null
    val value = (row.get(key) as? Number)?.toDouble() ?: error("invalid number")
    require(value.isFinite() && value in 0.0..max)
    return value
}
private fun <T> parseAcademicRows(envelope: AcademicSnapshotEnvelope,
    map: (JSONObject, String, String) -> T): List<T> {
    val seen = mutableSetOf<Pair<String, String>>()
    return (0 until envelope.rows.length()).map { i ->
        val row = envelope.rows.getJSONObject(i)
        val id = bounded(row, "externalId", 120, true)
        require(id.matches(Regex("[A-Za-z0-9:_-]{1,120}")))
        val term = bounded(row, "term", 40, true)
        require(seen.add(id to term))
        map(row, id, term)
    }
}
internal fun parseAcademicGradesSnapshot(json: JSONObject): AcademicGradesSnapshot {
    val e = academicEnvelope(json, "grades")
    val rows = parseAcademicRows(e) { row, id, term ->
        AcademicGradeRow(id, term, bounded(row, "title", 120, true), bounded(row, "score", 40),
            number(row, "credit", 30.0), number(row, "gradePoint", 10.0), bounded(row, "note", 220))
    }
    return AcademicGradesSnapshot(e.status, e.provenance, e.capturedAt, rows)
}
internal fun parseAcademicExamsSnapshot(json: JSONObject): AcademicExamsSnapshot {
    val e = academicEnvelope(json, "exams")
    val rows = parseAcademicRows(e) { row, id, term ->
        val date = bounded(row, "date", 10, true)
        require(date.matches(Regex("20[0-9]{2}-[0-9]{2}-[0-9]{2}")))
        java.time.LocalDate.parse(date)
        fun time(key: String): String = bounded(row, key, 5).also {
            if (it.isNotEmpty()) {
                require(it.matches(Regex("[0-9]{2}:[0-9]{2}")))
                java.time.LocalTime.parse(it)
            }
        }
        AcademicExamRow(id, term, bounded(row, "title", 120, true), date,
            time("startTime"), time("endTime"), bounded(row, "location", 120),
            bounded(row, "teacher", 120), bounded(row, "note", 220))
    }
    return AcademicExamsSnapshot(e.status, e.provenance, e.capturedAt, rows)
}
