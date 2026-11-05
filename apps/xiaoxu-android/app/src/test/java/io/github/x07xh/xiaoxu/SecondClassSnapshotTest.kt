package io.github.x07xh.xiaoxu
import io.github.x07xh.xiaoxu.data.NativeSchoolRecord
import org.junit.Assert.*
import org.junit.Test
class SecondClassSnapshotTest {
    private fun row(kind: String, stamp: Long = 10, provider: String = "second_class", id: String = kind) =
        NativeSchoolRecord(provider, kind, id, kind, "学校返回的记录", "dekt.ncwu.edu.cn", stamp)
    @Test fun verifiedZeroActivitiesRemainsComplete() {
        val result = secondClassSnapshot(listOf(row("credits"), row("activity_summary")))
        assertTrue(result.activitiesComplete)
        assertTrue(result.activities.isEmpty())
    }
    @Test fun mixedBatchesAreNotComplete() {
        val result = secondClassSnapshot(listOf(row("credits"), row("activity_summary"), row("activity", 9)))
        assertFalse(result.activitiesComplete)
        assertTrue(result.activities.isEmpty())
    }
    @Test fun manualRecordsDoNotBecomeCompleteOrAnotherProvider() {
        val result = secondClassSnapshot(listOf(row("activity"), row("credits", provider = "chaoxing")))
        assertFalse(result.activitiesComplete)
        assertTrue(result.scores.isEmpty())
    }
    @Test fun duplicateSummaryIsRejected() {
        assertFalse(secondClassSnapshot(listOf(row("activity_summary"), row("activity_summary", id = "other"))).activitiesComplete)
    }
}
