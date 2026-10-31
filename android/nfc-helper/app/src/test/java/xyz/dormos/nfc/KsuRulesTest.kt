package xyz.dormos.nfc

import org.junit.Assert.*
import org.junit.Test

class KsuRulesTest {
    @Test fun knownRootActionIsStrictlyAllowlisted() {
        assertTrue(KsuRules.approved(KsuRules.ACTION, ""))
        assertFalse(KsuRules.approved("shell", "id"))
        assertFalse(KsuRules.approved(KsuRules.ACTION, "extra"))
        assertFalse(KsuRules.approved("wifi-status;reboot", ""))
        assertFalse(KsuRules.approved("", ""))
    }

    @Test fun rootCommandIsImmutableAndDoesNotUseTagPayload() {
        assertEquals(
            "/system/bin/sh /data/adb/modules/dormos_nfc_actions/run.sh wifi-status",
            KsuRules.COMMAND
        )
        assertTrue(NfcProfiles.validProfile(
            NfcProfile(NfcProfiles.makeId(), "固定 Wi-Fi SH", NfcProfiles.KSU_WIFI_STATUS)
        ))
        assertFalse(NfcProfiles.validProfile(
            NfcProfile(NfcProfiles.makeId(), "任意脚本", "shell", "echo anything")
        ))
        assertFalse(NfcProfiles.validProfile(
            NfcProfile(NfcProfiles.makeId(), "乱改参数", NfcProfiles.KSU_WIFI_STATUS, "reboot")
        ))
    }

    @Test fun rootSuccessRequiresBothExpectedOutputMarkers() {
        assertTrue(KsuRules.succeeded(0, "DORM_KSU=ok\nACTION=wifi-status\n"))
        assertFalse(KsuRules.succeeded(1, "DORM_KSU=ok\nACTION=wifi-status"))
        assertFalse(KsuRules.succeeded(0, "DORM_KSU=ok"))
        assertFalse(KsuRules.succeeded(0, "ACTION=arbitrary"))
    }
}
