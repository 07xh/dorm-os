package io.github.x07xh.xiaoxu.data

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure scheduling contract: opt-in runtime and school-data boundaries live outside this function. */
class LocalDeadlineRemindersTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = LocalDate.of(2026, 11, 6).atTime(8, 30).atZone(zone)

    @Test fun futureTaskGetsOneDayAndDueDayAtLocalNine() {
        val task = LocalTask(id = 11, title = "复习课堂例题", due = "2026-11-08")
        val plans = planLocalReminders(listOf(task), now)
        assertEquals(listOf("before", "today"), plans.map { it.phase })
        assertEquals(
            listOf(LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 8)),
            plans.map { it.deliverOn }
        )
        assertEquals(listOf(24L, 48L).map { it * 60L * 60L * 1000L + 30L * 60L * 1000L },
            plans.map { it.delayMillis })
    }

    @Test fun sameDayAfterNineDoesNotScheduleLateOrDuplicateNotification() {
        val atTen = LocalDate.of(2026, 11, 6).atTime(10, 0).atZone(zone)
        val tasks = listOf(LocalTask(id = 9, title = "手工提醒", due = "2026-11-06"))
        assertTrue(planLocalReminders(tasks, atTen).isEmpty())
    }

    @Test fun completedInvalidOrDateMissingTasksProduceNothing() {
        val tasks = listOf(
            LocalTask(id = 1, title = "已完成", due = "2026-11-08", completed = true),
            LocalTask(id = 2, title = "无日期", due = null),
            LocalTask(id = 3, title = "非法日期", due = "2026-99-99"),
            LocalTask(id = 4, title = "空 ID", due = "2026-11-09"),
            LocalTask(id = 0, title = "未插入", due = "2026-11-08")
        )
        assertTrue(planLocalReminders(tasks.filter { it.id != 4L }, now).isEmpty())
    }

    @Test fun todayReminderAndDayBeforeReminderStayDistinct() {
        val dueTomorrow = LocalTask(id = 12, title = "明天截止", due = "2026-11-07")
        val plans = planLocalReminders(listOf(dueTomorrow), now)
        assertEquals(2, plans.size)
        assertEquals("before", plans[0].phase)
        assertEquals(LocalDate.of(2026, 11, 6), plans[0].deliverOn)
        assertEquals(30L * 60L * 1000L, plans[0].delayMillis)
    }
}
