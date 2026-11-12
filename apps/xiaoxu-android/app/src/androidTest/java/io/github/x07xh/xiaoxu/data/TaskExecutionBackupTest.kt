package io.github.x07xh.xiaoxu.data

import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskExecutionBackupTest {
    @Test fun roundTripRemapsRootAndStepsAndPreservesOwnerScope() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val a=Room.inMemoryDatabaseBuilder(context,LocalTaskDatabase::class.java).build()
        val b=Room.inMemoryDatabaseBuilder(context,LocalTaskDatabase::class.java).build()
        val file=File.createTempFile("task-execution", ".json", context.cacheDir)
        try {
            val id=a.tasks().insert(LocalTask(title="报告",createdAt=1700000000000))
            val root=ExecutionRoot("device","local:$id")
            a.executions().addSteps(root,listOf("代码","截图"))
            val steps=a.executions().stepsFor(root.scope,root.key)
            a.executions().setPrerequisites(root,steps[1].id,setOf(steps[0].id))
            a.executions().toggleStep(root,steps[0].id,true)
            a.executions().addSteps(ExecutionRoot("cloud:owner-a","cloud:record-7"),listOf("私有步骤"))
            // Identical editable metadata is not an identity. A legacy imported twin must stay unrelated.
            val unrelated=b.tasks().insert(LocalTask(title="报告",createdAt=1700000000000))
            LocalTaskBackup(a,context.contentResolver).exportTo(Uri.fromFile(file))
            val backup=LocalTaskBackup(b,context.contentResolver)
            assertEquals(1,backup.mergeFrom(Uri.fromFile(file)).first)
            assertTrue(b.executions().stepsFor("device","local:$unrelated").isEmpty())
            val restored=b.tasks().snapshot().first {it.id!=unrelated && it.title=="报告"}
            assertNotEquals(id,restored.id)
            val copied=b.executions().stepsFor("device","local:${restored.id}")
            assertEquals(listOf("代码","截图"),copied.map {it.title})
            assertTrue(copied[0].completed)
            assertEquals(listOf(TaskStepEdge(copied[1].id,copied[0].id)),b.executions().edgeSnapshot())
            assertEquals(1,b.executions().stepsFor("cloud:owner-a","cloud:record-7").size)
            assertTrue(b.executions().stepsFor("cloud:owner-b","cloud:record-7").isEmpty())
            backup.mergeFrom(Uri.fromFile(file))
            assertEquals(3,b.executions().stepSnapshot().size)
        } finally {a.close();b.close();file.delete()}
    }
}
