package io.github.x07xh.xiaoxu.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NcwuElectiveWishPlanTest {
    private fun wish(
        course: String = "TEST_C1", section: String = "TEST_A",
        week: List<Int> = listOf(1, 3), day: Int? = 2,
        start: Int? = 3, end: Int? = 4, term: String = "2026-2027-1"
    ) = NcwuElectiveWish(term, course, section, "虚构课程", "测试教师",
        day, start, end, week)

    @Test fun strictRoundTripAndDeduplication() {
        val a = wish()
        val b = wish(section = "TEST_B", day = null, start = null, end = null, week = emptyList())
        val entries = NcwuElectiveWishPlan.add(NcwuElectiveWishPlan.add(emptyList(), a), b)
        assertEquals(entries, NcwuElectiveWishPlan.decode(NcwuElectiveWishPlan.encode(entries)))
        assertThrows(IllegalArgumentException::class.java) { NcwuElectiveWishPlan.add(entries, a) }
        assertEquals(listOf(b, a), NcwuElectiveWishPlan.move(entries, 0, 1))
        assertEquals(listOf(b), NcwuElectiveWishPlan.remove(entries, a.stableKey))
        assertThrows(IllegalArgumentException::class.java) { NcwuElectiveWishPlan.remove(entries, "missing") }
    }

    @Test fun weekParserFailsClosed() {
        assertEquals(listOf(1, 2, 3, 6), NcwuElectiveWishPlan.parseWeeks("1-3，6"))
        assertEquals(emptyList<Int>(), NcwuElectiveWishPlan.parseWeeks(" "))
        for (bad in listOf("1-3,3", "0", "36", "5-2", "1-16(单)", "2,,3", "1;2")) {
            assertThrows(IllegalArgumentException::class.java) {
                NcwuElectiveWishPlan.parseWeeks(bad)
            }
        }
    }

    @Test fun sameWeeksAndSectionsOnlyWarnWhenBothKnown() {
        val a = wish()
        val clash = wish(course = "TEST_C2", section = "TEST_B", week = listOf(3), start = 4, end = 5)
        val otherWeek = clash.copy(weeks = listOf(2))
        assertEquals(NcwuWishConflict.OVERLAP, NcwuElectiveWishPlan.conflict(a, listOf(clash)))
        assertEquals(NcwuWishConflict.NO_KNOWN_CONFLICT,
            NcwuElectiveWishPlan.conflict(a, listOf(otherWeek)))
        assertEquals(NcwuWishConflict.INSUFFICIENT_DATA,
            NcwuElectiveWishPlan.conflict(a, listOf(clash.copy(weeks = emptyList()))))
        assertEquals(NcwuWishConflict.INSUFFICIENT_DATA,
            NcwuElectiveWishPlan.conflict(a, listOf(clash.copy(term = "2026-2027-2"))))
        // Two teaching classes for the same course are alternatives, not proof of a clash.
        assertEquals(NcwuWishConflict.INSUFFICIENT_DATA,
            NcwuElectiveWishPlan.conflict(a, listOf(wish(section = "TEST_B"))))
    }

    @Test fun invalidDataCannotOverwriteValidList() {
        val a = wish()
        for (bad in listOf(
            a.copy(courseCode = "C 1"),
            a.copy(term = "2026-2028-1"),
            a.copy(weekday = 8),
            a.copy(startSection = null),
            a.copy(weeks = listOf(1, 1)),
            a.copy(weeks = (1..36).toList()),
            a.copy(courseName = "<script>"),
            a.copy(teacher = "\n")
        )) {
            assertThrows(IllegalArgumentException::class.java) { NcwuElectiveWishPlan.normalize(bad) }
        }
        val good = NcwuElectiveWishPlan.encode(listOf(a))
        assertThrows(Exception::class.java) {
            NcwuElectiveWishPlan.decode(good.replace("TEST_A", "TEST A"))
        }
        assertThrows(Exception::class.java) {
            NcwuElectiveWishPlan.decode(good.replace("\"teacher\"", "\"cookie\""))
        }
        assertTrue(NcwuElectiveWishPlan.decode(good).single().classCode == "TEST_A")
    }
}
