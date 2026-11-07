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

    @Test fun firstReadIncludesEveryPersistedTaskForStaleWorkCleanup() {
        val current = listOf(
            LocalTask(id = 1, title = "未完成", due = "2026-11-08"),
            LocalTask(id = 2, title = "已完成", completed = true),
            LocalTask(id = 3, title = "无日期"),
            LocalTask(id = 0, title = "尚未写入")
        )
        assertEquals(setOf(1L, 2L, 3L), changedLocalReminderIds(null, current))
    }

    @Test fun titleAndSourceEditsDoNotRescheduleExistingJobs() {
        val initial = LocalTask(id = 21, title = "学习", due = "2026-11-08")
        val renamed = initial.copy(title = "复习", source = "course")
        assertTrue(changedLocalReminderIds(listOf(initial), listOf(renamed)).isEmpty())
    }

    @Test fun onlyChangedReminderOrCompletionIsReconciled() {
        val left = LocalTask(id = 11, title = "不变", due = "2026-11-09")
        val right = LocalTask(id = 12, title = "变化", due = "2026-11-10")
        assertEquals(setOf(12L), changedLocalReminderIds(
            listOf(left, right),
            listOf(left.copy(title = "标题变化"), right.copy(completed = true))
        ))
        assertEquals(setOf(12L), changedLocalReminderIds(
            listOf(left, right), listOf(left, right.copy(due = "2026-11-11"))
        ))
    }

    @Test fun deletingDueTaskCancelsWorkButDeletingUndatedTaskDoesNot() {
        val scheduled = LocalTask(id = 31, title = "提醒", due = "2026-11-08")
        val undated = LocalTask(id = 32, title = "无日期")
        assertEquals(setOf(31L), changedLocalReminderIds(
            listOf(scheduled, undated), emptyList()
        ))
    }

    @Test fun addingUndatedTaskDoesNotRequeueJobsUntilDateIsSet() {
        val task = LocalTask(id = 41, title = "暂未填写日期")
        assertTrue(changedLocalReminderIds(emptyList(), listOf(task)).isEmpty())
        assertEquals(setOf(41L), changedLocalReminderIds(
            listOf(task), listOf(task.copy(due = "2026-11-09"))
        ))
    }

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
    @Test fun approvedWechatPersonalPlanUsesExistingDayBeforeAndDayOfSchedule() {
        val task = CloudTask(id = "approved-1", title = "微信来源",
            status = "todo", kind = "manual", provider = "wechat", revision = 2,
            due = "2026-11-12",
            personalTargetAt = "2026-11-08T15:59:00.000Z")
        assertEquals(mapOf("approved-1" to "2026-11-08"),
            approvedWechatPersonalDates(listOf(task)))
        val plans = planWechatPersonalReminders(listOf(task), now)
        assertEquals(listOf("before", "today"), plans.map { it.phase })
        assertEquals(listOf(LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 8)),
            plans.map { it.deliverOn })
        assertEquals(listOf(24L, 48L).map { it * 60L * 60L * 1000L + 30L * 60L * 1000L },
            plans.map { it.delayMillis })
    }

    @Test fun completedOrUnapprovedOrSourceOnlyCloudTaskNeverSchedulesWechat() {
        val approved = CloudTask(id = "approved", title = "待办",
            status = "todo", kind = "manual", provider = "wechat", revision = 1,
            due = "2026-11-09",
            personalTargetAt = "2026-11-08T15:59:00.000Z")
        val ineligible = listOf(
            approved.copy(status = "done"),
            approved.copy(revision = -1),
            approved.copy(provider = "chaoxing"),
            approved.copy(personalTargetAt = null),
            approved.copy(personalTargetAt = "invalid"),
            approved.copy(id = "../unsafe"),
            approved.copy(personalTargetAt = "2026-99-99T15:59:00.000Z")
        )
        assertTrue(approvedWechatPersonalDates(ineligible).isEmpty())
        assertTrue(planWechatPersonalReminders(ineligible, now).isEmpty())
        assertEquals(mapOf("approved" to "2026-11-08"),
            approvedWechatPersonalDates(listOf(approved)))
        // The unverified source due must never become the user's plan.
        assertTrue(approvedWechatPersonalDates(listOf(
            approved.copy(personalTargetAt = null, due = "2026-11-08")
        )).isEmpty())
    }

    @Test fun wechatPlanPastTheMorningDoesNotCreateLateWork() {
        val atTen = LocalDate.of(2026, 11, 8).atTime(10, 0).atZone(zone)
        val task = CloudTask(id = "approved", title = "个人计划",
            status = "todo", kind = "manual", provider = "wechat", revision = 2,
            due = null, personalTargetAt = "2026-11-08T15:59:00.000Z")
        assertTrue(planWechatPersonalReminders(listOf(task), atTen).isEmpty())
    }

}
