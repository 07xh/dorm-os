package io.github.x07xh.xiaoxu.data

/**
 * Manual, user-approved decomposition into real Room tasks.
 * This is deliberately NOT AI generation, an official school task, or a separate task store.
 */
private const val MAX_BREAKDOWN_STEPS = 12
private val STEP_PREFIX = Regex("""^(?:[-*•]\s+|\d{1,2}[.)、]\s*)""")

/** Returns null instead of silently dropping malformed/duplicate steps. */
fun parseLocalTaskBreakdown(raw: String): List<String>? {
    if (raw.length > 2400) return null
    val steps = raw.lineSequence()
        .map { it.trim().replaceFirst(STEP_PREFIX, "").trim() }
        .filter { it.isNotBlank() }
        .toList()
    if (steps.size !in 2..MAX_BREAKDOWN_STEPS ||
        steps.any { it.length !in 1..120 } ||
        steps.map { it.lowercase() }.toSet().size != steps.size
    ) return null
    return steps
}

/**
 * A -> B -> original task: each newly created step waits for the previous one.
 * Step 1 additionally waits for the parent's EXISTING prerequisites; no shortcut
 * around an upstream gate. The original task retains all existing dependencies.
 * Child IDs here are newly allocated Room IDs, never synthetic preview IDs.
 */
fun localTaskBreakdownEdges(
    originalId: Long,
    stepIds: List<Long>,
    originalPrerequisites: List<Long>
): List<LocalTaskDependency> {
    require(originalId > 0 && stepIds.size in 2..MAX_BREAKDOWN_STEPS)
    require(stepIds.all { it > 0 && it != originalId } && stepIds.distinct() == stepIds)
    require(originalPrerequisites.all { it > 0 && it != originalId && it !in stepIds })
    return buildList {
        originalPrerequisites.distinct().forEach {
            add(LocalTaskDependency(stepIds.first(), it))
        }
        for (i in 1 until stepIds.size) {
            add(LocalTaskDependency(stepIds[i], stepIds[i - 1]))
        }
        add(LocalTaskDependency(originalId, stepIds.last()))
    }
}

/**
 * An already approved chain must not be cloned when the same plan is submitted
 * again (for example after reopening the original task). Match only real,
 * linked Room tasks, never titles elsewhere in the list. This is a bounded,
 * side-effect-free check and does not require a second task table or migration.
 */
internal fun hasEquivalentLocalTaskBreakdown(
    original: LocalTask,
    stepTitles: List<String>,
    tasks: List<LocalTask>,
    edges: List<LocalTaskDependency>
): Boolean {
    if (stepTitles.size !in 2..MAX_BREAKDOWN_STEPS) return false
    val byId = tasks.associateBy { it.id }
    val prerequisites = edges.groupBy { it.taskId }
    fun matches(fromId: Long, stepIndex: Int): Boolean =
        prerequisites[fromId].orEmpty().any { edge ->
            val candidate = byId[edge.prerequisiteId] ?: return@any false
            candidate.title == stepTitles[stepIndex] &&
                candidate.source == original.source &&
                candidate.due == null &&
                (stepIndex == 0 || matches(candidate.id, stepIndex - 1))
        }
    return matches(original.id, stepTitles.lastIndex)
}

/**
 * Read-only progress view over existing personal DAG. This is labelled
 * "前置任务" rather than implying every upstream task is a generated child.
 * The graph algorithm fails closed on corruption and never touches school data.
 */
fun localTaskAncestors(
    taskId: Long, tasks: List<LocalTask>, edges: List<LocalTaskDependency>
): List<LocalTask> {
    if(tasks.none { it.id == taskId } || edges.isEmpty()) return emptyList()
    val layers = localTaskGraphLayers(tasks, edges)
    if(layers.isEmpty()) return emptyList()
    val byId = tasks.associateBy { it.id }
    val before = edges.groupBy { it.taskId }
    val queue = java.util.ArrayDeque<Long>()
    before[taskId].orEmpty().forEach { queue.addLast(it.prerequisiteId) }
    val ancestors = hashSetOf<Long>()
    while(queue.isNotEmpty()) {
        val id = queue.removeFirst()
        if(id==taskId || id !in byId) return emptyList()
        if(ancestors.add(id)) {
            if(ancestors.size > 512) return emptyList()
            before[id].orEmpty().forEach { queue.addLast(it.prerequisiteId) }
        }
    }
    return layers.flatten().map { it.task }.filter { it.id in ancestors }
}
