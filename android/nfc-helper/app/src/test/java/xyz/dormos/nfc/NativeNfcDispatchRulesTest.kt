package xyz.dormos.nfc

import org.junit.Assert.assertEquals
import org.junit.Test

class NativeNfcDispatchRulesTest {
    private val uri = "tapflow://scene/native-a1"

    @Test fun acceptsOnlyExactUriReadBackFromPhysicalTag() {
        assertEquals(
            NativeNfcDispatchDecision.VERIFIED,
            NativeNfcDispatchRules.verify(uri, PhysicalUriRead.found(uri))
        )
    }

    @Test fun distinguishesTemporaryContactLossFromUriMismatch() {
        assertEquals(
            NativeNfcDispatchDecision.IO_FAILED,
            NativeNfcDispatchRules.verify(uri, PhysicalUriRead.ioFailed())
        )
        assertEquals(
            NativeNfcDispatchDecision.URI_MISMATCH,
            NativeNfcDispatchRules.verify(uri, PhysicalUriRead.found("tapflow://scene/native-b2"))
        )
    }

    @Test fun distinguishesWrongRecordFormatFromUnreadableTag() {
        assertEquals(
            NativeNfcDispatchDecision.NOT_NDEF,
            NativeNfcDispatchRules.verify(uri, PhysicalUriRead.notNdef())
        )
        assertEquals(
            NativeNfcDispatchDecision.MISSING_URI_RECORD,
            NativeNfcDispatchRules.verify(uri, PhysicalUriRead.missingUri())
        )
    }

    @Test fun rejectsForgedOrMalformedIncomingUrisEvenIfTagLooksPlausible() {
        listOf(
            "https://tapflow//scene/native-a1",
            "tapflow://scene/native-a1?extra=true",
            "tapflow://scene/%6eative-a1",
            "tapflow://other/native-a1",
            ""
        ).forEach { invalid ->
            assertEquals(invalid, NativeNfcDispatchDecision.INVALID_INTENT_URI,
                NativeNfcDispatchRules.verify(invalid, PhysicalUriRead.found(uri)))
        }
    }

    @Test fun refusesUnverifiedTagDataAndAmbiguousUriVariants() {
        assertEquals(
            NativeNfcDispatchDecision.URI_MISMATCH,
            NativeNfcDispatchRules.verify(uri,
                PhysicalUriRead.found("tapflow://scene/native-a1#fragment"))
        )
        assertEquals(
            NativeNfcDispatchDecision.URI_MISMATCH,
            NativeNfcDispatchRules.verify(uri,
                PhysicalUriRead(PhysicalUriReadState.FOUND))
        )
    }
}
