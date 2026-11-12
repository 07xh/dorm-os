package io.github.x07xh.xiaoxu.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskExecutionDatabaseTest {
    @Test fun rootCompletionIsExplicitAndStepReopenIsAtomic() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val db=Room.inMemoryDatabaseBuilder(context,LocalTaskDatabase::class.java).build()
        try {
            val id=db.tasks().insert(LocalTask(title="报告"))
            val root=ExecutionRoot("device","local:$id")
            val dao=db.executions()
            assertFalse(dao.addSteps(root,listOf("Code","code")))
            assertTrue(dao.stepsFor(root.scope,root.key).isEmpty())
            assertTrue(dao.addSteps(root,listOf("代码","截图")))
            assertFalse(db.tasks().trySetCompleted(id,true))
            val steps=dao.stepsFor(root.scope,root.key)
            assertTrue(dao.setPrerequisites(root,steps[1].id,setOf(steps[0].id)))
            assertFalse(dao.toggleStep(root,steps[1].id,true))
            assertTrue(dao.toggleStep(root,steps[0].id,true))
            assertTrue(dao.toggleStep(root,steps[1].id,true))
            assertFalse(db.tasks().findById(id)!!.completed)
            assertTrue(db.tasks().trySetCompleted(id,true))
            assertTrue(dao.toggleStep(root,steps[0].id,false))
            assertFalse(db.tasks().findById(id)!!.completed)
            assertTrue(dao.stepsFor(root.scope,root.key)[1].completed)
            assertTrue(dao.addSteps(root,listOf("检查")))
            assertEquals(3,dao.stepsFor(root.scope,root.key).size)
            db.tasks().remove(id)
            assertTrue(dao.stepsFor(root.scope,root.key).isEmpty())
        } finally { db.close() }
    }
    @Test fun foreignOwnersCannotEditOrLinkStepsAndCyclesRollback() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val db=Room.inMemoryDatabaseBuilder(context,LocalTaskDatabase::class.java).build()
        try {
            val dao=db.executions()
            val a=ExecutionRoot("wechat:account-a","cloud:1")
            val b=ExecutionRoot("wechat:account-b","cloud:1")
            dao.addSteps(a,listOf("A","B"));dao.addSteps(b,listOf("C"))
            val steps=dao.stepsFor(a.scope,a.key);val foreign=dao.stepsFor(b.scope,b.key).single()
            assertFalse(dao.toggleStep(a,foreign.id,true))
            assertFalse(dao.setPrerequisites(a,steps[1].id,setOf(foreign.id)))
            assertTrue(dao.setPrerequisites(a,steps[1].id,setOf(steps[0].id)))
            assertFalse(dao.setPrerequisites(a,steps[0].id,setOf(steps[1].id)))
            assertFalse(dao.finish(a))
            assertEquals(1,dao.edgeSnapshot().size)
            assertFalse(dao.stepsFor(b.scope,b.key).single().completed)
            assertTrue(dao.toggleStep(b,foreign.id,true))
            assertTrue(dao.finish(b))
            assertFalse(dao.addSteps(b,listOf("迟到的编辑")))
            assertFalse(dao.toggleStep(b,foreign.id,false))
            dao.reopen(b)
            assertTrue(dao.toggleStep(b,foreign.id,false))
        } finally {db.close()}
    }
}
