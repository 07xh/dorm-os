package io.github.x07xh.xiaoxu.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import io.github.x07xh.xiaoxu.data.VerifiedLesson
import java.time.LocalDate
import org.junit.Test

/** 保留本机课程原始顺序；首页预览不推断教学周或补造课块。 */
class HomeCourseSequenceTest {
    @Test
    fun homeProjectionInvalidatesOnDateArchiveOrDataChange() {
        val cache = HomeProjectionCache()
        val date = LocalDate.of(2026,11,25)
        val record = WorkspaceTask("local:1","报告","本机",false,date,"本人填写","local","local")
        val records = listOf(record)
        val initial = cache.tasks(records,emptySet(),emptyList(),emptyList(),date)
        assertSame(initial,cache.tasks(records,emptySet(),emptyList(),emptyList(),date))
        assertEquals(1,initial.attention)
        assertEquals(0,cache.tasks(records,emptySet(),emptyList(),emptyList(),date.plusDays(1)).attention)
        assertTrue(cache.tasks(records,setOf(record.key),emptyList(),emptyList(),date).all.isEmpty())
        val completed = cache.tasks(listOf(record.copy(done=true)),emptySet(),emptyList(),emptyList(),date)
        assertEquals(0,completed.pending)
    }

    @Test
    fun homeCourseProjectionKeepsOnlyRequestedWeekdayAndUpdatesOnImport() {
        val cache = HomeProjectionCache()
        val early = VerifiedLesson(id="a",term="2026-2027-1",title="早课",teacher="",location="",
            weekday=3,startSection=1,endSection=2,weeks=listOf(9))
        val late = early.copy(id="b",title="晚课",startSection=5,endSection=6)
        val other = early.copy(id="c",weekday=4)
        val lessons = listOf(late,other,early)
        val result = cache.courses(lessons,3)
        assertEquals(listOf(early,late),result)
        assertSame(result,cache.courses(lessons,3))
        assertEquals(listOf(other),cache.courses(lessons,4))
        assertTrue(cache.courses(emptyList(),3).isEmpty())
    }

    @Test
    fun emptySourceStaysEmpty() {
        assertEquals(emptyList<String>(), homeCoursePreview(emptyList<String>()))
    }

    @Test
    fun allRecordsVisibleWhenAtMostFour() {
        assertEquals(listOf("A", "B", "A"), homeCoursePreview(listOf("A", "B", "A")))
    }

    @Test
    fun firstFourPreserveSequenceFromLongList() {
        assertEquals(listOf("A", "B", "C", "D"),
            homeCoursePreview(listOf("A", "B", "C", "D", "E")))
    }
}
