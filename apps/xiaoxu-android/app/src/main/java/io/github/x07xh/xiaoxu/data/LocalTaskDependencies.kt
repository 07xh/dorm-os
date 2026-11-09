package io.github.x07xh.xiaoxu.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import java.util.ArrayDeque

/**
 * A personal task may wait for multiple other personal tasks.
 * No school/cloud identity or inferred official completion is stored here.
 * Based on NextTodo's MIT-licensed dependency graph model (JunieXD/NextTodo).
 */
@Entity(
    tableName = "local_task_dependencies",
    primaryKeys = ["taskId", "prerequisiteId"],
    foreignKeys = [
        ForeignKey(
            entity = LocalTask::class,
            parentColumns = ["id"], childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = LocalTask::class,
            parentColumns = ["id"], childColumns = ["prerequisiteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["prerequisiteId"])]
)
data class LocalTaskDependency(val taskId: Long, val prerequisiteId: Long)

/** Check a proposed edge task -> prerequisite with a bounded BFS, rejecting cycles. */
fun wouldCreateDependencyCycle(
    edges: List<LocalTaskDependency>,
    taskId: Long,
    prerequisiteId: Long
): Boolean {
    if (taskId == prerequisiteId) return true
    val parents = edges.groupBy { it.taskId }
    val queue = ArrayDeque<Long>()
    val visited = hashSetOf<Long>()
    queue.add(prerequisiteId)
    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        if (current == taskId) return true
        if (visited.add(current)) {
            parents[current]?.forEach { queue.addLast(it.prerequisiteId) }
        }
    }
    return false
}

/** Dependencies block only while a stored prerequisite is unfinished. */
fun waitingLocalTaskIds(
    tasks: List<LocalTask>,
    edges: List<LocalTaskDependency>
): Set<Long> {
    val completed = tasks.associate { it.id to it.completed }
    return edges.asSequence()
        .filter { completed[it.taskId] == false && completed[it.prerequisiteId] == false }
        .map { it.taskId }.toSet()
}

fun readyLocalTaskIds(
    tasks: List<LocalTask>, edges: List<LocalTaskDependency>
): Set<Long> {
    val waiting = waitingLocalTaskIds(tasks, edges)
    return tasks.asSequence().filter { !it.completed && it.id !in waiting }
        .map { it.id }.toSet()
}


/** Real, unfinished local prerequisites to display in the waiting section. */
fun unfinishedLocalPrerequisites(
    taskId: Long,
    tasks: List<LocalTask>,
    edges: List<LocalTaskDependency>
): List<LocalTask> {
    val byId = tasks.associateBy { it.id }
    return edges.asSequence()
        .filter { it.taskId == taskId }
        .mapNotNull { byId[it.prerequisiteId] }
        .filterNot { it.completed }
        .distinctBy { it.id }
        .toList()
}
