package io.github.x07xh.xiaoxu.ui

import io.github.x07xh.xiaoxu.data.CloudTask
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** New task-center search must not merge sources or silently treat candidates as tasks. */
class TaskCenterSearchTest {
    private val today = LocalDate.of(2026, 11, 6)
    private val records = listOf(
        WorkspaceTask("cloud:1", "电路实验报告", "学习通相关云端记录",
            false, today.plusDays(2), "云端待核对", "cloud"),
        WorkspaceTask("local:1", "准备工程制图工具", "本机待办",
            false, null, "本人填写", "local"),
        WorkspaceTask("local:2", "复习高等数学", "本机待办",
            true, today.minusDays(1), "本人填写", "local")
    )

    @Test
    fun searchByTitleAndSourceKeepsStorageAndOriginalOrder() {
        assertEquals(listOf("cloud:1"),
            searchWorkspaceTasks(records, "all", "实验报告", today).map { it.key })
        assertEquals(listOf("cloud:1"),
            searchWorkspaceTasks(records, "all", "学习通", today).map { it.key })
        assertEquals(listOf("local:1", "local:2"),
            searchWorkspaceTasks(records, "local", "本机", today).map { it.key })
    }

    @Test
    fun searchRespectsSelectedStatusAndWeekFilter() {
        assertEquals(listOf("cloud:1"),
            searchWorkspaceTasks(records, "pending", "报告", today).map { it.key })
        assertTrue(searchWorkspaceTasks(records, "done", "报告", today).isEmpty())
        // A cloud task date alone does not prove an official school deadline.
        assertTrue(searchWorkspaceTasks(records, "week", "", today).isEmpty())
        assertEquals(listOf("local:2"),
            searchWorkspaceTasks(records, "done", "复习", today).map { it.key })
    }

    @Test
    fun independentStatusSourceAndDueFiltersPreserveRealRecords() {
        val pending = searchWorkspaceTasks(records, "pending", "", today)
        assertEquals(listOf("cloud:1", "local:1"), pending.map { it.key })
        assertTrue(facetWorkspaceTasks(pending, "all", true, today).isEmpty())
        assertEquals(listOf("local:1"),
            facetWorkspaceTasks(pending, "local", false, today).map { it.key })
        assertTrue(facetWorkspaceTasks(pending, "local", true, today).isEmpty())
        assertEquals(listOf("local:2"),
            facetWorkspaceTasks(searchWorkspaceTasks(records, "done", "", today),
                "all", false, today).map { it.key })
        // Completed items must not appear in the this-week deadline subset.
        assertTrue(facetWorkspaceTasks(searchWorkspaceTasks(records, "done", "", today),
            "all", true, today).isEmpty())
    }

    @Test
    fun approvedWechatTasksStayInSeparateGroupWhileUsingExistingCloudStorage() {
        val cloud = listOf(
            CloudTask("wx-accepted", "群通知实验任务", "todo", "manual", "wechat", "2026-11-08", revision = 7),
            CloudTask("course-1", "课程待办", "todo", "manual", "chaoxing", null),
            CloudTask("manual-1", "普通云端待办", "done", "manual", null, null)
        )
        val combined = combineWorkspaceTasks(emptyList(), cloud)
        assertEquals(3, combined.size)
        val wechat = combined.single { it.key == "cloud:wx-accepted" }
        assertEquals("wechat", wechat.category)
        assertEquals("cloud", wechat.storage)
        assertEquals(7, wechat.revision)
        assertEquals("微信任务", wechat.origin)
        assertEquals(listOf("cloud:wx-accepted"),
            facetWorkspaceTasks(combined, "wechat", false, today).map { it.key })
        assertEquals(setOf("cloud:course-1", "cloud:manual-1"),
            facetWorkspaceTasks(combined, "cloud", false, today).map { it.key }.toSet())
        assertEquals(2, searchWorkspaceTasks(combined, "pending", "", today).size)
        assertEquals(1, searchWorkspaceTasks(combined, "done", "", today).size)
        // WeChat's extracted date is not a confirmed personal due date.
        assertTrue(facetWorkspaceTasks(
            searchWorkspaceTasks(combined, "pending", "", today),
            "wechat", true, today
        ).isEmpty())
    }

    @Test
    fun upcomingDeadlineCountUsesOnlyPersonalEnteredFutureDates() {
        val rows = listOf(
            WorkspaceTask("local:today", "今天截止", "本机待办", false,
                today, "本人填写", "local"),
            WorkspaceTask("local:three", "三天后", "本机待办", false,
                today.plusDays(3), "本人填写", "local"),
            WorkspaceTask("local:past", "之前逾期", "本机待办", false,
                today.minusDays(1), "本人填写", "local"),
            WorkspaceTask("local:later", "四天后", "本机待办", false,
                today.plusDays(4), "本人填写", "local"),
            WorkspaceTask("local:done", "已经完成", "本机待办", true,
                today.plusDays(1), "本人填写", "local"),
            WorkspaceTask("cloud:unverified", "未核验学校任务", "学习通记录", false,
                today.plusDays(1), "云端记录，需核对来源", "cloud",
                provider = "chaoxing")
        )
        // "近期截止" uses today through +3 days, excluding overdue dates.
        assertEquals(2, countUpcomingLocalDeadlines(rows, today))
        // "本周截止" uses the calendar week (Mon–Sun): an overdue task from
        // Thursday still belongs to this week; the Monday +3-day task does not.
        assertEquals(listOf("local:today", "local:past"),
            facetWorkspaceTasks(rows, "all", true, today).map { it.key })
    }

    @Test
    fun trustedOfficialStatusNeverChangesPersonalCompletion() {
        val records = combineWorkspaceTasks(emptyList(), listOf(
            CloudTask("school-verified", "电路作业", "done", "homework",
                "chaoxing", null, sourceExternalId = "work:123:456:789",
                officialStatus = "todo"),
            CloudTask("legacy", "标题看起来一样", "todo", "homework",
                "chaoxing", null, sourceExternalId = "legacy:789",
                officialStatus = "submitted"),
            CloudTask("not-chaoxing", "普通云端记录", "todo", "manual",
                null, null, sourceExternalId = "work:123:456:789",
                officialStatus = "graded")
        ))
        val verified = records.single { it.key == "cloud:school-verified" }
        assertTrue(verified.done)
        assertEquals("todo", verified.officialStatus)
        assertEquals("官方未提交", officialTaskStatusLabel(verified.officialStatus))
        assertEquals(null, records.single { it.key == "cloud:legacy" }.officialStatus)
        assertEquals(null, records.single { it.key == "cloud:not-chaoxing" }.officialStatus)
    }

    @Test
    fun trustedSubmissionLeavesPersonalFlagUntouchedButMovesOutOfPending() {
        val records = combineWorkspaceTasks(emptyList(), listOf(
            CloudTask("verified-submitted", "同名作业", "todo", "homework",
                "chaoxing", null, sourceExternalId = "work:1:2:3",
                officialStatus = "submitted"),
            CloudTask("verified-graded", "另一份已批阅", "todo", "homework",
                "chaoxing", null, sourceExternalId = "work:9:8:7",
                officialStatus = "graded"),
            CloudTask("returned", "需要重新交", "todo", "homework",
                "chaoxing", null, sourceExternalId = "work:2:2:2",
                officialStatus = "returned"),
            CloudTask("legacy", "同名作业", "todo", "homework",
                "chaoxing", null, sourceExternalId = "legacy-id",
                officialStatus = "submitted"),
            CloudTask("wechat", "其他平台", "todo", "manual",
                "wechat", null, sourceExternalId = "work:1:2:3",
                officialStatus = "graded")
        ))
        val submitted = records.single { it.key == "cloud:verified-submitted" }
        assertEquals(false, submitted.done)
        assertEquals(true, considerCompleteInTaskList(submitted))
        assertEquals(
            setOf("cloud:verified-submitted", "cloud:verified-graded"),
            searchWorkspaceTasks(records, "done", "", today).map { it.key }.toSet()
        )
        assertEquals(
            setOf("cloud:returned", "cloud:legacy", "cloud:wechat"),
            searchWorkspaceTasks(records, "pending", "", today).map { it.key }.toSet()
        )
    }

    @Test
    fun deviceOnlyArchiveIsExactIdScopedAndRestorable() {
        val records = listOf(
            WorkspaceTask("cloud:11", "同名", "学习通记录", false, null, "", "cloud"),
            WorkspaceTask("cloud:12", "同名", "学习通记录", false, null, "", "cloud"),
            WorkspaceTask("local:11", "同名", "本机", false, null, "", "local")
        )
        assertEquals(records, visibleWorkspaceTasks(records, emptySet()))
        val cleaned = visibleWorkspaceTasks(records, setOf("cloud:11"))
        assertEquals(listOf("cloud:12", "local:11"), cleaned.map { it.key })
        assertEquals(records, visibleWorkspaceTasks(records, emptySet()))
    }

    @Test
    fun editorScrollIndexMatchesActualOptionalRows() {
        // Always present: header, status strip, combined source/filter controls.
        assertEquals(3, taskEditorStartIndex(false, false, false, false))
        assertEquals(4, taskEditorStartIndex(true, false, false, false))
        assertEquals(4, taskEditorStartIndex(false, true, false, false))
        assertEquals(5, taskEditorStartIndex(false, true, false, true))
        assertEquals(7, taskEditorStartIndex(true, true, true, true))
        // Cloud editor follows the local editor only if that editor is visible.
        assertEquals(4, taskEditorStartIndex(false, false, false, false) + 1)
    }

    @Test
    fun whitespaceAndCaseInsensitiveSearchAreStable() {
        assertEquals(records, searchWorkspaceTasks(records, "all", "  ", today))
        assertEquals(listOf("cloud:1"),
            searchWorkspaceTasks(records, "cloud", "  学习通  ", today).map { it.key })
        assertEquals(emptyList<WorkspaceTask>(), searchWorkspaceTasks(records, "all", "无关内容", today))
    }
}
