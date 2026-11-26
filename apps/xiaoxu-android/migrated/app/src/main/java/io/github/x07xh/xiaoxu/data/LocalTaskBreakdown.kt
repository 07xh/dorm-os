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

