package io.github.x07xh.xiaoxu.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalTaskBreakdownTest {
    @Test fun acceptsOnlyExplicitValidStepsAndStripsNumberedPrefixes() {
        assertEquals(listOf("整理资料", "写报告", "检查提交"),
            parseLocalTaskBreakdown("1. 整理资料\n2、写报告\n• 检查提交"))
        assertEquals(listOf("A", "B"), parseLocalTaskBreakdown("- A\n* B"))
        assertNull(parseLocalTaskBreakdown("只有一步"))
        assertNull(parseLocalTaskBreakdown("A\nA"))
        assertNull(parseLocalTaskBreakdown("A\na"))
        assertNull(parseLocalTaskBreakdown("A\n" + "X".repeat(121)))
        assertNull(parseLocalTaskBreakdown((1..13).joinToString("\n") { "步骤 $it" }))
        assertNull(parseLocalTaskBreakdown("X".repeat(2401) + "\n下一步"))
    }

    @Test fun linkedProgressShowsOnlyTrueAncestorsAndRealCompletion() {
        val tasks = listOf(
            LocalTask(id = 1, title = "资料", completed = true),
            LocalTask(id = 2, title = "步骤一", completed = false),
            LocalTask(id = 3, title = "步骤二", completed = false),
            LocalTask(id = 4, title = "总任务"),
            LocalTask(id = 5, title = "无关事项"))
        val links = listOf(
            LocalTaskDependency(2, 1),
            LocalTaskDependency(3, 2),
            LocalTaskDependency(4, 3))
        val before = localTaskAncestors(4, tasks, links)
        assertEquals(listOf(1L, 2L, 3L), before.map { it.id })
        assertEquals(1, before.count { it.completed })
        assertTrue(localTaskAncestors(5, tasks, links).isEmpty())
        assertTrue(localTaskAncestors(4, tasks, links +
            LocalTaskDependency(2, 4)).isEmpty())
    }

}
