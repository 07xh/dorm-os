package io.github.x07xh.xiaoxu.data
import org.junit.Assert.*
import org.junit.Test
class ChaoxingReaderReviewEdgeTest {
 @Test fun unquotedOfficialUidRemainsAValidAccount() {
  val transport=ChaoxingReadTransport { request ->
   val text=when(java.net.URI(request.url).path) {
    "/base" -> "<script>var uid=42;</script>"
    "/pc/notice/getNoticeList" -> """{"status":true,"notices":{"list":[],"lastPage":1}}"""
    "/mooc2-ans/visit/courselistdata" -> "<p>暂无课程</p>"
    else -> throw AssertionError("unexpected path")
   }
   ChaoxingReadPage(request.url,text)
  }
  val result=ChaoxingLocalReader(transport).scan(2026)
  assertEquals(64,result.accountHash.length)
 }
 @Test fun malformedOptionalNoticeDoesNotAbortCourseReading() {
  val transport=ChaoxingReadTransport { request ->
   val text=when(java.net.URI(request.url).path) {
    "/base" -> "<script>var uid='42';</script>"
    "/pc/notice/getNoticeList" -> "<html>upstream unavailable</html>"
    "/mooc2-ans/visit/courselistdata" -> "<p>暂无课程</p>"
    else -> throw AssertionError("unexpected path")
   }
   ChaoxingReadPage(request.url,text)
  }
  val result=ChaoxingLocalReader(transport).scan(2026)
  assertEquals(1,result.failures.size)
  assertTrue(result.failures[0].contains("invalid_response"))
 }
}
