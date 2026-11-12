package io.github.x07xh.xiaoxu.data

import java.net.URI
import org.junit.Assert.*
import org.junit.Test

/** A second read-only catalog path must not silently borrow a school ID. */
class ChaoxingCourseCatalogFallbackTest {
    private val course = "https://mooc1.chaoxing.com/visit/stucoursemiddle?courseid=123&clazzid=456&cpi=789"
    private val work = "https://mooc1.chaoxing.com/work/dowork?courseId=123&classId=456&workId=901234"

    @Test fun signedInAccountFidRecoversCoursesWhenPostLayoutIsEmpty() {
        var postCalls = 0
        var getCalls = 0
        val result = ChaoxingLocalReader(ChaoxingReadTransport { request ->
            val uri = URI(request.url)
            val content = when (uri.path) {
                "/base" -> "<script>var uid = 42; var fid = '927';</script>"
                "/pc/notice/getNoticeList" -> """{"status":true,"notices":{"list":[],"lastPage":1}}"""
                "/mooc2-ans/visit/courselistdata" -> when (request.method) {
                    "POST" -> { postCalls++; "<div>暂无课程</div>" }
                    "GET" -> {
                        getCalls++
                        val params = uri.rawQuery.orEmpty().split('&').associate {
                            val parts = it.split('=', limit = 2)
                            java.net.URLDecoder.decode(parts[0], "UTF-8") to
                                java.net.URLDecoder.decode(parts.getOrElse(1) { "" }, "UTF-8")
                        }
                        assertEquals("927", params["courseFid"])
                        assertEquals("1", params["courseType"])
                        assertEquals("0", params["courseTeacherid"])
                        "<a href='$course'><span class='course-name'>Python</span></a>"
                    }
                    else -> error("unexpected HTTP method")
                }
                "/visit/stucoursemiddle" ->
                    "<a data-url='/mooc2/work/list?courseId=123&amp;classId=456'>作业</a>"
                "/mooc2/work/list" ->
                    "<ul class='bottomList'><li data='$work'>" +
                    "<p class='overHidden2'>实验报告</p><p class='status'>已完成</p></li></ul>"
                else -> error("unexpected official path: " + uri.path)
            }
            ChaoxingReadPage(request.url, content)
        }).scan(2026)
        assertTrue(result.failures.toString(), result.complete)
        assertEquals(1, postCalls)
        assertEquals(1, getCalls)
        assertEquals(1, result.courseCount)
        assertEquals("已完成", result.items.single { it.kind == "homework" }.status)
        assertFalse(result.toString().contains("927"))
    }

    @Test fun absentAccountFidNeverUsesAHardcodedSchoolIdentifier() {
        var getCalls = 0
        val result = ChaoxingLocalReader(ChaoxingReadTransport { request ->
            val uri = URI(request.url)
            val content = when (uri.path) {
                "/base" -> "<script>var uid = 42;</script>"
                "/pc/notice/getNoticeList" -> """{"status":true,"notices":{"list":[],"lastPage":1}}"""
                "/mooc2-ans/visit/courselistdata" -> {
                    if (request.method == "GET") getCalls++
                    "<div>暂无课程</div>"
                }
                else -> error("unexpected official path: " + uri.path)
            }
            ChaoxingReadPage(request.url, content)
        }).scan(2026)
        assertTrue(result.failures.toString(), result.complete)
        assertEquals(0, getCalls)
        assertEquals(0, result.courseCount)
    }

    @Test fun orphanPostCourseLabelWithoutAUsableLinkFallsBackToGet() {
        var getCalls = 0
        val result = ChaoxingLocalReader(ChaoxingReadTransport { request ->
            val uri = URI(request.url)
            val content = when (uri.path) {
                "/base" -> "<script>var uid = 42; var fid = '927';</script>"
                "/pc/notice/getNoticeList" -> """{"status":true,"notices":{"list":[],"lastPage":1}}"""
                "/mooc2-ans/visit/courselistdata" -> if (request.method == "POST") {
                    // A course-name label exists, but its parent has no course entry.
                    "<div><span class='course-name'>旧布局占位</span></div>"
                } else {
                    getCalls++
                    "<a href='$course'><span class='course-name'>Python</span></a>"
                }
                "/visit/stucoursemiddle" ->
                    "<a data-url='/mooc2/work/list?courseId=123&amp;classId=456'>作业</a>"
                "/mooc2/work/list" ->
                    "<ul class='bottomList'><li data='$work'>" +
                    "<p class='overHidden2'>实验报告</p><p class='status'>已完成</p></li></ul>"
                else -> error("unexpected path: " + uri.path)
            }
            ChaoxingReadPage(request.url, content)
        }).scan(2026)
        assertTrue(result.failures.toString(), result.complete)
        assertEquals(1, getCalls)
        assertEquals(1, result.courseCount)
        assertEquals("已完成", result.items.single { it.kind == "homework" }.status)
    }

    @Test fun validPostCourseDoesNotIssueRedundantCatalogGet() {
        var getCalls = 0
        val result = ChaoxingLocalReader(ChaoxingReadTransport { request ->
            val uri = URI(request.url)
            val content = when (uri.path) {
                "/base" -> "<script>var uid = 42; var fid = '927';</script>"
                "/pc/notice/getNoticeList" -> """{"status":true,"notices":{"list":[],"lastPage":1}}"""
                "/mooc2-ans/visit/courselistdata" -> {
                    if (request.method == "GET") getCalls++
                    "<a href='$course'><span class='course-name'>Python</span></a>"
                }
                "/visit/stucoursemiddle" ->
                    "<a data-url='/mooc2/work/list?courseId=123&amp;classId=456'>作业</a>"
                "/mooc2/work/list" ->
                    "<ul class='bottomList'><li data='$work'>" +
                    "<p class='overHidden2'>实验报告</p><p class='status'>待批阅</p></li></ul>"
                else -> error("unexpected path: " + uri.path)
            }
            ChaoxingReadPage(request.url, content)
        }).scan(2026)
        assertTrue(result.failures.toString(), result.complete)
        assertEquals(0, getCalls)
        assertEquals(1, result.courseCount)
        assertEquals("待批阅", result.items.single { it.kind == "homework" }.status)
    }

    @Test fun conflictingAccountFidsNeverSelectArbitrarySchool() {
        var getCalls = 0
        val result = ChaoxingLocalReader(ChaoxingReadTransport { request ->
            val uri = URI(request.url)
            val content = when (uri.path) {
                "/base" -> "<script>var uid = 42; var fid = '927'; var fid = '928';</script>"
                "/pc/notice/getNoticeList" -> """{"status":true,"notices":{"list":[],"lastPage":1}}"""
                "/mooc2-ans/visit/courselistdata" -> {
                    if (request.method == "GET") getCalls++
                    "<div>暂无课程</div>"
                }
                else -> error("unexpected path: " + uri.path)
            }
            ChaoxingReadPage(request.url, content)
        }).scan(2026)
        assertTrue(result.failures.toString(), result.complete)
        assertEquals(0, getCalls)
        assertEquals(0, result.courseCount)
    }

}
