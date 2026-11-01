package xyz.dormos.nfc

import org.junit.Assert.*
import org.junit.Test

class ExecutionLogFormatterTest {
    @Test fun formatsKnownScriptsAndSkipsMalformedLines() {
        val id = "123e4567-e89b-12d3-a456-426614174000"
        val scripts = listOf(PersonalScript(id, "校园网.sh", 20))
        val result = ExecutionLogFormatter.format("0|$id|ok\nbad-row\n1|$id|failed", scripts)
        assertTrue(result.contains("校园网.sh"))
        assertTrue(result.contains("成功"))
        assertTrue(result.contains("失败"))
        assertFalse(result.contains("bad-row"))
    }

    @Test fun limitsDisplayedHistoryToEightyValidEvents() {
        val id = "123e4567-e89b-12d3-a456-426614174000"
        val raw = (1L..100L).joinToString("\n") { "$it|$id|ok" }
        val result = ExecutionLogFormatter.format(raw, emptyList())
        assertEquals(80, result.lineSequence().count())
    }
}
