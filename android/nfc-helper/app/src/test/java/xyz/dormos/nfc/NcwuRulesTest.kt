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

    @Test fun copiedDiagnosticsDoNotContainOtherWifiNames() {
        val result=NcwuRules.redactDiagnostic("当前 Wi-Fi 为 私人家庭网络， 不是 Ncwu；未发送认证请求。")
        assertTrue(result.contains("当前 Wi-Fi 不是 Ncwu"))
        assertFalse(result.contains("私人家庭网络"))
        assertTrue(NcwuRules.redactDiagnostic("Ncwu Wi-Fi HTTPS 测试成功（HTTP 200）").contains("HTTP 200"))
    }

    @Test fun networkBoundSsidTakesPriorityOverGlobalWifiInfo() {
        assertEquals("Ncwu", NcwuRules.selectSsid("\"Ncwu\"", "\"Other\""))
        assertEquals("Other", NcwuRules.selectSsid("\"Other\"", "\"Ncwu\""))
        assertEquals("Ncwu", NcwuRules.selectSsid("<unknown ssid>", "\"Ncwu\""))
        assertNull(NcwuRules.selectSsid("<unknown ssid>", " "))
        assertTrue(NcwuRules.isSingleTargetWifi("\"Ncwu\"", 1))
        assertFalse(NcwuRules.isSingleTargetWifi("Ncwu-Guest", 1))
        assertFalse(NcwuRules.isSingleTargetWifi("Ncwu", 2))
    }

    @Test fun networkProbeDecisionIsFailClosedForVpnMobileAndCaptivePortal() {
        assertEquals(
            NcwuRules.ProbeDecision.STOP_NOT_WIFI,
            NcwuRules.probeDecision(false, true, true)
        )
        assertEquals(
            NcwuRules.ProbeDecision.STOP_NO_INTERNET,
            NcwuRules.probeDecision(true, false, true)
        )
        assertEquals(
            NcwuRules.ProbeDecision.MANUAL_PORTAL,
            NcwuRules.probeDecision(true, true, true)
        )
        assertEquals(
            NcwuRules.ProbeDecision.PROBE_HTTPS,
            NcwuRules.probeDecision(true, true, false)
        )
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
