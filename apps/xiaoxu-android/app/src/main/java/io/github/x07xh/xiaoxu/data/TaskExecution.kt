package io.github.x07xh.xiaoxu.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/** Non-secret, stable account scope + source record identity. Never a title or session token. */
data class ExecutionRoot(val scope: String, val key: String)

@Entity(tableName = "task_executions", primaryKeys = ["scope", "rootKey"])
data class TaskExecution(val scope: String, val rootKey: String, val ended: Boolean = false)

@Entity(tableName = "task_steps", foreignKeys = [ForeignKey(
    entity = TaskExecution::class, parentColumns = ["scope", "rootKey"],
    childColumns = ["scope", "rootKey"], onDelete = ForeignKey.CASCADE
)], indices = [Index(value = ["scope", "rootKey"])])
data class TaskStep(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scope: String,
    val rootKey: String,
    val title: String,
    val completed: Boolean = false,
    val position: Int = 0,
    val due: String? = null
)

@Entity(tableName = "task_step_edges", primaryKeys = ["stepId", "prerequisiteId"],
    foreignKeys = [
        ForeignKey(entity = TaskStep::class, parentColumns = ["id"], childColumns = ["stepId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TaskStep::class, parentColumns = ["id"], childColumns = ["prerequisiteId"], onDelete = ForeignKey.CASCADE)
    ], indices = [Index(value = ["prerequisiteId"])])
data class TaskStepEdge(val stepId: Long, val prerequisiteId: Long)

fun readyTaskSteps(steps: List<TaskStep>, edges: List<TaskStepEdge>): List<TaskStep> {
    val byId = steps.associateBy { it.id }
    val before = edges.groupBy { it.stepId }
    return steps.filter { step -> !step.completed && before[step.id].orEmpty().all { edge ->
        byId[edge.prerequisiteId]?.let {
            it.scope == step.scope && it.rootKey == step.rootKey && it.completed
        } == true
    }}.sortedWith(compareBy<TaskStep> { it.position }.thenBy { it.id })
}

fun validStepPrerequisites(steps: List<TaskStep>, edges: List<TaskStepEdge>, id: Long, chosen: Set<Long>): Boolean {
    val current = steps.firstOrNull { it.id == id } ?: return false
    val allowed = steps.filter { it.scope == current.scope && it.rootKey == current.rootKey }
        .mapTo(hashSetOf()) { it.id }
    if (id in chosen || chosen.any { it !in allowed }) return false
    val next = edges.filter { it.stepId != id } + chosen.map { TaskStepEdge(id, it) }
    // Reuse the already-tested iterative cycle detector of the existing task DAG.
    val graph = mutableListOf<LocalTaskDependency>()
    for (edge in next) {
        if (edge.stepId !in allowed || edge.prerequisiteId !in allowed) continue
        if (wouldCreateDependencyCycle(graph, edge.stepId, edge.prerequisiteId)) return false
        graph.add(LocalTaskDependency(edge.stepId, edge.prerequisiteId))
    }
    return true
}

@Dao
interface TaskExecutionDao {
    @Query("SELECT * FROM task_steps ORDER BY position, id")
    fun observeSteps(): Flow<List<TaskStep>>
    @Query("SELECT * FROM task_step_edges ORDER BY stepId, prerequisiteId")
    fun observeEdges(): Flow<List<TaskStepEdge>>
    @Query("SELECT * FROM task_executions")
    fun observeExecutions(): Flow<List<TaskExecution>>
    @Query("SELECT * FROM task_steps ORDER BY position, id")
    suspend fun stepSnapshot(): List<TaskStep>
    @Query("SELECT * FROM task_step_edges ORDER BY stepId, prerequisiteId")
    suspend fun edgeSnapshot(): List<TaskStepEdge>
    @Query("SELECT * FROM task_executions")
    suspend fun executionSnapshot(): List<TaskExecution>
    @Query("SELECT * FROM task_steps WHERE scope=:scope AND rootKey=:key ORDER BY position, id")
    suspend fun stepsFor(scope: String, key: String): List<TaskStep>
    @Query("SELECT * FROM task_executions WHERE scope=:scope AND rootKey=:key")
    suspend fun executionFor(scope: String, key: String): TaskExecution?
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertExecution(execution: TaskExecution): Long
    @Insert
    suspend fun insertStep(step: TaskStep): Long
    @Insert
    suspend fun insertEdge(edge: TaskStepEdge)
    @Query("UPDATE task_steps SET completed=:completed WHERE id=:id")
    suspend fun writeCompleted(id: Long, completed: Boolean)
    @Query("UPDATE task_steps SET title=:title, due=:due, position=:position WHERE id=:id")
    suspend fun writeDetails(id: Long, title: String, due: String?, position: Int)
    @Query("UPDATE task_executions SET ended=:ended WHERE scope=:scope AND rootKey=:key")
    suspend fun writeEnded(scope: String, key: String, ended: Boolean)
    @Query("DELETE FROM task_steps WHERE id=:id")
    suspend fun deleteStep(id: Long)
    @Query("DELETE FROM task_step_edges WHERE stepId=:id")
    suspend fun clearPrerequisites(id: Long)
    @Query("UPDATE local_tasks SET completed=0 WHERE id=:id")
    suspend fun reopenLocal(id: Long)
    @Query("SELECT completed FROM local_tasks WHERE id=:id")
    suspend fun localCompleted(id: Long): Boolean?

    @Transaction
    suspend fun reopen(root: ExecutionRoot) {
        writeEnded(root.scope, root.key, false)
        if (root.scope == "device") root.key.removePrefix("local:").toLongOrNull()?.let { reopenLocal(it) }
    }

    @Transaction
    suspend fun addSteps(root: ExecutionRoot, titles: List<String>): Boolean {
        if (root.scope.isBlank() || root.key.isBlank() || titles.isEmpty() ||
            titles.size > 12 || titles.any { it.isBlank() || it.trim() != it || it.length > 120 } ||
            titles.map { it.lowercase() }.distinct().size != titles.size) return false
        if (root.scope == "device" && localCompleted(root.key.removePrefix("local:").toLongOrNull() ?: return false) == null) return false
        if (root.scope.startsWith("wechat:") && executionFor(root.scope,root.key)?.ended == true) return false
        val old = stepsFor(root.scope, root.key)
        if (old.size + titles.size > 100 || titles.any { t -> old.any { it.title.equals(t, true) } }) return false
        insertExecution(TaskExecution(root.scope, root.key))
        reopen(root)
        val position = (old.maxOfOrNull { it.position } ?: -1) + 1
        titles.forEachIndexed { index, title -> insertStep(TaskStep(scope=root.scope, rootKey=root.key,
            title=title, position=position+index)) }
        return true
    }

    @Transaction
    suspend fun toggleStep(root: ExecutionRoot, id: Long, completed: Boolean): Boolean {
        if (root.scope.startsWith("wechat:") && executionFor(root.scope,root.key)?.ended == true) return false
        val steps = stepsFor(root.scope, root.key)
        val step = steps.firstOrNull { it.id == id } ?: return false
        if (completed && step !in readyTaskSteps(steps, edgeSnapshot())) return false
        if (completed && root.scope == "device") {
            val localId = root.key.removePrefix("local:").toLongOrNull() ?: return false
            if (unfinishedRootDependencies(localId) > 0) return false
        }
        writeCompleted(id, completed)
        if (!completed) reopen(root)
        return true
    }

    @Query("SELECT COUNT(*) FROM local_task_dependencies d LEFT JOIN local_tasks t ON t.id=d.prerequisiteId WHERE d.taskId=:id AND (t.id IS NULL OR t.completed=0)")
    suspend fun unfinishedRootDependencies(id: Long): Int

    @Transaction
    suspend fun editStep(root: ExecutionRoot, id: Long, title: String, due: String?, position: Int): Boolean {
        if (title.isBlank() || title.length > 120 || title != title.trim() || position < 0 ||
            (due != null && runCatching { java.time.LocalDate.parse(due) }.isFailure)) return false
        val steps = stepsFor(root.scope, root.key)
        if (steps.none { it.id == id } || steps.any { it.id != id && it.title.equals(title,true) }) return false
        writeDetails(id, title, due, position)
        return true
    }

    @Transaction
    suspend fun saveStep(root: ExecutionRoot, id: Long, title: String, due: String?, position: Int, chosen: Set<Long>): Boolean {
        val steps=stepsFor(root.scope,root.key)
        if (!validStepPrerequisites(steps,edgeSnapshot(),id,chosen)) return false
        if (!editStep(root,id,title,due,position)) return false
        clearPrerequisites(id)
        chosen.forEach {insertEdge(TaskStepEdge(id,it))}
        return true
    }

    @Transaction
    suspend fun moveStep(root: ExecutionRoot, id: Long, offset: Int): Boolean {
        val steps=stepsFor(root.scope,root.key)
        val index=steps.indexOfFirst {it.id==id}
        val target=index+offset
        if(index<0 || target !in steps.indices || offset !in setOf(-1,1)) return false
        val reordered=steps.toMutableList().apply {add(target,removeAt(index))}
        reordered.forEachIndexed { position, step -> writeDetails(step.id,step.title,step.due,position) }
        return true
    }

    @Transaction
    suspend fun removeStep(root: ExecutionRoot, id: Long): Boolean {
        if (stepsFor(root.scope,root.key).none {it.id==id}) return false
        deleteStep(id)
        return true
    }

    @Transaction
    suspend fun setPrerequisites(root: ExecutionRoot, id: Long, chosen: Set<Long>): Boolean {
        val steps=stepsFor(root.scope,root.key)
        if (!validStepPrerequisites(steps,edgeSnapshot(),id,chosen)) return false
        clearPrerequisites(id)
        chosen.forEach { insertEdge(TaskStepEdge(id,it)) }
        return true
    }

    @Transaction
    suspend fun finish(root: ExecutionRoot): Boolean {
        val steps=stepsFor(root.scope,root.key)
        if (steps.isEmpty() || steps.any {!it.completed}) return false
        writeEnded(root.scope,root.key,true)
        return true
    }
}
