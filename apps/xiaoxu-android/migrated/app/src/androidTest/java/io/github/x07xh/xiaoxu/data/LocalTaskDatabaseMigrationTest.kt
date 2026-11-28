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
            legacy.execSQL("INSERT INTO local_tasks(id,title,completed,createdAt,source,due) VALUES(41,'保留任务',0,1700000000000,'local','2026-12-01')")
            legacy.execSQL("INSERT INTO local_tasks(id,title,completed,createdAt,source,due) VALUES(42,'前置任务',1,1700000001000,NULL,NULL)")
            legacy.execSQL("INSERT INTO local_task_dependencies(taskId,prerequisiteId) VALUES(41,42)")
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
            // These tables are introduced by the v4 -> v5 migration, so a true v4
            // fixture must not contain legacy step rows before the migration.
            assertEquals(emptyList<TaskStep>(), database.executions().stepSnapshot())
            assertEquals(emptyList<TaskStepEdge>(), database.executions().edgeSnapshot())
            assertEquals(emptyList<TaskExecution>(), database.executions().executionSnapshot())
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
    @Test
    fun secondClassSnapshotReplacementChecksPreviousBindingBeforeReplacingRows() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, LocalTaskDatabase::class.java).build()
        try {
            val dao = database.nativeSchoolRecords()
            val firstHash = "a".repeat(64)
            val secondHash = "b".repeat(64)
            val firstAt = 1_700_000_000_000L
            val secondAt = firstAt + 10_000L
            fun score(fingerprint: String, capturedAt: Long) = NativeSchoolRecord(
                "second_class", "credits", fingerprint, "第1学期积分",
                "素质分：3 · 志愿服务时长：2", "dekt.ncwu.edu.cn", capturedAt)
            fun summary(fingerprint: String, capturedAt: Long) = NativeSchoolRecord(
                "second_class", "activity_summary", fingerprint, "本人报名活动",
                "已完整读取 0 条；活动分数不与学校积分合计相加。",
                "dekt.ncwu.edu.cn", capturedAt)

            dao.replaceSecondClassSnapshot(
                listOf(score("first-score", firstAt)),
                listOf(summary("first-summary", firstAt)),
                accountHash = firstHash,
                requirePreviousBindingMatch = true
            )
            assertEquals(firstHash, dao.snapshotKind("second_class", "account_binding").single().details)

            val rejected = runCatching {
                dao.replaceSecondClassSnapshot(
                    listOf(score("should-not-save", secondAt)),
                    listOf(summary("should-not-save-summary", secondAt)),
                    accountHash = secondHash,
                    requirePreviousBindingMatch = true,
                    expectedPreviousBinding = "c".repeat(64),
                    expectedPreviousBindingCapturedAt = firstAt
                )
            }.exceptionOrNull()
            assertNotNull(rejected)
            assertEquals(firstHash, dao.snapshotKind("second_class", "account_binding").single().details)
            assertEquals("first-score", dao.snapshotKind("second_class", "credits").single().fingerprint)

            dao.replaceSecondClassSnapshot(
                listOf(score("second-score", secondAt)),
                listOf(summary("second-summary", secondAt)),
                accountHash = secondHash,
                requirePreviousBindingMatch = true,
                expectedPreviousBinding = firstHash,
                expectedPreviousBindingCapturedAt = firstAt
            )
            assertEquals(secondHash, dao.snapshotKind("second_class", "account_binding").single().details)
            assertEquals("second-score", dao.snapshotKind("second_class", "credits").single().fingerprint)
            assertEquals(secondAt, dao.snapshotKind("second_class", "activity_summary").single().capturedAt)
        } finally {
            database.close()
        }
    }

}
