package io.github.x07xh.xiaoxu.ui

import io.github.x07xh.xiaoxu.data.CloudUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Never infer a public notice fetch from unrelated owner or WeChat state. */
class MessageCenterPhaseTest {
    @Test fun noOfficialFetchStaysUnknownEvenWithOwnerSessionAndWechatCandidates() {
        val state = CloudUiState(authenticated = true, pendingWechatCount = 2)
        assertEquals(MessagePhase.NotConnected, deriveMessagePhase(state))
    }

    @Test fun verifiedEmptyRequiresOfficialFetchEvidence() {
        val state = CloudUiState(noticeFetchedAt = "2026-11-06T00:00:00Z")
        assertEquals(MessagePhase.Empty, deriveMessagePhase(state))
    }

    @Test fun blankFetchTimestampDoesNotCountAsSuccessfulOfficialRead() {
        val state = CloudUiState(noticeFetchedAt = " ")
        assertEquals(MessagePhase.NotConnected, deriveMessagePhase(state))
    }

    @Test fun publicReadFailureAndLoadingAreDistinctFromEmpty() {
        assertTrue(deriveMessagePhase(CloudUiState(publicError = "读取失败")) is MessagePhase.Failed)
        assertEquals(MessagePhase.Loading, deriveMessagePhase(CloudUiState(busy = true)))
    }
}
