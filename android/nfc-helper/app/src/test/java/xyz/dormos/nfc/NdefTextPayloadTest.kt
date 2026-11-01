package xyz.dormos.nfc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.charset.StandardCharsets

class NdefTextPayloadTest {
    @Test fun utf8Text() {
        val payload = byteArrayOf(0x02, 0x65, 0x6e) +
            "Hello NFC".toByteArray(StandardCharsets.UTF_8)
        assertEquals("Hello NFC", NdefTextPayload.decode(payload))
    }

    @Test fun badPayloads() {
        assertNull(NdefTextPayload.decode(byteArrayOf()))
        assertNull(NdefTextPayload.decode(byteArrayOf(0x06, 0x65)))
        assertNull(NdefTextPayload.decode(byteArrayOf(0x40)))
    }

    @Test fun malformedUtf8DoesNotShowReplacementCharactersAsRealTagText() {
        val invalid = byteArrayOf(0x02, 0x65, 0x6e, 0xC3.toByte(), 0x28)
        assertNull(NdefTextPayload.decode(invalid))
    }

    @Test fun malformedUtf16AndTruncatedLanguageCodeAreRejected() {
        assertNull(NdefTextPayload.decode(byteArrayOf(0x80.toByte(), 0x00)))
        assertNull(NdefTextPayload.decode(byteArrayOf(0x03, 0x7a, 0x68)))
    }

    @Test fun utf16WithBomParsesCorrectly() {
        val utf16 = "中文".toByteArray(StandardCharsets.UTF_16)
        val value = byteArrayOf(0x80.toByte()) + utf16
        assertEquals("中文", NdefTextPayload.decode(value))
    }

    @Test fun chineseUtf8() {
        val payload = byteArrayOf(0x02, 0x7a, 0x68) +
            "学习模式".toByteArray(StandardCharsets.UTF_8)
        assertEquals("学习模式", NdefTextPayload.decode(payload))
    }
}