package io.github.x07xh.xiaoxu.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Actual Room transaction and dependency completion gate, no school account needed. */
@RunWith(AndroidJUnit4::class)
class LocalTaskBreakdownDatabaseTest {
    @Test fun savesOnlyApprovedStepsAndPersistsOriginalTask() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "xiaoxu-breakdown-${UUID.randomUUID()}.db"
        val db = Room.databaseBuilder(context, LocalTaskDatabase::class.java, name)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()
        try {
            val dao = db.tasks()
            val upstream = dao.insert(LocalTask(title = "准备数据", source = "local"))
            val original = dao.insert(LocalTask(title = "实验报告", source = "local",
                due = "2026-12-01"))
            assertTrue(dao.tryAddDependency(original, upstream))
            assertFalse(dao.tryBreakDown(original, "重复\n重复"))
            assertEquals(2, dao.snapshot().size)
            assertEquals(1, dao.dependencySnapshot().size)
            assertTrue(dao.tryBreakDown(original, "整理数据\n编写报告\n检查提交"))
            val all = dao.snapshot()
            assertEquals(5, all.size)
            assertEquals("2026-12-01", dao.findById(original)?.due)
            val ids = listOf("整理数据", "编写报告", "检查提交")
                .map { name -> all.single { it.title == name }.id }
            assertEquals(setOf(
                LocalTaskDependency(original, upstream),
                LocalTaskDependency(ids[0], upstream),
                LocalTaskDependency(ids[1], ids[0]),
                LocalTaskDependency(ids[2], ids[1]),
                LocalTaskDependency(original, ids[2])
            ), dao.dependencySnapshot().toSet())
            // Re-entering the breakdown UI and approving an equivalent plan
            // must not insert a second copy of the same tasks and links.
            val savedEdges = dao.dependencySnapshot()
            assertTrue(dao.tryBreakDown(original,
                "1. 整理数据\n2. 编写报告\n3. 检查提交"))
            assertEquals(5, dao.snapshot().size)
            assertEquals(savedEdges, dao.dependencySnapshot())
            assertFalse(dao.trySetCompleted(original, true))
            assertFalse(dao.trySetCompleted(ids[0], true))
            assertTrue(dao.trySetCompleted(upstream, true))
            assertTrue(dao.trySetCompleted(ids[0], true))
            assertFalse(dao.trySetCompleted(original, true))
            assertTrue(dao.trySetCompleted(ids[1], true))
            assertTrue(dao.trySetCompleted(ids[2], true))
            assertTrue(dao.trySetCompleted(original, true))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
