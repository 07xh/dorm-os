package io.github.x07xh.xiaoxu.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalTaskDatabaseMigrationTest {
    @Test
    fun upgradesExistingV4DatabaseWithoutDroppingUserData() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "xiaoxu-local-tasks.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()

        val legacy = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            legacy.execSQL("""
                CREATE TABLE local_tasks (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    title TEXT NOT NULL,
                    completed INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    source TEXT,
                    due TEXT
                )
            """.trimIndent())
            legacy.execSQL("""
                CREATE TABLE native_school_records (
                    provider TEXT NOT NULL,
                    kind TEXT NOT NULL,
                    fingerprint TEXT NOT NULL,
                    title TEXT NOT NULL,
                    details TEXT NOT NULL,
                    sourceHost TEXT NOT NULL,
                    capturedAt INTEGER NOT NULL,
                    PRIMARY KEY(provider, kind, fingerprint)
                )
            """.trimIndent())
            legacy.execSQL("""
                CREATE TABLE local_task_dependencies (
                    taskId INTEGER NOT NULL,
                    prerequisiteId INTEGER NOT NULL,
                    PRIMARY KEY(taskId, prerequisiteId),
                    FOREIGN KEY(taskId) REFERENCES local_tasks(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(prerequisiteId) REFERENCES local_tasks(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )
            """.trimIndent())
            legacy.execSQL("CREATE INDEX index_local_task_dependencies_prerequisiteId ON local_task_dependencies(prerequisiteId)")
            legacy.execSQL("""
                CREATE TABLE task_executions (
                    scope TEXT NOT NULL,
                    rootKey TEXT NOT NULL,
                    ended INTEGER NOT NULL,
                    PRIMARY KEY(scope, rootKey)
                )
            """.trimIndent())
            legacy.execSQL("""
                CREATE TABLE task_steps (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    scope TEXT NOT NULL,
                    rootKey TEXT NOT NULL,
                    title TEXT NOT NULL,
                    completed INTEGER NOT NULL,
                    position INTEGER NOT NULL,
                    due TEXT,
                    FOREIGN KEY(scope, rootKey) REFERENCES task_executions(scope, rootKey)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
            """.trimIndent())
            legacy.execSQL("CREATE INDEX index_task_steps_scope_rootKey ON task_steps(scope, rootKey)")
            legacy.execSQL("""
                CREATE TABLE task_step_edges (
                    stepId INTEGER NOT NULL,
                    prerequisiteId INTEGER NOT NULL,
                    PRIMARY KEY(stepId, prerequisiteId),
                    FOREIGN KEY(stepId) REFERENCES task_steps(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(prerequisiteId) REFERENCES task_steps(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )
            """.trimIndent())
            legacy.execSQL("CREATE INDEX index_task_step_edges_prerequisiteId ON task_step_edges(prerequisiteId)")

            legacy.execSQL("INSERT INTO local_tasks(id,title,completed,createdAt,source,due) VALUES(41,'保留任务',0,1700000000000,'local','2026-12-01')")
            legacy.execSQL("INSERT INTO local_tasks(id,title,completed,createdAt,source,due) VALUES(42,'前置任务',1,1700000001000,NULL,NULL)")
            legacy.execSQL("INSERT INTO local_task_dependencies(taskId,prerequisiteId) VALUES(41,42)")
            legacy.execSQL("INSERT INTO task_executions(scope,rootKey,ended) VALUES('device','local:41',0)")
            legacy.execSQL("INSERT INTO task_steps(id,scope,rootKey,title,completed,position,due) VALUES(101,'device','local:41','第一步',1,0,NULL)")
            legacy.execSQL("INSERT INTO task_steps(id,scope,rootKey,title,completed,position,due) VALUES(102,'device','local:41','第二步',0,1,'2026-12-01')")
            legacy.execSQL("INSERT INTO task_step_edges(stepId,prerequisiteId) VALUES(102,101)")
            legacy.execSQL("""
                INSERT INTO native_school_records(provider,kind,fingerprint,title,details,sourceHost,capturedAt)
                VALUES('second_class','credits','legacy-credit-row','积分快照','保留明细','dekt.ncwu.edu.cn',1700000002000)
            """.trimIndent())
            legacy.execSQL("PRAGMA user_version = 4")
        } finally {
            legacy.close()
        }

        val database = Room.databaseBuilder(context, LocalTaskDatabase::class.java, name)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .build()
        try {
            val tasks = database.tasks().snapshot()
            assertEquals(listOf(41L, 42L), tasks.map { it.id })
            assertEquals("保留任务", tasks.first().title)
            assertEquals("2026-12-01", tasks.first().due)
            assertEquals(null, tasks.last().source)
            assertTrue(tasks.all { !it.stableId.isNullOrBlank() && it.stableId.matches(Regex("[a-f0-9]{32}")) })

            assertEquals(listOf(LocalTaskDependency(41, 42)), database.tasks().dependencySnapshot())
            assertEquals(listOf(101L, 102L), database.executions().stepSnapshot().map { it.id })
            assertEquals(listOf(TaskStepEdge(102, 101)), database.executions().edgeSnapshot())
            val schoolRows = database.nativeSchoolRecords().snapshot()
            assertEquals(1, schoolRows.size)
            assertEquals("legacy-credit-row", schoolRows.single().fingerprint)

            val version = database.openHelper.writableDatabase.query("PRAGMA user_version")
            version.use {
                assertTrue(it.moveToFirst())
                assertEquals(5, it.getInt(0))
            }
            assertNotNull(database.tasks().findById(41))
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
