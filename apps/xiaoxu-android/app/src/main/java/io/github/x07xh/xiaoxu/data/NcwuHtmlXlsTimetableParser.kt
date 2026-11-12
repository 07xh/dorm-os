package io.github.x07xh.xiaoxu.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.Charset

/**
 * Local-only NCWU timetable adapter for HTML tables exported with .xls/.html extensions.
 *
 * Many school sites return an HTML <table> disguised as .xls. This parser does NOT read
 * binary OLE .xls or ZIP-based .xlsx and never contacts a school service.
 *
 * Only syntax explicitly present in the file is accepted. No fabricated weeks, dates,
 * teachers, or classrooms. LocalScheduleStore remains the single source of validation.
 *
 * Independently implemented from the publicly described NCWU Space parsing approach;
 * no third-party source code is copied.
 */
object NcwuHtmlXlsTimetableParser {
    private const val MAX_BYTES = 1_000_000
    private const val MAX_ROWS = 300
    private val htmlTable = Regex("""(?i)<table\b""")
    private val htmlBreaks = Regex("""(?i)<br\s*/?>|</(?:p|div|li)>""")
    private val classCode = Regex("""^\[[^\]]{1,40}]\s*""")
    private val sessionPattern = Regex(
        """^([0-9,\s，\-—–~～至]+?)\s*([单双])?周\s*(?:[(（]\s*([单双])\s*[)）]|([单双]))?\s*(?:星期|周)?\s*([一二三四五六日天1-7])\s*[\[【](\d{1,2})(?:\s*[-~～至]\s*(\d{1,2}))?[\]】]\s*(.*)$"""
    )
    private val weekPart = Regex("""^(\d{1,2})(?:-(\d{1,2}))?$""")
    private val weekday = mapOf('一' to 1, '二' to 2, '三' to 3, '四' to 4,
        '五' to 5, '六' to 6, '日' to 7, '天' to 7)

    fun parse(bytes: ByteArray): List<VerifiedLesson> {
        require(bytes.isNotEmpty() && bytes.size <= MAX_BYTES) { "课表文件为空或超过 1 MB" }
        if (bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) {
            throw IllegalArgumentException("这是二进制 .xlsx 文件，暂不支持直接导入；请从教务系统导出 HTML 格式课表或使用已有的 ICS/JSON 导入")
        }
        if (bytes.size >= 4 && bytes[0] == 0xD0.toByte() && bytes[1] == 0xCF.toByte() &&
            bytes[2] == 0x11.toByte() && bytes[3] == 0xE0.toByte()) {
            throw IllegalArgumentException("这是二进制 .xls 文件，暂不支持直接导入；请从教务系统导出 HTML 格式课表或使用已有的 ICS/JSON 导入")
        }
        val utf8 = bytes.toString(Charsets.UTF_8).trimStart('\uFEFF')
        val gbDeclared = Regex("""(?i)charset\s*=\s*["']?\s*(?:gbk|gb2312|gb18030)""")
            .containsMatchIn(utf8.take(2048))
        val text = if (gbDeclared || '\uFFFD' in utf8) {
            String(bytes, Charset.forName("GB18030")).trimStart('\uFEFF')
        } else utf8
        require(htmlTable.containsMatchIn(text)) {
            "文件不是可识别的 HTML 表格课表；原有课表未修改"
        }
        return parseHtml(text)
    }

    /** Context-free parser, reusable by JVM fixture tests. */
    fun parseHtml(html: String): List<VerifiedLesson> {
        require(html.isNotBlank() && html.length <= MAX_BYTES) { "课表内容为空或过大" }
        val doc = Jsoup.parse(html)
        val candidates = doc.select("table").mapNotNull { table ->
            val rows = table.select("tr").toList()
            val index = rows.take(8).indexOfFirst { row ->
                val labels = row.select("th, td").map { it.text().trim() }
                labels.any { it == "课程" || it == "课程名称" } &&
                    labels.any { it.contains("上课时间地点") } &&
                    labels.any { it == "任课教师" || it == "教师" }
            }
            if (index < 0) null else table to index
        }
        require(candidates.size == 1) {
            if (candidates.isEmpty()) "未找到华水个人课表的课程/教师/上课时间地点表头"
            else "检测到多张同类型课程表，无法确认应导入哪张"
        }

        val (table, headerRow) = candidates.single()
        val rows = table.select("tr").toList()
        val headers = rows[headerRow].select("th, td").map { it.text().trim() }
        val nameIndex = headers.indexOfFirst { it == "课程" || it == "课程名称" }
        val teacherIndex = headers.indexOfFirst { it == "任课教师" || it == "教师" }
        val timeIndex = headers.indexOfFirst { it.contains("上课时间地点") }
        require(nameIndex >= 0 && teacherIndex >= 0 && timeIndex >= 0) { "课表字段不完整" }
        val dataRows = rows.drop(headerRow + 1)
        require(dataRows.isNotEmpty() && dataRows.size <= MAX_ROWS) { "课程数量不合理，未导入" }
        val result = JSONArray()
        dataRows.forEachIndexed { i, row ->
            val cells = row.select("> td, > th").toList()
            if (cells.isEmpty() || cells.all { it.text().isBlank() }) return@forEachIndexed
            require(cells.size > maxOf(nameIndex, teacherIndex, timeIndex)) {
                "第" + (i + 1) + "行列数不完整，未导入"
            }
            val title = cells[nameIndex].text().trim().replace(classCode, "")
            require(title.isNotBlank() && title.length <= 120) {
                "第" + (i + 1) + "行缺少课程名，未导入"
            }
            val teacher = cells[teacherIndex].text().trim().replace(classCode, "")
            val schedule = segments(cells[timeIndex])
            require(schedule.isNotEmpty()) { "课程“" + title + "”没有上课时间地点，未导入" }
            schedule.forEach { part ->
                val match = sessionPattern.matchEntire(part)
                    ?: throw IllegalArgumentException("课程“" + title + "”存在无法识别的周次/节次，未导入")
                val parityMarkers = (2..4).map { match.groupValues[it] }.filter { it.isNotBlank() }
                require(parityMarkers.size <= 1) { "课程“" + title + "”存在重复或冲突的单双周标记，未导入" }
                val weeks = parseWeeks(match.groupValues[1], parityMarkers.singleOrNull().orEmpty(), title)
                val dayChar = match.groupValues[5].first()
                val day = weekday[dayChar] ?: dayChar.digitToIntOrNull()
                    ?: throw IllegalArgumentException("课程“" + title + "”星期无效")
                val start = match.groupValues[6].toInt()
                val end = match.groupValues[7].toIntOrNull() ?: start
                val location = match.groupValues[8].trim()
                result.put(JSONObject()
                    .put("name", title)
                    .put("teacher", teacher)
                    .put("position", location)
                    .put("day", day)
                    .put("startSection", start)
                    .put("endSection", end)
                    .put("weeks", JSONArray(weeks)))
            }
        }
        require(result.length() > 0) { "未识别到完整课程，原有课表未修改" }
        return LocalScheduleStore.parseCourses(result.toString())
    }

    private fun segments(cell: Element): List<String> {
        val separated = cell.html().replace(htmlBreaks, "；")
        return Jsoup.parseBodyFragment(separated).text()
            .split(Regex("""[；;\r\n]+"""))
            .map { it.trim().replace(Regex("""\s+"""), " ") }
            .filter { it.isNotBlank() }
    }

    private fun parseWeeks(source: String, parity: String, title: String): List<Int> {
        val values = sortedSetOf<Int>()
        val parts = source.replace(Regex("""[—–~～至]"""), "-")
            .replace('，', ',').replace(Regex("""\s+"""), "")
            .split(',').filter { it.isNotEmpty() }
        require(parts.isNotEmpty() && parts.size <= 35) { "课程“" + title + "”周次无效" }
        for (part in parts) {
            val matched = weekPart.matchEntire(part)
                ?: throw IllegalArgumentException("课程“" + title + "”周次格式无法识别")
            val from = matched.groupValues[1].toInt()
            val to = matched.groupValues[2].toIntOrNull() ?: from
            require(from in 1..35 && to in from..35) { "课程“" + title + "”周次范围无效" }
            for (week in from..to) {
                if (parity.isBlank() || (parity == "单" && week % 2 == 1) ||
                    (parity == "双" && week % 2 == 0)) values.add(week)
            }
        }
        require(values.isNotEmpty()) { "课程“" + title + "”没有有效上课周次" }
        return values.toList()
    }
}
