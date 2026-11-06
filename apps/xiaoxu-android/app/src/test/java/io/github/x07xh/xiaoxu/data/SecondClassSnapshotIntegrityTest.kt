package io.github.x07xh.xiaoxu.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Execute the production DAO's default method; only SQL calls use a recorder.
 * These tests prove rejection before any write, not Room's SQLite rollback. */
class SecondClassSnapshotIntegrityTest {
    private fun record(kind: String, at: Long = 200, host: String = "dekt.ncwu.edu.cn") =
        NativeSchoolRecord("second_class", kind, kind, "Synthetic $kind", "", host, at)

    private class RecordingDao(initial: List<NativeSchoolRecord>) : NativeSchoolRecordDao {
        val rows = initial.toMutableList()
        val writes = mutableListOf<String>()
        override fun observe(): Flow<List<NativeSchoolRecord>> = flowOf(rows.toList())
        override suspend fun snapshot() = rows.toList()
        override suspend fun snapshotKind(provider: String, kind: String) =
            rows.filter { it.provider == provider && it.kind == kind }
        override suspend fun deleteKind(provider: String, kind: String) {
            writes += "delete:$provider:$kind"
            rows.removeAll { it.provider == provider && it.kind == kind }
        }
        override suspend fun insertAll(rows: List<NativeSchoolRecord>) {
            writes += "insert"
            this.rows += rows
        }
    }

    private fun rejectedWithoutWrites(scores: List<NativeSchoolRecord>, activities: List<NativeSchoolRecord>) {
        val old = listOf(record("credits", 100), record("activity_summary", 100))
        val dao = RecordingDao(old)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { dao.replaceSecondClassSnapshot(scores, activities) }
        }
        assertEquals("invalid read must preserve the previous snapshot", old, dao.rows)
        assertEquals("validation must precede deletes and inserts", emptyList<String>(), dao.writes)
    }

    @Test fun mixedScoreAndActivityBatchesCannotOverwriteSavedRecords() {
        rejectedWithoutWrites(listOf(record("credits")), listOf(record("activity_summary", 199)))
        rejectedWithoutWrites(listOf(record("credits"), record("credits", 199)),
            listOf(record("activity_summary")))
        rejectedWithoutWrites(listOf(record("credits")),
            listOf(record("activity_summary"), record("activity", 199)))
    }

    @Test fun aDifferentSourceCannotUseTheSecondClassProviderLabelToWrite() {
        for (host in listOf("", "sec.ncwu.edu.cn", "dekt.ncwu.edu.cn.attacker.invalid")) {
            rejectedWithoutWrites(listOf(record("credits", host = host)), listOf(record("activity_summary")))
            rejectedWithoutWrites(listOf(record("credits")), listOf(record("activity_summary", host = host)))
        }
    }

    @Test fun anUndatedBatchCannotBecomeTheCurrentOfflineSnapshot() {
        for (at in listOf(0L, -1L))
            rejectedWithoutWrites(listOf(record("credits", at)), listOf(record("activity_summary", at)))
    }

    @Test fun aCoherentReadReplacesOnlySecondClassRecordsAndClearsOldCatalog() = runBlocking {
        val other = record("course", 100).copy(provider = "chaoxing", sourceHost = "i.chaoxing.com")
        val dao = RecordingDao(listOf(record("credits", 100), record("activity_summary", 100),
            record("catalog_empty", 100), other))
        val scores = listOf(record("credits"))
        val activities = listOf(record("activity_summary"), record("activity"))
        dao.replaceSecondClassSnapshot(scores, activities)
        assertEquals(listOf(other) + scores + activities, dao.rows)
    }
}
