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

    @Test fun malformedAndLegacyCampusPayloadsAreRejected() {
        assertNull(NfcProfiles.decodeId(null))
        assertNull(NfcProfiles.decodeId("v1:ncwu"))
        assertNull(NfcProfiles.decodeId("v2:tag:bad"))
        assertNull(NfcProfiles.decodeId("v2:tag:123e4567-e89b-12d3-a456-426614174000;id"))
        assertFalse(NfcProfiles.validId(""))
    }

    @Test fun failedOrCanceledWriteCannotCreateBinding() {
        val tag = "123e4567-e89b-12d3-a456-426614174000"
        val script = "123e4567-e89b-12d3-a456-426614174001"
        assertTrue(NfcProfiles.bindingMayCommit(tag, script, tag, true))
        assertFalse(NfcProfiles.bindingMayCommit(tag, script, tag, false))
        assertFalse(NfcProfiles.bindingMayCommit(tag, script, script, true))
        assertFalse(NfcProfiles.bindingMayCommit(tag, "../invalid", tag, true))
        assertFalse(NfcProfiles.bindingMayCommit(null, script, tag, true))
    }

    @Test fun newMimeAndOldMimeAreBothRecognized() {
        assertEquals("application/vnd.dormos.nfc-script", NfcProfiles.MIME)
        assertTrue(NfcProfiles.isSupportedMime("application/vnd.dormos.nfc-script"))
        assertTrue(NfcProfiles.isSupportedMime("application/vnd.dormos.ncwu"))
        assertFalse(NfcProfiles.isSupportedMime("text/plain"))
    }
}
