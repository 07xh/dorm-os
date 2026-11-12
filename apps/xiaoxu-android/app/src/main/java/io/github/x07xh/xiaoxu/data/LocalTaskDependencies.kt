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


/**
 * Topological layer view of explicitly related local tasks only.
 *
 * An edge (taskId, prerequisiteId) means the task waits for its prerequisite.
 * This is the native Compose equivalent of NextTodo's DAG relationship view:
 * no synthetic source IDs, no cloud state, and no persisted duplicate graph.
 * A corrupt cycle fails closed rather than being drawn as an executable chain.
 */
data class LocalTaskGraphNode(
    val task: LocalTask,
    val stage: Int,
    val prerequisites: List<LocalTask>,
    val unlocks: List<LocalTask>
)

fun localTaskGraphLayers(
    tasks: List<LocalTask>,
    edges: List<LocalTaskDependency>
): List<List<LocalTaskGraphNode>> {
    val byId = tasks.associateBy { it.id }
    // A persisted self-loop is corrupt, not a relation that can be silently dropped.
    if (edges.any { it.taskId == it.prerequisiteId && it.taskId in byId }) return emptyList()
    val valid = edges.asSequence()
        .filter { it.taskId != it.prerequisiteId &&
            it.taskId in byId && it.prerequisiteId in byId }
        .distinct().toList()
    if (valid.isEmpty()) return emptyList()

    val linked = valid.flatMapTo(mutableSetOf()) {
        listOf(it.taskId, it.prerequisiteId)
    }
    val prerequisites = valid.groupBy { it.taskId }
    val outgoing = valid.groupBy { it.prerequisiteId }
    val indegree = linked.associateWith { prerequisites[it]?.size ?: 0 }.toMutableMap()
    val stages = linked.associateWith { 0 }.toMutableMap()
    val queue = ArrayDeque<Long>()
    linked.sorted().filter { indegree[it] == 0 }.forEach(queue::addLast)

    var visited = 0
    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        visited++
        for (edge in outgoing[current].orEmpty()) {
            val dependent = edge.taskId
            stages[dependent] = maxOf(stages.getValue(dependent),
                stages.getValue(current) + 1)
            val remaining = indegree.getValue(dependent) - 1
            indegree[dependent] = remaining
            if (remaining == 0) queue.addLast(dependent)
        }
    }
    if (visited != linked.size) return emptyList()

    return linked.map { id ->
        LocalTaskGraphNode(
            task = byId.getValue(id),
            stage = stages.getValue(id),
            prerequisites = prerequisites[id].orEmpty().map { byId.getValue(it.prerequisiteId) }
                .sortedWith(compareBy<LocalTask> { it.createdAt }.thenBy { it.id }),
            unlocks = outgoing[id].orEmpty().map { byId.getValue(it.taskId) }
                .sortedWith(compareBy<LocalTask> { it.createdAt }.thenBy { it.id })
        )
    }.groupBy { it.stage }.toSortedMap().values.map { layer ->
        layer.sortedWith(compareBy<LocalTaskGraphNode> { it.task.completed }
            .thenBy { it.task.createdAt }.thenBy { it.task.id })
    }
}

/**
 * Focus mode includes only genuine ancestors and descendants of one task,
 * NOT sibling branches that merely share an ancestor. Search includes title
 * matches and their ancestors, to keep the visible prerequisites explainable.
 * Returns IDs from the supplied graph only; never fabricates a local task.
 */
fun localTaskGraphVisibleIds(
    layers: List<List<LocalTaskGraphNode>>,
    focusTaskId: Long? = null,
    titleSearch: String = ""
): Set<Long> {
    val nodes = layers.flatten()
    val byId = nodes.associateBy { it.task.id }
    if (focusTaskId != null && focusTaskId !in byId) return emptySet()
    val result = linkedSetOf<Long>()
    fun visit(start: Collection<Long>, walkToDependents: Boolean) {
        val queue = ArrayDeque<Long>()
        start.forEach(queue::addLast)
        val visited = hashSetOf<Long>()
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            if (!visited.add(id)) continue
            val node = byId[id] ?: continue
            result.add(id)
            val adjacent = if (walkToDependents) node.unlocks else node.prerequisites
            adjacent.forEach { if (it.id in byId && it.id !in visited) queue.addLast(it.id) }
        }
    }
    if (focusTaskId != null) {
        // Two independent traversals avoid pulling in unrelated sibling tasks.
        visit(listOf(focusTaskId), walkToDependents = false)
        visit(listOf(focusTaskId), walkToDependents = true)
        // A downstream task may ALSO require another branch; show those
        // prerequisites without including unrelated sibling-only branches.
        visit(result.toList(), walkToDependents = false)
    } else {
        val term = titleSearch.trim()
        if (term.isEmpty()) return byId.keys
        val matches = nodes.filter { it.task.title.contains(term, ignoreCase = true) }
            .map { it.task.id }
        visit(matches, walkToDependents = false)
    }
    return result
}
