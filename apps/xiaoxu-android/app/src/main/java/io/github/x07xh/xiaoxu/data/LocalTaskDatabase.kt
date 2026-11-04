package io.github.x07xh.xiaoxu.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * Only locally created tasks live here. Verified school records must use a separate
 * source-aware model and are never inferred from manual tasks.
 */
@Entity(tableName = "local_tasks")
data class LocalTask(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val completed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface LocalTaskDao {
    @Query("SELECT * FROM local_tasks ORDER BY createdAt DESC, id DESC")
    fun observe(): Flow<List<LocalTask>>

    @Insert
    suspend fun insert(task: LocalTask)

    @Query("UPDATE local_tasks SET completed = :completed WHERE id = :id")
    suspend fun setCompleted(id: Long, completed: Boolean)

    @Query("DELETE FROM local_tasks WHERE id = :id")
    suspend fun remove(id: Long)
}

@Database(entities = [LocalTask::class], version = 1, exportSchema = false)
abstract class LocalTaskDatabase : RoomDatabase() {
    abstract fun tasks(): LocalTaskDao

    companion object {
        @Volatile private var instance: LocalTaskDatabase? = null

        fun open(context: Context): LocalTaskDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    LocalTaskDatabase::class.java,
                    "xiaoxu-local-tasks.db"
                ).build().also { instance = it }
            }
    }
}
