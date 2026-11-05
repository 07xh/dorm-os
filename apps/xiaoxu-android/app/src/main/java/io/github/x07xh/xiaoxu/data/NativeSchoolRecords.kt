package io.github.x07xh.xiaoxu.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Local-only snapshots obtained by the user from allowed official pages.
 * They are NOT automatically verified against the school's authoritative
 * academic record. Never include passwords, raw Cookies, HTML, URLs or CAS tickets.
 * Source-aware table shares the existing local task Room via additive v2->v3 migration.
 */
@Entity(tableName = "native_school_records", primaryKeys = ["provider", "kind", "fingerprint"])
data class NativeSchoolRecord(
    val provider: String,
    val kind: String,
    val fingerprint: String,
    val title: String,
    val details: String,
    val sourceHost: String,
    val capturedAt: Long
)

@Dao
interface NativeSchoolRecordDao {
    @Query("SELECT * FROM native_school_records ORDER BY capturedAt DESC, provider, kind, title")
    fun observe(): Flow<List<NativeSchoolRecord>>

    @Query("SELECT * FROM native_school_records ORDER BY capturedAt DESC, provider, kind, title")
    suspend fun snapshot(): List<NativeSchoolRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<NativeSchoolRecord>)

    @Query("DELETE FROM native_school_records WHERE provider = :provider AND kind = :kind")
    suspend fun deleteKind(provider: String, kind: String)

    @Query("SELECT * FROM native_school_records WHERE provider = :provider AND kind = :kind ORDER BY capturedAt DESC, title")
    suspend fun snapshotKind(provider: String, kind: String): List<NativeSchoolRecord>

    /**
     * Explicit user-chosen multi-page collection, not a complete or official
     * sync. Exact fingerprints replace duplicates; other previously captured
     * pages stay intact. Room transaction enforces the bound atomically.
     */
    @Transaction
    suspend fun appendKind(provider: String, kind: String, rows: List<NativeSchoolRecord>): Int {
        require(provider == "chaoxing" && kind in setOf("course", "homework"))
        require(rows.isNotEmpty() && rows.size <= 120)
        require(rows.all { it.provider == provider && it.kind == kind })
        val old = snapshotKind(provider, kind)
        val allKeys = (old.map { it.fingerprint } + rows.map { it.fingerprint }).toSet()
        require(allKeys.size <= 500) { "too_many_saved_pages" }
        insertAll(rows)
        return allKeys.size
    }

    @Transaction
    suspend fun replaceKind(provider: String, kind: String, rows: List<NativeSchoolRecord>) {
        require(provider in setOf("ncwu", "chaoxing", "second_class"))
        require(kind in setOf("schedule", "course", "homework", "activity", "credits"))
        require(rows.isNotEmpty() && rows.size <= 120)
        require(rows.all { it.provider == provider && it.kind == kind })
        deleteKind(provider, kind)
        insertAll(rows)
    }
}

