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

/** Each reminder is an inexact 09:00 local-time WorkManager job. */
internal fun planLocalReminders(tasks: List<LocalTask>, now: ZonedDateTime): List<LocalReminderPlan> =
    buildList {
        tasks.forEach { task ->
            if (task.id <= 0 || task.completed) return@forEach
            val due = runCatching { LocalDate.parse(task.due) }.getOrNull() ?: return@forEach
            listOf("before" to due.minusDays(1), "today" to due).forEach { (phase, day) ->
                val target = day.atTime(9, 0).atZone(now.zone)
                val delay = ChronoUnit.MILLIS.between(now.toInstant(), target.toInstant())
                if (delay > 0) add(LocalReminderPlan(task.id, phase, due.toString(), day, delay))
            }
        }
    }

object LocalDeadlineReminders {
    private const val PREFS = "xiaoxu-local-reminders-v1"
    private const val ENABLED = "enabled"
    private const val TAG = "xiaoxu-local-deadline-v1"
    private const val CHANNEL = "xiaoxu-local-deadlines"

    fun enabled(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(ENABLED, false)

    fun setEnabled(context: Context, value: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(ENABLED, value).apply()
        if (!value) WorkManager.getInstance(context.applicationContext).cancelAllWorkByTag(TAG)
    }

    /** Invoked on every actual Room task update; WorkManager persists jobs through reboot. */
    fun reconcile(context: Context, tasks: List<LocalTask>, zone: ZoneId = ZoneId.systemDefault()) {
        if (!enabled(context)) return
        val app = context.applicationContext
        val manager = WorkManager.getInstance(app)
        val plans = planLocalReminders(tasks, ZonedDateTime.now(zone))
        val wanted = plans.associateBy { workName(it.taskId, it.phase) }

        tasks.forEach { task ->
            listOf("before", "today").forEach { phase ->
                val name = workName(task.id, phase)
                val plan = wanted[name]
                if (plan == null) {
                    manager.cancelUniqueWork(name)
                } else {
                    val input = Data.Builder()
                        .putLong("task_id", plan.taskId)
                        .putString("due", plan.due)
                        .putString("phase", plan.phase)
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

    internal fun canNotify(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    internal fun deliver(context: Context, taskId: Long, phase: String) {
        if (!enabled(context) || !canNotify(context)) return
        val app = context.applicationContext
        val notifications = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            notifications.createNotificationChannel(
                NotificationChannel(CHANNEL, "本机待办截止提醒", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val launch = Intent(app, MainActivity::class.java).apply {
            putExtra("xiaoxu_open_tasks_from_reminder", true)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            app, (taskId % Int.MAX_VALUE).toInt(), launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = if (phase == "before") "明天有一项本机待办截止" else "今天有一项本机待办截止"
        val notification = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText("这是你自己填写的截止日期，点击进入校序任务中心核对。")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        notifications.notify((taskId % 1000000).toInt() * 2 + if (phase == "today") 1 else 0,
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
        val task = runBlocking { LocalTaskDatabase.open(applicationContext).tasks().findById(id) }
            ?: return Result.success()
        if (task.completed || task.due != due) return Result.success()
        LocalDeadlineReminders.deliver(applicationContext, id, phase)
        return Result.success()
    }
}
