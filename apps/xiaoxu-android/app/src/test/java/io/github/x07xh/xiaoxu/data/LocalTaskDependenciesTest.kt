package io.github.x07xh.xiaoxu.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalTaskDependenciesTest {
    @Test fun rejectsSelfAndIndirectCycles() {
        val edges = listOf(LocalTaskDependency(2, 1), LocalTaskDependency(3, 2))
        assertTrue(wouldCreateDependencyCycle(edges, 1, 1))
        assertTrue(wouldCreateDependencyCycle(edges, 1, 3))
        assertTrue(wouldCreateDependencyCycle(edges, 2, 3))
        assertFalse(wouldCreateDependencyCycle(edges, 4, 3))
    }

    @Test fun multiplePrerequisitesUnlockOnlyAfterBothDone() {
        val edges = listOf(LocalTaskDependency(3, 1), LocalTaskDependency(3, 2))
        val tasks = listOf(LocalTask(id = 1, title = "A"),
            LocalTask(id = 2, title = "B"), LocalTask(id = 3, title = "C"))
        assertEquals(setOf(1L, 2L), readyLocalTaskIds(tasks, edges))
        assertEquals(setOf(3L), waitingLocalTaskIds(tasks, edges))
        val oneDone = tasks.map { if (it.id == 1L) it.copy(completed = true) else it }
        assertEquals(setOf(2L), readyLocalTaskIds(oneDone, edges))
        val allDone = oneDone.map { if (it.id == 2L) it.copy(completed = true) else it }
        assertEquals(setOf(3L), readyLocalTaskIds(allDone, edges))
    }

    @Test fun deletingPrerequisiteDoesNotBlockTaskForever() {
        val tasks = listOf(LocalTask(id = 2, title = "B"))
        assertEquals(setOf(2L), readyLocalTaskIds(tasks, emptyList()))
    }

    @Test fun waitingListTracksOnlyUnfinishedPredecessors() {
        val tasks = listOf(LocalTask(id = 1, title = "已完成", completed = true),
            LocalTask(id = 2, title = "还在准备"),
            LocalTask(id = 3, title = "报告"))
        val edges = listOf(LocalTaskDependency(3, 1), LocalTaskDependency(3, 2))
        assertEquals(listOf(2L),
            unfinishedLocalPrerequisites(3, tasks, edges).map { it.id })
        assertEquals(setOf(3L), waitingLocalTaskIds(tasks, edges))
        val finished = tasks.map { if (it.id == 2L) it.copy(completed = true) else it }
        assertTrue(unfinishedLocalPrerequisites(3, finished, edges).isEmpty())
        assertTrue(waitingLocalTaskIds(finished, edges).isEmpty())
    }
    @Test fun relationGraphOrdersChainsAndBranchesWithoutInventingTasks() {
        val tasks = listOf(
            LocalTask(id = 1, title = "准备材料", completed = true),
            LocalTask(id = 2, title = "实验 A"),
            LocalTask(id = 3, title = "实验 B"),
            LocalTask(id = 4, title = "完成报告"),
            LocalTask(id = 5, title = "独立事项")
        )
        val edges = listOf(LocalTaskDependency(2, 1),
            LocalTaskDependency(3, 1), LocalTaskDependency(4, 2),
            LocalTaskDependency(4, 3))
        val layers = localTaskGraphLayers(tasks, edges)
        assertEquals(3, layers.size)
        assertEquals(listOf(1L), layers[0].map { it.task.id })
        assertEquals(listOf(2L, 3L), layers[1].map { it.task.id })
        assertEquals(listOf(4L), layers[2].map { it.task.id })
        assertEquals(setOf(2L, 3L), layers[2].single().prerequisites.map { it.id }.toSet())
        assertEquals(setOf(2L, 3L), layers[0].single().unlocks.map { it.id }.toSet())
        assertEquals(4, layers.flatten().size) // unlinked task 5 stays out of the diagram
        assertTrue(layers[0].single().task.completed) // never rewrite personal status
    }

    @Test fun relationGraphRejectsCorruptCyclesAndIgnoresMissingIds() {
        val tasks = listOf(LocalTask(id = 1, title = "A"),
            LocalTask(id = 2, title = "B"))
        assertTrue(localTaskGraphLayers(tasks, emptyList()).isEmpty())
        assertTrue(localTaskGraphLayers(tasks,
            listOf(LocalTaskDependency(1, 2), LocalTaskDependency(2, 1))).isEmpty())
        val layers = localTaskGraphLayers(tasks,
            listOf(LocalTaskDependency(2, 1), LocalTaskDependency(9, 1)))
        assertEquals(listOf(1L, 2L), layers.flatten().map { it.task.id })
    }

}
