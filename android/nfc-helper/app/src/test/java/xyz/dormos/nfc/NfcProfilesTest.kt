package xyz.dormos.nfc

import org.junit.Assert.*
import org.junit.Test

class NfcProfilesTest {
    @Test fun uuidPayloadRoundTripsAndKeepsV2Compatibility() {
        val id = NfcProfiles.makeId()
        assertTrue(NfcProfiles.validId(id))
        assertEquals(id, NfcProfiles.decodeId(NfcProfiles.payload(id)))
        assertEquals(id, NfcProfiles.decodeId("v3:scene:$id"))
    }

    @Test fun malformedPayloadsAreRejected() {
        assertNull(NfcProfiles.decodeId(null))
        assertNull(NfcProfiles.decodeId("v1:ncwu"))
        assertNull(NfcProfiles.decodeId("v2:tag:bad"))
        assertNull(NfcProfiles.decodeId("v2:tag:123e4567-e89b-12d3-a456-426614174000;id"))
        assertFalse(NfcProfiles.validId(""))
    }

    @Test fun legacyNfcMimeAndPayloadRemainStable() {
        assertEquals("application/vnd.dormos.ncwu", NfcProfiles.MIME)
        assertEquals("v1:ncwu", NfcProfiles.LEGACY_NCWU)
    }
}
