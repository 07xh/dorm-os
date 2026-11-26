package com.tyust.course.academic.plugin

import io.github.x07xh.xiaoxu.data.AcademicNotice
import io.github.x07xh.xiaoxu.data.CloudTask
import io.github.x07xh.xiaoxu.data.CloudUiState
import io.github.x07xh.xiaoxu.data.WechatCandidate
import org.junit.Assert.*
import org.junit.Test

class CloudServiceScreenTest {
    @Test fun messagePageUsesTheExistingServicePageBlockContract() {
        val page = buildCloudMessagePage(CloudUiState())
        assertEquals(BundledServiceProviders.CLOUD_MESSAGES_PAGE, page.getString("pageId"))
        val blocks = PluginJson.objects(page.getJSONArray("blocks"))
        assertTrue(blocks.any { it.getString("id") == "message_overview" && it.getString("type") == "profile" })
        assertTrue(blocks.any { it.getString("id") == "cloud_login" && it.getString("type") == "form" })
        assertTrue(blocks.any { it.getString("id") == "school_notices" } == false)
    }

    @Test fun wechatReviewAndCompletionActionsAreBoundToTheOwnerTaskIds() {
        val state = CloudUiState(
            authenticated = true,
            ownerId = "owner-only",
            tasksFresh = true,
            connectionsFresh = true,
            notices = listOf(AcademicNotice("n1", "学校通知标题", "教学", "2026-11-27", "https://www2.ncwu.edu.cn/jiaowu/info/100/1001.htm")),
            tasks = listOf(CloudTask("task_12345678", "本人微信任务", "todo", "todo", "wechat", null, revision = 4)),
            wechatCandidates = listOf(WechatCandidate(
                "candidate_12345678", "deadline", "待核对候选",
                "来自本人群聊的候选内容", "本人群聊", "2026-11-27T08:00:00Z",
                "date", "2026-11-28"
            ))
        )
        val blocks = PluginJson.objects(buildCloudMessagePage(state).getJSONArray("blocks"))
        val task = blocks.single { it.getString("id") == "wechat_tasks" }
            .getJSONArray("items").getJSONObject(0)
        assertEquals("task_12345678", task.getJSONObject("action").getJSONObject("params").getString("id"))
        assertEquals("4", task.getJSONObject("action").getJSONObject("params").getString("revision"))

        val candidateActions = blocks.single { it.getString("id") == "candidate_actions_0" }
            .getJSONArray("items")
        assertEquals("wechat.candidate.accept", candidateActions.getJSONObject(0)
            .getJSONObject("action").getString("actionId"))
        assertEquals("candidate_12345678", candidateActions.getJSONObject(0)
            .getJSONObject("action").getJSONObject("params").getString("id"))
        assertEquals("wechat.candidate.dismiss", candidateActions.getJSONObject(1)
            .getJSONObject("action").getString("actionId"))
    }

    @Test fun schoolAndPersonalSecondClassProvenanceIsNeverMerged() {
        val page = buildCloudMessagePage(CloudUiState(
            authenticated = true,
            secondClass = io.github.x07xh.xiaoxu.data.SecondClassSnapshot(
                status = "ready",
                provenance = "user_import",
                lastSuccessAt = "2026-11-27T00:00:00Z",
                activities = emptyList(),
                officialScore = null,
                personalScore = io.github.x07xh.xiaoxu.data.SecondClassScoreBreakdown(20.0, 3.0)
            )
        ))
        val profile = PluginJson.objects(page.getJSONArray("blocks"))
            .single { it.getString("id") == "second_class_snapshot" }
        assertEquals("本人导入数据", profile.getString("badge"))
        assertFalse(profile.getJSONArray("details").toString().contains("学校汇总质量分"))
    }
}
