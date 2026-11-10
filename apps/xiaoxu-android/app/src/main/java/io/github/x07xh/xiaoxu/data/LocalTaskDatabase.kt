package io.github.x07xh.xiaoxu.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Transaction
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** 本机来源代码。source 只表示"本人标注"，不代表已与学校平台核实。 */
const val TASK_SOURCE_LOCAL = "local"

/**
 * Only locally created tasks live here. Verified school records must use a separate
 * source-aware model and are never inferred from manual tasks.
 *
 * source / due 都是本人填写：source 是自选标签，due 是本地提醒日期（yyyy-MM-dd）。
 * 它们不是学校平台的官方字段，任何界面都必须按"本人标注"表述。
 */
@Entity(tableName = "local_tasks")
data class LocalTask(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val completed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    /** null = 升级前的旧任务确实没有来源标注，不推断、不补默认值。 */
    val source: String? = null,
    /** null = 本人没有填写截止日期。 */
    val due: String? = null
)

@Dao
interface LocalTaskDao {
    @Query("SELECT * FROM local_tasks ORDER BY createdAt DESC, id DESC")
    fun observe(): Flow<List<LocalTask>>

    /** 备份导出用：按创建时间正序的整体快照（PR #213 的 LocalTaskBackup 依赖它）。 */
    @Query("SELECT * FROM local_tasks ORDER BY createdAt ASC, id ASC")
    suspend fun snapshot(): List<LocalTask>

    @Query("SELECT * FROM local_tasks WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): LocalTask?

    @Insert
    suspend fun insert(task: LocalTask): Long

    @Query("UPDATE local_tasks SET completed = :completed WHERE id = :id")
    suspend fun setCompleted(id: Long, completed: Boolean)

    /** Edit only personal metadata; completion, creation time and school records remain unchanged. */
    @Query("UPDATE local_tasks SET title = :title, source = :source, due = :due WHERE id = :id")
    suspend fun updateDetails(id: Long, title: String, source: String?, due: String?): Int

    /** Date-only fast edit must never overwrite a concurrent title/source or DAG edit. */
    @Query("UPDATE local_tasks SET due = :due WHERE id = :id")
    suspend fun updateDue(id: Long, due: String?): Int

    @Query("DELETE FROM local_tasks WHERE id = :id")
    suspend fun remove(id: Long)

    @Query("SELECT * FROM local_task_dependencies ORDER BY taskId, prerequisiteId")
    fun observeDependencies(): Flow<List<LocalTaskDependency>>

    @Query("SELECT * FROM local_task_dependencies ORDER BY taskId, prerequisiteId")
    suspend fun dependencySnapshot(): List<LocalTaskDependency>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDependency(edge: LocalTaskDependency): Long

    @Query("DELETE FROM local_task_dependencies WHERE taskId = :taskId AND prerequisiteId = :prerequisiteId")
    suspend fun deleteDependency(taskId: Long, prerequisiteId: Long)

    @Query("DELETE FROM local_task_dependencies WHERE taskId = :taskId")
    suspend fun clearDependencies(taskId: Long)

    /**
     * Saving edits and dependency changes is atomic, so "Cancel" never makes
     * a partially saved link. School-origin records are not writable here.
     */
    @Transaction
    suspend fun updateDetailsWithDependencies(
        taskId: Long, title: String, source: String?, due: String?,
        prerequisites: Set<Long>
    ): Boolean {
        if (findById(taskId) == null) return false
        val knownIds = snapshot().mapTo(hashSetOf()) { it.id }
        if (taskId in prerequisites || prerequisites.any { it !in knownIds }) return false
        val edges = dependencySnapshot().filter { it.taskId != taskId }.toMutableList()
        for (before in prerequisites.sorted()) {
            if (wouldCreateDependencyCycle(edges, taskId, before)) return false
            edges.add(LocalTaskDependency(taskId, before))
        }
        if (updateDetails(taskId, title, source, due) < 1) return false
        clearDependencies(taskId)
        for (before in prerequisites) insertDependency(LocalTaskDependency(taskId, before))
        return true
    }

    /** Validate the DAG and task existence in a single Room transaction. */
    @Transaction
    suspend fun tryAddDependency(taskId: Long, prerequisiteId: Long): Boolean {
        if (taskId == prerequisiteId || findById(taskId) == null ||
            findById(prerequisiteId) == null) return false
        val edges = dependencySnapshot()
        if (wouldCreateDependencyCycle(edges, taskId, prerequisiteId)) return false
        return insertDependency(LocalTaskDependency(taskId, prerequisiteId)) != -1L
    }
}

/**
 * v1 -> v2：新增 source / due 两列。
 * 两列都可空、都不带 SQL 默认值，因此与实体声明逐字段一致（不让 Room 在运行期因
 * defaultValue 不匹配抛 IllegalStateException）。旧任务的标题、完成状态、创建时间原样
 * 保留；旧任务的 source 保持 null，界面显示"未标注来源"，而不是替它编一个来源。
 */
internal val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE local_tasks ADD COLUMN source TEXT")
        db.execSQL("ALTER TABLE local_tasks ADD COLUMN due TEXT")
    }
}

/**
 * v2 -> v3: Create a SECOND TABLE in the SAME existing Room database. Existing
 * task rows and v1->v2 migration remain untouched. No duplicate database,
 * school password, token or raw WebView cookie is ever persisted.
 */
internal val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS native_school_records (
                provider TEXT NOT NULL,
                kind TEXT NOT NULL,
                fingerprint TEXT NOT NULL,
                title TEXT NOT NULL,
                details TEXT NOT NULL,
                sourceHost TEXT NOT NULL,
                capturedAt INTEGER NOT NULL,
                PRIMARY KEY(provider, kind, fingerprint)
            )
        """.trimIndent())
    }
}

/** Preserve existing records; local dependencies share the same Room database. */
internal val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS local_task_dependencies (
                taskId INTEGER NOT NULL,
                prerequisiteId INTEGER NOT NULL,
                PRIMARY KEY(taskId, prerequisiteId),
                FOREIGN KEY(taskId) REFERENCES local_tasks(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(prerequisiteId) REFERENCES local_tasks(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_local_task_dependencies_prerequisiteId ON local_task_dependencies(prerequisiteId)")
    }
}

@Database(entities = [LocalTask::class, NativeSchoolRecord::class, LocalTaskDependency::class], version = 4, exportSchema = false)
abstract class LocalTaskDatabase : RoomDatabase() {
    abstract fun tasks(): LocalTaskDao
    abstract fun nativeSchoolRecords(): NativeSchoolRecordDao

    companion object {
        @Volatile private var instance: LocalTaskDatabase? = null

        fun open(context: Context): LocalTaskDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    LocalTaskDatabase::class.java,
                    "xiaoxu-local-tasks.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { instance = it }
            }
    }
}
