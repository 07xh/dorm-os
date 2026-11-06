package io.github.x07xh.xiaoxu.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Private, portable timetable backup. No credentials, session fields, cloud sync
 * state or unrelated task data. Legacy JSON-array imports remain supported.
 */
object LocalTimetableBackup {
    private const val FORMAT = "xiaoxu-local-timetable"
    private const val VERSION = 1
    private const val MAX_ROWS = 300

    data class Imported(
        val lessons: List<VerifiedLesson>,
        val calendar: SemesterCalendar? = null,
        val fromBackup: Boolean = false
    )

    fun export(lessons: List<VerifiedLesson>, calendar: SemesterCalendar?): String {
        require(lessons.size in 1..MAX_ROWS) { "课表为空或超过课程上限，无法导出" }
        val rows = JSONArray()
        lessons.forEach { lesson ->
            rows.put(JSONObject()
                .put("name", lesson.title)
                .put("teacher", lesson.teacher)
                .put("position", lesson.location)
                .put("day", lesson.weekday)
                .put("startSection", lesson.startSection)
                .put("endSection", lesson.endSection)
                .put("weeks", JSONArray(lesson.weeks)))
        }
        // Revalidate before writing a portable file; do not export invalid rows.
        LocalScheduleStore.parseCourses(rows.toString())
        val root = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("courses", rows)
        if (calendar != null) {
            root.put("calendar", JSONObject()
                .put("firstMonday", calendar.firstMonday.toString())
                .put("totalWeeks", calendar.totalWeeks)
                .put("sectionTimes", calendar.editableTimes()))
        }
        return root.toString(2)
    }

    fun parse(text: String): Imported {
        require(text.length <= 1_000_000) { "备份文件过大" }
        val root = JSONObject(text)
        require(root.optString("format") == FORMAT && root.optInt("version") == VERSION) {
            "不是受支持的校序课表备份"
        }
        val courses = root.optJSONArray("courses")
            ?: throw IllegalArgumentException("备份缺少课程列表")
        val lessons = LocalScheduleStore.parseCourses(courses.toString())
        val calendar = if (root.has("calendar") && !root.isNull("calendar")) {
            val entry = root.optJSONObject("calendar")
                ?: throw IllegalArgumentException("备份中的学期设置无效")
            SemesterCalendar.parse(
                entry.getString("firstMonday"),
                entry.getInt("totalWeeks").toString(),
                entry.optString("sectionTimes", "")
            )
        } else null
        return Imported(lessons, calendar, fromBackup = true)
    }
}
