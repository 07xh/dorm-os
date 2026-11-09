package io.github.x07xh.xiaoxu.data

import org.junit.Assert.*
import org.junit.Test
import java.net.URI

class ChaoxingPlanContinuationTest {
    private fun scan(failedGroup: Boolean = false, fatal: Boolean = false, missingFinish: Boolean = false): ChaoxingLocalRead {
        return ChaoxingLocalReader(ChaoxingReadTransport { request ->
            val uri = URI(request.url)
            val body = when (uri.path) {
                "/base" -> "<script>var uid=42;</script>"
                "/pc/notice/getNoticeList" -> """{"status":true,"notices":{"list":[{"title":"计划","attachment":[{"url":"https://task.chaoxing.com/userStudyPlan/index?taskId=20"}]}],"lastPage":1}}"""
                "/userStudyPlan/index" -> "<script>const eTaskUserId='user';</script>"
                "/userStudyPlan/getGroupData" -> """{"result":true,"data":[{"encryptGroupId":"g1","groupAllowStudy":true},{"encryptGroupId":"g2","groupAllowStudy":true}]}"""
                "/userStudyPlan/getPlanDataByGroupId" -> if (uri.query.contains("encryGroupId=g1")) {
                    if (failedGroup) """{"result":false}""" else
                    """{"result":true,"data":[{"planId":700,"name":"失效作业","planType":4,"isFinish":0,"planAllowStudy":true,"encryptPlanId":"bad"},{"planId":701,"name":"后续作业","planType":4,"isFinish":0,"planAllowStudy":true,"encryptPlanId":"good"}]}"""
                } else """{"result":true,"data":[{"planId":702,"name":"下一分组","planType":4,"isFinish":0,"planAllowStudy":true,"encryptPlanId":"next"}]}"""
                "/userStudyPlan/getToStudyUrl" -> {
                    if (uri.query.contains("encryptPlanId=bad")) {
                        if (fatal) throw ChaoxingReadFailure("login_required")
                        """{"result":false}"""
                    } else {
                        val id = if (uri.query.contains("encryptPlanId=good")) 901 else 902
                        """{"result":true,"data":{"url":"https://mooc1.chaoxing.com/work/task?courseId=123&classId=456&workId=$id"}}"""
                    }
                }
                "/work/task" -> "<p class='status'>已提交</p>"
                "/mooc2-ans/visit/courselistdata" -> "暂无课程"
                else -> throw AssertionError(uri.path)
            }
            ChaoxingReadPage(request.url, if (missingFinish) body.replace("\"isFinish\":0,", "") else body)
        }).scan(2026)
    }

    @Test fun failedDestinationDoesNotSkipLaterAssignmentsOrGroups() {
        val read = scan()
        assertEquals(setOf("work:123:456:901", "work:123:456:902"), read.items.filter {it.kind == "homework"}.map {it.sourceKey}.toSet())
        assertTrue(read.items.any {it.sourceKey == "plan:700"})
        assertEquals(setOf("plan:701", "plan:702"), read.supersededKeys)
        assertFalse(read.complete)
        assertEquals(1, read.failures.size)
    }

    @Test fun failedGroupDoesNotSkipNextGroup() {
        val read = scan(failedGroup = true)
        assertTrue(read.items.any {it.sourceKey == "work:123:456:902"})
        assertFalse(read.complete)
    }

    @Test fun missingFinishFieldDoesNotCrashTheWholeRead() {
        val read = scan(missingFinish = true)
        assertEquals("状态未知", read.items.first {it.sourceKey == "plan:700"}.status)
        assertTrue(read.items.any {it.sourceKey == "work:123:456:902"})
        assertFalse(read.complete)
    }

    @Test fun expiredSessionStillStopsTheScan() {
        try { scan(fatal = true); fail("Expected login_required") }
        catch (e: ChaoxingReadFailure) { assertEquals("login_required", e.code) }
    }
}
