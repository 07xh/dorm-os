package io.github.x07xh.xiaoxu.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import io.github.x07xh.xiaoxu.MainActivity
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/** Inexact, device-local, opt-in class reminders; no network or wake lock. */
internal data class CourseReminderPlan(
    val id: String,
    val title: String,
    val day: LocalDate,
    val start: LocalTime,
    val expectedStartEpoch: Long,
    val delayMillis: Long
)

internal fun planCourseReminders(
    lessons: List<VerifiedLesson>,
    calendar: SemesterCalendar,
    now: ZonedDateTime,
    horizonDays: Long = 14
): List<CourseReminderPlan> {
    require(horizonDays in 1..31)
    val result = mutableListOf<CourseReminderPlan>()
    lessons.forEach { lesson ->
        val start = calendar.sectionTimes[lesson.startSection]?.start ?: return@forEach
        lesson.weeks.distinct().forEach weekLoop@{ week ->
            val date = calendar.dateFor(week, lesson.weekday) ?: return@weekLoop
            if (date.isAfter(now.toLocalDate().plusDays(horizonDays))) return@weekLoop
            val target = date.atTime(start).atZone(now.zone)
            val delay = ChronoUnit.MILLIS.between(now.toInstant(), target.minusMinutes(10).toInstant())
            if (delay > 0) {
                result += CourseReminderPlan(
                    id = lesson.id,
                    title = lesson.title,
                    day = date,
                    start = start,
                    expectedStartEpoch = target.toEpochSecond(),
                    delayMillis = delay
                )
            }
        }
    }
    return result.sortedWith(compareBy({ it.expectedStartEpoch }, { it.id })).take(250)
}

/**
 * Reconciles only the next 14 days; one Android WorkManager daily maintenance
 * job keeps the rolling window alive without continuous background polling.
 * WorkManager is inexact and may deliver later under battery restrictions.
 */
object LocalCourseReminders {
    private const val PREFS = "xiaoxu-course-reminders-v1"
    private const val ENABLED = "enabled"
    private const val ACTIVE_NAMES = "scheduled-names"
    private const val TAG = "xiaoxu-course-reminder"
    private const val CHANNEL = "xiaoxu-class-alerts"
    private const val DAILY = "xiaoxu-course-reminder-daily"

    fun enabled(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(ENABLED, false)

    fun canNotify(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun setEnabled(context: Context, value: Boolean) {
        val app = context.applicationContext
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(ENABLED, value).apply()
        if (!value) {
            val manager = WorkManager.getInstance(app)
            manager.cancelAllWorkByTag(TAG)
            manager.cancelUniqueWork(DAILY)
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().remove(ACTIVE_NAMES).apply()
        }
    }

    fun reconcile(
        context: Context,
        lessons: List<VerifiedLesson>,
        calendar: SemesterCalendar?,
        zone: ZoneId = ZoneId.systemDefault()
    ) {
        if (!enabled(context)) return
        val app = context.applicationContext
        val manager = WorkManager.getInstance(app)
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val plans = if (calendar == null) emptyList()
            else planCourseReminders(lessons, calendar, ZonedDateTime.now(zone))
        val desired = plans.associateBy { plan ->
            "xiaoxu-course-${plan.id}-${plan.day}-${plan.expectedStartEpoch}"
        }
        val previous = prefs.getStringSet(ACTIVE_NAMES, emptySet()).orEmpty().toSet()
        // Avoid re-enqueuing the entire semester every time the app resumes.
        // Existing WorkManager jobs persist across activity restarts and reboots.
        (previous - desired.keys).forEach { manager.cancelUniqueWork(it) }
        for ((name, plan) in desired.filterKeys { it !in previous }) {
            val input = Data.Builder()
                .putString("id", plan.id)
                .putString("date", plan.day.toString())
                .putLong("startEpoch", plan.expectedStartEpoch)
                .build()
            val work = OneTimeWorkRequestBuilder<LocalCourseReminderWorker>()
                .setInitialDelay(plan.delayMillis, TimeUnit.MILLISECONDS)
                .setInputData(input)
                .addTag(TAG)
                .build()
            manager.enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, work)
        }
        prefs.edit().putStringSet(ACTIVE_NAMES, desired.keys.toSet()).apply()
        val daily = PeriodicWorkRequestBuilder<LocalCourseReminderRefreshWorker>(
            1, TimeUnit.DAYS
        ).build()
        manager.enqueueUniquePeriodicWork(DAILY, ExistingPeriodicWorkPolicy.KEEP, daily)
    }

    internal fun deliver(context: Context, lesson: VerifiedLesson) {
        if (!enabled(context) || !canNotify(context)) return
        val app = context.applicationContext
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "本机上课提醒", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val intent = Intent(app, MainActivity::class.java).apply {
            putExtra("xiaoxu_open_schedule_from_reminder", true)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            app, 17015, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alert = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("即将上课 · ${lesson.title}")
            .setContentText("来自本机课表，点此查看课程和教室；通知可能受省电策略延后。")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        manager.notify(("class-" + lesson.id).hashCode(), alert)
    }
}

/** Re-read the actual current timetable before showing any notification. */
class LocalCourseReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        if (!LocalCourseReminders.enabled(applicationContext)) return Result.success()
        val id = inputData.getString("id") ?: return Result.success()
        val dateText = inputData.getString("date") ?: return Result.success()
        val date = runCatching { LocalDate.parse(dateText) }.getOrNull()
            ?: return Result.success()
        val expectedStart = inputData.getLong("startEpoch", -1L)
        if (expectedStart <= 0L || !LocalCourseReminders.canNotify(applicationContext)) {
            return Result.success()
        }
        val config = LocalSemesterConfigStore.load(applicationContext) ?: return Result.success()
        val now = ZonedDateTime.now()
        val lesson = LocalScheduleStore.load(applicationContext).firstOrNull { lesson ->
            lesson.id == id && lesson.weeks.any { week ->
                config.dateFor(week, lesson.weekday) == date
            } && config.sectionTimes[lesson.startSection]?.start?.let { start ->
                date.atTime(start).atZone(now.zone).toEpochSecond() == expectedStart
            } == true
        } ?: return Result.success()
        val startAt = java.time.Instant.ofEpochSecond(expectedStart)
        if (now.toInstant().isBefore(startAt.minusSeconds(12 * 60)) ||
            !now.toInstant().isBefore(startAt.plusSeconds(5 * 60))) return Result.success()
        LocalCourseReminders.deliver(applicationContext, lesson)
        return Result.success()
    }
}

/** One lightweight local refresh per day keeps future class reminders scheduled. */
class LocalCourseReminderRefreshWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        if (!LocalCourseReminders.enabled(applicationContext)) return Result.success()
        LocalCourseReminders.reconcile(
            applicationContext,
            LocalScheduleStore.load(applicationContext),
            LocalSemesterConfigStore.load(applicationContext)
        )
        return Result.success()
    }
}
