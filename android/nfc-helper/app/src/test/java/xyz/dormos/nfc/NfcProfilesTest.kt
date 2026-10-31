package xyz.dormos.nfc

import org.junit.Assert.*
import org.junit.Test

class NfcProfilesTest {
    @Test fun v2TagIdsAreOpaqueAndStrictlyDecoded() {
        val id=NfcProfiles.makeId()
        assertTrue(NfcProfiles.validId(id))
        assertEquals(id,NfcProfiles.decodeId(NfcProfiles.payload(id)))
        assertNull(NfcProfiles.decodeId("v2:tag:other"))
        assertNull(NfcProfiles.decodeId("v1:ncwu"))
    }
    @Test fun onlySafeUserChosenHttpsCanBeOpened() {
        assertTrue(NfcProfiles.isSafeHttpsUrl("https://example.org/learn"))
        assertFalse(NfcProfiles.isSafeHttpsUrl("http://example.org"))
        assertFalse(NfcProfiles.isSafeHttpsUrl("javascript:alert(1)"))
        assertFalse(NfcProfiles.isSafeHttpsUrl("https://user:password@example.org/"))
        assertFalse(NfcProfiles.isSafeHttpsUrl("https://example.org/?access_token=secret"))
        assertFalse(NfcProfiles.isSafeHttpsUrl("https://example.org/?access%5Ftoken=secret"))
        assertFalse(NfcProfiles.isSafeHttpsUrl("https://example.org/#access_token=secret"))
        assertFalse(NfcProfiles.isSafeHttpsUrl("https://example.org/path#route?client_secret=value"))
        assertFalse(NfcProfiles.isSafeHttpsUrl("https://example.org/?id_token=secret"))
        assertFalse(NfcProfiles.isSafeHttpsUrl("https://example.org/?invalid%QQ=value"))
        assertTrue(NfcProfiles.isSafeHttpsUrl("https://example.org/path?course=math&week=2"))
        assertFalse(NfcProfiles.isSafeHttpsUrl("https://localhost/"))
        assertFalse(NfcProfiles.isSafeHttpsUrl("https://example.org/#" + "x".repeat(121)))
    }
    @Test fun sceneProfilesMustStayWithinFourKnownScenes() {
        val id=NfcProfiles.makeId()
        assertTrue(NfcProfiles.validProfile(NfcProfile(id,"学习",NfcProfiles.DORM_SCENE,"study")))
        assertFalse(NfcProfiles.validProfile(NfcProfile(id,"恶意",NfcProfiles.DORM_SCENE,"unknown")))
        assertEquals("https://dorm-os-dk9.pages.dev/?dormScene=study#scenes",NfcProfiles.sceneUrl("study"))
        assertNull(NfcProfiles.sceneUrl("bad"))
    }
}
