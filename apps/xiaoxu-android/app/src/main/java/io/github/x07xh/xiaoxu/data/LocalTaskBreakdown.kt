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
