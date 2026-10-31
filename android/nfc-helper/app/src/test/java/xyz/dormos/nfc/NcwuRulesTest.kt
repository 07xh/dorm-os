package xyz.dormos.nfc

import org.junit.Assert.*
import org.junit.Test

class NcwuRulesTest {
    @Test fun nfcMustMatchExactPrivateMimeAndVersion() {
        assertTrue(NcwuRules.isExpectedTag(NcwuRules.MIME, NcwuRules.COMMAND))
        assertFalse(NcwuRules.isExpectedTag("text/plain", "v1:ncwu"))
        assertFalse(NcwuRules.isExpectedTag(NcwuRules.MIME, "v2:ncwu"))
        assertFalse(NcwuRules.isExpectedTag(NcwuRules.MIME, "v1:other"))
        assertFalse(NcwuRules.isExpectedTag(null, null))
    }

    @Test fun wifiMustBeExactlyNcwuWithSingleNetwork() {
        assertTrue(NcwuRules.isSingleTargetWifi("Ncwu", 1))
        assertFalse(NcwuRules.isSingleTargetWifi("Ncwu", 0))
        assertFalse(NcwuRules.isSingleTargetWifi("Ncwu", 2))
        assertFalse(NcwuRules.isSingleTargetWifi("Ncwu-Guest", 1))
        assertFalse(NcwuRules.isSingleTargetWifi("<unknown ssid>", 1))
        assertFalse(NcwuRules.isSingleTargetWifi(null, 1))
    }

    @Test fun captiveRedirectIsNeverHttpsSuccess() {
        assertTrue(NcwuRules.isHttpsSuccess(200))
        assertTrue(NcwuRules.isHttpsSuccess(204))
        assertFalse(NcwuRules.isHttpsSuccess(301))
        assertFalse(NcwuRules.isHttpsSuccess(302))
        assertFalse(NcwuRules.isHttpsSuccess(307))
        assertFalse(NcwuRules.isHttpsSuccess(403))
        assertFalse(NcwuRules.isHttpsSuccess(500))
    }
}
