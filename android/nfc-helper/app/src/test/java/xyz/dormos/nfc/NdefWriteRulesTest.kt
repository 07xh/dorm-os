package xyz.dormos.nfc

import org.junit.Assert.*
import org.junit.Test

class NdefWriteRulesTest {
    @Test fun chineseTextIsAccepted() {
        val value = NdefWriteRules.prepare(NdefWriteKind.TEXT, "  学习模式  ")
        assertNull(value.error)
        assertEquals("学习模式", value.draft?.content)
        assertEquals(12, value.draft?.utf8Bytes)
    }

    @Test fun plainDomainUsesHttps() {
        val value = NdefWriteRules.prepare(NdefWriteKind.WEB_URL, "example.com/path")
        assertEquals("https://example.com/path", value.draft?.content)
    }

    @Test fun campusHttpIsAllowed() {
        val value = NdefWriteRules.prepare(NdefWriteKind.WEB_URL, "http://example.com/")
        assertEquals("http://example.com/", value.draft?.content)
    }

    @Test fun invalidUrlsAreRejected() {
        listOf("javascript:alert(1)", "file:///sdcard/test",
            "https://user:password@example.com",
            "https://", "example.com/line break"
        ).forEach {
            assertNull(it, NdefWriteRules.prepare(NdefWriteKind.WEB_URL, it).draft)
        }
    }

    @Test fun emptyAndOversizedContentAreRejected() {
        assertNull(NdefWriteRules.prepare(NdefWriteKind.TEXT, "  ").draft)
        assertNull(
            NdefWriteRules.prepare(
                NdefWriteKind.TEXT, "字".repeat(700)
            ).draft
        )
    }

    @Test fun cannotOverwriteDormAutomationTag() {
        assertNotNull(NdefWriteRules.replacementError(
            1, listOf(NfcProfiles.MIME), true
        ))
        assertNotNull(NdefWriteRules.replacementError(
            3, listOf("application/vnd.dormos.future"), true
        ))
    }

    @Test fun normalExistingTagRequiresExplicitConsent() {
        assertNotNull(NdefWriteRules.replacementError(
            1, emptyList(), false
        ))
        assertNull(NdefWriteRules.replacementError(
            1, emptyList(), true
        ))
        assertNull(NdefWriteRules.replacementError(
            0, emptyList(), false
        ))
    }
}
