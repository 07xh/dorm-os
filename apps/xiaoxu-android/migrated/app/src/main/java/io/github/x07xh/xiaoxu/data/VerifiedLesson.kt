package io.github.x07xh.xiaoxu.data

/**
 * Local timetable record imported on this device. It is not an authoritative
 * school-side record and must not be described as server-verified.
 */
data class VerifiedLesson(
    val id: String,
    val term: String,
    val title: String,
    val teacher: String,
    val location: String,
    val weekday: Int,
    val startSection: Int,
    val endSection: Int,
    val weeks: List<Int>
)
