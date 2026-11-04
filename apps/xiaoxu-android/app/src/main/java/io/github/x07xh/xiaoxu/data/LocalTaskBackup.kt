package io.github.x07xh.xiaoxu.data

import android.content.ContentResolver
import android.net.Uri
import androidx.room.withTransaction
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException
import org.json.JSONArray
import org.json.JSONObject

private const val FORMAT_V1 = "xiaoxu/local-tasks-backup-v1"
private const val FORMAT_V2 = "xiaoxu/local-tasks-backup-v2"
private val VALID_SOURCES = setOf("local", "course", "chaoxing", "second_class", "other")
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
            require(task.source == null || task.source in VALID_SOURCES)
            require(task.due == null || validDue(task.due))
            records.put(JSONObject()
                .put("title", task.title)
                .put("completed", task.completed)
                .put("createdAt", task.createdAt)
                .put("source", task.source ?: JSONObject.NULL)
                .put("due", task.due ?: JSONObject.NULL))
        }
        val payload = JSONObject()
            .put("schema", FORMAT_V2)
            .put("exportedAt", Instant.now().toString())
            .put("tasks", records)
            .toString().toByteArray(Charsets.UTF_8)
        require(payload.size <= MAX_BYTES) { "backup_too_large" }
        val output = resolver.openOutputStream(uri, "wt")
            ?: throw IllegalStateException("document_write_unavailable")
        output.use { it.write(payload) }
        return tasks.size
    }

    private fun validDue(value: String): Boolean {
        if (!Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(value)) return false
        return try { LocalDate.parse(value); true } catch (_: DateTimeParseException) { false }
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
        require(root.keys().asSequence().toSet() == setOf("schema", "exportedAt", "tasks"))
        val schema = root.getString("schema")
        require(schema == FORMAT_V1 || schema == FORMAT_V2)
        require(Instant.parse(root.getString("exportedAt")).toEpochMilli() > 0L)
        val arr = root.getJSONArray("tasks")
        require(arr.length() <= MAX_TASKS) { "too_many_local_tasks" }
        val now = System.currentTimeMillis()
        val parsed = buildList {
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                require(item.keys().asSequence().toSet() == if (schema == FORMAT_V1)
                    setOf("title", "completed", "createdAt")
                    else setOf("title", "completed", "createdAt", "source", "due"))
                val title = item.opt("title")
                val completed = item.opt("completed")
                val timestamp = item.opt("createdAt")
                require(title is String && title.isNotBlank() && title.length <= 120 &&
                    title == title.trim() && completed is Boolean && timestamp is Number)
                val at = timestamp.toLong()
                require(at > 0L && at <= now + 86_400_000L &&
                    timestamp.toDouble() == at.toDouble())
                val source = if (schema == FORMAT_V2 && !item.isNull("source")) {
                    val value = item.getString("source")
                    require(value in VALID_SOURCES)
                    value
                } else null
                val due = if (schema == FORMAT_V2 && !item.isNull("due")) {
                    val value = item.getString("due")
                    require(validDue(value))
                    value
                } else null
                add(LocalTask(title = title, completed = completed,
                    createdAt = at, source = source, due = due))
            }
        }
        // A single Room transaction makes repeat imports idempotent even when
        // a document contains duplicated records.
        val added = database.withTransaction {
            val existing = database.tasks().snapshot()
            val seen = existing.mapTo(mutableSetOf()) {
                Pair(it.title, it.createdAt)
            }
            var count = 0
            for (task in parsed) {
                if (seen.add(Pair(task.title, task.createdAt))) {
                    database.tasks().insert(task)
                    count++
                }
            }
            count
        }
        return added to (parsed.size - added)
    }
}
