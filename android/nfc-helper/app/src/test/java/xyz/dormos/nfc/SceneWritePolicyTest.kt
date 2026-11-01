package xyz.dormos.nfc

import org.junit.Assert.*
import org.junit.Test

class SceneWritePolicyTest {
    @Test fun existingDataRequiresExplicitOverwrite() {
        assertTrue(SceneWritePolicy.needsOverwrite(true, false, false))
        assertFalse(SceneWritePolicy.needsOverwrite(false, false, false))
        assertFalse(SceneWritePolicy.needsOverwrite(true, true, false))
        assertFalse(SceneWritePolicy.needsOverwrite(true, false, true))
    }

    @Test fun onlyFullVerifiedReadbackMayCreateRootBinding() {
        for (state in SceneWriteState.values()) {
            assertEquals(state == SceneWriteState.VERIFIED, SceneWritePolicy.mayCommit(state))
        }
    }

    @Test fun formattingAloneNeverRegistersAScene() {
        assertFalse(SceneWritePolicy.mayCommit(SceneWriteState.FORMATTED))
        assertFalse(SceneWritePolicy.mayCommit(SceneWriteState.NEED_FORMAT))
        assertFalse(SceneWritePolicy.mayCommit(SceneWriteState.NEED_OVERWRITE))
    }

    @Test fun invalidSceneAndScriptMustNotBind() {
        val scene = "123e4567-e89b-12d3-a456-426614174000"
        val script = "123e4567-e89b-12d3-a456-426614174001"
        assertFalse(NfcProfiles.bindingMayCommit(scene, script, scene, false))
        assertFalse(NfcProfiles.bindingMayCommit(scene, "bad", scene, true))
        assertFalse(NfcProfiles.bindingMayCommit(scene, script, script, true))
        assertTrue(NfcProfiles.bindingMayCommit(scene, script, scene, true))
    }
}
