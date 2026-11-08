package io.github.x07xh.xiaoxu.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NativeChaoxingVisibleStatusTest {
    private fun row(details: String, kind: String = "homework",
                    host: String = "mooc1.chaoxing.com") =
        NativeSchoolRecord("chaoxing", kind, "test-id", "实验报告一",
            details, host, 1234L)

    @Test fun officialSubmittedAndReviewStatesCountAsCompleted() {
        for (status in listOf("已完成", "已提交", "待批阅",
            "待評閱", "已达标", "提交成功", "已互评", "互评完成", "互評完成")) {
            assertEquals(status, "submitted",
                nativeChaoxingVisibleStatus(row("当前网页显示的状态：$status")))
        }
        for (status in listOf("已批阅", "已评分", "已批改")) {
            assertEquals(status, "graded",
                nativeChaoxingVisibleStatus(row("状态：$status")))
        }
    }

    @Test fun negativeAndReturnedStatesAreNeverMarkedComplete() {
        for (status in listOf("未提交", "未完成", "未达标", "待完成")) {
            assertEquals(status, "todo",
                nativeChaoxingVisibleStatus(row("任务状态：$status")))
        }
        assertEquals("returned",
            nativeChaoxingVisibleStatus(row("审核状态：退回重做")))
    }

    @Test fun studyPlansAreReadOnlyButCanShowTheirOfficialStatus() {
        assertEquals("submitted", nativeChaoxingVisibleStatus(
            row("学习计划状态：待批阅", "study_task", "task.chaoxing.com")))
        assertEquals("todo", nativeChaoxingVisibleStatus(
            row("学习计划状态：未完成", "study_task", "task.chaoxing.com")))
    }

    @Test fun exactSchoolWorkTupleRequiresMatchingFingerprint() {
        val identity = "work:123:456:987"
        val details = "当前网页显示的状态：已完成 · 官方作业身份：$identity"
        val hash = java.security.MessageDigest.getInstance("SHA-256")
            .digest(("chaoxing|homework|" + identity).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val record = row(details).copy(fingerprint = hash)
        assertEquals(identity, nativeChaoxingWorkSourceId(record))
        assertNull(nativeChaoxingWorkSourceId(record.copy(fingerprint = "wrong")))
        assertNull(nativeChaoxingWorkSourceId(record.copy(sourceHost = "other.example")))
        assertNull(nativeChaoxingWorkSourceId(record.copy(
            details = "当前网页显示的状态：已完成 · 官方作业身份：work:123:456:bad")))
    }

    @Test fun planIdentityIsStableAndBoundToOfficialCard() {
        val identity = "plan:987654"
        val value = "学习计划状态：待批阅 · 学习任务身份：$identity"
        val hash = java.security.MessageDigest.getInstance("SHA-256")
            .digest(("chaoxing|study_task|" + identity).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val plan = row(value, "study_task", "task.chaoxing.com")
            .copy(fingerprint = hash)
        assertEquals(identity, nativeChaoxingPlanSourceId(plan))
        assertEquals("submitted", nativeChaoxingVisibleStatus(plan))
        assertNull(nativeChaoxingPlanSourceId(plan.copy(fingerprint = "wrong")))
        assertNull(nativeChaoxingPlanSourceId(plan.copy(sourceHost = "other.example")))
        assertNull(nativeChaoxingPlanSourceId(plan.copy(
            details = "学习计划状态：待批阅 · 学习任务身份：plan:abc")))
    }

    @Test fun ignoreUnrelatedWordsAndUntrustedOrigin() {
        assertNull(nativeChaoxingVisibleStatus(row("作业标题：已完成的程序")))
        assertNull(nativeChaoxingVisibleStatus(row(
            "当前网页显示的状态：已完成", host = "untrusted.example")))
        assertNull(nativeChaoxingVisibleStatus(row(
            "当前网页显示的状态：已完成", kind = "course")))
        assertEquals("unknown", nativeChaoxingVisibleStatus(row(
            "当前网页显示的状态：暂未明确")))
    }
}
