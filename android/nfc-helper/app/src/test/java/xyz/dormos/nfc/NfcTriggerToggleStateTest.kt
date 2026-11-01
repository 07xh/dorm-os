package xyz.dormos.nfc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NfcTriggerToggleStateTest {
    @Test fun enableShowsVisualPendingBeforeRootConfirmation() {
        val requested = NfcTriggerToggleState().request(true)
        assertTrue(requested.pending)
        assertTrue(requested.displayed)
        assertFalse(requested.confirmed)
        val confirmed = requested.complete(true)
        assertTrue(confirmed.confirmed)
        assertFalse(confirmed.pending)
    }

    @Test fun failedRootActionRollsBackToConfirmedValue() {
        val failedOn = NfcTriggerToggleState().request(true).complete(false)
        assertFalse(failedOn.displayed)
        assertFalse(failedOn.confirmed)
        val failedOff = NfcTriggerToggleState(confirmed = true).request(false).complete(false)
        assertTrue(failedOff.displayed)
        assertTrue(failedOff.confirmed)
    }

    @Test fun duplicateToggleAndServerRefreshCannotOverridePending() {
        val pending = NfcTriggerToggleState().request(true)
        assertEquals(pending, pending.request(false))
        assertEquals(pending, pending.withConfirmed(false))
    }

    @Test fun identicalTargetDoesNotCauseNewRootRequest() {
        val enabled = NfcTriggerToggleState(confirmed = true)
        assertEquals(enabled, enabled.request(true))
        assertEquals(enabled, enabled.complete(true))
    }
}
