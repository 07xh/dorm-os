package io.github.x07xh.xiaoxu.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Home courses are saved local timetable blocks, not a verified current-week schedule. */
class HomeCourseSequenceTest {
    @Test
    fun emptyAndUnselectedWeekdayStartAtFirstPosition() {
        assertEquals(0, resolveHomeCourseIndex(emptyList(), null))
        assertEquals(0, resolveHomeCourseIndex(emptyList(), "old"))
        assertEquals(0, resolveHomeCourseIndex(listOf("a", "b"), null))
    }

    @Test
    fun selectingLaterCourseKeepsExactBlock() {
        assertEquals(2, resolveHomeCourseIndex(listOf("math", "english", "physics"), "physics"))
        assertEquals(1, resolveHomeCourseIndex(listOf("physics", "math", "english"), "math"))
    }

    @Test
    fun removedOrReimportedCourseFallsBackWithoutIndexCrash() {
        assertEquals(0, resolveHomeCourseIndex(listOf("physics"), "deleted-course"))
        assertEquals(0, resolveHomeCourseIndex(emptyList(), "deleted-course"))
        assertEquals(0, resolveHomeCourseIndex(listOf("single"), "single"))
    }
}
