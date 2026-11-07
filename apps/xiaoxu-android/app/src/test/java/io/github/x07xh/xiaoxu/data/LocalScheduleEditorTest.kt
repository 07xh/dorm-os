package io.github.x07xh.xiaoxu.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalScheduleEditorTest {
    private fun lesson(
        id: String, name: String, day: Int = 1, start: Int = 1,
        end: Int = 2, weeks: List<Int> = (1..16).toList()
    ) = VerifiedLesson(
        id = id, term = "", title = name, teacher = "",
        location = "", weekday = day, startSection = start,
        endSection = end, weeks = weeks
    )

    @Test fun singleWeekChangePreservesAllOtherOccurrences() {
        val original = lesson("math", "高数")
        val edited = LocalScheduleEditor.upsert(
            listOf(original), "math",
            original.copy(weekday = 3, startSection = 3, endSection = 4), weekOnly = 5
        )
        assertEquals(2, edited.size)
        assertEquals((1..16).filterNot { it == 5 }, edited.single { it.weekday == 1 }.weeks)
        assertEquals(listOf(5), edited.single { it.weekday == 3 }.weeks)
    }

    @Test fun wholeCourseEditReplacesOnlyTarget() {
        val original = lesson("math", "高数")
        val other = lesson("english", "英语", day = 3)
        val edited = LocalScheduleEditor.upsert(
            listOf(original, other), "math", original.copy(title = "高等数学", startSection = 5, endSection = 6)
        )
        assertEquals(2, edited.size)
        assertTrue(edited.any { it.id == "english" })
        assertTrue(edited.any { it.title == "高等数学" })
    }

    @Test fun overlapOnlyBlocksSameWeekAndWeekday() {
        val original = lesson("math", "高数", weeks = listOf(1, 3, 5))
        assertThrows(IllegalArgumentException::class.java) {
            LocalScheduleEditor.upsert(
                listOf(original), null,
                lesson("physics", "物理", start = 2, end = 3, weeks = listOf(3))
            )
        }
        val valid = LocalScheduleEditor.upsert(
            listOf(original), null,
            lesson("physics", "物理", start = 2, end = 3, weeks = listOf(2))
        )
        assertEquals(2, valid.size)
    }

    @Test fun singleWeekDeleteKeepsOtherWeeksAndWholeDeleteAllowsEmpty() {
        val original = lesson("math", "高数", weeks = listOf(1, 2, 3))
        val partial = LocalScheduleEditor.delete(listOf(original), "math", weekOnly = 2)
        assertEquals(listOf(1, 3), partial.single().weeks)
        assertTrue(LocalScheduleEditor.delete(listOf(original), "math").isEmpty())
    }

    @Test fun doesNotEditAbsentMeetingOrUnscheduledWeek() {
        val original = lesson("math", "高数", weeks = listOf(1, 2))
        assertThrows(IllegalArgumentException::class.java) {
            LocalScheduleEditor.upsert(listOf(original), "not-found", original)
        }
        assertThrows(IllegalArgumentException::class.java) {
            LocalScheduleEditor.delete(listOf(original), "math", weekOnly = 3)
        }
    }
}
