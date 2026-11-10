package io.github.x07xh.xiaoxu.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset

class NcwuHtmlXlsTimetableParserTest {
    private fun sample(timetable: String) = """
        <html><body><table>
          <tr><th>课程</th><th>任课教师</th><th>上课时间地点</th></tr>
          <tr><td>[ABC]高等数学</td><td>[01]张老师</td><td>$timetable</td></tr>
        </table></body></html>
    """.trimIndent()

    @Test fun htmlXlsParsesMultipleSessionsAndOddEvenWeeks() {
        val input = sample("1-8单周一[3-4][江淮校区] 教学楼101<br>2-8双周二[6-7]实验楼202")
        val items = NcwuHtmlXlsTimetableParser.parse(input.toByteArray())
        assertEquals(2, items.size)
        assertEquals("高等数学", items[0].title)
        assertEquals("张老师", items[0].teacher)
        assertEquals(1, items[0].weekday)
        assertEquals(3, items[0].startSection)
        assertEquals(4, items[0].endSection)
        assertEquals(listOf(1, 3, 5, 7), items[0].weeks)
        assertEquals(listOf(2, 4, 6, 8), items[1].weeks)
    }

    @Test fun identicalScheduleRowsAreDeduplicatedByExistingStore() {
        val row = "<tr><td>Python程序设计</td><td>李老师</td><td>1-12周三[6-7] 机房</td></tr>"
        val html = "<table><tr><td>课程</td><td>教师</td><td>上课时间地点</td></tr>$row$row</table>"
        assertEquals(1, NcwuHtmlXlsTimetableParser.parseHtml(html).size)
    }

    @Test fun incompleteTimeFailsInsteadOfInventingWeeks() {
        val failure = assertThrows(IllegalArgumentException::class.java) {
            NcwuHtmlXlsTimetableParser.parseHtml(sample("待安排"))
        }
        assertTrue(failure.message.orEmpty().contains("无法识别"))
    }

    @Test fun multipleCandidateTablesFailRatherThanChoosingOne() {
        val html = sample("1-10周一[1-2]A101")
        assertThrows(IllegalArgumentException::class.java) {
            NcwuHtmlXlsTimetableParser.parseHtml(html + html)
        }
    }

    @Test fun legacyBinaryAndZipBasedExcelAreNeverMistakenForHtml() {
        val ole = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0, 0)
        val zip = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0, 0)
        assertThrows(IllegalArgumentException::class.java) {
            NcwuHtmlXlsTimetableParser.parse(ole)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NcwuHtmlXlsTimetableParser.parse(zip)
        }
    }

    @Test fun gb18030EncodedHtmlExportKeepsChineseFields() {
        val bytes = sample("1-10周五[1-2]教学楼101")
            .replace("<html>", "<html><head><meta charset='GB18030'></head>")
            .toByteArray(Charset.forName("GB18030"))
        val parsed = NcwuHtmlXlsTimetableParser.parse(bytes)
        assertEquals("高等数学", parsed.single().title)
    }
}
