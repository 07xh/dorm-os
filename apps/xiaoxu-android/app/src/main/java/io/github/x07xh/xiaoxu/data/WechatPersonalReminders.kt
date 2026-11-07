package io.github.x07xh.xiaoxu.data

import android.content.Context
import android.content.SharedPreferences
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit


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
