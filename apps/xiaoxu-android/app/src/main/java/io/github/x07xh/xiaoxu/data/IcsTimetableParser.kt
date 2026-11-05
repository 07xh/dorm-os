package io.github.x07xh.xiaoxu.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Parses the already-produced SleepDown -> iCalendar export.
 *
 * A generic iCalendar event does not identify academic sections or teaching
 * week numbers. Rather than inventing them, reject calendars without explicit
 * "课程节次" and "原始周次" in their event descriptions.
 *
 * Pure Kotlin/JVM: never reads a school session, file path, or network.
 */
data class ImportedIcsCourse(
    val title: String,
    val teacher: String,
    val location: String,
    val weekday: Int,
    val startSection: Int,
    val endSection: Int,
    val weeks: List<Int>
)

object IcsTimetableParser {
    private const val MAX_INPUT = 1_000_000
    private const val MAX_EVENTS = 1200
    private data class Event(
        val date: LocalDate,
        val title: String,
        val teacher: String,
        val location: String,
        val weekday: Int,
        val start: Int,
        val end: Int,
        val sourceWeeks: List<Int>
    )
    private data class Group(
        val title: String,
        val teacher: String,
        val location: String,
        val weekday: Int,
        val start: Int,
        val end: Int,
        val sourceWeeks: List<Int>
    )

    fun parse(source: String): List<ImportedIcsCourse> {
        require(source.length in 1..MAX_INPUT) { "日历文件为空或超过大小上限" }
        val lines = mutableListOf<String>()
        source.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
            .split('\n').forEach { line ->
                if (line.startsWith(" ") || line.startsWith("\t")) {
                    require(lines.isNotEmpty()) { "iCalendar 折行不完整" }
                    lines[lines.lastIndex] += line.drop(1)
                } else {
                    lines += line
                }
            }
        require(lines.any { it == "BEGIN:VCALENDAR" } &&
            lines.any { it == "END:VCALENDAR" }) { "不是有效的 .ics 日历文件" }

        val events = mutableListOf<Event>()
        var fields: MutableMap<String, String>? = null
        for (line in lines) {
            when (line) {
                "BEGIN:VEVENT" -> {
                    require(fields == null) { "iCalendar 事件嵌套异常" }
                    fields = linkedMapOf()
                }
                "END:VEVENT" -> {
                    val item = fields ?: throw IllegalArgumentException("缺少事件起始标记")
                    require(item["RRULE"] == null && item["RDATE"] == null) {
                        "此日历包含未展开的重复规则，请先导出完整日期课程 .ics"
                    }
                    if (item["STATUS"] != "CANCELLED") {
                        events += parseEvent(item)
                        require(events.size <= MAX_EVENTS) { "日历事件数量超出安全上限" }
                    }
                    fields = null
                }
                else -> if (fields != null && ':' in line) {
                    val delimiter = line.indexOf(':')
                    val name = line.substring(0, delimiter)
                        .substringBefore(';').uppercase()
                    // One entry per field; do not interpret untrusted parameters.
                    if (name !in fields) fields[name] = line.substring(delimiter + 1)
                }
            }
        }
        require(fields == null) { "iCalendar 事件未结束" }
        require(events.isNotEmpty()) { "未找到可识别的课程事件" }

        val groups = events.groupBy {
            Group(it.title, it.teacher, it.location, it.weekday,
                it.start, it.end, it.sourceWeeks)
        }
        require(groups.size <= 300) { "课表课程课块超过支持数量" }

        // Infer the first Monday only from fully enumerated, self-consistent
        // groups. It is not safe to guess a term start from arbitrary dates.
        val anchors = mutableSetOf<LocalDate>()
        for ((group, occurrences) in groups) {
            val days = occurrences.map { it.date }.distinct().sorted()
            require(days.size == occurrences.size) { "发现重复的相同日期课程，请检查日历" }
            if (days.size != group.sourceWeeks.size) continue
            val candidate = weekMonday(days.first()).minusWeeks(
                group.sourceWeeks.first().toLong() - 1L)
            val match = days.indices.all { index ->
                weekMonday(days[index]) == candidate.plusWeeks(
                    group.sourceWeeks[index].toLong() - 1L)
            }
            require(match) { "日历课程日期与原始周次不一致，未导入" }
            anchors += candidate
        }
        require(anchors.size == 1) {
            "无法唯一确定开学周，请使用保留原始周次的 SleepDown 转换 .ics 文件"
        }
        val firstMonday = anchors.single()
        return groups.map { (group, occurrences) ->
            val weeks = occurrences.map { item ->
                val week = ChronoUnit.WEEKS.between(
                    firstMonday, weekMonday(item.date)).toInt() + 1
                require(week in group.sourceWeeks && week in 1..35) {
                    "存在无法核对周次的课程日期，旧课表未更改"
                }
                week
            }.distinct().sorted()
            ImportedIcsCourse(group.title, group.teacher, group.location,
                group.weekday, group.start, group.end, weeks)
        }
    }

    private fun parseEvent(fields: Map<String, String>): Event {
        val title = unescape(fields["SUMMARY"].orEmpty()).trim()
        val teacherAndPeriod = unescape(fields["DESCRIPTION"].orEmpty())
        val teacher = Regex("""(?m)^教师[：:]\s*(.*)$""")
            .find(teacherAndPeriod)?.groupValues?.get(1)?.trim().orEmpty()
        val sectionText = Regex("""(?m)^课程节次[：:]\s*([0-9,，、\s]+)$""")
            .find(teacherAndPeriod)?.groupValues?.get(1).orEmpty()
        val weekText = Regex("""(?m)^原始周次[：:]\s*([0-9,，、\s]+)$""")
            .find(teacherAndPeriod)?.groupValues?.get(1).orEmpty()
        val sections = Regex("""\d+""").findAll(sectionText)
            .map { it.value.toInt() }.toList()
        val weeks = Regex("""\d+""").findAll(weekText)
            .map { it.value.toInt() }.distinct().sorted().toList()
        require(title.isNotBlank() && title.length <= 120 &&
            teacher.length <= 90 && sections.isNotEmpty() &&
            sections.size <= 24 && sections.all { it in 1..24 } &&
            sections.zipWithNext().all { (a, b) -> b == a + 1 } &&
            weeks.isNotEmpty() && weeks.size <= 35 &&
            weeks.all { it in 1..35 }) {
            "事件缺少完整课程名称、连续节次或原始周次，不能直接生成周课表"
        }
        val startDate = parseDate(fields["DTSTART"].orEmpty())
        val endDate = parseDate(fields["DTEND"].orEmpty())
        require(startDate == endDate) { "跨日期课程无法转换成单日节次课表" }
        val location = unescape(fields["LOCATION"].orEmpty()).trim()
        require(location.length <= 130) { "教室字段过长" }
        return Event(startDate, title, teacher, location,
            startDate.dayOfWeek.value, sections.first(), sections.last(), weeks)
    }

    private fun parseDate(value: String): LocalDate {
        require(value.length >= 8 && value.take(8).all(Char::isDigit)) {
            "课程缺少可识别的日期"
        }
        return LocalDate.of(value.substring(0, 4).toInt(),
            value.substring(4, 6).toInt(),
            value.substring(6, 8).toInt())
    }

    private fun weekMonday(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    private fun unescape(text: String): String {
        val result = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            if (text[i] == '\\' && i + 1 < text.length) {
                i++
                result.append(when (text[i]) {
                    'n', 'N' -> '\n'
                    ',' -> ','
                    ';' -> ';'
                    '\\' -> '\\'
                    else -> text[i]
                })
            } else {
                result.append(text[i])
            }
            i++
        }
        return result.toString()
    }
}
