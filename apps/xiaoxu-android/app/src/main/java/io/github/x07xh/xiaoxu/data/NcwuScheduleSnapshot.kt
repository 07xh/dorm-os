package io.github.x07xh.xiaoxu.data

import java.time.Instant
import org.json.JSONObject

data class NcwuScheduleSnapshot(val capturedAt: String, val lessons: List<VerifiedLesson>)

/** The existing owner API is the only source of this separate read-only timetable. */
internal fun parseNcwuScheduleSnapshot(json: JSONObject, minimumCapturedAt: String): NcwuScheduleSnapshot {
    require(json.optString("kind") == "schedule" && json.optString("source") == "ncwu" &&
        json.optString("status") == "ready" && json.optString("provenance") == "trusted_readonly")
    val at = json.getString("lastSuccessAt")
    require(!Instant.parse(at).isBefore(Instant.parse(minimumCapturedAt)))
    val rows = json.getJSONArray("records")
    require(rows.length() in 0..250)
    val seen = mutableSetOf<Pair<String, String>>()
    val lessons = (0 until rows.length()).map { i ->
        val row = rows.getJSONObject(i)
        val id = row.getString("externalId")
        val term = row.getString("term")
        val title = row.getString("title")
        val teacher = row.optString("teacher", "")
        val location = row.optString("location", "")
        val day = row.getInt("weekday")
        val start = row.getInt("startSection")
        val end = row.getInt("endSection")
        require(Regex("^[A-Za-z0-9:_-]{1,120}$").matches(id) &&
            term.isNotBlank() && term.length <= 40 && seen.add(term to id) &&
            title.isNotBlank() && title.length <= 120 &&
            teacher.length <= 120 && location.length <= 120 &&
            day in 1..7 && start in 1..24 && end in start..24)
        val weekArray = row.getJSONArray("weeks")
        require(weekArray.length() in 1..35)
        val weeks = (0 until weekArray.length()).map { w ->
            val value = weekArray.get(w)
            require(value is Int && value in 1..35)
            value
        }
        require(weeks.distinct().size == weeks.size)
        VerifiedLesson(id = "school:$term:$id", term = term, title = title,
            teacher = teacher, location = location, weekday = day, startSection = start,
            endSection = end, weeks = weeks.sorted())
    }
    require(lessons.map { it.term }.distinct().size <= 1)
    return NcwuScheduleSnapshot(at, lessons)
}
