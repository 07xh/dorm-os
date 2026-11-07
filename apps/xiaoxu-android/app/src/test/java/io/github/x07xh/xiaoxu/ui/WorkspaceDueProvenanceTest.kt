package io.github.x07xh.xiaoxu.ui

import java.time.LocalDate
import io.github.x07xh.xiaoxu.data.CloudTask
import org.junit.Assert.assertEquals
import org.junit.Test

/** Unverified school dates must never inflate actionable local deadline statistics. */
class WorkspaceDueProvenanceTest {
    private val today = LocalDate.of(2026, 11, 7)

    private fun task(
        id: String,
        storage: String,
        date: LocalDate?,
        done: Boolean = false
    ) = WorkspaceTask(
        key = "$storage:$id",
        title = id,
        origin = if (storage == "cloud") "学习通记录" else "本机任务",
        done = done,
        date = date,
        dateSource = if (storage == "cloud") "云端记录，需核对来源" else "本人填写",
        storage = storage,
        provider = if (storage == "cloud") "chaoxing" else null
    )

    @Test
    fun upcomingCountRequiresLocalNotOverdueAndNotDone() {
        val items = listOf(
            task("today", "local", today),
            task("third-day", "local", today.plusDays(3)),
            task("past", "local", today.minusDays(1)),
            task("far", "local", today.plusDays(4)),
            task("finished", "local", today.plusDays(1), true),
            task("unverified-cloud-today", "cloud", today),
            task("unverified-cloud-past", "cloud", today.minusDays(30))
        )
        assertEquals(2, countUpcomingLocalDeadlines(items, today))
    }

    @Test
    fun weeklyFiltersDoNotTreatCloudRecordsAsVerifiedSchoolDueDates() {
        val items = listOf(
            task("self-entered", "local", today),
            task("unverified-school", "cloud", today)
        )
        assertEquals(listOf("self-entered"),
            searchWorkspaceTasks(items, "week", "", today).map { it.title })
        assertEquals(listOf("self-entered"),
            facetWorkspaceTasks(items, "all", true, today).map { it.title })
    }

    @Test
    fun unknownDatesAreNotCountedAsUpcoming() {
        assertEquals(0, countUpcomingLocalDeadlines(
            listOf(task("none", "local", null), task("cloud", "cloud", null)), today))
    }

    @Test
    fun reviewedWechatPersonalPlanNeverOverwritesMessageDeadline() {
        val fromCloud = CloudTask(
            id = "reviewed-1", title = "提交实验报告",
            status = "todo", kind = "manual", provider = "wechat",
            due = "2026-11-12", revision = 2,
            personalTargetAt = "2026-11-09T15:59:00.000Z"
        )
        val item = combineWorkspaceTasks(emptyList(), listOf(fromCloud)).single()
        assertEquals("wechat", item.category)
        assertEquals(LocalDate.of(2026, 11, 9), item.date)
        assertEquals(LocalDate.of(2026, 11, 12), item.sourceDueDate)
        assertEquals("本人计划", item.dateSource)
        assertEquals(1, countUpcomingLocalDeadlines(listOf(item), today))
        val monday = LocalDate.of(2026, 11, 9)
        assertEquals(listOf("提交实验报告"),
            searchWorkspaceTasks(listOf(item), "week", "", monday).map { it.title })
        assertEquals(listOf("提交实验报告"),
            facetWorkspaceTasks(listOf(item), "all", true, monday).map { it.title })
    }

    @Test
    fun cloudDueWithoutPersonalPlanNeverEntersLocalDeadlineCounts() {
        val sourceOnly = CloudTask(
            id = "source-1", title = "未核实云端任务",
            status = "todo", kind = "platform", provider = "chaoxing",
            due = "2026-11-08", revision = 3
        )
        val item = combineWorkspaceTasks(emptyList(), listOf(sourceOnly)).single()
        assertEquals(0, countUpcomingLocalDeadlines(listOf(item), today))
        assertEquals(emptyList<String>(),
            searchWorkspaceTasks(listOf(item), "week", "", today).map { it.title })
    }
}
