package io.github.x07xh.xiaoxu.data

import android.content.ContentResolver
import android.net.Uri
import androidx.room.withTransaction
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

private const val FORMAT = "xiaoxu/local-tasks-backup-v1"
private const val MAX_BYTES = 1_048_576
private const val MAX_TASKS = 3_000

/**
 * Offline-only manual task backup via Android's user-selected document provider.
 *
 * The JSON is deliberately plaintext for portability: never export tokens,
 * school records, or private D1 records. No storage permission is needed.
 */
class LocalTaskBackup(
    private val database: LocalTaskDatabase,
    private val resolver: ContentResolver
) {
    suspend fun exportTo(uri: Uri): Int {
        val tasks = database.tasks().snapshot()
        require(tasks.size <= MAX_TASKS) { "too_many_local_tasks" }
        val records = JSONArray()
        for (task in tasks) {
            records.put(JSONObject()
                .put("title", task.title)
                .put("completed", task.completed)
                .put("createdAt", task.createdAt))
        }
        val payload = JSONObject()
            .put("schema", FORMAT)
            .put("exportedAt", Instant.now().toString())
            .put("tasks", records)
            .toString().toByteArray(Charsets.UTF_8)
        require(payload.size <= MAX_BYTES) { "backup_too_large" }
        val output = resolver.openOutputStream(uri, "wt")
            ?: throw IllegalStateException("document_write_unavailable")
        output.use { it.write(payload) }
        return tasks.size
    }

    /** Merge only; never clear the database or overwrite an existing task. */
    suspend fun mergeFrom(uri: Uri): Pair<Int, Int> {
        val input = resolver.openInputStream(uri)
            ?: throw IllegalStateException("document_read_unavailable")
        val raw = input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val n = stream.read(buffer)
                if (n < 0) break
                if (output.size() + n > MAX_BYTES)
                    throw IllegalArgumentException("backup_too_large")
                output.write(buffer, 0, n)
            }
            output.toByteArray()
        }
        val contents = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(raw)).toString()
        val root = JSONObject(contents)
        require(root.keySet() == setOf("schema", "exportedAt", "tasks"))
        require(root.optString("schema") == FORMAT)
        require(Instant.parse(root.getString("exportedAt")).toEpochMilli() > 0L)
        val arr = root.getJSONArray("tasks")
        require(arr.length() <= MAX_TASKS) { "too_many_local_tasks" }
        val now = System.currentTimeMillis()
        val parsed = buildList {
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                require(item.keySet() == setOf("title", "completed", "createdAt"))
                val title = item.opt("title")
                val completed = item.opt("completed")
                val timestamp = item.opt("createdAt")
                require(title is String && title.isNotBlank() && title.length <= 120 &&
                    title == title.trim() && completed is Boolean && timestamp is Number)
                val at = timestamp.toLong()
                require(at > 0L && at <= now + 86_400_000L &&
                    timestamp.toDouble() == at.toDouble())
                add(LocalTask(title = title, completed = completed, createdAt = at))
            }
        }
        // A single Room transaction makes repeat imports idempotent even when
        // a document contains duplicated records.
        val added = database.withTransaction {
            val existing = database.tasks().snapshot()
            val seen = existing.mapTo(mutableSetOf()) {
                Triple(it.title, it.completed, it.createdAt)
            }
            var count = 0
            for (task in parsed) {
                if (seen.add(Triple(task.title, task.completed, task.createdAt))) {
                    database.tasks().insert(task)
                    count++
                }
            }
            count
        }
        return added to (parsed.size - added)
    }
}
