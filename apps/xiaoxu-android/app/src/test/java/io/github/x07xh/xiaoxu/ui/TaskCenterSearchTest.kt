package io.github.x07xh.xiaoxu.ui

import io.github.x07xh.xiaoxu.data.CloudTask
import io.github.x07xh.xiaoxu.data.LocalTask
import io.github.x07xh.xiaoxu.data.LocalTaskDependency
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
    fun sameTitleHistoricNoticeMustNotReturnAsPendingAfterOfficialHomeworkCompleted() {
        val rows = combineWorkspaceTasks(emptyList(), listOf(
            CloudTask("old", "实验报告一", "todo", "homework", "chaoxing",
                null, sourceExternalId = "cxn:" + "a".repeat(40)),
            CloudTask("completed", "实验报告一", "todo", "homework", "chaoxing",
                null, sourceExternalId = "work:123:456:901", officialStatus = "graded"),
            CloudTask("unfinished", "另一门课的实验报告一", "todo", "homework", "chaoxing",
                null, sourceExternalId = "work:999:456:901", officialStatus = "todo")
        ))
        assertEquals(3, rows.size)
        assertEquals(listOf("cloud:unfinished"),
            searchWorkspaceTasks(rows, "pending", "", today).map { it.key })
        assertEquals(listOf("cloud:old"),
            searchWorkspaceTasks(rows, "verify", "", today).map { it.key })
        assertEquals(listOf("cloud:completed"),
            searchWorkspaceTasks(rows, "done", "", today).map { it.key })
        assertEquals(3, searchWorkspaceTasks(rows, "all", "", today).size)
        assertEquals(listOf("cloud:unfinished"),
            readyWorkspaceTasks(rows, emptyList(), emptyList()).map { it.key })
        assertTrue(requiresChaoxingVerification(rows.single {it.key == "cloud:old"}))
        assertTrue(rows.none { it.done })
    }

    @Test
    fun dependencyUnlockDoesNotAccidentallyRestoreAnUnknownChaoxingNotice() {
        val local = listOf(LocalTask(id = 1, title = "前置任务"),
            LocalTask(id = 2, title = "后续任务"))
        val edges = listOf(LocalTaskDependency(2, 1))
        val unknown = CloudTask("history", "已完成但尚未关联的旧通知", "todo",
            "homework", "chaoxing", null, sourceExternalId = "cxn:" + "f".repeat(40))
        val before = combineWorkspaceTasks(local, listOf(unknown))
        assertEquals(listOf("local:1"),
            readyWorkspaceTasks(before, local, edges).map { it.key })
        val completed = local.map { if (it.id == 1L) it.copy(completed = true) else it }
        val after = combineWorkspaceTasks(completed, listOf(unknown))
        assertEquals(listOf("local:2"),
            readyWorkspaceTasks(after, completed, edges).map { it.key })
        assertEquals(listOf("cloud:history"),
            searchWorkspaceTasks(after, "verify", "", today).map { it.key })
        assertEquals(listOf("local:2"),
            searchWorkspaceTasks(after, "pending", "", today).map { it.key })
    }

    @Test
    fun nextViewShowsOnlyReadyTasksAndNeverWritesOfficialState() {
        val local = listOf(LocalTask(id = 1, title = "准备材料"),
            LocalTask(id = 2, title = "完成报告"),
            LocalTask(id = 3, title = "提交作业", completed = true))
        val edges = listOf(LocalTaskDependency(2, 1))
        val rows = combineWorkspaceTasks(local, listOf(
            CloudTask("wx-ready", "确认班级通知", "todo", "manual", "wechat", null),
            CloudTask("cx-submitted", "官方已提交", "todo", "homework", "chaoxing",
                null, sourceExternalId = "work:4:5:6", officialStatus = "submitted")
        ))
        val ready = readyWorkspaceTasks(rows, local, edges)
        assertEquals(setOf("local:1", "cloud:wx-ready"), ready.map { it.key }.toSet())
        assertTrue("local:2" !in ready.map { it.key })
        // Completing a prerequisite unlocks the next task without ever
        // mutating an official Chaoxing submission.
        val doneLocal = local.map { if (it.id == 1L) it.copy(completed = true) else it }
        val after = readyWorkspaceTasks(combineWorkspaceTasks(doneLocal, emptyList()),
            doneLocal, edges)
        assertEquals(listOf("local:2"), after.map { it.key })
    }


    @Test
    fun unknownChaoxingHistoryMustNotBeOfferedAsActionableHomework() {
        val rows = combineWorkspaceTasks(emptyList(), listOf(
            CloudTask("unverified", "历史通知残留", "todo", "homework",
                "chaoxing", null, sourceExternalId = "legacy-inbox-record"),
            CloudTask("unknown", "待核对状态", "todo", "homework",
                "chaoxing", null, sourceExternalId = "work:1:2:3", officialStatus = "unknown"),
            CloudTask("real-todo", "官方未提交", "todo", "homework",
                "chaoxing", null, sourceExternalId = "work:3:2:1", officialStatus = "todo"),
            CloudTask("real-done", "已提交", "todo", "homework",
                "chaoxing", null, sourceExternalId = "work:4:2:1", officialStatus = "submitted")
        ))
        val ready = readyWorkspaceTasks(rows, emptyList(), emptyList()).map { it.key }
        assertEquals(listOf("cloud:real-todo"), ready)
        val review = unverifiedChaoxingWorkspaceTasks(rows).map { it.key }.toSet()
        assertEquals(setOf("cloud:unverified", "cloud:unknown"), review)
        assertEquals(4, rows.size) // no historical task is deleted or rewritten
        assertEquals(1, searchWorkspaceTasks(rows, "pending", "", today).size)
        assertEquals(setOf("cloud:unverified", "cloud:unknown"),
            searchWorkspaceTasks(rows, "verify", "", today).map { it.key }.toSet())
    }

    @Test
    fun waitingSectionOnlyContainsBlockedLocalTasksAndRespectsSearchFilter() {
        val local = listOf(
            LocalTask(id = 1, title = "准备实验"),
            LocalTask(id = 2, title = "写报告"),
            LocalTask(id = 3, title = "独立任务")
        )
        val edges = listOf(LocalTaskDependency(2, 1))
        val rows = combineWorkspaceTasks(local, listOf(
            CloudTask("school", "课程待办", "todo", "manual", "chaoxing", null)
        ))
        val waiting = waitingWorkspaceTasks(rows, local, edges)
        assertEquals(listOf("local:2"), waiting.map { it.key })
        assertEquals(listOf("local:2"), waitingWorkspaceTasks(
            searchWorkspaceTasks(rows, "pending", "报告", today), local, edges
        ).map { it.key })
        assertTrue(waitingWorkspaceTasks(
            facetWorkspaceTasks(rows, "chaoxing", false, today), local, edges
        ).isEmpty())
        val unlocked = local.map { if (it.id == 1L) it.copy(completed = true) else it }
        assertTrue(waitingWorkspaceTasks(combineWorkspaceTasks(unlocked, emptyList()),
            unlocked, edges).isEmpty())
    }

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
        assertEquals(setOf("cloud:manual-1"),
            facetWorkspaceTasks(combined, "cloud", false, today).map { it.key }.toSet())
        assertEquals(listOf("cloud:course-1"),
            facetWorkspaceTasks(combined, "chaoxing", false, today).map { it.key })
        assertEquals(1, searchWorkspaceTasks(combined, "pending", "", today).size)
        assertEquals(listOf("cloud:course-1"),
            searchWorkspaceTasks(combined, "verify", "", today).map { it.key })
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
            setOf("cloud:returned", "cloud:wechat"),
            searchWorkspaceTasks(records, "pending", "", today).map { it.key }.toSet()
        )
        assertEquals(listOf("cloud:legacy"),
            searchWorkspaceTasks(records, "verify", "", today).map { it.key })
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
    fun oldDuplicateWorkIdsRequireCanonicalVerifiedRowBeforeCoalescing() {
        val records = listOf(
            CloudTask("old-a", "实验报告一", "done", "platform", "chaoxing",
                null, sourceExternalId = "work:11:22:33"),
            CloudTask("old-b", "实验报告一", "todo", "platform", "chaoxing",
                null, sourceExternalId = "work:11:22:33"),
            CloudTask("official", "实验报告一", "todo", "platform", "chaoxing",
                null, sourceExternalId = "work:11:22:33", officialStatus = "submitted"),
            CloudTask("other-work", "实验报告一", "todo", "platform", "chaoxing",
                null, sourceExternalId = "work:11:22:34", officialStatus = "todo"),
            CloudTask("other-course", "实验报告一", "todo", "platform", "chaoxing",
                null, sourceExternalId = "work:12:22:33", officialStatus = "todo"),
            CloudTask("other-provider", "实验报告一", "todo", "platform", "ncwu",
                null, sourceExternalId = "work:11:22:33")
        )
        assertEquals(5, distinctVerifiedChaoxingHomeworkRows(records.take(2) + records.drop(3)).size)
        val combined = combineWorkspaceTasks(emptyList(), records)
        assertEquals(4, combined.size)
        assertEquals(
            setOf("cloud:official", "cloud:other-work", "cloud:other-course", "cloud:other-provider"),
            combined.map { it.key }.toSet()
        )
        assertEquals(3, searchWorkspaceTasks(combined, "pending", "", today).size)
        assertTrue(searchWorkspaceTasks(combined, "verify", "", today).isEmpty())
        assertEquals(1, searchWorkspaceTasks(combined, "done", "", today).size)
    }

    @Test
    fun conflictingTrustedOfficialRowsAreNotSilentlyCollapsed() {
        val rows = listOf(
            CloudTask("official-todo", "同名作业", "todo", "platform", "chaoxing",
                null, sourceExternalId = "work:11:22:33", officialStatus = "todo"),
            CloudTask("official-submitted", "同名作业", "todo", "platform", "chaoxing",
                null, sourceExternalId = "work:11:22:33", officialStatus = "submitted"),
            CloudTask("legacy", "同名作业", "todo", "platform", "chaoxing",
                null, sourceExternalId = "work:11:22:33", officialStatus = null)
        )
        assertEquals(rows, distinctVerifiedChaoxingHomeworkRows(rows))
        assertEquals(3, combineWorkspaceTasks(emptyList(), rows).size)
    }

    @Test
    fun courseLabelDistinguishesSameTitleButDoesNotDeduplicateIdentity() {
        val rows = combineWorkspaceTasks(emptyList(), listOf(
            CloudTask("a", "实验报告一", "todo", "homework", "chaoxing", null,
                sourceExternalId = "work:10:20:30", officialStatus = "todo",
                sourceCourse = "简单的Python程序"),
            CloudTask("b", "实验报告一", "todo", "homework", "chaoxing", null,
                sourceExternalId = "work:11:22:33", officialStatus = "todo",
                sourceCourse = "Python实践")
        ))
        assertEquals(2, rows.size)
        assertEquals(setOf("学习通 · 简单的Python程序", "学习通 · Python实践"),
            rows.map { it.origin }.toSet())
        assertEquals(2, searchWorkspaceTasks(rows, "pending", "Python", today).size)
    }



    @Test
    fun readyViewRanksVerifiedOfficialAndPersonalDueDatesWithoutTrustingCloudSnapshots() {
        val local = listOf(
            LocalTask(id = 1, title = "个人作业"),
            LocalTask(id = 2, title = "没有日期的任务")
        )
        val rows = listOf(
            WorkspaceTask("cloud:unverified-date", "未知来源截止", "学习通", false,
                today.minusDays(3), "云端记录，需核对来源", "cloud",
                category = "chaoxing", provider = "chaoxing", officialStatus = "todo"),
            WorkspaceTask("local:1", "个人作业", "本机待办", false,
                today.plusDays(3), "本人填写", "local"),
            WorkspaceTask("cloud:verified", "官方截止", "学习通", false,
                today.plusDays(1), "学习通本机官方截止", "cloud",
                category = "chaoxing", provider = "chaoxing", officialStatus = "todo",
                officialDeadline = "2026-11-07"),
            WorkspaceTask("local:2", "没有日期的任务", "本机待办", false,
                null, "本人填写", "local"),
            WorkspaceTask("native:verified", "官方退回", "学习通", false,
                today.minusDays(1), "本机官方页面观察", "native",
                category = "chaoxing", provider = "chaoxing", officialStatus = "returned",
                officialDeadline = "2026-11-05"),
            WorkspaceTask("cloud:submitted", "已交作业", "学习通", false,
                today.minusDays(2), "学习通本机官方截止", "cloud",
                category = "chaoxing", provider = "chaoxing", officialStatus = "submitted",
                officialDeadline = "2026-11-04")
        )
        assertEquals(
            listOf("native:verified", "cloud:verified", "local:1",
                "cloud:unverified-date", "local:2"),
            readyWorkspaceTasks(rows, local, emptyList()).map { it.key }
        )
        assertEquals("todo", rows.single { it.key == "cloud:verified" }.officialStatus)
        assertTrue(rows.none { it.done })
    }

    @Test
    fun whitespaceAndCaseInsensitiveSearchAreStable() {
        assertEquals(records, searchWorkspaceTasks(records, "all", "  ", today))
        assertEquals(listOf("cloud:1"),
            searchWorkspaceTasks(records, "cloud", "  学习通  ", today).map { it.key })
        assertEquals(emptyList<WorkspaceTask>(), searchWorkspaceTasks(records, "all", "无关内容", today))
    }
}
