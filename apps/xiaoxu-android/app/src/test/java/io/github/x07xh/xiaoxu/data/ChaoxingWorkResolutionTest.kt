package io.github.x07xh.xiaoxu.data

import java.net.URI
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ChaoxingWorkResolutionTest {
    private val work = "https://mooc1.chaoxing.com/work/dowork?courseId=123&classId=456&workId=901234"
    private val gateway = "https://mooc1.chaoxing.com/examapi/intoexamorwork?taskId=55"

    private fun scan(link: String, response: (ChaoxingReadRequest) -> ChaoxingReadPage): ChaoxingLocalRead {
        val notice = JSONObject().put("title", "作业通知").put("tag", "courseId123")
            .put("content", "作业名称：实验报告一")
            .put("extendParam", JSONObject().put("cparams", """{"funConfig":{"content":{"key":"901234"}}}"""))
            .put("attachment", org.json.JSONArray().put(JSONObject().put("url", link)))
        return ChaoxingLocalReader(ChaoxingReadTransport { request ->
            when (URI(request.url).path) {
                "/base" -> ChaoxingReadPage(request.url, "<script>var uid=42;</script>")
                "/pc/notice/getNoticeList" -> ChaoxingReadPage(request.url,
                    """{"status":true,"notices":{"list":[$notice],"lastPage":1}}""")
                "/mooc2-ans/visit/courselistdata" -> ChaoxingReadPage(request.url, "暂无课程")
                else -> response(request)
            }
        }).scan(2026)
    }

    @Test fun plainOfficialSubmissionAndCompletionTextIsRead() {
        for (status in listOf("已提交", "已完成", "已批改")) {
            val read = scan(work) { ChaoxingReadPage(it.url, "<div>$status</div>") }
            assertTrue(read.complete)
            assertEquals(status, read.items.single().status)
        }
    }

    @Test fun redirectUsesFinalWorkIdentityAndBindsOriginalNotice() {
        val read = scan(gateway) { ChaoxingReadPage(work, "<p class='status'>已完成</p>") }
        assertTrue(read.failures.toString(), read.complete)
        val row = read.items.single()
        assertEquals("homework", row.kind)
        assertEquals("work:123:456:901234", row.sourceKey)
        assertEquals(1, row.noticeAliases.size)
        assertTrue(read.supersededKeys.single().startsWith("study:"))
    }

    @Test fun workEntryAnchorIsFollowedBeforeReadingStatus() {
        val calls = mutableListOf<String>()
        val read = scan(gateway) {
            calls.add(it.url)
            if (it.url == gateway) ChaoxingReadPage(it.url, "<a href='$work'>查看作业</a>")
            else ChaoxingReadPage(it.url, "<div>已提交</div>")
        }
        assertEquals(listOf(gateway, work), calls)
        assertEquals("work:123:456:901234", read.items.single().sourceKey)
        assertEquals("已提交", read.items.single().status)
    }

    @Test fun conflictingRedirectCannotBorrowAnotherWorksCompletion() {
        val read = scan(work) { ChaoxingReadPage(work.replace("901234", "901235"), "<p class='status'>已完成</p>") }
        assertFalse(read.complete)
        assertTrue(read.items.isEmpty())
        assertTrue(read.supersededKeys.isEmpty())
    }

    @Test fun questionTextCannotPretendThatTheWorkWasSubmitted() {
        val read = scan(work) { ChaoxingReadPage(it.url, "<form id='submitForm'><div>待批阅</div><a href='$work'>作业题目链接</a></form>") }
        assertEquals("状态未知", read.items.single().status)
    }
    @Test fun signedCourseEntryKeepsItsOfficialHostPathAndTimestamp() {
        val course="https://mooc1.chaoxing.com/visit/stucoursemiddle?courseid=123&clazzid=456&cpi=789"
        val read=ChaoxingLocalReader(ChaoxingReadTransport { request ->
            val uri=URI(request.url)
            val body=when(uri.path) {
                "/base" -> "<script>var uid=42;</script>"
                "/pc/notice/getNoticeList" -> """{"status":true,"notices":{"list":[],"lastPage":1}}"""
                "/mooc2-ans/visit/courselistdata" -> "<a href='$course'><span class='course-name'>Python</span></a>"
                "/visit/stucoursemiddle" -> "<input id='courseid' value='123'><input id='clazzid' value='456'>" +
                    "<input id='cpi' value='789'><input id='workEnc' value='workSignature'>" +
                    "<input id='t' value='1794230000000'>" +
                    "<a data-url='https://mooc1-1.chaoxing.com/mooc-ans/mooc2/work/list?officialMode=student'>作业</a>"
                else -> {
                    assertEquals("mooc1-1.chaoxing.com",uri.host)
                    assertEquals("/mooc-ans/mooc2/work/list",uri.path)
                    assertTrue(uri.query.contains("t=1794230000000"))
                    assertTrue(uri.query.contains("officialMode=student"))
                    "<ul class='bottomList'><li data='$work'><p class='overHidden2'>实验报告一</p><p class='status'>已完成</p></li></ul>"
                }
            }
            ChaoxingReadPage(request.url,body)
        }).scan(2026)
        assertTrue(read.complete)
        assertEquals("已完成",read.items.single {it.kind=="homework"}.status)
    }

    @Test fun statuslessCourseRowReadsItsOfficialDetail() {
        val course="https://mooc1.chaoxing.com/visit/stucoursemiddle?courseid=123&clazzid=456&cpi=789"
        val read=ChaoxingLocalReader(ChaoxingReadTransport { request ->
            val body=when(URI(request.url).path) {
                "/base" -> "<script>var uid=42;</script>"
                "/pc/notice/getNoticeList" -> """{"status":true,"notices":{"list":[],"lastPage":1}}"""
                "/mooc2-ans/visit/courselistdata" -> "<a href='$course'><span class='course-name'>Python</span></a>"
                "/visit/stucoursemiddle" -> "<ul class='bottomList'><li data='$work'><p class='overHidden2'>实验报告一</p></li></ul>"
                "/work/dowork" -> "<div>已提交</div>"
                else -> throw AssertionError(request.url)
            }
            ChaoxingReadPage(request.url,body)
        }).scan(2026)
        assertTrue(read.complete)
        assertEquals("已提交",read.items.single {it.kind=="homework"}.status)
    }

    @Test fun resolvedPlanChildReplacesOnlyItsExactPlanAndGatewayAliases() {
        val plan="https://task.chaoxing.com/userStudyPlan/index?taskId=20"
        val read=scan(plan) { request ->
            val body=when(URI(request.url).path) {
                "/userStudyPlan/index" -> "<script>const eTaskUserId='user';</script>"
                "/userStudyPlan/getGroupData" -> """{"result":true,"data":[{"encryptGroupId":"g","groupAllowStudy":true}]}"""
                "/userStudyPlan/getPlanDataByGroupId" -> """{"result":true,"data":[{"planId":700,"name":"实验报告一","planType":4,"planAllowStudy":true,"isFinish":1,"encryptPlanId":"p"}]}"""
                "/userStudyPlan/getToStudyUrl" -> """{"result":true,"data":{"url":"$gateway"}}"""
                else -> "<div>已完成</div>"
            }
            ChaoxingReadPage(if(request.url==gateway) work else request.url,body)
        }
        assertTrue(read.failures.toString(),read.complete)
        assertEquals(listOf("work:123:456:901234"),read.items.map {it.sourceKey})
        assertTrue("plan:700" in read.supersededKeys)
        assertEquals(1,read.items.single().noticeAliases.size)
    }

    @Test fun nonWorkRedirectCannotBeAcceptedAsWorkCompletion() {
        val read=scan(work) { ChaoxingReadPage("https://mooc1.chaoxing.com/exam/test?courseId=123&classId=456&workId=901234", "<div>已完成</div>") }
        assertFalse(read.complete)
        assertTrue(read.items.isEmpty())
        assertTrue(read.failures.any {it.contains("work_redirect_exam")})
        assertFalse(read.failures.toString().contains("courseId"))
    }

    @Test fun courseLandingIsDistinguishedFromAnExamRedirect() {
        val read=scan(work) { ChaoxingReadPage("https://mooc1.chaoxing.com/mycourse/studentcourse?enc=privateSignature", "课程首页") }
        assertFalse(read.complete)
        assertTrue(read.failures.any {it.contains("work_redirect_course")})
        assertFalse(read.failures.toString().contains("privateSignature"))
    }

    @Test fun typedExamNoticeWithRealMessageIdNeverEntersTheHomeworkGateway() {
        val course="https://mooc1.chaoxing.com/visit/stucoursemiddle?courseid=123&clazzid=456&cpi=789"
        val entry="https://mooc1.chaoxing.com/examapi/intoexamorwork?courseId=123&classId=456&workId=901234"
        val notice=JSONObject().put("idCode","abc-123_message").put("title","考试通知")
            .put("tag","courseId123")
            .put("content","试卷名称：期末考试\n考试时间：2026-11-20 08:00 至 2026-11-21 23:59")
            .put("attachment",org.json.JSONArray().put(JSONObject().put("title","课程").put("url",entry)))
        val result=ChaoxingLocalReader(ChaoxingReadTransport { req ->
            val body=when(URI(req.url).path) {
                "/base" -> "<script>var uid=42;</script>"
                "/pc/notice/getNoticeList" -> """{"status":true,"notices":{"list":[$notice],"lastPage":1}}"""
                "/mooc2-ans/visit/courselistdata" -> "<a href='$course'><span class='course-name'>课程</span></a>"
                "/visit/stucoursemiddle" -> "<ul class='bottomList'><li data='$entry'><p class='overHidden2'>期末考试</p></li></ul>"
                else -> throw AssertionError("Must not enter an exam attempt: "+URI(req.url).path)
            }
            ChaoxingReadPage(req.url,body)
        }).scan(2026)
        assertTrue(result.failures.toString(),result.complete)
        assertFalse(result.items.any {it.kind=="homework"})
        val exam=result.items.single {it.kind=="exam_notice"}
        assertEquals("examnotice:"+ChaoxingLocalReader.hash("chaoxing-exam-notice:abc-123_message"),exam.sourceKey)
        assertEquals(setOf("cxn:"+ChaoxingLocalReader.hash("exam|123|考试：期末考试|2026-11-20").take(40)),exam.noticeAliases)
        assertEquals("2026-11-21 23:59",exam.deadline?.displayed)
    }
    @Test fun realUuidExamNoticeWithoutTimeStillHasAnIdentity() {
        val notice=JSONObject().put("uuid","message-uuid").put("title","考试通知")
            .put("tag","courseId123").put("content","试卷名称：期末考试")
        val result=ChaoxingLocalReader(ChaoxingReadTransport { req ->
            ChaoxingReadPage(req.url,when(URI(req.url).path) {
                "/base" -> "<script>var uid=42;</script>"
                "/pc/notice/getNoticeList" -> """{"status":true,"notices":{"list":[$notice],"lastPage":1}}"""
                "/mooc2-ans/visit/courselistdata" -> "暂无课程"
                else -> throw AssertionError("unexpected request")
            })
        }).scan(2026)
        assertTrue(result.complete)
        val exam=result.items.single {it.kind=="exam_notice"}
        assertNull(exam.deadline)
        assertEquals(setOf("cxn:"+ChaoxingLocalReader.hash("exam|123|考试：期末考试|unknown").take(40)),exam.noticeAliases)
    }

}
