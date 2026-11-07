package io.github.x07xh.xiaoxu.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.SharedPreferences
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
import java.time.Instant
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
    private const val TAG = "xiaoxu-local-deadline-v1"
    private const val CHANNEL = "xiaoxu-local-deadlines"

    fun enabled(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(ENABLED, false)

    fun setEnabled(context: Context, value: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(ENABLED, value).apply()
        if (!value) {
            WorkManager.getInstance(context.applicationContext).cancelAllWorkByTag(TAG)
            WechatPersonalReminders.clear(context)
        }
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
        val plans = planLocalReminders(affectedTasks, ZonedDateTime.now(zone))
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
        val id = (taskId % 1000000).toInt() * 2 + if (phase == "today") 1 else 0
        val requestCode = (taskId % Int.MAX_VALUE).toInt()
        sendPersonalNotification(context, id, requestCode,
            if (phase == "before") "明天有一项本机待办截止" else "今天有一项本机待办截止",
            "这是你自己填写的截止日期，点击进入校序任务中心核对。")
    }

    internal fun deliverWechat(context: Context, taskId: String, phase: String) {
        val salt = "wechat-personal:$taskId:$phase"
        val notificationId = salt.hashCode() and Int.MAX_VALUE
        sendPersonalNotification(context, notificationId, notificationId,
            if (phase == "before") "明天有一项微信任务个人计划到期"
            else "今天有一项微信任务个人计划到期",
            "仅依据上次同步的个人计划日期；点击进入任务中心核对，不代表微信原消息截止。")
    }

    private fun sendPersonalNotification(
        context: Context, notificationId: Int, requestCode: Int,
        title: String, detail: String
    ) {
        if (!enabled(context) || !canNotify(context)) return
        val app = context.applicationContext
        val notifications = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            notifications.createNotificationChannel(NotificationChannel(
                CHANNEL, "个人任务截止提醒", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val launch = Intent(app, MainActivity::class.java).apply {
            putExtra("xiaoxu_open_tasks_from_reminder", true)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            app, requestCode, launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(detail)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        notifications.notify(notificationId, notification)
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


/** Account-scoped reminders for personally approved WeChat plans only.
 * Reuses the existing task-notification permission, channel and opt-in switch.
 * SharedPreferences contains ONLY task IDs and dates, never chat text or cookies.
 * Last-synced data may be stale; the notification explicitly asks for verification.
 */
internal fun approvedWechatPersonalDates(tasks: List<CloudTask>): Map<String, String> =
    tasks.asSequence()
        .filter { it.provider == "wechat" && it.revision >= 0 &&
            it.status != "done" && Regex("^[A-Za-z0-9_-]{1,100}$").matches(it.id) }
        .mapNotNull { task ->
            val date = task.personalTargetAt?.let { raw ->
                runCatching {
                    Instant.parse(raw).atZone(ZoneId.of("Asia/Shanghai"))
                        .toLocalDate().toString()
                }.getOrNull()
            }
            date?.let { task.id to it }
        }.toMap()

internal data class WechatReminderPlan(
    val taskId: String, val phase: String,
    val due: String, val deliverOn: LocalDate, val delayMillis: Long
)

internal fun planWechatPersonalReminders(
    tasks: List<CloudTask>, now: ZonedDateTime
): List<WechatReminderPlan> = buildList {
    approvedWechatPersonalDates(tasks).forEach { (taskId, dateText) ->
        val day = LocalDate.parse(dateText)
        listOf("before" to day.minusDays(1), "today" to day).forEach { (phase, delivery) ->
            val target = delivery.atTime(9, 0).atZone(now.zone)
            val delay = ChronoUnit.MILLIS.between(now.toInstant(), target.toInstant())
            if (delay > 0) add(WechatReminderPlan(taskId, phase, dateText, delivery, delay))
        }
    }
}

internal object WechatPersonalReminders {
    private const val PREFS = "xiaoxu-wechat-personal-reminders-v1"
    private const val TAG = "xiaoxu-wechat-personal-reminder-v1"
    private const val OWNER = "owner_binding"
    private const val DAYS = "scheduled_dates"
    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun workName(id: String, phase: String) =
        "xiaoxu-wechat-personal-reminder-$id-$phase"

    private fun stored(prefs: SharedPreferences): Map<String, String> =
        (prefs.getStringSet(DAYS, emptySet()) ?: emptySet()).mapNotNull { entry ->
            val pair = entry.split('=', limit = 2)
            if (pair.size != 2 ||
                !Regex("^[A-Za-z0-9_-]{1,100}$").matches(pair[0]) ||
                runCatching { LocalDate.parse(pair[1]) }.isFailure) null
            else pair[0] to pair[1]
        }.toMap()

    /** Never cancel valid old jobs during transient network failures. */
    @Synchronized fun reconcile(
        context: Context, tasks: List<CloudTask>, ownerBinding: String?
    ) {
        if (!LocalDeadlineReminders.enabled(context) || ownerBinding.isNullOrBlank()) return
        val app = context.applicationContext
        val prefs = prefs(app)
        val manager = WorkManager.getInstance(app)
        val previousOwner = prefs.getString(OWNER, null)
        val previous = if (previousOwner == ownerBinding) stored(prefs) else emptyMap()
        if (previousOwner != ownerBinding) {
            // Authentication session changed: revoke old jobs before storing the new scope.
            manager.cancelAllWorkByTag(TAG)
            prefs.edit().clear().commit()
        }
        val current = approvedWechatPersonalDates(tasks)
        val changed = (previous.keys + current.keys).filter { previous[it] != current[it] }
        // Persist the minimal snapshot first: any pending old worker now fails closed.
        if (!prefs.edit().putString(OWNER, ownerBinding)
            .putStringSet(DAYS, current.map { "${it.key}=${it.value}" }.toSet()).commit()) {
            return
        }
        val plans = planWechatPersonalReminders(
            tasks.filter { it.id in changed }, ZonedDateTime.now())
            .associateBy { workName(it.taskId, it.phase) }
        changed.forEach { taskId ->
            listOf("before", "today").forEach { phase ->
                val name = workName(taskId, phase)
                val plan = plans[name]
                if (plan == null) manager.cancelUniqueWork(name)
                else {
                    val input = Data.Builder()
                        .putString("id", taskId)
                        .putString("date", plan.due)
                        .putString("phase", phase)
                        .putString("owner_binding", ownerBinding)
                        .build()
                    val work = OneTimeWorkRequestBuilder<WechatPersonalReminderWorker>()
                        .setInitialDelay(plan.delayMillis, TimeUnit.MILLISECONDS)
                        .setInputData(input)
                        .addTag(TAG)
                        .build()
                    manager.enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, work)
                }
            }
        }
    }

    @Synchronized fun clear(context: Context) {
        // Clear local date snapshot first; a queued worker validates this map.
        prefs(context).edit().clear().commit()
        WorkManager.getInstance(context.applicationContext).cancelAllWorkByTag(TAG)
    }

    fun canDeliver(context: Context, id: String, date: String, ownerBinding: String): Boolean {
        if (!LocalDeadlineReminders.enabled(context)) return false
        val preferences = prefs(context)
        return preferences.getString(OWNER, null) == ownerBinding &&
            stored(preferences)[id] == date &&
            CloudClient(context.applicationContext).ownerSessionBinding() == ownerBinding
    }
}

/** Validate current local plan, owner session, date and opt-in before notifying. */
class WechatPersonalReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val id = inputData.getString("id") ?: return Result.success()
        val due = inputData.getString("date") ?: return Result.success()
        val phase = inputData.getString("phase") ?: return Result.success()
        val owner = inputData.getString("owner_binding") ?: return Result.success()
        if (!Regex("^[A-Za-z0-9_-]{1,100}$").matches(id) ||
            phase !in setOf("before", "today")) return Result.success()
        val day = runCatching { LocalDate.parse(due) }.getOrNull() ?: return Result.success()
        val expected = if (phase == "before") day.minusDays(1) else day
        if (LocalDate.now() != expected) return Result.success()
        if (!WechatPersonalReminders.canDeliver(applicationContext, id, due, owner))
            return Result.success()
        LocalDeadlineReminders.deliverWechat(applicationContext, id, phase)
        return Result.success()
    }
}
