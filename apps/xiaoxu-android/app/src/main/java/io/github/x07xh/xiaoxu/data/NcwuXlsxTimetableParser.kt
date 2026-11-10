package io.github.x07xh.xiaoxu.data

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.TreeMap
import java.util.zip.ZipInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Offline, bounded XLSX adapter for the *personal* NCWU Qingguo detailed timetable.
 *
 * Reuses NcwuHtmlXlsTimetableParser and LocalScheduleStore. A university-wide
 * classroom timetable is not proof of the owner's enrolment and is not imported.
 * No Apache POI, other repository, network request, credentials or new UI.
 *
 * Supports normal OOXML shared-string, inline-string and text cells. Files with
 * macro content, encrypted content or unsupported grid/merged timetable formats
 * fail without writing to the existing schedule.
 */
internal object NcwuXlsxTimetableParser {
    private const val MAX_COMPRESSED = 1_000_000
    private const val MAX_ARCHIVE_BYTES = 12_000_000
    private const val MAX_ENTRY_BYTES = 3_000_000
    private const val MAX_ENTRIES = 160
    private const val MAX_SHEETS = 8
    private const val MAX_ROWS = 350
    private const val MAX_COLUMNS = 96
    private const val MAX_CELL_LENGTH = 4000
    private val worksheet = Regex("""xl/worksheets/sheet([1-9]\d{0,2})\.xml""")
    private val cellAddress = Regex("""([A-Z]{1,3})\d{1,7}""")

    fun parse(input: ByteArray): List<VerifiedLesson> {
        require(input.size in 4..MAX_COMPRESSED &&
            input[0] == 'P'.code.toByte() && input[1] == 'K'.code.toByte()) {
            "不是有效的 .xlsx 文件；原课表未修改"
        }
        var sharedXml: ByteArray? = null
        var workbookFound = false
        var contentTypesFound = false
        var entries = 0
        var uncompressed = 0
        val sheets = TreeMap<Int, ByteArray>()
        ZipInputStream(ByteArrayInputStream(input)).use { zip ->
            val buffer = ByteArray(8192)
            while (true) {
                val entry = zip.nextEntry ?: break
                entries++
                require(entries <= MAX_ENTRIES) { "Excel 文件内部条目数量异常" }
                val id = worksheet.matchEntire(entry.name)?.groupValues?.get(1)?.toIntOrNull()
                val relevant = id != null || entry.name == "xl/sharedStrings.xml"
                val output = if (relevant) ByteArrayOutputStream() else null
                while (true) {
                    val read = zip.read(buffer)
                    if (read < 0) break
                    uncompressed += read
                    require(uncompressed <= MAX_ARCHIVE_BYTES) {
                        "Excel 文件解压后过大，拒绝导入"
                    }
                    if (output != null) {
                        require(output.size() + read <= MAX_ENTRY_BYTES) {
                            "Excel 工作表过大，拒绝导入"
                        }
                        output.write(buffer, 0, read)
                    }
                }
                when {
                    entry.name == "xl/workbook.xml" -> workbookFound = true
                    entry.name == "[Content_Types].xml" -> contentTypesFound = true
                    entry.name == "xl/sharedStrings.xml" -> {
                        require(sharedXml == null) { "Excel 共享字符串文件重复" }
                        sharedXml = output!!.toByteArray()
                    }
                    id != null -> {
                        require(sheets.size < MAX_SHEETS && !sheets.containsKey(id)) {
                            "Excel 工作表数量过多或存在重复工作表"
                        }
                        sheets[id] = output!!.toByteArray()
                    }
                }
                zip.closeEntry()
            }
        }
        require(workbookFound && contentTypesFound && sheets.isNotEmpty()) {
            "不是标准的 Excel XLSX 工作簿"
        }
        val shared = sharedXml?.let { parseSharedStrings(it) } ?: emptyList()
        val candidateHtml = mutableListOf<String>()
        var classroomExport = false
        for ((_, sheet) in sheets) {
            val rows = parseSheet(sheet, shared)
            val header = rows.take(12).indexOfFirst(::personalHeader)
            if (header >= 0) candidateHtml += tableHtml(rows.drop(header))
            if (rows.take(12).any(::classroomHeader)) classroomExport = true
        }
        require(candidateHtml.size == 1) {
            when {
                candidateHtml.size > 1 -> "文件内有多张个人课表，无法判断应导入哪张"
                classroomExport -> "这是全校按教室导出的课表，不能证明本人选课；请导出个人课表"
                else -> "未找到个人课表的课程、任课教师、上课时间地点三列表头"
            }
        }
        // Existing normalization, de-duplication and preview remain authoritative.
        return NcwuHtmlXlsTimetableParser.parseHtml(candidateHtml.single())
    }

    private fun personalHeader(row: List<String>): Boolean =
        row.any { it.trim() in setOf("课程", "课程名称") } &&
            row.any { it.trim() in setOf("任课教师", "教师") } &&
            row.any { it.contains("上课时间地点") }

    private fun classroomHeader(row: List<String>): Boolean =
        row.any { it.trim() == "课程名称" } &&
            row.any { it.trim() == "周次" } &&
            row.any { it.trim() == "节次" } &&
            row.any { it.trim() in setOf("教室", "上课教室") }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val nodes = parseXml(bytes).getElementsByTagNameNS("*", "si")
        require(nodes.length <= 20000) { "Excel 共享字符串过多" }
        return (0 until nodes.length).map { nodes.item(it).textContent.orEmpty().take(MAX_CELL_LENGTH + 1) }
    }

    private fun parseSheet(bytes: ByteArray, shared: List<String>): List<List<String>> {
        val rows = parseXml(bytes).getElementsByTagNameNS("*", "row")
        require(rows.length in 1..MAX_ROWS) { "Excel 行数为空或超过限制" }
        return (0 until rows.length).map { rowIndex ->
            val cells = (rows.item(rowIndex) as Element).getElementsByTagNameNS("*", "c")
            require(cells.length <= MAX_COLUMNS) { "Excel 表格列数过多" }
            val values = mutableMapOf<Int, String>()
            for (n in 0 until cells.length) {
                val cell = cells.item(n) as Element
                val address = cellAddress.matchEntire(cell.getAttribute("r"))
                    ?: throw IllegalArgumentException("Excel 第" + (rowIndex + 1) + "行单元格地址不正确")
                val column = address.groupValues[1].fold(0) { acc, char -> acc * 26 + (char - 'A' + 1) } - 1
                require(column in 0 until MAX_COLUMNS) { "Excel 列号超过限制" }
                val text = when (cell.getAttribute("t")) {
                    "inlineStr" -> cell.getElementsByTagNameNS("*", "is").item(0)?.textContent.orEmpty()
                    "s" -> {
                        val idx = cell.getElementsByTagNameNS("*", "v").item(0)?.textContent?.toIntOrNull()
                            ?: throw IllegalArgumentException("Excel 共享字符串索引缺失")
                        require(idx in shared.indices) { "Excel 共享字符串索引无效" }
                        shared[idx]
                    }
                    "e" -> throw IllegalArgumentException("Excel 单元格含错误值，拒绝导入")
                    else -> cell.getElementsByTagNameNS("*", "v").item(0)?.textContent.orEmpty()
                }
                require(text.length <= MAX_CELL_LENGTH) { "Excel 单元格文本过长" }
                require(values.put(column, text) == null) { "Excel 单元格地址重复" }
            }
            val max = values.keys.maxOrNull() ?: -1
            (0..max).map { values[it].orEmpty() }
        }
    }

    private fun tableHtml(rows: List<List<String>>): String {
        val html = StringBuilder("<html><body><table>")
        rows.forEach { row ->
            html.append("<tr>")
            row.forEach { cell ->
                html.append("<td>").append(escape(cell)).append("</td>")
            }
            html.append("</tr>")
        }
        return html.append("</table></body></html>").toString()
    }

    private fun escape(value: String): String = value
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("\r\n", "\n").replace('\r', '\n').replace("\n", "<br>")

    /** Disallow external entities and DTD; never resolve references from XLSX XML. */
    private fun parseXml(bytes: ByteArray) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        isExpandEntityReferences = false
        isXIncludeAware = false
    }.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
}
