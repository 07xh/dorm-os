package io.github.x07xh.xiaoxu.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalTaskBreakdownTest {
    @Test fun acceptsOnlyExplicitValidStepsAndStripsNumberedPrefixes() {
        assertEquals(listOf("整理资料", "写报告", "检查提交"),
            parseLocalTaskBreakdown("1. 整理资料\n2、写报告\n• 检查提交"))
        assertEquals(listOf("A", "B"), parseLocalTaskBreakdown("- A\n* B"))
        assertNull(parseLocalTaskBreakdown("只有一步"))
        assertNull(parseLocalTaskBreakdown("A\nA"))
        assertNull(parseLocalTaskBreakdown("A\na"))
        assertNull(parseLocalTaskBreakdown("A\n" + "X".repeat(121)))
        assertNull(parseLocalTaskBreakdown((1..13).joinToString("\n") { "步骤 $it" }))
        assertNull(parseLocalTaskBreakdown("X".repeat(2401) + "\n下一步"))
    }


}
