package io.github.x07xh.xiaoxu.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

class SemesterCalendarTest {
    private val config = SemesterCalendar.parse(
        "2026-09-07",
        "18",
        "1 08:00-08:45\n2 08:55-09:40\n3 10:00-10:45"
    )

    @Test fun configuredEmptyWeekRemainsSelectableWithoutInventingLessons() {
        val sparse = listOf(VerifiedLesson(
            id = "local-lab", term = "", title = "实验",
            teacher = "", location = "",
            weekday = 3, startSection = 3, endSection = 4,
            weeks = listOf(1, 3, 5)
        ))
        assertEquals(listOf(1, 3, 5), selectableTeachingWeeks(sparse, null))
        assertEquals((1..18).toList(), selectableTeachingWeeks(sparse, config))
        assertEquals(2, config.currentWeek(LocalDate.parse("2026-09-14")))
        assertEquals(LocalDate.parse("2026-09-14"), config.dateFor(2, 1))
        assertEquals((1..18).toList(), selectableTeachingWeeks(emptyList(), config))
        val inconsistentSource = sparse.single().copy(weeks = listOf(19))
        assertEquals((1..19).toList(), selectableTeachingWeeks(listOf(inconsistentSource), config))
    }

    @Test fun academicWeeksAreDerivedFromRealDatesOnly() {
        assertNull(config.currentWeek(LocalDate.parse("2026-09-06")))
        assertEquals(1, config.currentWeek(LocalDate.parse("2026-09-07")))
        assertEquals(1, config.currentWeek(LocalDate.parse("2026-09-13")))
        assertEquals(2, config.currentWeek(LocalDate.parse("2026-09-14")))
        assertEquals(18, config.currentWeek(LocalDate.parse("2027-01-10")))
        assertNull(config.currentWeek(LocalDate.parse("2027-01-11")))
    }

    @Test fun weekdayDateAndSectionTimeAreStable() {
        assertEquals(LocalDate.parse("2026-09-22"), config.dateFor(3, 2))
        assertNull(config.dateFor(19, 1))
        assertNull(config.dateFor(1, 0))
        assertEquals("08:00–09:40", config.periodLabel(1, 2))
        assertNull(config.periodLabel(2, 4))
    }

    @Test fun invalidInputsDoNotCreateFakeTermData() {
        assertThrows(IllegalArgumentException::class.java) {
            SemesterCalendar.parse("2026-09-08", "18", "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SemesterCalendar.parse("2026-09-07", "0", "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SemesterCalendar.parse("2026-09-07", "18", "1 09:00-08:00")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SemesterCalendar.parse("2026-09-07", "18", "1 08:00-08:45\n1 08:50-09:40")
        }
    }

    @Test fun optionalHoursRemainUnknownAndRoundTrip() {
        val calendar = SemesterCalendar.parse("2026-09-07", "18", "")
        assertNull(calendar.periodLabel(1, 2))
        assertEquals(config, SemesterCalendar.parse(
            config.firstMonday.toString(), config.totalWeeks.toString(), config.editableTimes()
        ))
    }
}
