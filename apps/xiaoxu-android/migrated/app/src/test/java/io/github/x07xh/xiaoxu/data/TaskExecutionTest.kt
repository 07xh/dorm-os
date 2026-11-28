package io.github.x07xh.xiaoxu.data

import org.junit.Assert.*
import org.junit.Test

class TaskExecutionTest {
    private val root = ExecutionRoot("device", "local:1")
    @Test fun multiplePrerequisitesUnlockOnlyWhenCommitted() {
        val steps = listOf(TaskStep(id=1, scope=root.scope, rootKey=root.key, title="A"),
            TaskStep(id=2, scope=root.scope, rootKey=root.key, title="B"),
            TaskStep(id=3, scope=root.scope, rootKey=root.key, title="C"))
        val edges = listOf(TaskStepEdge(3,1), TaskStepEdge(3,2))
        assertEquals(listOf(1L,2L), readyTaskSteps(steps,edges).map { it.id })
        assertEquals(listOf(2L), readyTaskSteps(steps.map { if(it.id==1L)it.copy(completed=true) else it },edges).map { it.id })
        assertEquals(listOf(3L), readyTaskSteps(steps.map { if(it.id!=3L)it.copy(completed=true) else it },edges).map { it.id })
    }
    @Test fun crossRootAndDanglingDependenciesFailClosed() {
        val a=TaskStep(id=1,scope="device",rootKey="local:1",title="A")
        val foreign=TaskStep(id=2,scope="other-owner",rootKey="local:1",title="B",completed=true)
        assertTrue(readyTaskSteps(listOf(a,foreign),listOf(TaskStepEdge(1,2))).none {it.id==1L})
        assertTrue(readyTaskSteps(listOf(a),listOf(TaskStepEdge(1,99))).isEmpty())
    }
    @Test fun completedHistorySurvivesReopenedPrerequisite() {
        val steps=listOf(TaskStep(id=1,scope="device",rootKey="local:1",title="A"),
            TaskStep(id=2,scope="device",rootKey="local:1",title="B",completed=true))
        assertEquals(listOf(1L),readyTaskSteps(steps,listOf(TaskStepEdge(2,1))).map {it.id})
        assertTrue(steps[1].completed)
    }
    @Test fun validatesCyclesAndOneParentScope() {
        val steps=(1L..3L).map {TaskStep(id=it,scope="device",rootKey="local:1",title="S$it")}
        assertTrue(validStepPrerequisites(steps,listOf(TaskStepEdge(2,1)),3,setOf(1,2)))
        assertFalse(validStepPrerequisites(steps,listOf(TaskStepEdge(2,1)),1,setOf(2)))
        assertFalse(validStepPrerequisites(steps,emptyList(),1,setOf(1)))
        assertFalse(validStepPrerequisites(steps,emptyList(),1,setOf(99)))
    }
}
