package io.github.x07xh.xiaoxu.data

import java.net.URI
import org.junit.Assert.*
import org.junit.Test

/** Only enrich verified pending homework. Never replace a live course-list status. */
class ChaoxingPendingDeadlineSupplementTest {
    private val course = "https://mooc1.chaoxing.com/visit/stucoursemiddle?courseid=123&clazzid=456&cpi=789"
    private fun task(id: Int) =
        "https://mooc1.chaoxing.com/mooc-ans/mooc2/work/task?courseId=123&classId=456&workId=$id"

    private fun scan(
        rows: String,
        onDetail: (ChaoxingReadRequest) -> ChaoxingReadPage
    ): Pair<ChaoxingLocalRead, List<String>> {
        val requested = mutableListOf<String>()
        val transport = ChaoxingReadTransport { request ->
            val path = URI(request.url).path
            requested.add(path)
            when (path) {
                "/base" -> ChaoxingReadPage(request.url, "<script>var uid='42';</script>")
                "/pc/notice/getNoticeList" -> ChaoxingReadPage(request.url,
                    """{"status":true,"notices":{"list":[],"lastPage":1}}""")
                "/mooc2-ans/visit/courselistdata" -> ChaoxingReadPage(request.url,
                    "<div class='learnCourse'><a href='$course'><span class='course-name'>Python</span></a></div>")
                "/visit/stucoursemiddle" -> ChaoxingReadPage(request.url,
                    "<a data-url='/mooc-ans/mooc2/work/list?courseId=123&amp;classId=456'>作业</a>")
                "/mooc-ans/mooc2/work/list" -> ChaoxingReadPage(request.url,
                    "<ul class='bottomList'>$rows</ul>")
                "/mooc-ans/mooc2/work/task" -> onDetail(request)
                else -> error("unexpected route: $path")
            }
        }
        return ChaoxingLocalReader(transport).scan(2026) to requested
    }

    private fun row(id: Int, status: String = "未提交") =
        "<li data='${task(id)}'><p class='overHidden2'>实验作业$id</p><p class='status'>$status</p></li>"

    @Test fun verifiedPendingWorkMayReadDateButNotOverwriteItsListStatus() {
        val (read, requests) = scan(row(902)) { request ->
            assertEquals("GET",request.method)
            assertEquals(task(902), request.url)
            ChaoxingReadPage(request.url,
                "<p class='status'>已提交</p><div>截止时间：2026-11-19 23:59</div>")
        }
        assertTrue(read.failures.toString(),read.complete)
        val work = read.items.single { it.sourceKey == "work:123:456:902" }
        assertEquals("未提交",work.status)
        assertEquals("2026-11-19 23:59",work.deadline?.displayed)
        assertTrue(work.deadline!!.hasYear)
        assertEquals(1, requests.count { it == "/mooc-ans/mooc2/work/task" })
    }

    @Test fun optionalDetailFailureRetainsVerifiedWorkAndCompleteness() {
        val (read, requests) = scan(row(902)) { _ ->
            throw ChaoxingReadFailure("upstream_http_404")
        }
        assertTrue(read.failures.toString(),read.complete)
        val work = read.items.single { it.sourceKey == "work:123:456:902" }
        assertEquals("未提交",work.status)
        assertNull(work.deadline)
        assertEquals(1,requests.count { it == "/mooc-ans/mooc2/work/task" })
    }

    @Test fun rateLimitedOptionalDetailsStopFurtherRequestsWithoutDiscardingRows() {
        val twoRows = row(902) + row(903)
        var detailCalls = 0
        val (read, _) = scan(twoRows) { _ ->
            detailCalls++
            throw ChaoxingReadFailure("upstream_http_429")
        }
        assertTrue(read.failures.toString(),read.complete)
        assertEquals(2,read.items.count { it.kind == "homework" })
        assertEquals(1,detailCalls)
        assertTrue(read.items.filter { it.kind == "homework" }.all {
            it.status == "未提交" && it.deadline == null
        })
    }

    @Test fun actionRouteCannotBeRequestedAsOptionalDeadline() {
        val unsafe = "https://mooc1.chaoxing.com/work/submit?courseId=123&classId=456&workId=904"
        val actionRow =
            "<li data='$unsafe'><p class='overHidden2'>实验作业904</p><p class='status'>未提交</p></li>"
        val (read, requests) = scan(actionRow) { request ->
            error("unexpected work detail: ${request.url}")
        }
        assertTrue(read.failures.toString(),read.complete)
        assertEquals("未提交",read.items.single { it.kind == "homework" }.status)
        assertFalse(requests.contains("/work/submit"))
    }
}
