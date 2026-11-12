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

    @Test fun breakdownPreservesUpstreamGateAndUnlocksOneStepAtATime() {
        // Task 10 already requires task 1. Task 20 is downstream of task 10.
        val originalEdges = listOf(LocalTaskDependency(10, 1),
            LocalTaskDependency(20, 10))
        val added = localTaskBreakdownEdges(10, listOf(11, 12, 13), listOf(1))
        assertEquals(listOf(
            LocalTaskDependency(11, 1),
            LocalTaskDependency(12, 11),
            LocalTaskDependency(13, 12),
            LocalTaskDependency(10, 13)), added)
        val graph = originalEdges + added
        val all = listOf(1L, 10L, 11L, 12L, 13L, 20L)
            .map { LocalTask(id = it, title = "任务 $it") }
        assertEquals(setOf(1L), readyLocalTaskIds(all, graph))
        var current = all.map { if (it.id == 1L) it.copy(completed = true) else it }
        assertEquals(setOf(11L), readyLocalTaskIds(current, graph))
        current = current.map { if (it.id == 11L) it.copy(completed = true) else it }
        assertEquals(setOf(12L), readyLocalTaskIds(current, graph))
        current = current.map { if (it.id in setOf(12L, 13L)) it.copy(completed = true) else it }
        assertEquals(setOf(10L), readyLocalTaskIds(current, graph))
        current = current.map { if (it.id == 10L) it.copy(completed = true) else it }
        assertEquals(setOf(20L), readyLocalTaskIds(current, graph))
        assertFalse(graph.any { wouldCreateDependencyCycle(graph.filterNot { edge -> edge == it },
            it.taskId, it.prerequisiteId) })
        assertTrue(added.all { it.taskId > 0 && it.prerequisiteId > 0 })
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidPlanIds() {
        localTaskBreakdownEdges(10, listOf(11, 11), listOf(1))
    }
}
