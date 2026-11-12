package io.github.x07xh.xiaoxu.ui

import io.github.x07xh.xiaoxu.data.*
import java.time.LocalDate

internal data class ExecutionRow(
    val parent: WorkspaceTask,
    val root: ExecutionRoot?,
    val step: TaskStep? = null,
    val completed: Int = 0,
    val total: Int = 0,
    val confirm: Boolean = false,
    val officialAction: Boolean = false
) {
    val key: String get() = step?.let { "step:${it.id}" } ?: "root:${parent.key}"
    val date: LocalDate? get() = parseWorkspaceDue(step?.due) ?: trustedExecutionDate(parent)
}

internal fun trustedExecutionDate(row: WorkspaceTask): LocalDate? = when {
    row.storage == "local" -> row.date
    row.provider == "chaoxing" && row.officialDeadline != null &&
        (row.storage == "native" || row.dateSource == "学习通本机官方截止") -> row.date
    else -> null
}

internal fun taskExecutionRoot(row: WorkspaceTask, ownerId: String?, schoolBinding: String?): ExecutionRoot? = when {
    row.storage == "local" -> ExecutionRoot("device", row.key)
    row.provider == "chaoxing" && !schoolBinding.isNullOrBlank() &&
        Regex("work:\\d{1,24}:\\d{1,24}:\\d{1,24}").matches(row.sourceExternalId.orEmpty()) ->
        ExecutionRoot("chaoxing:$schoolBinding", row.sourceExternalId!!)
    row.provider != "chaoxing" && row.storage == "cloud" && !ownerId.isNullOrBlank() ->
        ExecutionRoot((if (row.category == "wechat") "wechat:" else "cloud:") + ownerId, row.key)
    else -> null
}

/** Consumes already-authorized/readiness-filtered roots; no step can make an unknown official task actionable. */
internal fun executionReadyRows(
    parents: List<WorkspaceTask>, steps: List<TaskStep>, edges: List<TaskStepEdge>,
    executions: List<TaskExecution>, resolve: (WorkspaceTask) -> ExecutionRoot?, query: String
): List<ExecutionRow> {
    val needle = query.trim()
    val grouped = steps.groupBy { ExecutionRoot(it.scope,it.rootKey) }
    return parents.filter(::isActionableWorkspaceTask).flatMap { parent ->
        val root=resolve(parent)
        val children=grouped[root].orEmpty()
        val parentMatches=needle.isBlank() || parent.title.contains(needle,true) || parent.origin.contains(needle,true)
        val childMatches=children.any { it.title.contains(needle,true) }
        if(children.isEmpty()) return@flatMap if(parentMatches) listOf(ExecutionRow(parent,root)) else emptyList()
        val done=children.count {it.completed}
        if(done==children.size) {
            val ended=executions.any {it.scope==root?.scope && it.rootKey==root?.key && it.ended}
            if(parentMatches || childMatches) listOf(ExecutionRow(parent,root,completed=done,total=children.size,
                confirm=!ended,officialAction=ended && parent.provider=="chaoxing")) else emptyList()
        } else readyTaskSteps(children,edges).filter {parentMatches || it.title.contains(needle,true)}.map {
            ExecutionRow(parent,root,it,done,children.size)
        }
    }.sortedWith(compareBy<ExecutionRow> {it.date ?: LocalDate.MAX}.thenBy {it.parent.key}.thenBy {it.step?.position ?: -1}.thenBy {it.key})
}

internal fun searchExecutionRoots(parents: List<WorkspaceTask>, steps: List<TaskStep>,
    resolve: (WorkspaceTask) -> ExecutionRoot?, query: String): List<WorkspaceTask> {
    val needle=query.trim()
    if(needle.isBlank()) return parents
    val matching=steps.filter {it.title.contains(needle,true)}.mapTo(hashSetOf()) {ExecutionRoot(it.scope,it.rootKey)}
    return parents.filter {it.title.contains(needle,true) || it.origin.contains(needle,true) || resolve(it) in matching}
}
