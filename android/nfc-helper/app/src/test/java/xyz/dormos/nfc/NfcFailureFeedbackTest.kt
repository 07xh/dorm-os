package xyz.dormos.nfc

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class NfcFailureFeedbackTest {
    @Test fun nonNdefLabelsDistinguishFormatableAndUnsupportedTags() {
        assertTrue(NfcFailureFeedback.noNdef(true).contains("尚未格式化"))
        assertTrue(NfcFailureFeedback.noNdef(false).contains("不支持"))
    }

    @Test fun readIoFailureRecommendsRescanWithoutClaimingWrite() {
        val message = NfcFailureFeedback.readFailure(IOException("private detail"))
        assertTrue(message.contains("移开标签"))
        assertFalse(message.contains("private detail"))
    }

    @Test fun uncertainWriteNeverClaimsDefiniteFailureOrSuccess() {
        val message = NfcFailureFeedback.writeFailure(IOException("credential"), true)
        assertTrue(message.contains("可能已发生"))
        assertTrue(message.contains("读取标签"))
        assertFalse(message.contains("credential"))
        assertFalse(message.contains("写入成功"))
    }

    @Test fun neverIssuedWriteDoesNotSuggestReadbackSucceeded() {
        val message = NfcFailureFeedback.writeFailure(IOException("secret"), false)
        assertTrue(message.contains("没有确认写入成功"))
        assertFalse(message.contains("secret"))
    }

    @Test fun insufficientCapacityNamesActualSizes() {
        val message = NfcFailureFeedback.insufficientCapacity(100, 48)
        assertTrue(message.contains("100 B"))
        assertTrue(message.contains("48 B"))
    }

    @Test fun verificationMessagesAvoidFalseSuccess() {
        assertTrue(NfcFailureFeedback.readbackMismatch().contains("结果不可信"))
        assertTrue(NfcFailureFeedback.readbackUnavailable().contains("无法重新读取"))
    }
}
