package io.github.x07xh.xiaoxu.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class IcsTimetableParserTest {
    private fun event(
        day: String,
        weeks: String = "7\\,8",
        title: String = "工程制图",
        description: String = "教师：张臻臻\\n课程节次：8\\,9\\,10\\n原始周次："
    ): String = listOf(
        "BEGIN:VEVENT",
        "DTSTART;TZID=Asia/Shanghai:" + day + "T155500",
        "DTEND;TZID=Asia/Shanghai:" + day + "T183000",
        "SUMMARY:" + title,
        "LOCATION:三号楼3302",
        "DESCRIPTION:" + description + weeks,
        "STATUS:CONFIRMED",
        "END:VEVENT"
    ).joinToString("\r\n")

    private fun calendar(vararg events: String) =
        (listOf("BEGIN:VCALENDAR", "VERSION:2.0") +
            events.toList() + "END:VCALENDAR").joinToString("\r\n")

    @Test fun converterEventsBecomeOneWeeklyCourse() {
        val parsed = IcsTimetableParser.parse(calendar(
            event("20261014", weeks = "7\\,\r\n 8"),
            event("20261021")
        ))
        assertEquals(1, parsed.size)
        val item = parsed.single()
        assertEquals("工程制图", item.title)
        assertEquals("张臻臻", item.teacher)
        assertEquals("三号楼3302", item.location)
        assertEquals(3, item.weekday)
        assertEquals(8, item.startSection)
        assertEquals(10, item.endSection)
        assertEquals(listOf(7, 8), item.weeks)
    }

    @Test fun multipleCoursesShareTermAnchor() {
        val parsed = IcsTimetableParser.parse(calendar(
            event("20261014"),
            event("20261021"),
            event("20261012", title = "Python程序设计"),
            event("20261019", title = "Python程序设计")
        ))
        assertEquals(2, parsed.size)
        assertEquals(listOf(7, 8), parsed.first { it.title == "Python程序设计" }.weeks)
        assertEquals(1, parsed.first { it.title == "Python程序设计" }.weekday)
    }

    @Test fun partialVisibleDateGroupCanUseIndependentFullGroupAnchor() {
        val parsed = IcsTimetableParser.parse(calendar(
            event("20261014"),
            event("20261021"),
            event("20261012", weeks = "7\\,8\\,9", title = "Python程序设计")
        ))
        assertEquals(listOf(7), parsed.first { it.title == "Python程序设计" }.weeks)
    }

    @Test fun oneHundredFourEventsCollapseToTwentyFiveWeeklyBlocks() {
        // Anonymous stress fixture matching the known file shape;
        // no personal timetable or teacher details are committed.
        val monday = LocalDate.of(2026, 8, 31)
        val entries = (0 until 25).flatMap { index ->
            val numberOfWeeks = if (index == 0) 8 else 4
            val sourceWeeks = (1..numberOfWeeks).joinToString("\\,")
            (1..numberOfWeeks).map { week ->
                val date = monday.plusWeeks(week.toLong() - 1)
                    .plusDays((index % 7).toLong())
                    .format(DateTimeFormatter.BASIC_ISO_DATE)
                event(date, weeks = sourceWeeks, title = "课程" + (index + 1))
            }
        }
        assertEquals(104, entries.size)
        val parsed = IcsTimetableParser.parse(calendar(*entries.toTypedArray()))
        assertEquals(25, parsed.size)
        assertEquals(104, parsed.sumOf { it.weeks.size })
    }

    @Test fun rejectsWeekInconsistencyInsteadOfGuessing() {
        assertThrows(IllegalArgumentException::class.java) {
            IcsTimetableParser.parse(calendar(event("20261014"), event("20261028")))
        }
    }

    @Test fun rejectsGenericIcsWithoutAcademicSectionsOrWeeks() {
        val invalid = calendar(event("20261014",
            description = "普通事件，没有节次和教学周", weeks = ""))
        val error = assertThrows(IllegalArgumentException::class.java) {
            IcsTimetableParser.parse(invalid)
        }
        assertTrue(error.message.orEmpty().contains("原始周次"))
    }

    @Test fun rejectsUnexpandedRecurringEvents() {
        val recurring = event("20261014").replace(
            "END:VEVENT", "RRULE:FREQ=WEEKLY;COUNT=4\r\nEND:VEVENT")
        assertThrows(IllegalArgumentException::class.java) {
            IcsTimetableParser.parse(calendar(recurring))
        }
    }

    @Test fun rejectsConflictingSemesterAnchors() {
        val parsed = calendar(
            event("20261014"), event("20261021"),
            event("20270113", title = "另一个学期"), event("20270120", title = "另一个学期")
        )
        assertThrows(IllegalArgumentException::class.java) {
            IcsTimetableParser.parse(parsed)
        }
    }
}
