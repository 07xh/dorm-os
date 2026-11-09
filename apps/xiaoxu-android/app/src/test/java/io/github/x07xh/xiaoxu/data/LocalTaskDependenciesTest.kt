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
}
