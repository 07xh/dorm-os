package io.github.x07xh.xiaoxu

import io.github.x07xh.xiaoxu.data.NativeSchoolRecord
import org.junit.Assert.*
import org.junit.Test

class SecondClassActivityPointsTest {
    private fun row(details: String, kind: String = "activity", host: String = "dekt.ncwu.edu.cn") =
        NativeSchoolRecord("second_class", kind, "test", "活动", details, host, 1L)

    @Test fun usesOnlyExplicitSettledPoints() {
        assertEquals("1.5", secondClassActivityPoints(row("报名：已报名 · 结算：已结算\n已结算素质分：1.5")))
        assertEquals("0", secondClassActivityPoints(row("报名：已报名 · 结算：已结算\n已结算素质分：0")))
        assertNull(secondClassActivityPoints(row("报名：已报名 · 结算：已结算")))
        assertNull(secondClassActivityPoints(row("报名：已报名 · 结算：待结算\n已结算素质分：2")))
    }

    @Test fun rejectsNonActivityOrAmbiguousScores() {
        val detail = "报名：已报名 · 结算：已结算\n已结算素质分：3"
        assertNull(secondClassActivityPoints(row(detail, "catalog_activity")))
        assertNull(secondClassActivityPoints(row(detail, host = "example.com")))
        assertNull(secondClassActivityPoints(row(detail + "\n已结算素质分：4")))
        assertNull(secondClassActivityPoints(row("报名：已报名 · 结算：已结算\n已结算素质分：100001")))
    }
}
