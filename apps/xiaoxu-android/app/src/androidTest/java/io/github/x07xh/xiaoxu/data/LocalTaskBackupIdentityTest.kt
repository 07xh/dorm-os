package io.github.x07xh.xiaoxu.data

import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Offline V3 import must never attach a saved edge to another same-title task. */
@RunWith(AndroidJUnit4::class)
class LocalTaskBackupIdentityTest {
    @Test fun sameTitleAndTimestampWithDifferentMetadataKeepTheirOwnDependencies() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "xiaoxu-backup-${UUID.randomUUID()}.db"
        val file = File(context.cacheDir, "xiaoxu-backup-${UUID.randomUUID()}.json")
        val db = Room.databaseBuilder(context, LocalTaskDatabase::class.java, name)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .build()
        try {
            val createdAt = 1_700_000_000_000L
            val before = db.tasks().insert(LocalTask(
                title = "重复标题", completed = false, createdAt = createdAt,
                source = "local", due = null
            ))
            val actualPrerequisite = db.tasks().insert(LocalTask(
                title = "重复标题", completed = true, createdAt = createdAt,
                source = "course", due = "2026-11-25"
            ))
            fun backupTask(id: Long, title: String, done: Boolean, created: Long,
                           source: String, due: String?): JSONObject = JSONObject()
                .put("backupId", id)
                .put("title", title)
                .put("completed", done)
                .put("createdAt", created)
                .put("source", source)
                .put("due", due ?: JSONObject.NULL)
            val backup = JSONObject()
                .put("schema", "xiaoxu/local-tasks-backup-v3")
                .put("exportedAt", Instant.now().toString())
                .put("tasks", JSONArray()
                    // The source order is deliberately reversed relative to Room.
                    .put(backupTask(201L, "重复标题", true, createdAt, "course", "2026-11-25"))
                    .put(backupTask(202L, "重复标题", false, createdAt, "local", null))
                    .put(backupTask(203L, "后续任务", false, createdAt + 1, "local", null)))
                .put("dependencies", JSONArray()
                    .put(JSONObject().put("task", 203L).put("before", 201L)))
            file.writeText(backup.toString(), Charsets.UTF_8)

            val (added, skipped) = LocalTaskBackup(db, context.contentResolver)
                .mergeFrom(Uri.fromFile(file))
            assertEquals(1, added)
            assertEquals(2, skipped)
            val tasks = db.tasks().snapshot()
            assertEquals(3, tasks.size)
            val successor = tasks.single { it.title == "后续任务" }
            val link = db.tasks().dependencySnapshot().single()
            assertEquals(successor.id, link.taskId)
            assertEquals(actualPrerequisite, link.prerequisiteId)
            assertTrue(link.prerequisiteId != before)
        } finally {
            db.close()
            context.deleteDatabase(name)
            file.delete()
        }
    }
}
