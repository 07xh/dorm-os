package io.github.x07xh.xiaoxu.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local-only timetable import. No school passwords, session cookies, student IDs or
 * HTML are stored; only an explicit, normalized list of course meetings.
 *
 * A locally imported meeting is NOT a server-verified Qingguo record.
 */
object LocalScheduleStore {
    private const val PREFS = "xiaoxu-local-schedule-v1"
    private const val KEY = "meetings"
    private const val MAX_MEETINGS = 300

    fun parseCourses(json: String): List<VerifiedLesson> {
        require(json.length <= 500_000) { "课表数据过大" }
        val array = JSONArray(json)
        require(array.length() in 1..MAX_MEETINGS) { "没有找到完整课程，未覆盖旧课表" }
        val unique = LinkedHashMap<String, VerifiedLesson>()
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val title = item.optString("name").trim()
            val teacher = item.optString("teacher").trim()
            val location = item.optString("position").trim()
            val day = item.optInt("day")
            val start = item.optInt("startSection")
            val end = item.optInt("endSection")
            require(title.isNotEmpty() && title.length <= 120 &&
                    teacher.length <= 90 && location.length <= 130 &&
                    day in 1..7 && start in 1..24 && end in start..24) {
                "第${i + 1}个课块字段不完整，未保存"
            }
            val sourceWeeks = item.optJSONArray("weeks")
                ?: throw IllegalArgumentException("第${i + 1}个课块缺少周次")
            val weeks = (0 until sourceWeeks.length()).map { sourceWeeks.getInt(it) }
                .distinct().sorted()
            require(weeks.isNotEmpty() && weeks.size <= 35 &&
                    weeks.all { it in 1..35 }) { "第${i + 1}个课块周次无效，未保存" }
            val key = listOf(title, teacher, location, day, start, end, weeks).joinToString("|")
            unique[key] = VerifiedLesson(
                id = "local-" + key.hashCode().toUInt().toString(16),
                term = "", // Never fabricate a semester without an official term marker.
                title = title,
                teacher = teacher,
                location = location,
                weekday = day,
                startSection = start,
                endSection = end,
                weeks = weeks
            )
        }
        return unique.values.toList()
    }

    /** Accepts converter-produced ICS only when it explicitly identifies sections and weeks. */
    fun parseIcs(input: String): List<VerifiedLesson> {
        val rows = IcsTimetableParser.parse(input)
        val normalized = JSONArray()
        for (row in rows) {
            normalized.put(JSONObject()
                .put("name", row.title)
                .put("teacher", row.teacher)
                .put("position", row.location)
                .put("day", row.weekday)
                .put("startSection", row.startSection)
                .put("endSection", row.endSection)
                .put("weeks", JSONArray(row.weeks)))
        }
        return parseCourses(normalized.toString())
    }

    /** Re-validates preview contents at commit time; the old schedule remains on any error. */
    fun saveParsed(context: Context, lessons: List<VerifiedLesson>): Int {
        val normalized = JSONArray()
        for (lesson in lessons) {
            normalized.put(JSONObject()
                .put("name", lesson.title)
                .put("teacher", lesson.teacher)
                .put("position", lesson.location)
                .put("day", lesson.weekday)
                .put("startSection", lesson.startSection)
                .put("endSection", lesson.endSection)
                .put("weeks", JSONArray(lesson.weeks)))
        }
        return save(context, normalized.toString())
    }

    fun save(context: Context, input: String): Int {
        val parsed = parseCourses(input)
        val clean = JSONArray()
        for (item in parsed) {
            clean.put(JSONObject()
                .put("name", item.title)
                .put("teacher", item.teacher)
                .put("position", item.location)
                .put("day", item.weekday)
                .put("startSection", item.startSection)
                .put("endSection", item.endSection)
                .put("weeks", JSONArray(item.weeks)))
        }
        require(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, clean.toString()).commit()) { "本机课表保存失败" }
        return parsed.size
    }

    fun load(context: Context): List<VerifiedLesson> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        return runCatching { parseCourses(raw) }.getOrDefault(emptyList())
    }
}
