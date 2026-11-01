package xyz.dormos.nfc

import org.junit.Assert.*
import org.junit.Test

class KsuRulesTest {
    @Test fun onlyValidatedUuidCanReachTheFixedDispatcher() {
        val id = "123e4567-e89b-12d3-a456-426614174000"
        assertTrue(KsuRules.validSceneId(id))
        assertFalse(KsuRules.validSceneId("bad"))
        assertFalse(KsuRules.validSceneId("123e4567-e89b-12d3-a456-426614174000;reboot"))
        assertEquals(
            "/system/bin/sh /data/adb/modules/dormos_nfc_actions/run.sh --nfc $id",
            KsuRules.sceneCommand(id)
        )
    }

    @Test fun successRequiresDispatcherMarkerAndZeroExit() {
        assertTrue(KsuRules.succeeded(0, "DORM_NFC_RESULT=success\nDORM_ACTION=personal-script\n"))
        assertFalse(KsuRules.succeeded(1, "DORM_NFC_RESULT=success"))
        assertFalse(KsuRules.succeeded(0, "DORM_NFC_RESULT=failed"))
        assertFalse(KsuRules.succeeded(0, "DORM_NFC_RESULT=success\n" + "x".repeat(4097)))
    }
}
