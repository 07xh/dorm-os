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
    /** Atomically replace a complete score+activity read, including verified zero activities. */
    @Transaction
    suspend fun replaceSecondClassSnapshot(
        scores: List<NativeSchoolRecord>, activities: List<NativeSchoolRecord>,
        catalogEmpty: List<NativeSchoolRecord> = emptyList()
    ) {
        require(scores.isNotEmpty() && scores.size <= 50)
        require(scores.all { it.provider == "second_class" && it.kind == "credits" })
        require(activities.size in 1..251)
        require(activities.count { it.kind == "activity_summary" } == 1)
        require(activities.all { it.provider == "second_class" && it.kind in setOf("activity", "activity_summary") })
        // The validated catalog is tied to this account's verified score read.
        // Clear an earlier owner's empty snapshot on successful account switch,
        // even when the current catalog is unavailable or nonempty/unverified.
        require(catalogEmpty.size == 0 || catalogEmpty.size == 3)
        require(catalogEmpty.all {
            it.provider == "second_class" && it.kind == "catalog_empty" &&
                it.sourceHost == "dekt.ncwu.edu.cn" && it.capturedAt == scores.first().capturedAt
        })
        require(catalogEmpty.map { it.title }.toSet() ==
            if (catalogEmpty.isEmpty()) emptySet() else
                setOf("报名中", "待验证", "即将开始报名"))
        deleteKind("second_class", "catalog_empty")
        deleteKind("second_class", "credits")
        deleteKind("second_class", "activity")
        deleteKind("second_class", "activity_summary")
        insertAll(scores + activities + catalogEmpty)
    }
    @Query("SELECT * FROM native_school_records ORDER BY capturedAt DESC, provider, kind, title")
    fun observe(): Flow<List<NativeSchoolRecord>>

    @Query("SELECT * FROM native_school_records ORDER BY capturedAt DESC, provider, kind, title")
    suspend fun snapshot(): List<NativeSchoolRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<NativeSchoolRecord>)

    @Query("DELETE FROM native_school_records WHERE provider = :provider AND kind = :kind")
    suspend fun deleteKind(provider: String, kind: String)

    @Query("SELECT * FROM native_school_records WHERE provider = :provider AND kind = :kind")
    suspend fun snapshotKind(provider: String, kind: String): List<NativeSchoolRecord>

    /**
     * Owner explicitly requests another visible official page. This remains a
     * local, unverified multi-page snapshot, not complete school synchronization.
     * Duplicate fingerprints update in-place. The size bound is checked within
     * the Room transaction before any write, preserving all old rows on error.
     */
    @Transaction
    suspend fun appendKind(provider: String, kind: String, rows: List<NativeSchoolRecord>): Int {
        require(provider == "chaoxing" && kind in setOf("course", "homework"))
        require(rows.isNotEmpty() && rows.size <= 120)
        require(rows.all { it.provider == provider && it.kind == kind })
        val keys = (snapshotKind(provider, kind).map { it.fingerprint } +
            rows.map { it.fingerprint }).toSet()
        require(keys.size <= 500) { "page_collection_limit" }
        insertAll(rows)
        return keys.size
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

