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
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import io.github.x07xh.xiaoxu.MainActivity
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking

/** Only opt-in Room tasks with a USER-ENTERED date. Never schedule a school snapshot. */
internal data class LocalReminderPlan(
    val taskId: Long,
    val phase: String,
    val due: String,
    val deliverOn: LocalDate,
    val delayMillis: Long
)

/** Saved only on this device. Existing users keep the 09:00 two-phase default. */
data class DeadlineReminderSchedule(
    val dayBefore: Boolean = true,
    val dueDay: Boolean = true,
    val hour: Int = 9,
    val minute: Int = 0
) {
    init { require(hour in 0..23 && minute in 0..59) }
}

internal fun planLocalReminders(
    tasks: List<LocalTask>, now: ZonedDateTime,
    schedule: DeadlineReminderSchedule = DeadlineReminderSchedule()
): List<LocalReminderPlan> =
    buildList {
        tasks.forEach { task ->
            if (task.id <= 0 || task.completed) return@forEach
            val due = runCatching { LocalDate.parse(task.due) }.getOrNull() ?: return@forEach
            listOfNotNull(
                if (schedule.dayBefore) "before" to due.minusDays(1) else null,
                if (schedule.dueDay) "today" to due else null
            ).forEach { (phase, day) ->
                val target = day.atTime(schedule.hour, schedule.minute).atZone(now.zone)
                val delay = ChronoUnit.MILLIS.between(now.toInstant(), target.toInstant())
                if (delay > 0) add(LocalReminderPlan(task.id, phase, due.toString(), day, delay))
            }
        }
    }

/**
 * The active reminder date is the only persisted field that affects its jobs.
 * A full first reconciliation also cancels stale jobs for completed records.
 * Subsequent Room emissions touch just the affected IDs, including deletions.
 */
internal fun changedLocalReminderIds(
    previous: List<LocalTask>?,
    current: List<LocalTask>
): Set<Long> {
    val currentById = current.asSequence().filter { it.id > 0 }.associateBy { it.id }
    if (previous == null) return currentById.keys

    val previousById = previous.asSequence().filter { it.id > 0 }.associateBy { it.id }
    fun activeDue(task: LocalTask?): String? =
        task?.takeUnless { it.completed }?.due?.takeIf { it.isNotBlank() }

    return (previousById.keys + currentById.keys).filterTo(mutableSetOf()) { id ->
        activeDue(previousById[id]) != activeDue(currentById[id])
    }
}

object LocalDeadlineReminders {
    private const val PREFS = "xiaoxu-local-reminders-v1"
    private const val ENABLED = "enabled"
    private const val BEFORE = "day-before"
    private const val ON_DUE = "due-day"
    private const val HOUR = "hour"
    private const val MINUTE = "minute"
    private const val TAG = "xiaoxu-local-deadline-v1"
    private const val CHANNEL = "xiaoxu-local-deadlines"

    fun enabled(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(ENABLED, false)

    fun schedule(context: Context): DeadlineReminderSchedule {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return DeadlineReminderSchedule(
            dayBefore = prefs.getBoolean(BEFORE, true),
            dueDay = prefs.getBoolean(ON_DUE, true),
            hour = prefs.getInt(HOUR, 9).coerceIn(0, 23),
            minute = prefs.getInt(MINUTE, 0).coerceIn(0, 59)
        )
    }

    fun setSchedule(context: Context, value: DeadlineReminderSchedule): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(BEFORE, value.dayBefore).putBoolean(ON_DUE, value.dueDay)
            .putInt(HOUR, value.hour).putInt(MINUTE, value.minute).commit()

    fun setEnabled(context: Context, value: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(ENABLED, value).apply()
        if (!value) WorkManager.getInstance(context.applicationContext).cancelAllWorkByTag(TAG)
    }

    /** Reconcile changed Room records; WorkManager persists jobs across reboot. */
    fun reconcile(
        context: Context,
        tasks: List<LocalTask>,
        zone: ZoneId = ZoneId.systemDefault(),
        previousTasks: List<LocalTask>? = null
    ) {
        if (!enabled(context)) return
        val affectedIds = changedLocalReminderIds(previousTasks, tasks)
        if (affectedIds.isEmpty()) return

        val app = context.applicationContext
        val manager = WorkManager.getInstance(app)
        val affectedTasks = tasks.filter { it.id in affectedIds }
        val activeSchedule = schedule(app)
        val plans = planLocalReminders(affectedTasks, ZonedDateTime.now(zone), activeSchedule)
        val wanted = plans.associateBy { workName(it.taskId, it.phase) }

        // IDs no longer in Room are still present in affectedIds, so deleted
        // tasks' scheduled jobs get cancelled rather than merely expiring later.
        affectedIds.forEach { taskId ->
            listOf("before", "today").forEach { phase ->
                val name = workName(taskId, phase)
                val plan = wanted[name]
                if (plan == null) {
                    manager.cancelUniqueWork(name)
                } else {
                    val input = Data.Builder()
                        .putLong("task_id", plan.taskId)
                        .putString("due", plan.due)
                        .putString("phase", plan.phase)
                        .putInt("schedule_hour", activeSchedule.hour)
                        .putInt("schedule_minute", activeSchedule.minute)
                        .build()
                    val work = OneTimeWorkRequestBuilder<LocalDeadlineReminderWorker>()
                        .setInitialDelay(plan.delayMillis, TimeUnit.MILLISECONDS)
                        .setInputData(input)
                        .addTag(TAG)
                        .build()
                    manager.enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, work)
                }
            }
        }
    }

    private fun workName(taskId: Long, phase: String): String =
        "xiaoxu-local-reminder-$taskId-$phase"

    internal fun canNotify(context: Context): Boolean {
        val app = context.applicationContext
        val permission = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = if (Build.VERSION.SDK_INT >= 26)
            manager.getNotificationChannel(CHANNEL) else null
        return permission && NotificationManagerCompat.from(app).areNotificationsEnabled() &&
            (channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE)
    }

    internal fun deliver(context: Context, task: LocalTask, phase: String) {
        if (!enabled(context) || !canNotify(context) || LocalReminderQuietHours.suppressNow(context)) return
        val app = context.applicationContext
        val notifications = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            notifications.createNotificationChannel(
                NotificationChannel(CHANNEL, "本机待办截止提醒", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val launch = Intent(app, MainActivity::class.java).apply {
            putExtra("xiaoxu_open_tasks_from_reminder", true)
            putExtra("xiaoxu_open_task_id", task.id)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            app, (task.id % Int.MAX_VALUE).toInt(), launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = if (phase == "before") "明天截止 · " + task.title
            else "今天截止 · " + task.title
        val notification = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText("个人任务 · 点击查看详情")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        notifications.notify((task.id % 1000000).toInt() * 2 + if (phase == "today") 1 else 0,
            notification)
    }
}

/** Verify fresh Room row + exact intended date before showing anything. */
class LocalDeadlineReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val id = inputData.getLong("task_id", -1)
        val due = inputData.getString("due") ?: return Result.success()
        val phase = inputData.getString("phase") ?: return Result.success()
        if (id <= 0 || phase !in setOf("before", "today")) return Result.success()
        if (!LocalDeadlineReminders.enabled(applicationContext)) return Result.success()
        val date = runCatching { LocalDate.parse(due) }.getOrNull() ?: return Result.success()
        val expected = if (phase == "before") date.minusDays(1) else date
        if (LocalDate.now() != expected) return Result.success()
        val schedule = LocalDeadlineReminders.schedule(applicationContext)
        if ((phase == "before" && !schedule.dayBefore) ||
            (phase == "today" && !schedule.dueDay) ||
            inputData.getInt("schedule_hour", 9) != schedule.hour ||
            inputData.getInt("schedule_minute", 0) != schedule.minute) return Result.success()
        // A job delayed past the end of quiet hours must still be skipped:
        // check its *scheduled* local clock time as well as the current time.
        val quiet = LocalReminderQuietHours.load(applicationContext)
        if (isQuietMinute(schedule.hour * 60 + schedule.minute, quiet)) return Result.success()
        val task = runBlocking { LocalTaskDatabase.open(applicationContext).tasks().findById(id) }
            ?: return Result.success()
        if (task.completed || task.due != due) return Result.success()
        LocalDeadlineReminders.deliver(applicationContext, task, phase)
        return Result.success()
    }
}
