package io.github.x07xh.xiaoxu.ui

import io.github.x07xh.xiaoxu.data.*
import org.junit.Assert.*
import org.junit.Test

class TaskExecutionRowsTest {
    private fun task()=WorkspaceTask("local:1","报告","本机",false,null,"本人填写","local","local")
    @Test fun progressIndexKeepsAccountScopesAndUpdatesCommittedCounts() {
        val a = ExecutionRoot("cloud:owner-a", "cloud:1")
        val b = ExecutionRoot("cloud:owner-b", "cloud:1")
        val missing = ExecutionRoot("cloud:owner-c", "cloud:1")
        val steps = listOf(
            TaskStep(id=1,scope=a.scope,rootKey=a.key,title="A",completed=true),
            TaskStep(id=2,scope=a.scope,rootKey=a.key,title="B"),
            TaskStep(id=3,scope=b.scope,rootKey=b.key,title="C")
        )
        val progress = executionProgressIndex(steps)
        assertEquals("步骤 1/2", progress[a])
        assertEquals("步骤 0/1", progress[b])
        assertNull(progress[missing])
        assertTrue(executionProgressIndex(emptyList()).isEmpty())
        assertEquals("步骤 2/2", executionProgressIndex(steps.map {
            if (it.id == 2L) it.copy(completed=true) else it
        })[a])
    }
    @Test fun indexedEdgesStillBlockCrossRootPrerequisiteAndIgnoreUnrelatedEdges() {
        val parent = task()
        val key = ExecutionRoot("device", parent.key)
        val first = TaskStep(id=1,scope=key.scope,rootKey=key.key,title="第一步",completed=true)
        val second = TaskStep(id=2,scope=key.scope,rootKey=key.key,title="第二步")
        val other = TaskStep(id=3,scope="device",rootKey="local:2",title="其他任务",completed=true)
        val steps = listOf(first,second,other)
        val edges = listOf(TaskStepEdge(2,1),TaskStepEdge(3,1))
        val ready = executionReadyRows(listOf(parent),steps,edges,emptyList(),{key},"")
        assertEquals(2L,ready.single().step!!.id)
        val invalid = edges + TaskStepEdge(2,3)
        assertTrue(executionReadyRows(listOf(parent),steps,invalid,emptyList(),{key},"").isEmpty())
    }

    @Test fun indexedEndedStateDoesNotLeakBetweenScopes() {
        val key = ExecutionRoot("device", "local:1")
        val step = TaskStep(id=1,scope=key.scope,rootKey=key.key,title="完成",completed=true)
        val other = TaskExecution("cloud:other",key.key,true)
        val rows = executionReadyRows(listOf(task()),listOf(step),emptyList(),listOf(other),{key},"")
        assertTrue(rows.single().confirm)
        val ended = TaskExecution(key.scope,key.key,true)
        val completed = executionReadyRows(listOf(task()),listOf(step),emptyList(),listOf(other,ended),{key},"")
        assertFalse(completed.single().confirm)
    }

    @Test fun childSearchKeepsParentAndDoesNotInflateRootCount() {
        val root=task();val key=ExecutionRoot("device","local:1")
        val steps=listOf(TaskStep(id=1,scope=key.scope,rootKey=key.key,title="截图"),TaskStep(id=2,scope=key.scope,rootKey=key.key,title="写代码"))
        val rows=executionReadyRows(listOf(root),steps,emptyList(),emptyList(),{key},"截图")
        assertEquals(1,rows.size);assertEquals("报告",rows.single().parent.title)
        assertEquals("截图",rows.single().step!!.title)
        assertEquals(1,executionReadyRows(listOf(root),steps,emptyList(),emptyList(),{key},"").map {it.parent.key}.distinct().size)
    }
    @Test fun allStepsDoneShowsExplicitConfirmation() {
        val key=ExecutionRoot("device","local:1")
        val step=TaskStep(id=1,scope=key.scope,rootKey=key.key,title="代码",completed=true)
        val row=executionReadyRows(listOf(task()),listOf(step),emptyList(),emptyList(),{key},"").single()
        assertTrue(row.confirm);assertNull(row.step)
    }
    @Test fun personalEndDoesNotHideOfficialTodo() {
        val key=ExecutionRoot("chaoxing:account-a","work:1:2:3")
        val root=task().copy(key="native:hash",storage="native",provider="chaoxing",category="chaoxing",sourceExternalId="work:1:2:3",officialStatus="todo")
        val step=TaskStep(id=1,scope=key.scope,rootKey=key.key,title="写完",completed=true)
        val row=executionReadyRows(listOf(root),listOf(step),emptyList(),listOf(TaskExecution(key.scope,key.key,true)),{key},"").single()
        assertTrue(row.officialAction);assertEquals("报告",row.parent.title)
        assertTrue(executionReadyRows(listOf(root.copy(officialStatus="unknown")),listOf(step),emptyList(),emptyList(),{key},"").isEmpty())
    }
    @Test fun differentAccountNeverAttachesToSameSourceKey() {
        val step=TaskStep(id=1,scope="cloud:old-owner",rootKey="cloud:1",title="私有步骤")
        val root=task().copy(key="cloud:1",storage="cloud",category="wechat")
        val row=executionReadyRows(listOf(root),listOf(step),emptyList(),emptyList(),{ExecutionRoot("cloud:new-owner",it.key)},"").single()
        assertNull(row.step)
    }
    @Test fun completedPersonalAndTrustedOfficialDatesRemainIndependentFacets() {
        val today=java.time.LocalDate.of(2026,11,13)
        val done=task().copy(done=true,date=today)
        val official=task().copy(key="native:1",storage="native",provider="chaoxing",category="chaoxing",
            sourceExternalId="work:1:2:3",officialStatus="todo",date=today,officialDeadline="2026-11-13")
        val oldCloud=task().copy(key="cloud:old",storage="cloud",date=today)
        assertEquals(listOf(done,official),facetWorkspaceTasks(listOf(done,official,oldCloud),"all",true,today))
    }
    @Test fun completedStepsRemainSearchableOnConfirmationRow() {
        val key=ExecutionRoot("device","local:1")
        val step=TaskStep(id=1,scope=key.scope,rootKey=key.key,title="截图",completed=true)
        val rows=executionReadyRows(listOf(task()),listOf(step),emptyList(),emptyList(),{key},"截图")
        assertEquals(1,rows.size)
        assertTrue(rows.single().confirm)
    }
    @Test fun childSearchRetainsRootContextInGroupedStatusViews() {
        val root=task();val key=ExecutionRoot("device",root.key)
        val steps=listOf(TaskStep(id=1,scope=key.scope,rootKey=key.key,title="截图"))
        val today=java.time.LocalDate.of(2026,11,13)
        for(filter in listOf("pending","all","done")) {
            val parent=if(filter=="done") root.copy(done=true) else root
            assertEquals(listOf(parent),searchExecutionRoots(searchWorkspaceTasks(listOf(parent),filter,"",today),steps,{key},"截图"))
        }
    }
}
