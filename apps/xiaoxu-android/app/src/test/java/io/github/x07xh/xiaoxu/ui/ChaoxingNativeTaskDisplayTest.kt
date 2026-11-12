package io.github.x07xh.xiaoxu.ui

import io.github.x07xh.xiaoxu.data.CloudTask
import io.github.x07xh.xiaoxu.data.NativeSchoolRecord
import java.security.MessageDigest
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ChaoxingNativeTaskDisplayTest {
    private fun row(id:String,status:String,host:String="mooc1.chaoxing.com",plan:Boolean=false):NativeSchoolRecord {
        val kind=if(plan) "study_task" else "homework"
        val identity=if(plan) "plan:$id" else "work:123:456:$id"
        val label=if(plan) "学习计划状态：" else "当前网页显示的状态："
        val keyLabel=if(plan) "学习任务身份：" else "官方作业身份："
        val hash=MessageDigest.getInstance("SHA-256").digest(("chaoxing|$kind|$identity").toByteArray())
            .joinToString("") {"%02x".format(it)}
        return NativeSchoolRecord("chaoxing",kind,hash,"学习通作业$id",label+status+" · "+keyLabel+identity,host,1000L)
    }
    @Test fun officialKnownTodoAndUnknownPlanStaySeparateAndReadable() {
        val records=listOf(row("901","未提交","mooc1-1.chaoxing.com"),
            row("902","未完成","task.chaoxing.com"),
            row("903","状态未知","task.chaoxing.com",plan=true),row("904","待批阅"))
        val all=combineWorkspaceTasks(emptyList(),emptyList(),records)
        assertEquals("all newly-read tasks must survive the presentation adapter",4,all.size)
        val pending=searchWorkspaceTasks(all,"pending","",LocalDate.of(2026,11,8))
        assertEquals(setOf("学习通作业901","学习通作业902"),pending.map {it.title}.toSet())
        assertTrue(pending.all {it.category=="chaoxing"})
        assertEquals(2,facetWorkspaceTasks(pending,"chaoxing",false,LocalDate.of(2026,11,8)).size)
        assertEquals(listOf("学习通作业903"),
            searchWorkspaceTasks(all,"verify","",LocalDate.of(2026,11,8)).map {it.title})
        assertTrue(facetWorkspaceTasks(pending,"local",false,LocalDate.of(2026,11,8)).isEmpty())
        assertEquals(1,all.count {considerCompleteInTaskList(it)})
    }
    @Test fun firstPartyHostCompatibilityDoesNotAcceptSpoofedHostsOrFingerprints() {
        val real=row("905","未提交","mooc1-1.chaoxing.com")
        for(fake in listOf(real.copy(sourceHost="mooc1.chaoxing.com.evil.example"),
            real.copy(sourceHost="person@mooc1.chaoxing.com"),real.copy(fingerprint="invalid")))
            assertTrue(combineWorkspaceTasks(emptyList(),emptyList(),listOf(fake)).isEmpty())
    }
    @Test fun cloudOnlyAccountKeepsItsOwnChaoxingPendingAndCompletedGroup() {
        val cloud=listOf(
            CloudTask("pending","云端作业一","todo","homework","chaoxing",null,
                sourceExternalId="work:123:456:910",officialStatus="todo"),
            CloudTask("done","云端作业二","todo","homework","chaoxing",null,
                sourceExternalId="work:123:456:911",officialStatus="submitted"))
        val tasks=combineWorkspaceTasks(emptyList(),cloud,emptyList())
        assertEquals(2,tasks.size)
        assertTrue("cloud records belong in the same Chaoxing source",tasks.all {it.category=="chaoxing"})
        val today=LocalDate.of(2026,11,8)
        assertEquals(1,facetWorkspaceTasks(searchWorkspaceTasks(tasks,"pending","",today),"chaoxing",false,today).size)
        assertEquals(1,facetWorkspaceTasks(searchWorkspaceTasks(tasks,"done","",today),"chaoxing",false,today).size)
    }
    @Test fun cloudAndNativeStillMergeByExactOfficialIdentity() {
        val cloud=CloudTask("same","云端作业","todo","homework","chaoxing",null,
            sourceExternalId="work:123:456:912",officialStatus="todo")
        val shown=combineWorkspaceTasks(emptyList(),listOf(cloud),listOf(row("912","待批阅")))
        assertEquals(1,shown.size)
        assertEquals("cloud:same",shown.single().key)
        assertEquals("chaoxing",shown.single().category)
        assertTrue(considerCompleteInTaskList(shown.single()))
    }
}
