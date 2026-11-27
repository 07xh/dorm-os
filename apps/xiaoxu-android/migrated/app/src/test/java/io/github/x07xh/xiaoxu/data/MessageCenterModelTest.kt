package io.github.x07xh.xiaoxu.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageCenterModelTest {
    private val notices = listOf(
        AcademicNotice("n1", "期末考试安排", "exam", "2026-11-27", "https://www2.ncwu.edu.cn/jiaowu/info/100/1001.htm"),
        AcademicNotice("n2", "关于教学工作的通知", "notice", "2026-11-26", "https://www2.ncwu.edu.cn/jiaowu/info/100/1002.htm")
    )

    @Test fun noticeSearchAndSourceFiltersNeverMixWechatCandidatesIntoOfficialNotices() {
        assertEquals(2, filterNotices(notices, "", MSG_FILTER_ALL).size)
        assertEquals(listOf("n2"), filterNotices(notices, "", MSG_FILTER_NOTICE).map { it.id })
        assertEquals(listOf("n1"), filterNotices(notices, "", MSG_FILTER_EXAM).map { it.id })
        assertTrue(filterNotices(notices, "", MSG_FILTER_WECHAT).isEmpty())
        assertEquals(listOf("n1"), filterNotices(notices, "期末", MSG_FILTER_ALL).map { it.id })
        assertEquals(listOf("n2"), filterNotices(notices, "通知公告", MSG_FILTER_ALL).map { it.id })
    }

    @Test fun officialEmptyRequiresThePublicNoticeFetchTimestamp() {
        assertEquals(MessagePhase.NotConnected,
            deriveMessagePhase(CloudUiState(authenticated = true, pendingWechatCount = 2)))
        assertEquals(MessagePhase.NotConnected,
            deriveMessagePhase(CloudUiState(noticeFetchedAt = " ")))
        assertEquals(MessagePhase.Empty,
            deriveMessagePhase(CloudUiState(noticeFetchedAt = "2026-11-27T00:00:00Z")))
    }

    @Test fun loadingFailureAndContentHaveDistinctPhases() {
        assertEquals(MessagePhase.Loading, deriveMessagePhase(CloudUiState(busy = true)))
        assertTrue(deriveMessagePhase(CloudUiState(publicError = "读取失败")) is MessagePhase.Failed)
        assertEquals(MessagePhase.Content(notices),
            deriveMessagePhase(CloudUiState(notices = notices)))
    }

    @Test fun newerConfirmedWechatRevisionWinsOverAnOlderOverlappingRead() {
        val older = CloudTask("task_12345678", "微信群任务", "todo", "todo", "wechat", null, revision = 3)
        val confirmed = older.copy(status = "done", revision = 4)
        assertEquals(confirmed, preferConfirmedWechatRevisions(listOf(older), listOf(confirmed)).single())
        val newerServer = older.copy(status = "todo", revision = 5)
        assertEquals(newerServer, preferConfirmedWechatRevisions(listOf(newerServer), listOf(confirmed)).single())
        assertEquals(older, preferConfirmedWechatRevisions(listOf(older), emptyList()).single())
    }

    @Test fun unknownAndStaleWechatCountsAreNotPresentedAsZeroOrFresh() {
        assertEquals("未连接校序云端，无法读取待审核数量",
            pendingWechatLabel(0, authenticated = false))
        assertEquals("本次未成功读取待审核数量",
            pendingWechatLabel(null, authenticated = true))
        assertEquals("待审核候选 4 条（快照，本次读取失败）",
            pendingWechatLabel(4, authenticated = true, wechatError = "stale"))
        assertEquals("至少 30 条待审核候选（接口单次只返回前 30 条）",
            pendingWechatLabel(30, authenticated = true))
        assertEquals("无待审核", pendingWechatShortLabel(0, authenticated = true))
        assertEquals("4（快照）", pendingWechatShortLabel(4, authenticated = true, wechatError = "stale"))
    }
}
