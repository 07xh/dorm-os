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

    @Test fun chineseUtf8() {
        val payload = byteArrayOf(0x02, 0x7a, 0x68) +
            "学习模式".toByteArray(StandardCharsets.UTF_8)
        assertEquals("学习模式", NdefTextPayload.decode(payload))
    }
}