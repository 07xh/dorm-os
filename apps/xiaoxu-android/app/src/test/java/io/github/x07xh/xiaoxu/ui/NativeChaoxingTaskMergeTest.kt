package io.github.x07xh.xiaoxu.ui

import io.github.x07xh.xiaoxu.data.CloudTask
import io.github.x07xh.xiaoxu.data.NativeSchoolRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class NativeChaoxingTaskMergeTest {
    private val id = "work:123:456:987"
    private fun record(status: String, title: String = "实验报告一"): NativeSchoolRecord {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(("chaoxing|homework|" + id).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return NativeSchoolRecord(
            "chaoxing", "homework", hash, title,
            "当前网页显示的状态：$status · 官方作业身份：$id",
            "mooc1.chaoxing.com", 1720000000000L)
    }
    private fun cloud(source: String? = id, state: String? = null) =
        CloudTask("cloud-1", "实验报告一", "todo", "homework", "chaoxing",
            null, sourceExternalId = source, officialStatus = state)

    @Test fun sourceIdentitySeparatesLiveHomeworkFromHistoricalNotice() {
        val official = combineWorkspaceTasks(emptyList(), listOf(cloud()), listOf(record("已完成"))).single()
        assertEquals("官方作业身份：$id", chaoxingTaskIdentityHint(official))
        val notice = cloud(source="cxn:" + "a".repeat(40))
        val historic = combineWorkspaceTasks(emptyList(), listOf(notice), emptyList()).single()
        assertTrue(chaoxingTaskIdentityHint(historic)!!.contains("尚未关联当前官方作业"))
        assertEquals("unknown", historic.officialStatus ?: "unknown")
        assertFalse(considerCompleteInTaskList(historic))
    }

    @Test fun completedOfficialLocalWorkShowsInExistingTaskCounts() {
        val tasks = combineWorkspaceTasks(emptyList(), emptyList(), listOf(record("待批阅")))
        assertEquals(1, tasks.size)
        assertEquals("native", tasks.single().storage)
        assertEquals(id, tasks.single().sourceExternalId)
        assertTrue(considerCompleteInTaskList(tasks.single()))
    }

    @Test fun sameExactWorkIdDoesNotCreateDuplicateCloudTask() {
        val tasks = combineWorkspaceTasks(emptyList(), listOf(cloud()), listOf(record("已完成")))
        assertEquals(1, tasks.size)
        assertEquals("cloud:cloud-1", tasks.single().key)
        assertEquals("submitted", tasks.single().officialStatus)
        assertTrue(considerCompleteInTaskList(tasks.single()))
    }

    @Test fun knownLocalOfficialStatusReplacesRetiredCloudSnapshot() {
        val tasks = combineWorkspaceTasks(emptyList(), listOf(cloud(state = "returned")),
            listOf(record("已完成")))
        assertEquals(1, tasks.size)
        assertEquals("submitted", tasks.single().officialStatus)
        assertTrue(considerCompleteInTaskList(tasks.single()))
    }

    @Test fun realPlanIdCreatesOneLocalOfficialTaskWithCompletedState() {
        val identity = "plan:987654"
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(("chaoxing|study_task|" + identity).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val plan = NativeSchoolRecord("chaoxing", "study_task", hash,
            "第四次作业", "学习计划状态：待批阅 · 学习任务身份：$identity",
            "task.chaoxing.com", 1720000000000L)
        val shown = combineWorkspaceTasks(emptyList(), emptyList(), listOf(plan))
        assertEquals(1, shown.size)
        assertEquals("native", shown.single().storage)
        assertEquals("chaoxing", shown.single().category)
        assertEquals(identity, shown.single().sourceExternalId)
        assertEquals("待批阅", shown.single().officialStatusRaw)
        assertTrue(considerCompleteInTaskList(shown.single()))
        assertEquals(0, combineWorkspaceTasks(emptyList(), emptyList(),
            listOf(plan.copy(fingerprint = "spoofed"))).size)
    }

    @Test fun neverMergeSameTitleWithDifferentOrUnverifiedSourceId() {
        val records = listOf(record("已完成"))
        val cloud = listOf(cloud(source = "work:123:456:998"))
        val tasks = combineWorkspaceTasks(emptyList(), cloud, records)
        assertEquals(2, tasks.size)
        assertTrue(tasks.any { it.storage == "native" && considerCompleteInTaskList(it) })
        assertTrue(tasks.any { it.storage == "cloud" && !considerCompleteInTaskList(it) })
        val fake = records.single().copy(fingerprint = "not-a-valid-source")
        assertEquals(0, combineWorkspaceTasks(emptyList(), emptyList(), listOf(fake)).size)
    }
    @Test fun returnedOfficialLocalWorkCannotDisappearBehindCloudCompletion() {
        val tasks=combineWorkspaceTasks(emptyList(),listOf(cloud(state="submitted")),listOf(record("已退回")))
        assertEquals(1,tasks.size)
        assertEquals("returned",tasks.single().officialStatus)
        assertFalse(considerCompleteInTaskList(tasks.single()))
    }
    @Test fun unknownLocalStatusDoesNotEraseKnownCloudStatus() {
        val tasks=combineWorkspaceTasks(emptyList(),listOf(cloud(state="submitted")),listOf(record("状态未知")))
        assertEquals("submitted",tasks.single().officialStatus)
        assertTrue(considerCompleteInTaskList(tasks.single()))
    }
}
