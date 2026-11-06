package io.github.x07xh.xiaoxu.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class LocalCourseReminderPlanTest {
    private val config = SemesterCalendar.parse(
        "2026-09-07", "18", "1 08:00-08:45\n2 08:55-09:40"
    )
    private val lesson = VerifiedLesson(
        id = "course1", term = "", title = "高数",
        teacher = "", location = "", weekday = 1,
        startSection = 1, endSection = 2,
        weeks = listOf(1, 2, 3, 4)
    )
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test fun onlyScheduledWeeksWithinRollingWindowAreConsidered() {
        val now = ZonedDateTime.of(2026, 9, 6, 12, 0, 0, 0, zone)
        val result = planCourseReminders(listOf(lesson), config, now, 14)
        assertEquals(listOf(LocalDate.parse("2026-09-07"),
            LocalDate.parse("2026-09-14")), result.map { it.day })
        assertEquals(19 * 60 * 60 * 1000L + 50 * 60 * 1000L, result.first().delayMillis)
    }

    @Test fun unchangedTimetableDoesNotResubmitExistingJobs() {
        val existing = setOf("monday-math", "tuesday-english")
        val unchanged = reminderWorkDelta(existing, existing)
        assertTrue(unchanged.toCancel.isEmpty())
        assertTrue(unchanged.toEnqueue.isEmpty())

        val changed = reminderWorkDelta(existing, setOf("tuesday-english", "friday-lab"))
        assertEquals(setOf("monday-math"), changed.toCancel)
        assertEquals(setOf("friday-lab"), changed.toEnqueue)
    }

    @Test fun changingPeriodStartReplacesTheOldReminder() {
        val now = ZonedDateTime.of(2026, 9, 6, 12, 0, 0, 0, zone)
        val moved = SemesterCalendar.parse(
            "2026-09-07", "18",
            "1 08:30-09:15\\n2 09:20-10:05"
        )
        val original = planCourseReminders(listOf(lesson), config, now).first()
        val modified = planCourseReminders(listOf(lesson), moved, now).first()
        assertTrue(modified.expectedStartEpoch > original.expectedStartEpoch)
        val delta = reminderWorkDelta(
            setOf(original.expectedStartEpoch.toString()),
            setOf(modified.expectedStartEpoch.toString())
        )
        assertEquals(setOf(original.expectedStartEpoch.toString()), delta.toCancel)
        assertEquals(setOf(modified.expectedStartEpoch.toString()), delta.toEnqueue)
    }

    @Test fun missingTimetableHoursNeverInventReminder() {
        val unknown = SemesterCalendar.parse("2026-09-07", "18", "")
        val now = ZonedDateTime.of(2026, 9, 6, 12, 0, 0, 0, zone)
        assertTrue(planCourseReminders(listOf(lesson), unknown, now).isEmpty())
    }

    @Test fun pastClassOrUnscheduledWeekDoesNotWarn() {
        val now = ZonedDateTime.of(2026, 9, 7, 8, 0, 0, 0, zone)
        val result = planCourseReminders(
            listOf(lesson.copy(weeks = listOf(1))), config, now)
        assertTrue(result.isEmpty())
    }
}
