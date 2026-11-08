package io.github.x07xh.xiaoxu.ui

import io.github.x07xh.xiaoxu.data.*
import java.security.MessageDigest
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ChaoxingNoticeAliasTest {
    private val alias = "cxn:" + "a".repeat(40)
    private fun work(id: String = "901", state: String = "已互评", peer: Boolean = false): NativeSchoolRecord {
        val key = "work:123:456:$id"
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(("chaoxing|homework|"+key).toByteArray())
            .joinToString("") { "%02x".format(it) }
        return NativeSchoolRecord("chaoxing", "homework", fingerprint, "实验报告二", 
            "当前网页显示的状态：$state · ${if(peer) "互评通知身份" else "旧通知身份"}：$alias · 官方作业身份：$key",
            "mooc1.chaoxing.com", 100)
    }
    private fun cloud() = CloudTask("old", "实验报告二", "todo", "homework", "chaoxing", null,
        sourceExternalId = alias)
    @Test fun noticeAndOfficialWorkBecomeOneCompletedRowWithoutChangingPersonalFlag() {
        val tasks = combineWorkspaceTasks(emptyList(), listOf(cloud()), listOf(work()))
        assertEquals(1, tasks.size)
        assertEquals("cloud:old", tasks.single().key)
        assertFalse(tasks.single().done)
        assertEquals("work:123:456:901", tasks.single().sourceExternalId)
        assertTrue(searchWorkspaceTasks(tasks,"pending","",LocalDate.of(2026,11,9)).isEmpty())
    }
    @Test fun submissionAloneDoesNotCompleteSeparatePeerReview() {
        val tasks = combineWorkspaceTasks(emptyList(), listOf(cloud()), listOf(work(state="待批阅",peer=true)))
        assertEquals(2, tasks.size)
        assertFalse(considerCompleteInTaskList(tasks.single {it.key=="cloud:old"}))
        val reviewed = combineWorkspaceTasks(emptyList(), listOf(cloud()), listOf(work(peer=true)))
        assertEquals(1, reviewed.size)
        assertTrue(considerCompleteInTaskList(reviewed.single()))
    }
    @Test fun conflictingAliasesAndSameTitlesCannotMergeDifferentOfficialWorks() {
        val tasks = combineWorkspaceTasks(emptyList(), listOf(cloud()), listOf(work(),work("902")))
        assertEquals(3,tasks.size)
        assertFalse(considerCompleteInTaskList(tasks.single {it.key=="cloud:old"}))
    }
    @Test fun ambiguousPeerAliasCannotUseOnlyTheCompletedWorkAsEvidence() {
        val tasks = combineWorkspaceTasks(emptyList(),listOf(cloud()),
            listOf(work(peer=true),work("902",state="未提交",peer=true)))
        assertEquals(3,tasks.size)
        assertFalse(considerCompleteInTaskList(tasks.single {it.key=="cloud:old"}))
    }
    @Test fun existingTrustedCanonicalCannotDisappearDuringAliasMerge() {
        val unverified=cloud().copy(id="a",sourceExternalId="work:123:456:901")
        val verified=unverified.copy(id="z",officialStatus="submitted")
        val tasks=combineWorkspaceTasks(emptyList(),listOf(cloud(),unverified,verified),listOf(work()))
        assertEquals(1,tasks.size)
        assertEquals("cloud:z",tasks.single().key)
        assertTrue(considerCompleteInTaskList(tasks.single()))
    }
}
