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

    @Test fun identicalPlanRequiresARealLinkedChainAndRemainsRecognizedAfterCompletion() {
        val original = LocalTask(id = 10, title = "实验报告", source = "personal")
        val steps = listOf(
            LocalTask(id = 11, title = "整理数据", source = "personal"),
            LocalTask(id = 12, title = "写报告", source = "personal")
        )
        val unrelated = LocalTask(id = 20, title = "整理数据", source = "personal")
        val titles = listOf("整理数据", "写报告")
        val edges = listOf(LocalTaskDependency(12, 11), LocalTaskDependency(10, 12))
        assertTrue(hasEquivalentLocalTaskBreakdown(
            original, titles, listOf(original) + steps + unrelated, edges))
        assertTrue(hasEquivalentLocalTaskBreakdown(
            original, titles, listOf(original) + steps.map { it.copy(completed = true) }, edges))
        assertFalse(hasEquivalentLocalTaskBreakdown(
            original, titles, listOf(original) + steps, listOf(LocalTaskDependency(10, 12))))
        assertFalse(hasEquivalentLocalTaskBreakdown(
            original, titles, listOf(original) + steps, listOf(LocalTaskDependency(10, 20))))
        assertFalse(hasEquivalentLocalTaskBreakdown(
            original, titles.reversed(), listOf(original) + steps, edges))
        assertFalse(hasEquivalentLocalTaskBreakdown(
            original, titles, listOf(original, steps[0].copy(due = "2026-12-01"), steps[1]), edges))
        assertFalse(hasEquivalentLocalTaskBreakdown(
            original, titles, listOf(original, steps[0].copy(source = "other"), steps[1]), edges))
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

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidPlanIds() {
        localTaskBreakdownEdges(10, listOf(11, 11), listOf(1))
    }
}
