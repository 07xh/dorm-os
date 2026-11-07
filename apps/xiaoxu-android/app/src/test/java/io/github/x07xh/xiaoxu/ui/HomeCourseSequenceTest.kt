package io.github.x07xh.xiaoxu.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** 保留本机课程原始顺序；首页预览不推断教学周或补造课块。 */
class HomeCourseSequenceTest {
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
