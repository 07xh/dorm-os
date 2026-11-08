package io.github.x07xh.xiaoxu.data

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class ChaoxingLocalReaderTest {
    private val profile = "<script>var uid='42';</script><span id='siteName'>华北水利水电大学</span>"
    private val courseUrl = "https://mooc1.chaoxing.com/visit/stucoursemiddle?courseid=123&clazzid=456&cpi=789"
    private val workUrl = "https://mooc1.chaoxing.com/mooc-ans/mooc2/work/task?courseId=123&classId=456&workId=901&enc=privateWork"
    private fun fixture(changeOwner: Boolean = false, failCourse: Boolean = false): Pair<ChaoxingLocalRead, List<String>> {
        val calls=mutableListOf<String>()
        var profiles=0
        val transport=ChaoxingReadTransport { request ->
            val u=java.net.URI(request.url)
            calls.add(request.method+" "+u.path)
            val body=when(u.path) {
                "/base" -> if(++profiles>1&&changeOwner) profile.replace("uid='42'", "uid='43'") else profile
                "/pc/notice/getNoticeList" -> {
                    assertEquals("POST",request.method)
                    assertEquals("2",request.form["type"])
                    if(request.form["lastValue"].isNullOrEmpty())
                        """{"status":true,"notices":{"list":[{"title":"Python任务","attachment":"[{\"url\":\"https://task.chaoxing.com/userStudyPlan/studyPlanSubPage?encryTaskId=privateTask\"}]"}],"lastPage":0,"lastGetId":"nextPage"}}"""
                    else """{"status":true,"notices":{"list":[],"lastPage":1}}"""
                }
                "/userStudyPlan/studyPlanSubPage" -> "<script>const iframeUrl = \"https://task.chaoxing.com/userStudyPlan/detail\";</script>"
                "/userStudyPlan/detail" -> "<script>const eTaskUserId = \"privateUser\";</script>"
                "/userStudyPlan/getGroupData" -> """{"result":true,"data":[{"encryptGroupId":"privateGroup","groupAllowStudy":true},{"encryptGroupId":"lockedGroup","groupAllowStudy":false}]}"""
                "/userStudyPlan/getPlanDataByGroupId" -> """{"result":true,"data":[{"planId":700,"name":"Python已完成","planType":4,"isFinish":1,"planAllowStudy":true},{"planId":701,"name":"Python未交","planType":4,"isFinish":0,"planAllowStudy":true,"encryptPlanId":"privatePlan"}]}"""
                "/userStudyPlan/getToStudyUrl" -> """{"result":true,"data":{"url":"$workUrl"}}"""
                "/mooc2-ans/visit/courselistdata" -> {
                    assertEquals("POST",request.method)
                    assertEquals("0",request.form["courseFolderId"])
                    "<div class='learnCourse'><a href='$courseUrl'><span class='course-name'>Python</span></a></div>"
                }
                "/visit/stucoursemiddle" -> if(failCourse) throw ChaoxingReadFailure("course_http") else
                    "<a data-url='/mooc-ans/mooc2/work/list?courseId=123&amp;classId=456&amp;cpi=789&amp;enc=privateEntry&amp;t=1700000000123'>作业</a>"
                "/mooc-ans/mooc2/work/list" -> {
                    assertTrue(request.url.contains("t=1700000000123"))
                    "<ul class='bottomList'><li data='$workUrl'><p class='overHidden2'>Python未交</p><p class='status'>待批阅</p></li></ul>"
                }
                "/mooc-ans/mooc2/work/task" -> "<p class='status'>待批阅</p>"
                else -> error("unexpected first-party read: "+u.path)
            }
            ChaoxingReadPage(request.url,body)
        }
        return ChaoxingLocalReader(transport).scan(2026) to calls
    }
    @Test fun noticeAttachmentPlansAndActualCourseEntryShareOneReader() {
        val (read,calls)=fixture()
        assertTrue(calls.indexOf("POST /pc/notice/getNoticeList")<calls.indexOf("POST /userStudyPlan/getGroupData"))
        assertTrue(calls.indexOf("POST /userStudyPlan/getGroupData")<calls.indexOf("POST /mooc2-ans/visit/courselistdata"))
        assertEquals(2,calls.count {it=="POST /pc/notice/getNoticeList"})
        assertEquals(1,read.courseCount)
        assertTrue(read.items.any {it.sourceKey=="plan:700"&&it.status=="已完成"})
        assertTrue(read.items.any {it.sourceKey=="work:123:456:901"&&it.status=="待批阅"})
        assertTrue(read.items.any {it.status=="未解锁"})
        assertEquals(64,read.accountHash.length)
        for(secret in listOf("privateWork","privateTask","privateUser","privateGroup","privatePlan","privateEntry"))
            assertFalse(secret,read.toString().contains(secret))
    }
    @Test fun failedCourseDoesNotEraseSuccessfullyExpandedNoticePlans() {
        val (read,_)=fixture(failCourse=true)
        assertTrue(read.failures.isNotEmpty())
        assertTrue(read.items.any {it.sourceKey=="plan:700"})
        assertFalse(read.complete)
    }
    @Test fun accountChangeDuringReadRejectsEntireBatch() {
        try {fixture(changeOwner=true);fail("account change must fail before Room write")}
        catch(e:ChaoxingReadFailure){assertEquals("account_changed",e.code)}
    }
    @Test fun externalAttachmentsAndExamActionsAreNotRead() {
        val links=ChaoxingLocalReader.noticeLinks(JSONObject("""{"attachment":[{"url":"https://evil.example/work/task"},{"url":"https://mooc1.chaoxing.com/examapi/intoexamorwork?workorexam=exam"},{"url":"$workUrl"}]}"""))
        assertEquals(1,links.size)
        assertEquals(workUrl,links.single().url)
    }
}
