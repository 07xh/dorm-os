package io.github.x07xh.xiaoxu.data

import java.net.URI
import org.junit.Assert.*
import org.junit.Test

class ChaoxingPaginationTest {
    private fun row(id: Int, status: String="已完成") =
        "<li data='https://mooc1.chaoxing.com/work/dowork?courseId=123&amp;classId=456&amp;workId=$id'><p class='overHidden2'>作业$id</p><p class='status'>$status</p></li>"
    private fun page(n:Int, rows:String, pages:Int=1) = "<ul class='bottomList'>$rows</ul>" +
        "<ul id='page'>"+(1..pages).joinToString("") {"<li class='${if(it==n) "xl-active" else ""}'>$it</li>"}+"</ul>"
    private fun read(stripQuery:Boolean=false,respond:(Int)->String):ChaoxingLocalRead {
        val course="https://mooc1.chaoxing.com/visit/stucoursemiddle?courseid=123&clazzid=456&cpi=789"
        return ChaoxingLocalReader(ChaoxingReadTransport {req ->
            val uri=URI(req.url)
            val html=when(uri.path) {
                "/base" -> "<script>var uid=42;</script>"
                "/pc/notice/getNoticeList" -> """{"status":true,"notices":{"list":[],"lastPage":1}}"""
                "/mooc2-ans/visit/courselistdata" -> "<a href='$course'><span class='course-name'>Python</span></a>"
                "/visit/stucoursemiddle" -> "<a data-url='/mooc2/work/list?courseId=123&amp;classId=456&amp;enc=privateSignature'>作业</a>"
                "/mooc2/work/list" -> {
                    assertTrue("Paging must retain the published signature",uri.query.contains("enc=privateSignature"))
                    val number=Regex("(?:^|&)pageNum=(\\d+)").find(uri.query)?.groupValues?.get(1)?.toInt()?:1
                    respond(number)
                }
                else -> throw AssertionError(uri.path)
            }
            // Some official redirects remove the query from the response URL.
            ChaoxingReadPage(if(stripQuery&&uri.path=="/mooc2/work/list") "https://mooc1.chaoxing.com/mooc2/work/list" else req.url,html)
        }).scan(2026)
    }
    @Test fun identicalRowsOnOnePageDoNotStopLaterWork() {
        val result=read {page(1,row(901)+row(901)+row(902))}
        assertTrue(result.failures.toString(),result.complete)
        assertEquals(2,result.items.count {it.kind=="homework"})
    }
    @Test fun hiddenAlternativeListDoesNotConflictWithOfficialWorkPanel() {
        val result=read {page(1,row(901))+"<ul class='other'>"+row(901,"未提交")+"</ul>"}
        assertTrue(result.failures.toString(),result.complete)
        assertEquals("已完成",result.items.single {it.kind=="homework"}.status)
    }
    @Test fun realSecondPageUsesOriginalSignedEntry() {
        val result=read(stripQuery=true) {n->page(n,row(900+n),2)}
        assertTrue(result.failures.toString(),result.complete)
        assertEquals(2,result.items.count {it.kind=="homework"})
    }
    @Test fun repeatedFirstPageIsNotReportedAsComplete() {
        val result=read {page(1,row(901),2)}
        assertFalse(result.complete)
        assertTrue(result.failures.any {it.contains("work_page_mismatch")})
    }
    @Test fun overlapDoesNotHideNewWorkOnLaterPage() {
        val result=read {n->page(n,if(n==1) row(901) else row(901)+row(902),2)}
        assertFalse(result.complete)
        assertTrue(result.items.any {it.sourceKey=="work:123:456:902"})
        assertTrue(result.failures.any {it.contains("work_page_overlap")})
    }
    @Test fun conflictingSamePageRowsStayUnknownAndDoNotStopLaterWork() {
        val result=read {page(1,row(901)+row(901,"未提交")+row(902))}
        assertFalse(result.complete)
        assertEquals("状态未知",result.items.single {it.sourceKey=="work:123:456:901"}.status)
        assertTrue(result.items.any {it.sourceKey=="work:123:456:902"})
    }
}
