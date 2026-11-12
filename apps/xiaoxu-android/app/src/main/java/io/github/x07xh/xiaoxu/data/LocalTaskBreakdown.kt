package io.github.x07xh.xiaoxu.data

/**
 * Shared AI-output validation and read-only progress for pre-existing local DAGs.
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
