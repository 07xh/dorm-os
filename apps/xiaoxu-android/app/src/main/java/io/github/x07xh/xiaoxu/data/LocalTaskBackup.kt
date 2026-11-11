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
private const val FORMAT_V3 = "xiaoxu/local-tasks-backup-v3"
private val VALID_SOURCES = setOf("local", "course", "chaoxing", "second_class", "other")
private const val MAX_BYTES = 1_048_576
private const val MAX_TASKS = 3_000
private const val MAX_LINKS = 10_000

/** A V3 backup ID may only bind to a truly identical stored personal task.
 * A shared title/timestamp alone is not an identity: metadata may have changed.
 */
private data class BackupTaskMatch(
    val title: String,
    val createdAt: Long,
    val completed: Boolean,
    val source: String?,
    val due: String?
)

private fun LocalTask.backupMatch() =
    BackupTaskMatch(title, createdAt, completed, source, due)

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
        // Read both tables from one transaction: concurrent edits must never
        // create a backup with a task snapshot and mismatched dependency edges.
        val (tasks, snapshotEdges) = database.withTransaction {
            database.tasks().snapshot() to database.tasks().dependencySnapshot()
        }
        require(tasks.size <= MAX_TASKS) { "too_many_local_tasks" }
        val records = JSONArray()
        val ids = tasks.mapTo(hashSetOf()) { it.id }
        val links = JSONArray()
        for (edge in snapshotEdges) {
            if (edge.taskId in ids && edge.prerequisiteId in ids) {
                links.put(JSONObject().put("task", edge.taskId)
                    .put("before", edge.prerequisiteId))
            }
        }
        require(links.length() <= MAX_LINKS) { "too_many_dependencies" }
        for (task in tasks) {
            require(task.source == null || task.source in VALID_SOURCES)
            require(task.due == null || validDue(task.due))
            records.put(JSONObject()
                .put("backupId", task.id)
                .put("title", task.title)
                .put("completed", task.completed)
                .put("createdAt", task.createdAt)
                .put("source", task.source ?: JSONObject.NULL)
                .put("due", task.due ?: JSONObject.NULL))
        }
        val payload = JSONObject()
            .put("schema", FORMAT_V3)
            .put("exportedAt", Instant.now().toString())
            .put("tasks", records)
            .put("dependencies", links)
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
        val schema = root.getString("schema")
        val expectedRootKeys = if (schema == FORMAT_V3)
            setOf("schema", "exportedAt", "tasks", "dependencies")
            else setOf("schema", "exportedAt", "tasks")
        require(root.keys().asSequence().toSet() == expectedRootKeys)
        require(schema == FORMAT_V1 || schema == FORMAT_V2 || schema == FORMAT_V3)
        require(Instant.parse(root.getString("exportedAt")).toEpochMilli() > 0L)
        val arr = root.getJSONArray("tasks")
        require(arr.length() <= MAX_TASKS) { "too_many_local_tasks" }
        val now = System.currentTimeMillis()
        val backupIds = mutableListOf<Long>()
        val parsed = buildList {
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                val expectedKeys = when (schema) {
                    FORMAT_V1 -> setOf("title", "completed", "createdAt")
                    FORMAT_V2 -> setOf("title", "completed", "createdAt", "source", "due")
                    else -> setOf("backupId", "title", "completed", "createdAt", "source", "due")
                }
                require(item.keys().asSequence().toSet() == expectedKeys)
                if (schema == FORMAT_V3) {
                    val original = item.get("backupId")
                    require(original is Number && original.toLong() > 0L &&
                        original.toDouble() == original.toLong().toDouble())
                    backupIds.add(original.toLong())
                }
                val title = item.opt("title")
                val completed = item.opt("completed")
                val timestamp = item.opt("createdAt")
                require(title is String && title.isNotBlank() && title.length <= 120 &&
                    title == title.trim() && completed is Boolean && timestamp is Number)
                val at = timestamp.toLong()
                require(at > 0L && at <= now + 86_400_000L &&
                    timestamp.toDouble() == at.toDouble())
                val source = if (schema != FORMAT_V1 && !item.isNull("source")) {
                    val value = item.getString("source")
                    require(value in VALID_SOURCES)
                    value
                } else null
                val due = if (schema != FORMAT_V1 && !item.isNull("due")) {
                    val value = item.getString("due")
                    require(validDue(value))
                    value
                } else null
                add(LocalTask(title = title, completed = completed,
                    createdAt = at, source = source, due = due))
            }
        }
        // Validate all references and cycles before writing any task or edge.
        require(backupIds.distinct().size == backupIds.size)
        val parsedLinks = mutableListOf<LocalTaskDependency>()
        if (schema == FORMAT_V3) {
            val links = root.getJSONArray("dependencies")
            require(links.length() <= MAX_LINKS) { "too_many_dependencies" }
            val known = backupIds.toSet()
            for (i in 0 until links.length()) {
                val edge = links.getJSONObject(i)
                require(edge.keys().asSequence().toSet() == setOf("task", "before"))
                val task = edge.get("task")
                val before = edge.get("before")
                require(task is Number && before is Number &&
                    task.toLong() > 0L && before.toLong() > 0L &&
                    task.toDouble() == task.toLong().toDouble() &&
                    before.toDouble() == before.toLong().toDouble())
                val from = task.toLong()
                val to = before.toLong()
                require(from in known && to in known)
                require(parsedLinks.none { it.taskId == from && it.prerequisiteId == to })
                require(!wouldCreateDependencyCycle(parsedLinks, from, to))
                parsedLinks.add(LocalTaskDependency(from, to))
            }
        }
        // One transaction remaps backup IDs to stored Room IDs and preserves all
        // links; an incompatible existing graph rolls back the entire import.
        val added = database.withTransaction {
            val dao = database.tasks()
            val existing = dao.snapshot()
            val available = existing.groupBy { it.backupMatch() }
                .mapValues { (_, rows) ->
                    java.util.ArrayDeque(rows.map { it.id })
                }.toMutableMap()
            val seen = existing.mapTo(mutableSetOf()) {
                it.title to it.createdAt
            }
            val idMap = mutableMapOf<Long, Long>()
            var count = 0
            for ((index, task) in parsed.withIndex()) {
                val key = task.title to task.createdAt
                if (schema == FORMAT_V3) {
                    // Pair matching with a queue also preserves two legitimately
                    // distinct same-title / same-millisecond tasks.
                    val matchingId = available[task.backupMatch()]?.pollFirst()
                    val stored = matchingId ?: dao.insert(task).also { count++ }
                    idMap[backupIds[index]] = stored
                } else if (seen.add(key)) {
                    dao.insert(task)
                    count++
                }
            }
            for (edge in parsedLinks) {
                val from = requireNotNull(idMap[edge.taskId])
                val to = requireNotNull(idMap[edge.prerequisiteId])
                require(from != to) { "dependency_identity_conflict" }
                if (dao.dependencySnapshot().none {
                    it.taskId == from && it.prerequisiteId == to
                }) {
                    require(dao.tryAddDependency(from, to)) { "dependency_conflict" }
                }
            }
            count
        }
        return added to (parsed.size - added)
    }
}
