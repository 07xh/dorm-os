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
private const val FORMAT_V4 = "xiaoxu/local-tasks-backup-v4"
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
        val snapshot = database.withTransaction {
            ExecutionBackupSnapshot(database.tasks().snapshot(), database.tasks().dependencySnapshot(),
                database.executions().executionSnapshot(), database.executions().stepSnapshot(),
                database.executions().edgeSnapshot())
        }
        val tasks = snapshot.tasks
        val snapshotEdges = snapshot.dependencies
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
                .put("stableId", requireNotNull(task.stableId))
                .put("title", task.title)
                .put("completed", task.completed)
                .put("createdAt", task.createdAt)
                .put("source", task.source ?: JSONObject.NULL)
                .put("due", task.due ?: JSONObject.NULL))
        }
        require(snapshot.steps.size <= 10_000 && snapshot.stepEdges.size <= MAX_LINKS)
        val executionRecords = JSONArray()
        snapshot.executions.forEach { executionRecords.put(JSONObject()
            .put("scope",it.scope).put("rootKey",it.rootKey).put("ended",it.ended)) }
        val stepRecords = JSONArray()
        snapshot.steps.forEach { stepRecords.put(JSONObject().put("backupId",it.id)
            .put("scope",it.scope).put("rootKey",it.rootKey).put("title",it.title)
            .put("completed",it.completed).put("position",it.position).put("due",it.due ?: JSONObject.NULL)) }
        val stepLinks = JSONArray()
        snapshot.stepEdges.forEach { stepLinks.put(JSONObject().put("step",it.stepId).put("before",it.prerequisiteId)) }
        val payload = JSONObject()
            .put("schema", FORMAT_V4)
            .put("executions",executionRecords).put("steps",stepRecords).put("stepEdges",stepLinks)
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
        val hasIds = schema == FORMAT_V3 || schema == FORMAT_V4
        val expectedRootKeys = when (schema) {
            FORMAT_V4 -> setOf("schema","exportedAt","tasks","dependencies","executions","steps","stepEdges")
            FORMAT_V3 -> setOf("schema", "exportedAt", "tasks", "dependencies")
            else -> setOf("schema", "exportedAt", "tasks")
        }
        require(root.keys().asSequence().toSet() == expectedRootKeys)
        require(schema == FORMAT_V1 || schema == FORMAT_V2 || schema == FORMAT_V3 || schema == FORMAT_V4)
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
                    FORMAT_V4 -> setOf("backupId", "stableId", "title", "completed", "createdAt", "source", "due")
                    else -> setOf("backupId", "title", "completed", "createdAt", "source", "due")
                }
                require(item.keys().asSequence().toSet() == expectedKeys)
                if (hasIds) {
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
                val stableId = if (schema == FORMAT_V4) item.getString("stableId").also {
                    require(it.matches(Regex("(?:[a-f0-9]{32}|[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12})")))
                } else java.util.UUID.randomUUID().toString()
                add(LocalTask(title = title, completed = completed,
                    createdAt = at, source = source, due = due, stableId = stableId))
            }
        }
        // Validate all references and cycles before writing any task or edge.
        require(backupIds.distinct().size == backupIds.size)
        if (schema == FORMAT_V4) require(parsed.map { it.stableId }.distinct().size == parsed.size)
        val parsedLinks = mutableListOf<LocalTaskDependency>()
        if (hasIds) {
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
        val executionRows = mutableListOf<TaskExecution>()
        val stepRows = mutableListOf<TaskStep>()
        val stepEdges = mutableListOf<TaskStepEdge>()
        if (schema == FORMAT_V4) {
            fun integer(o: JSONObject, key: String): Long {
                val value=o.get(key)
                require(value is Number && value.toLong().toDouble()==value.toDouble())
                return value.toLong()
            }
            val groups=root.getJSONArray("executions")
            val children=root.getJSONArray("steps")
            val relations=root.getJSONArray("stepEdges")
            require(groups.length()<=MAX_TASKS && children.length()<=10_000 && relations.length()<=MAX_LINKS)
            for(i in 0 until groups.length()) {
                val o=groups.getJSONObject(i)
                require(o.keys().asSequence().toSet()==setOf("scope","rootKey","ended"))
                val scope=o.getString("scope");val key=o.getString("rootKey")
                require(scope.length in 1..180 && key.length in 1..180 && o.get("ended") is Boolean)
                require(scope=="device" || scope.startsWith("cloud:") || scope.startsWith("wechat:") || scope.matches(Regex("chaoxing:[a-f0-9]{64}")))
                if(scope=="device") require(key.startsWith("local:") && key.removePrefix("local:").toLongOrNull() in backupIds)
                require(executionRows.none {it.scope==scope && it.rootKey==key})
                executionRows.add(TaskExecution(scope,key,o.getBoolean("ended")))
            }
            for(i in 0 until children.length()) {
                val o=children.getJSONObject(i)
                require(o.keys().asSequence().toSet()==setOf("backupId","scope","rootKey","title","completed","position","due"))
                val id=integer(o,"backupId");val title=o.getString("title")
                val position=integer(o,"position")
                val due=if(o.isNull("due")) null else o.getString("due")
                require(id>0 && stepRows.none {it.id==id} && title.isNotBlank() && title==title.trim() && title.length<=120 &&
                    position in 0..Int.MAX_VALUE.toLong() && (due==null || validDue(due)) && o.get("completed") is Boolean)
                val step=TaskStep(id,o.getString("scope"),o.getString("rootKey"),title,o.getBoolean("completed"),position.toInt(),due)
                require(executionRows.any {it.scope==step.scope && it.rootKey==step.rootKey})
                stepRows.add(step)
            }
            for(i in 0 until relations.length()) {
                val o=relations.getJSONObject(i)
                require(o.keys().asSequence().toSet()==setOf("step","before"))
                val edge=TaskStepEdge(integer(o,"step"),integer(o,"before"))
                require(edge !in stepEdges)
                val step=stepRows.singleOrNull {it.id==edge.stepId}
                val before=stepRows.singleOrNull {it.id==edge.prerequisiteId}
                require(step!=null && before!=null && step.scope==before.scope && step.rootKey==before.rootKey)
                stepEdges.add(edge)
            }
            stepRows.forEach {step-> require(validStepPrerequisites(stepRows,stepEdges,step.id,
                stepEdges.filter {it.stepId==step.id}.mapTo(hashSetOf()) {it.prerequisiteId})) }
            executionRows.forEach {execution->
                val childrenForRoot=stepRows.filter {it.scope==execution.scope && it.rootKey==execution.rootKey}
                require(childrenForRoot.size<=100 && (!execution.ended || childrenForRoot.all {it.completed}))
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
                if (schema == FORMAT_V4) {
                    val sameIdentity=existing.singleOrNull { it.stableId == task.stableId }
                    require(sameIdentity == null || sameIdentity.backupMatch() == task.backupMatch()) {"task_identity_restore_conflict"}
                    idMap[backupIds[index]] = sameIdentity?.id ?: dao.insert(task).also {count++}
                } else if (hasIds) {
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
            val executionDao=database.executions()
            val stepMap=mutableMapOf<Long,Long>()
            val reusedExecutions=hashSetOf<ExecutionRoot>()
            for(execution in executionRows) {
                val key=if(execution.scope=="device") "local:"+requireNotNull(idMap[execution.rootKey.removePrefix("local:").toLong()]) else execution.rootKey
                val old=executionDao.stepsFor(execution.scope,key)
                val incoming=stepRows.filter {it.scope==execution.scope && it.rootKey==execution.rootKey}
                val existingExecution=executionDao.executionFor(execution.scope,key)
                require(existingExecution==null || (existingExecution.ended==execution.ended && old.size==incoming.size)) {"execution_restore_conflict"}
                if(existingExecution!=null) reusedExecutions.add(ExecutionRoot(execution.scope,execution.rootKey))
                executionDao.insertExecution(TaskExecution(execution.scope,key,execution.ended))
                val availableSteps=old.toMutableList()
                for(step in incoming) {
                    val same=availableSteps.firstOrNull {it.title==step.title && it.completed==step.completed && it.position==step.position && it.due==step.due}
                    if(existingExecution!=null) require(same!=null) {"execution_restore_conflict"}
                    val stored=if(same!=null) {availableSteps.remove(same);same.id} else executionDao.insertStep(step.copy(id=0,rootKey=key))
                    stepMap[step.id]=stored
                }
            }
            val restoredEdges=stepEdges.map {TaskStepEdge(requireNotNull(stepMap[it.stepId]),requireNotNull(stepMap[it.prerequisiteId]))}
            val storedEdges=executionDao.edgeSnapshot()
            // Existing roots must match, rather than silently unioning a different dependency graph.
            for(execution in executionRows) {
                val ids=stepRows.filter {it.scope==execution.scope && it.rootKey==execution.rootKey}.mapTo(hashSetOf()) {requireNotNull(stepMap[it.id])}
                val expected=restoredEdges.filter {it.stepId in ids}.toSet()
                val actual=storedEdges.filter {it.stepId in ids}.toSet()
                require(ExecutionRoot(execution.scope,execution.rootKey) !in reusedExecutions || actual==expected) {"execution_dependency_restore_conflict"}
            }
            restoredEdges.forEach {if(it !in storedEdges) executionDao.insertEdge(it)}
            count
        }
        return added to (parsed.size - added)
    }
}

private data class ExecutionBackupSnapshot(
    val tasks: List<LocalTask>, val dependencies: List<LocalTaskDependency>,
    val executions: List<TaskExecution>, val steps: List<TaskStep>, val stepEdges: List<TaskStepEdge>
)
