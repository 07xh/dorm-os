package io.github.x07xh.xiaoxu.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Base64
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** True on-device AndroidKeyStore; synthetic records and no network. */
@RunWith(AndroidJUnit4::class)
class CampusRunEncryptedStorageTest {
    private val now = 1_800_000_000_000L
    private fun ownerScope(): String = UUID.randomUUID().toString().replace("-", "")
        .let { "owner_$it$it" }

    @Test fun localRecordRemainsEncryptedAfterCorruptionUntilExplicitClear() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val owner = ownerScope()
        val store = LocalCampusRunStore(context, owner)
        val prefs = context.getSharedPreferences(
            "xiaoxu_campus_run_manual_v2", Context.MODE_PRIVATE)
        val bodyKey = "cipher_$owner"
        try {
            store.save(CampusRunManualInput(
                "synthetic", "", "10.003", "12.001", "", "", "5", "3"), now)
            val restored = LocalCampusRunStore(context, owner).load(now + 1L)
            assertNotNull(restored)
            assertEquals(10_003L, restored!!.effectiveMeters)
            assertEquals(12_001L, restored.rawMeters)
            assertEquals(5, restored.totalRunCount)
            assertEquals(3, restored.effectiveRunCount)
            assertNull(LocalCampusRunStore(context, ownerScope()).load(now + 1L))

            val bytes = Base64.getDecoder().decode(prefs.getString(bodyKey, null)!!)
            bytes[0] = (bytes[0].toInt() xor 0x40).toByte()
            assertTrue(prefs.edit().putString(
                bodyKey, Base64.getEncoder().encodeToString(bytes)).commit())
            assertNull(store.load(now + 1L))
            assertTrue(store.hasEncryptedRecord())
            assertTrue(prefs.contains(bodyKey))
            val protectedBytes = prefs.getString(bodyKey, null)
            try {
                store.save(CampusRunManualInput(
                    "replacement", "", "2", "3", "", "", "1", "1"), now + 1L)
                throw AssertionError("corrupt ciphertext was overwritten")
            } catch (_: IllegalStateException) {
                assertEquals(protectedBytes, prefs.getString(bodyKey, null))
            }
        } finally {
            store.clear()
            assertFalse(prefs.contains(bodyKey))
        }
    }

    @Test fun trustedCacheRejectsDifferentSubjectWithoutErasingPreviousData() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val owner = ownerScope()
        val cache = AndroidCampusRunTrustedSnapshotCache(context, owner)
        val prefs = context.getSharedPreferences(
            "xiaoxu_campus_run_trusted_v1", Context.MODE_PRIVATE)
        val bodyKey = "body_$owner"
        val subjectA = "a".repeat(64)
        val subjectB = "b".repeat(64)
        val original = CampusRunSnapshot(
            CampusRunProvenance.TRUSTED_READONLY, true, now,
            "synthetic", 20_000, 10_003, 12_001, false, emptyList(),
            totalRunCount = 5, effectiveRunCount = 3)
        try {
            assertTrue(cache.save(subjectA, original, now))
            assertEquals(original, cache.load(subjectA, now + 1L))
            assertNull(cache.load(subjectB, now + 1L))
            assertEquals(original, cache.load(subjectA, now + 1L))

            val bytes = Base64.getDecoder().decode(prefs.getString(bodyKey, null)!!)
            bytes[0] = (bytes[0].toInt() xor 0x40).toByte()
            assertTrue(prefs.edit().putString(
                bodyKey, Base64.getEncoder().encodeToString(bytes)).commit())
            assertNull(cache.load(subjectA, now + 1L))
            assertTrue(prefs.contains(bodyKey))
        } finally {
            assertTrue(cache.clear())
            assertFalse(prefs.contains(bodyKey))
        }
    }
}
