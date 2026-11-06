package io.github.x07xh.xiaoxu.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/**
 * Explicit local timetable configuration. Never infer the school's actual term
 * start or period times from a course import.
 */
data class SemesterCalendar(
    val firstMonday: LocalDate,
    val totalWeeks: Int,
    val sectionTimes: Map<Int, SectionTime>
) {
    init {
        require(firstMonday.dayOfWeek == DayOfWeek.MONDAY) { "第1周日期必须是周一" }
        require(totalWeeks in 1..35) { "学期周数必须在1至35周之间" }
        require(sectionTimes.keys.all { it in 1..24 }) { "节次必须在1至24节之间" }
    }

    /** Outside the configured teaching term is not represented as a valid week. */
    fun currentWeek(date: LocalDate): Int? {
        if (date.isBefore(firstMonday)) return null
        val week = ChronoUnit.DAYS.between(firstMonday, date).toInt() / 7 + 1
        return week.takeIf { it in 1..totalWeeks }
    }

    fun dateFor(week: Int, weekday: Int): LocalDate? {
        if (week !in 1..totalWeeks || weekday !in 1..7) return null
        return firstMonday.plusWeeks(week.toLong() - 1).plusDays(weekday.toLong() - 1)
    }

    fun periodLabel(startSection: Int, endSection: Int): String? {
        if (startSection !in 1..24 || endSection !in startSection..24) return null
        val first = sectionTimes[startSection] ?: return null
        val last = sectionTimes[endSection] ?: return null
        return "${first.start}–${last.end}"
    }

    fun editableTimes(): String = sectionTimes.toSortedMap().entries.joinToString("\n") {
        "${it.key} ${it.value.start}-${it.value.end}"
    }

    companion object {
        /**
         * One line per configured period, e.g. "1 08:00-08:45".
         * Optional periods stay unknown; no placeholder schedule is invented.
         */
        fun parse(firstMondayText: String, weeksText: String, periodsText: String): SemesterCalendar {
            val monday = try {
                LocalDate.parse(firstMondayText.trim())
            } catch (_: DateTimeParseException) {
                throw IllegalArgumentException("请填写有效的第1周周一日期，如2026-09-07")
            }
            val weeks = weeksText.trim().toIntOrNull()
                ?: throw IllegalArgumentException("请填写正确的学期周数")
            val times = linkedMapOf<Int, SectionTime>()
            val pattern = Regex("""^(\d{1,2})\s+(\d{2}:\d{2})\s*[-–]\s*(\d{2}:\d{2})$""")
            periodsText.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }
                .forEach { row ->
                    val match = pattern.matchEntire(row)
                        ?: throw IllegalArgumentException("节次时间格式不正确：$row")
                    val section = match.groupValues[1].toInt()
                    require(section in 1..24 && section !in times) { "节次重复或超出范围：$section" }
                    val start = try {
                        LocalTime.parse(match.groupValues[2])
                    } catch (_: DateTimeParseException) {
                        throw IllegalArgumentException("开始时间不正确：$row")
                    }
                    val end = try {
                        LocalTime.parse(match.groupValues[3])
                    } catch (_: DateTimeParseException) {
                        throw IllegalArgumentException("结束时间不正确：$row")
                    }
                    require(end.isAfter(start)) { "结束时间必须晚于开始时间：$row" }
                    times[section] = SectionTime(start, end)
                }
            return SemesterCalendar(monday, weeks, times)
        }
    }
}

/**
 * Without an explicit semester, expose only weeks present in the imported data.
 * Once configured, allow viewing empty teaching weeks instead of hiding them.
 * Imported out-of-range weeks remain visible for user correction rather than
 * silently dropping real course records.
 */
fun selectableTeachingWeeks(
    lessons: List<VerifiedLesson>, calendar: SemesterCalendar?
): List<Int> = (
    lessons.flatMap { it.weeks.filter { week -> week in 1..35 } } +
        (calendar?.let { (1..it.totalWeeks).toList() } ?: emptyList())
).distinct().sorted()

data class SectionTime(val start: LocalTime, val end: LocalTime)
