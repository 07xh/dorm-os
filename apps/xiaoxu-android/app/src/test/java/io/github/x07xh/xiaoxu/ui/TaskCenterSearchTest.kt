package io.github.x07xh.xiaoxu.ui

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
        assertEquals(listOf("cloud:1"),
            searchWorkspaceTasks(records, "week", "", today).map { it.key })
        assertEquals(listOf("local:2"),
            searchWorkspaceTasks(records, "done", "复习", today).map { it.key })
    }

    @Test
    fun independentStatusSourceAndDueFiltersPreserveRealRecords() {
        val pending = searchWorkspaceTasks(records, "pending", "", today)
        assertEquals(listOf("cloud:1", "local:1"), pending.map { it.key })
        assertEquals(listOf("cloud:1"),
            facetWorkspaceTasks(pending, "all", true, today).map { it.key })
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
    fun editorScrollIndexMatchesActualOptionalRows() {
        // Always present: header, status tabs, filter controls, source chips.
        assertEquals(4, taskEditorStartIndex(false, false, false, false))
        assertEquals(5, taskEditorStartIndex(true, false, false, false))
        assertEquals(5, taskEditorStartIndex(false, true, false, false))
        assertEquals(6, taskEditorStartIndex(false, true, false, true))
        assertEquals(8, taskEditorStartIndex(true, true, true, true))
        // Cloud editor starts immediately after local editor only if local is open.
        assertEquals(5, taskEditorStartIndex(false, false, false, false) + 1)
    }

    @Test
    fun whitespaceAndCaseInsensitiveSearchAreStable() {
        assertEquals(records, searchWorkspaceTasks(records, "all", "  ", today))
        assertEquals(listOf("cloud:1"),
            searchWorkspaceTasks(records, "cloud", "  学习通  ", today).map { it.key })
        assertEquals(emptyList<WorkspaceTask>(), searchWorkspaceTasks(records, "all", "无关内容", today))
    }
}
