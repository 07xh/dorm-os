package io.github.x07xh.xiaoxu.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LocalTaskDatabaseMigrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun databaseName() = "xiaoxu-migration-${UUID.randomUUID()}.db"

    private fun createV1(name: String) {
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        val legacy = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            legacy.execSQL(
                "CREATE TABLE local_tasks (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "title TEXT NOT NULL, completed INTEGER NOT NULL, createdAt INTEGER NOT NULL)"
            )
            legacy.execSQL(
                "INSERT INTO local_tasks (id,title,completed,createdAt) VALUES (?,?,?,?)",
                arrayOf(41L, "升级前保留的任务", 1, 1_700_000_000_000L)
            )
            legacy.version = 1
        } finally {
            legacy.close()
        }
    }

    private fun createV3(name: String) {
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        val legacy = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            legacy.execSQL(
                "CREATE TABLE local_tasks (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "title TEXT NOT NULL, completed INTEGER NOT NULL, createdAt INTEGER NOT NULL, " +
                    "source TEXT, due TEXT)"
            )
            legacy.execSQL(
                "CREATE TABLE native_school_records (" +
                    "provider TEXT NOT NULL, kind TEXT NOT NULL, fingerprint TEXT NOT NULL, " +
                    "title TEXT NOT NULL, details TEXT NOT NULL, sourceHost TEXT NOT NULL, " +
                    "capturedAt INTEGER NOT NULL, PRIMARY KEY(provider,kind,fingerprint))"
            )
            legacy.execSQL(
                "INSERT INTO local_tasks (id,title,completed,createdAt,source,due) VALUES (?,?,?,?,?,?)",
                arrayOf(52L, "本机旧任务", 0, 1_700_000_000_100L, "local", "2026-12-01")
            )
            legacy.execSQL(
                "INSERT INTO native_school_records " +
                    "(provider,kind,fingerprint,title,details,sourceHost,capturedAt) VALUES (?,?,?,?,?,?,?)",
                arrayOf("chaoxing", "homework", "legacy-fingerprint", "旧作业记录",
                    "官方页面观察：未提交", "mooc1.chaoxing.com", 1_700_000_000_200L)
            )
            legacy.version = 3
        } finally {
            legacy.close()
        }
    }

    private fun open(name: String) =
        Room.databaseBuilder(context, LocalTaskDatabase::class.java, name)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .build()

    @Test
    fun v1ToV4PreservesLegacyTaskAndEnforcesDependencies() = runBlocking {
        val name = databaseName()
        createV1(name)
        val db = open(name)
        try {
            val old = db.tasks().snapshot().single()
            assertEquals(41L, old.id)
            assertEquals("升级前保留的任务", old.title)
            assertTrue(old.completed)
            assertNull(old.source)
            assertNull(old.due)
            assertTrue(db.nativeSchoolRecords().snapshot().isEmpty())
            assertTrue(db.tasks().dependencySnapshot().isEmpty())

            val before = db.tasks().insert(LocalTask(title = "前置任务"))
            val after = db.tasks().insert(LocalTask(title = "后续任务"))
            assertTrue(db.tasks().tryAddDependency(after, before))
            assertFalse(db.tasks().tryAddDependency(before, after))
            assertFalse(db.tasks().trySetCompleted(after, true))
            assertTrue(db.tasks().trySetCompleted(before, true))
            assertTrue(db.tasks().trySetCompleted(after, true))
            db.tasks().remove(before)
            assertTrue(db.tasks().dependencySnapshot().isEmpty())
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun v3ToV4PreservesPersonalTasksAndNativeSchoolRecords() = runBlocking {
        val name = databaseName()
        createV3(name)
        val db = open(name)
        try {
            val task = db.tasks().snapshot().single()
            assertEquals(52L, task.id)
            assertEquals("本机旧任务", task.title)
            assertEquals("local", task.source)
            assertEquals("2026-12-01", task.due)

            val record = db.nativeSchoolRecords().snapshot().single()
            assertEquals("legacy-fingerprint", record.fingerprint)
            assertEquals("旧作业记录", record.title)
            assertEquals("mooc1.chaoxing.com", record.sourceHost)
            assertTrue(db.tasks().dependencySnapshot().isEmpty())

            val prerequisite = db.tasks().insert(LocalTask(title = "新前置"))
            val dependent = db.tasks().insert(LocalTask(title = "新后续"))
            assertTrue(db.tasks().tryAddDependency(dependent, prerequisite))
            assertEquals(1, db.tasks().dependencySnapshot().size)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
    @Test fun v4ToV5PreservesDagAndAssignsStableRootIdentity() = runBlocking {
        val name=databaseName()
        createV3(name)
        val legacy=SQLiteDatabase.openDatabase(context.getDatabasePath(name).path,null,SQLiteDatabase.OPEN_READWRITE)
        try {
            legacy.execSQL("CREATE TABLE local_task_dependencies (taskId INTEGER NOT NULL, prerequisiteId INTEGER NOT NULL, PRIMARY KEY(taskId,prerequisiteId), FOREIGN KEY(taskId) REFERENCES local_tasks(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(prerequisiteId) REFERENCES local_tasks(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            legacy.execSQL("CREATE INDEX index_local_task_dependencies_prerequisiteId ON local_task_dependencies(prerequisiteId)")
            legacy.execSQL("INSERT INTO local_tasks (id,title,completed,createdAt) VALUES (53,'前置',0,1700000000001)")
            legacy.execSQL("INSERT INTO local_task_dependencies VALUES (52,53)")
            legacy.version=4
        } finally {legacy.close()}
        val db=open(name)
        try {
            val tasks=db.tasks().snapshot()
            assertEquals(2,tasks.size)
            assertTrue(tasks.all {it.stableId?.matches(Regex("[a-f0-9]{32}"))==true})
            assertEquals(2,tasks.map {it.stableId}.distinct().size)
            assertEquals(listOf(LocalTaskDependency(52,53)),db.tasks().dependencySnapshot())
            assertTrue(db.executions().stepSnapshot().isEmpty())
        } finally {db.close();context.deleteDatabase(name)}
    }
}
