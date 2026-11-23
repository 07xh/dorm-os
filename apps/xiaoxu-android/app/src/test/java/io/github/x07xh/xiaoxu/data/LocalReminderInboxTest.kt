package io.github.x07xh.xiaoxu.data

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalReminderInboxTest {
    private val now = LocalDate.of(2026, 11, 6).atTime(8, 30)
        .atZone(ZoneId.of("Asia/Shanghai"))

    @Test fun onlyEnabledRealPersonalDatesAreShown() {
        val tasks = listOf(
            LocalTask(id = 8L, title = "复习", due = "2026-11-08"),
            LocalTask(id = 9L, title = "做完", due = "2026-11-08", completed = true),
            LocalTask(id = 10L, title = "无日期")
        )
        val rows = plannedLocalReminderInbox(
            tasks, emptyList(), null, now, true, emptySet(),
            DeadlineReminderSchedule(), 15, LocalQuietHours())
        assertEquals(2, rows.size)
        assertEquals(setOf("task:8:before", "task:8:today"),
            rows.map { it.key }.toSet())
        assertTrue(rows.all { it.detail.startsWith("本机任务") })
    }

    @Test fun quietHoursSuppressProjectedJobsAndDisableIsEmpty() {
        val tasks = listOf(LocalTask(id = 1L, title = "请查看", due = "2026-11-08"))
        val night = LocalQuietHours(true, 8 * 60, 10 * 60)
        val quietRows = plannedLocalReminderInbox(tasks, emptyList(), null,
            now, true, emptySet(), DeadlineReminderSchedule(), 15, night)
        assertTrue(quietRows.isEmpty())
        val offRows = plannedLocalReminderInbox(tasks, emptyList(), null,
            now, false, emptySet(), DeadlineReminderSchedule(), 15, LocalQuietHours())
        assertTrue(offRows.isEmpty())
    }
}
