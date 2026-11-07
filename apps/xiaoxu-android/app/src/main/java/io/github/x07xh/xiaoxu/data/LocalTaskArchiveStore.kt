package io.github.x07xh.xiaoxu.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Device-only task-list archive: never changes D1 assignments or school submissions.
 * Store stable cloud assignment IDs, not titles, since homework names repeat.
 */
internal class LocalTaskArchiveStore private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "xiaoxu_task_archive_v1", Context.MODE_PRIVATE
    )

    var archivedKeys: Set<String> by mutableStateOf(
        prefs.getStringSet("cloud_keys", emptySet())?.toSet() ?: emptySet()
    )
        private set

    fun archive(key: String) = archiveAll(listOf(key))

    fun archiveAll(keys: Collection<String>) {
        val eligible = keys.filterTo(mutableSetOf()) { it.startsWith("cloud:") }
        if (eligible.isEmpty()) return
        val updated = archivedKeys + eligible
        if (updated == archivedKeys) return
        prefs.edit().putStringSet("cloud_keys", updated).apply()
        archivedKeys = updated
    }

    fun restore(key: String) {
        if (key !in archivedKeys) return
        val updated = archivedKeys - key
        prefs.edit().putStringSet("cloud_keys", updated).apply()
        archivedKeys = updated
    }

    companion object {
        @Volatile private var instance: LocalTaskArchiveStore? = null

        fun get(context: Context): LocalTaskArchiveStore =
            instance ?: synchronized(this) {
                instance ?: LocalTaskArchiveStore(context).also { instance = it }
            }
    }
}
