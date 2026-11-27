package com.tyust.course.academic.plugin

import io.github.x07xh.xiaoxu.data.NativeSchoolRecord
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecondClassServiceScreenModelTest {
    @Test fun pageShowsOnlySanitizedLocalStateAndDoesNotExposeAccountHash() {
        val secretHash = "a".repeat(64)
        val rows = listOf(
            NativeSchoolRecord("second_class", "account_binding", "current", "", secretHash,
                "dekt.ncwu.edu.cn", 1_700_000_000_000),
            NativeSchoolRecord("second_class", "credits", "score", "第1学期积分",
                "素质分：3 · 志愿服务时长：2", "dekt.ncwu.edu.cn", 1_700_000_000_000),
            NativeSchoolRecord("second_class", "activity", "activity-1", "合成活动",
                "活动时间：2026-11-27", "dekt.ncwu.edu.cn", 1_700_000_000_000)
        )
        val page = buildSecondClassServicePage(rows, "updated", 1_700_000_000_000,
            autoEnabled = true, backgroundEnabled = false)
        val json = page.toString()
        assertTrue(json.contains("第二课堂"))
        assertTrue(json.contains("本人账号绑定：已确认"))
        assertTrue(json.contains("立即同步学校只读数据"))
        assertTrue(json.contains("合成活动"))
        assertFalse(json.contains(secretHash))
    }
}
