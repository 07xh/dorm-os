package io.github.x07xh.xiaoxu.data
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
class ChaoxingReaderAliasReviewTest {
 private val work="https://mooc1.chaoxing.com/mooc-ans/mooc2/work/task?courseId=123&classId=456&workId=901&enc=secret"
 @Test fun finishedPlanAndCourseWorkHaveOneOfficialIdentity() {
  var destinations=0
  val reader=ChaoxingLocalReader(ChaoxingReadTransport { req ->
   val path=java.net.URI(req.url).path
   val text=when(path) {
    "/base" -> "<script>var uid=42;</script>"
    "/pc/notice/getNoticeList" -> """{"status":true,"notices":{"list":[{"attachment":[{"url":"https://task.chaoxing.com/userStudyPlan/studyPlanSubPage?taskId=20"}]}],"lastPage":1}}"""
    "/userStudyPlan/studyPlanSubPage" -> "<script>const eTaskUserId=\"userSecret\";</script>"
    "/userStudyPlan/getGroupData" -> """{"result":true,"data":[{"groupAllowStudy":true,"encryptGroupId":"groupSecret"}]}"""
    "/userStudyPlan/getPlanDataByGroupId" -> """{"result":true,"data":[{"planId":700,"name":"同一作业","planType":4,"isFinish":1,"planAllowStudy":true,"encryptPlanId":"planSecret"}]}"""
    "/userStudyPlan/getToStudyUrl" -> { destinations++; """{"result":true,"data":{"url":"$work"}}""" }
    "/mooc-ans/mooc2/work/task" -> "<p class='status'>已完成</p>"
    "/mooc2-ans/visit/courselistdata" -> "<a href='https://mooc1.chaoxing.com/visit/stucoursemiddle?courseid=123&clazzid=456&cpi=789'><span class='course-name'>Python</span></a>"
    "/visit/stucoursemiddle" -> "<ul class='bottomList'><li data='$work'><p class='overHidden2'>同一作业</p><p class='status'>待批阅</p></li></ul>"
    else -> throw AssertionError(path)
   }
   ChaoxingReadPage(req.url,text)
  })
  val read=reader.scan(2026)
  assertEquals(1,destinations)
  assertEquals(listOf("work:123:456:901"),read.items.filter {it.kind!="course"}.map {it.sourceKey})
  // The detail proves completion; generic list "待批阅" must not downgrade it.
  assertEquals("已完成",read.items.first {it.kind=="homework"}.status)
  assertEquals(setOf("plan:700"),read.supersededKeys)
 }
 private class MemoryDao:NativeSchoolRecordDao {
  val rows=mutableListOf<NativeSchoolRecord>()
  override fun observe():Flow<List<NativeSchoolRecord>> = flowOf(rows.toList())
  override suspend fun snapshot()=rows.toList()
  override suspend fun snapshotKind(provider:String,kind:String)=rows.filter {it.provider==provider&&it.kind==kind}
  override suspend fun deleteKind(provider:String,kind:String) {rows.removeAll {it.provider==provider&&it.kind==kind}}
  override suspend fun insertAll(rows:List<NativeSchoolRecord>) {for(value in rows) {this.rows.removeAll {it.provider==value.provider&&it.kind==value.kind&&it.fingerprint==value.fingerprint};this.rows.add(value)}}
 }
 private fun record(key:String,kind:String="study_task")=NativeSchoolRecord("chaoxing",kind,ChaoxingLocalReader.hash("chaoxing|$kind|$key"),key,"","task.chaoxing.com",1L)
 @Test fun partialResolvedBatchDeletesOnlyProvenOldPlanAlias()=runBlocking {
  val dao=MemoryDao();val owner="a".repeat(64)
  dao.mergeChaoxingRead(listOf(record("plan:700"),record("plan:800")),owner)
  dao.mergeChaoxingRead(listOf(record("work:123:456:901","homework")),owner,false,setOf("plan:700"))
  assertEquals(listOf("plan:800"),dao.snapshotKind("chaoxing","study_task").map {it.title})
  assertEquals(1,dao.snapshotKind("chaoxing","homework").size)
 }
 @Test fun changedAccountNeverRetainsPreviousOwnersTasks()=runBlocking {
  val dao=MemoryDao()
  dao.mergeChaoxingRead(listOf(record("plan:700")),"a".repeat(64))
  dao.mergeChaoxingRead(listOf(record("plan:800")),"b".repeat(64))
  assertEquals(listOf("plan:800"),dao.snapshotKind("chaoxing","study_task").map {it.title})
 }
}
