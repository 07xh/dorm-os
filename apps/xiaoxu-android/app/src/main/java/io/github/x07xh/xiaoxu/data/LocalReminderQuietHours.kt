package io.github.x07xh.xiaoxu.data

import android.content.Context
import java.time.LocalTime

/**
 * An opt-in app-only silent interval. No ACCESS_NOTIFICATION_POLICY or system DND changes.
 * Start inclusive, end exclusive. Equal endpoints mean an empty interval, not 24h silence.
 * Notifications falling inside this interval are skipped rather than replayed late.
 */
data class LocalQuietHours(
    val enabled: Boolean = false,
    val startMinutes: Int = 23 * 60,
    val endMinutes: Int = 7 * 60
) {
    init {
        require(startMinutes in 0..1439 && endMinutes in 0..1439)
    }
}

internal fun isQuietMinute(minute: Int, policy: LocalQuietHours): Boolean {
    if (!policy.enabled || policy.startMinutes == policy.endMinutes ||
        minute !in 0..1439) return false
    return if (policy.startMinutes < policy.endMinutes) {
        minute >= policy.startMinutes && minute < policy.endMinutes
    } else {
        minute >= policy.startMinutes || minute < policy.endMinutes
    }
}

object LocalReminderQuietHours {
    private const val PREFS = "xiaoxu-local-quiet-hours-v1"
    private const val ENABLED = "enabled"
    private const val START = "start-minutes"
    private const val END = "end-minutes"

    fun load(context: Context): LocalQuietHours {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return LocalQuietHours(
            enabled = prefs.getBoolean(ENABLED, false),
            startMinutes = prefs.getInt(START, 23 * 60).coerceIn(0, 1439),
            endMinutes = prefs.getInt(END, 7 * 60).coerceIn(0, 1439)
        )
    }

    fun save(context: Context, policy: LocalQuietHours): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(ENABLED, policy.enabled)
            .putInt(START, policy.startMinutes).putInt(END, policy.endMinutes).commit()

    fun suppressNow(context: Context, now: LocalTime = LocalTime.now()): Boolean =
        isQuietMinute(now.hour * 60 + now.minute, load(context))
}
