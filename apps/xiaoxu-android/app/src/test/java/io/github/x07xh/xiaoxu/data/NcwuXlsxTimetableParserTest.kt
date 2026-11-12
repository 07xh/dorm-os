package io.github.x07xh.xiaoxu.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class NcwuXlsxTimetableParserTest {
    private fun zip(sheets: List<String>, sharedStrings: String? = null,
                    workbook: Boolean = true): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { z ->
            fun add(path: String, content: String) {
                z.putNextEntry(ZipEntry(path))
                z.write(content.toByteArray(StandardCharsets.UTF_8))
                z.closeEntry()
            }
            add("[Content_Types].xml", "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"/>")
            if (workbook) add("xl/workbook.xml",
                "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"/>")
            sharedStrings?.let { add("xl/sharedStrings.xml", it) }
            sheets.forEachIndexed { index, value ->
                add("xl/worksheets/sheet" + (index + 1) + ".xml", value)
            }
        }
        return bytes.toByteArray()
    }

    private fun worksheet(vararg rows: String): String =
        "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
            "<sheetData>" + rows.joinToString("") + "</sheetData></worksheet>"

    private fun row(n: Int, vararg columns: String): String =
        "<row r=\"$n\">" + columns.joinToString("") + "</row>"

    private fun inline(ref: String, content: String): String =
        "<c r=\"$ref\" t=\"inlineStr\"><is><t>$content</t></is></c>"

    private fun shared(ref: String, index: Int): String =
        "<c r=\"$ref\" t=\"s\"><v>$index</v></c>"

    @Test fun personalXlsxSharedStringsAndParityUseExistingNormalization() {
        val strings = listOf("课程名称", "任课教师", "上课时间地点", "高等数学",
            "张老师", "1-9单周一[1-2]教学楼101\n2-10双周二[3-4]实验楼202")
        val sharedXml = "<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
            strings.joinToString("") { "<si><t>" + it + "</t></si>" } + "</sst>"
        val file = zip(listOf(worksheet(
            row(1, inline("A1", "个人课表")),
            row(2, shared("A2", 0), shared("B2", 1), shared("C2", 2)),
            row(3, shared("A3", 3), shared("B3", 4), shared("C3", 5))
        )), sharedXml)
        val courses = NcwuXlsxTimetableParser.parse(file)
        assertEquals(2, courses.size)
        assertEquals("高等数学", courses[0].title)
        assertEquals("张老师", courses[0].teacher)
        assertEquals("教学楼101", courses[0].location)
        assertEquals(listOf(1, 3, 5, 7, 9), courses[0].weeks)
        assertEquals(listOf(2, 4, 6, 8, 10), courses[1].weeks)
        assertEquals(2, courses[1].weekday)
    }

    @Test fun inlineStringsKeepUnknownClassroomAndSkipNonPersonalSheet() {
        val cover = worksheet(row(1, inline("A1", "个人信息汇总")))
        val personal = worksheet(
            row(1, inline("A1", "课程"), inline("B1", "教师"), inline("C1", "上课时间地点")),
            row(2, inline("A2", "工程制图"), inline("B2", "李老师"),
                inline("C2", "6-18周五[6-7]"))
        )
        val results = NcwuXlsxTimetableParser.parse(zip(listOf(cover, personal)))
        assertEquals(1, results.size)
        assertEquals("工程制图", results.single().title)
        assertEquals("", results.single().location)
        assertEquals((6..18).toList(), results.single().weeks)
    }

    @Test fun rejectUniversityWideClassroomExportRatherThanFabricatingPersonalEnrolment() {
        val campus = worksheet(
            row(1, inline("A1", "课程名称"), inline("B1", "教师"),
                inline("C1", "周次"), inline("D1", "节次"), inline("E1", "教室")),
            row(2, inline("A2", "大学物理"), inline("B2", "王老师"),
                inline("C2", "1-16"), inline("D2", "周三 3-4"), inline("E2", "A101"))
        )
        val ex = assertThrows(IllegalArgumentException::class.java) {
            NcwuXlsxTimetableParser.parse(zip(listOf(campus)))
        }
        assertTrue(ex.message.orEmpty().contains("全校按教室"))
    }

    @Test fun rejectTwoPersonalSheetsWithoutSilentlyChoosingOne() {
        val one = worksheet(
            row(1, inline("A1", "课程"), inline("B1", "教师"), inline("C1", "上课时间地点")),
            row(2, inline("A2", "英语"), inline("B2", "陈老师"),
                inline("C2", "1-8周一[1-2]A101")))
        assertThrows(IllegalArgumentException::class.java) {
            NcwuXlsxTimetableParser.parse(zip(listOf(one, one)))
        }
    }

    @Test fun rejectNonXlsxZipAndDecompressionBomb() {
        assertThrows(IllegalArgumentException::class.java) {
            NcwuXlsxTimetableParser.parse(zip(listOf(worksheet(
                row(1, inline("A1", "课程")))), workbook = false))
        }
        val huge = "X".repeat(3_100_000)
        assertThrows(IllegalArgumentException::class.java) {
            NcwuXlsxTimetableParser.parse(zip(listOf(worksheet(row(1, inline("A1", huge))))))
        }
    }

    @Test fun rejectXlsxWithMaliciousDoctype() {
        val xml = "<!DOCTYPE foo [<!ENTITY file SYSTEM \"file:///etc/passwd\">]>" +
            worksheet(row(1, inline("A1", "&file;")))
        assertThrows(Exception::class.java) {
            NcwuXlsxTimetableParser.parse(zip(listOf(xml)))
        }
    }

    @Test fun xlsxUsesSameQingguoParenthesizedParityNormalizerAsHtmlXls() {
        val personal = worksheet(
            row(1, inline("A1", "课程"), inline("B1", "教师"), inline("C1", "上课时间地点")),
            row(2, inline("A2", "工程制图"), inline("B2", "李老师"),
                inline("C2", "1-11周(单) 五[3-4] 三教楼106(172)"))
        )
        val result = NcwuXlsxTimetableParser.parse(zip(listOf(personal)))
        assertEquals(listOf(1, 3, 5, 7, 9, 11), result.single().weeks)
        assertEquals(5, result.single().weekday)
        assertEquals("三教楼106(172)", result.single().location)
    }

    @Test fun rejectMissingWeeksWithoutModifyingOldTimetable() {
        val personal = worksheet(
            row(1, inline("A1", "课程"), inline("B1", "教师"), inline("C1", "上课时间地点")),
            row(2, inline("A2", "电路原理"), inline("B2", "周老师"),
                inline("C2", "待通知"))
        )
        assertThrows(IllegalArgumentException::class.java) {
            NcwuXlsxTimetableParser.parse(zip(listOf(personal)))
        }
    }
}
