package io.github.x07xh.xiaoxu.ui

import android.content.Context

/** Persist only presentation preferences; never substitute timetable data. */
internal object ScheduleViewPreferences {
    private const val PREFS = "xiaoxu-timetable-view-v1"
    private const val MODE = "mode"
    private const val SECTIONS = "sections"

    fun loadMode(context: Context): String =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(MODE, "week").takeIf { it == "week" || it == "day" } ?: "week"

    fun loadSections(context: Context): Int =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(SECTIONS, 12).coerceIn(1, 24)

    fun save(context: Context, mode: String, sections: Int) {
        require(mode == "week" || mode == "day")
        require(sections in 1..24)
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(MODE, mode).putInt(SECTIONS, sections).apply()
    }
}
