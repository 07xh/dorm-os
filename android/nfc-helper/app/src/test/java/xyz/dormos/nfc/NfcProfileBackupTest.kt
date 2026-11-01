package xyz.dormos.nfc

import org.junit.Assert.*
import org.junit.Test

class NfcProfileBackupTest {
    private fun profile(
        id: String = NfcProfiles.makeId(),
        name: String = "书桌学习",
        action: String = NfcProfiles.DIAGNOSE_NCWU,
        target: String = ""
    ) = NfcProfile(id, name, action, target)

    @Test fun validProfilesRoundTripWithoutChangingOpaqueTagIds() {
        val profiles = listOf(
            profile(name = "校园网检测"),
            profile(name = "课程资料", action = NfcProfiles.OPEN_HTTPS, target = "https://example.org/course"),
            profile(name = "学习模式", action = NfcProfiles.DORM_SCENE, target = "study"),
            profile(name = "只读网卡诊断", action = NfcProfiles.KSU_WIFI_STATUS)
        )
        val decoded = NfcProfileBackup.decode(NfcProfileBackup.encode(profiles))
        assertEquals(profiles, decoded)
    }

    @Test fun emptyBackupIsValidAndVersioned() {
        val encoded = NfcProfileBackup.encode(emptyList())
        assertTrue(encoded.contains("\"version\": 1"))
        assertEquals(emptyList<NfcProfile>(), NfcProfileBackup.decode(encoded))
    }

    @Test fun malformedOrWrongVersionBackupIsRejected() {
        assertNull(NfcProfileBackup.decode("not-json"))
        assertNull(NfcProfileBackup.decode("""{"type":"other","version":1,"profiles":[]}"""))
        assertNull(NfcProfileBackup.decode("""{"type":"dorm-os-nfc-profiles","version":2,"profiles":[]}"""))
        assertNull(NfcProfileBackup.decode("""{"type":"dorm-os-nfc-profiles","version":1,"profiles":"not-array"}"""))
    }

    @Test fun duplicateIdsAndUnknownActionsAreRejected() {
        val id = "123e4567-e89b-12d3-a456-426614174000"
        val duplicate = """{"type":"dorm-os-nfc-profiles","version":1,"profiles":[
            {"id":"$id","name":"A","action":"ncwu","target":""},
            {"id":"$id","name":"B","action":"ncwu","target":""}
        ]}"""
        assertNull(NfcProfileBackup.decode(duplicate))
        val unknown = """{"type":"dorm-os-nfc-profiles","version":1,"profiles":[
            {"id":"$id","name":"bad","action":"shell","target":"id"}
        ]}"""
        assertNull(NfcProfileBackup.decode(unknown))
    }

    @Test fun credentialBearingHttpsTargetsAreRejectedOnRestore() {
        val id = "123e4567-e89b-12d3-a456-426614174000"
        val unsafe = """{"type":"dorm-os-nfc-profiles","version":1,"profiles":[
            {"id":"$id","name":"bad","action":"https","target":"https://example.org/?access_token=secret"}
        ]}"""
        assertNull(NfcProfileBackup.decode(unsafe))
    }

    @Test fun inputSizeAndProfileCountAreBounded() {
        assertNull(NfcProfileBackup.decode(" ".repeat(NfcProfileBackup.MAX_BYTES + 1)))
        val tooMany = (0..NfcProfileBackup.MAX_PROFILES).joinToString(",") { i ->
            val id = "00000000-0000-0000-0000-" + i.toString().padStart(12, '0')
            """{"id":"$id","name":"tag$i","action":"ncwu","target":""}"""
        }
        assertNull(NfcProfileBackup.decode(
            """{"type":"dorm-os-nfc-profiles","version":1,"profiles":[$tooMany]}"""
        ))
    }

    @Test fun encodeRejectsDuplicateIdsAndInvalidProfiles() {
        val sameId = NfcProfiles.makeId()
        assertThrows(IllegalArgumentException::class.java) {
            NfcProfileBackup.encode(listOf(profile(id = sameId), profile(id = sameId, name = "duplicate")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            NfcProfileBackup.encode(listOf(profile(action = "shell", target = "reboot")))
        }
    }
}
