package io.github.x07xh.xiaoxu.data

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Projected local jobs only; never assert that a notification was actually delivered. */
internal enum class LocalReminderOrigin { TASK, COURSE }

internal data class LocalReminderPreview(
    val key: String,
    val title: String,
    val detail: String,
    val fireAtMillis: Long,
    val origin: LocalReminderOrigin,
    val targetId: String,
    val weekday: Int = 0
)

/**
 * Cross-source read model. Official school notices and WeChat review are deliberately
 * excluded; unknown school due dates cannot enter this projection.
 */
internal fun plannedLocalReminderInbox(
    tasks: List<LocalTask>,
    lessons: List<VerifiedLesson>,
    calendar: SemesterCalendar?,
    now: ZonedDateTime,
    taskEnabled: Boolean,
    selectedCourseIds: Set<String>,
    taskSchedule: DeadlineReminderSchedule,
    leadMinutes: Int,
    quiet: LocalQuietHours,
    limit: Int = 6
): List<LocalReminderPreview> {
    require(limit in 1..20)
    val localFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
    val maxDate = now.toLocalDate().plusDays(7)
    fun visible(at: ZonedDateTime): Boolean =
        !at.toLocalDate().isAfter(maxDate) &&
            !isQuietMinute(at.hour * 60 + at.minute, quiet)

    val projectedTasks = if (taskEnabled) {
        val byId = tasks.associateBy { it.id }
        planLocalReminders(tasks, now, taskSchedule).mapNotNull { plan ->
            val local = byId[plan.taskId] ?: return@mapNotNull null
            val at = now.toInstant().plusMillis(plan.delayMillis).atZone(now.zone)
            if (!visible(at)) return@mapNotNull null
            LocalReminderPreview(
                key = "task:${plan.taskId}:${plan.phase}",
                title = local.title,
                detail = "本机任务 · " + at.format(localFormatter) +
                    if (plan.phase == "before") " · 提前一天" else " · 截止当天",
                fireAtMillis = at.toInstant().toEpochMilli(),
                origin = LocalReminderOrigin.TASK,
                targetId = plan.taskId.toString()
            )
        }
    } else emptyList()

    val selected = lessons.filter { it.id.isNotBlank() && it.id in selectedCourseIds }
    val projectedCourses = if (calendar != null && selected.isNotEmpty()) {
        planCourseReminders(selected, calendar, now, horizonDays = 7,
            leadMinutes = leadMinutes).mapNotNull { plan ->
            val at = now.toInstant().plusMillis(plan.delayMillis).atZone(now.zone)
            if (!visible(at)) return@mapNotNull null
            LocalReminderPreview(
                key = "course:${plan.id}:${plan.day}",
                title = plan.title,
                detail = "本机课表 · " + at.format(localFormatter),
                fireAtMillis = at.toInstant().toEpochMilli(),
                origin = LocalReminderOrigin.COURSE,
                targetId = plan.id,
                weekday = plan.day.dayOfWeek.value
            )
        }
    } else emptyList()

    return (projectedTasks + projectedCourses)
        .sortedWith(compareBy<LocalReminderPreview> { it.fireAtMillis }.thenBy { it.key })
        .distinctBy { it.key }
        .take(limit)
}
