package io.github.x07xh.xiaoxu.data

import android.content.Context
import org.json.JSONObject

/** Local-only calendar preferences, independent of school authorization and cloud state. */
object LocalSemesterConfigStore {
    private const val PREFS = "xiaoxu-local-schedule-calendar-v1"
    private const val KEY = "calendar"

    fun load(context: Context): SemesterCalendar? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            SemesterCalendar.parse(
                json.getString("firstMonday"),
                json.getInt("totalWeeks").toString(),
                json.optString("sectionTimes", "")
            )
        }.getOrNull()
    }

    fun save(context: Context, config: SemesterCalendar) {
        val json = JSONObject()
            .put("firstMonday", config.firstMonday.toString())
            .put("totalWeeks", config.totalWeeks)
            .put("sectionTimes", config.editableTimes())
        // Small preferences payload; apply updates memory without UI-thread fsync.
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, json.toString()).apply()
    }
}
