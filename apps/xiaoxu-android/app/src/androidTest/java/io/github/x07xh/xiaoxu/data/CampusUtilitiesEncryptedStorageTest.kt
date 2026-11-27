package io.github.x07xh.xiaoxu.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.YearMonth
import java.util.Base64
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Actual Android Keystore, synthetic snapshots only; never contacts school. */
@RunWith(AndroidJUnit4::class)
class CampusUtilitiesEncryptedStorageTest {
    private val month = YearMonth.of(2026, 11)
    private val now = 1_800_000_000_000L
    private val interval = CampusUtilitiesSyncCoordinator.DEFAULT_INTERVAL_MILLIS
    private val sample = CampusUtilityMonthResult(
        CampusUtilityKind.ELECTRIC, month.toString(), null, "12.50", 3, "v2", now)

    private fun scope(): String = UUID.randomUUID().toString().replace("-", "")
        .let { "$it$it" }

    @Test fun persistedSnapshotAndBudgetSurviveStoreRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val owner = scope()
        val store = AndroidCampusUtilitiesSyncStore(context)
        val prefs = context.getSharedPreferences(
            "xiaoxu_campus_utilities_sync_v1", Context.MODE_PRIVATE)
        try {
            assertTrue(store.reserveIfDue(owner, now, interval))
            store.save(owner, sample)
            val reopened = AndroidCampusUtilitiesSyncStore(context)
            assertEquals(sample, reopened.load(owner, month))
            assertNull(reopened.load(owner, month.plusMonths(1)))
            assertFalse(reopened.reserveIfDue(owner, now + 1, interval))
            reopened.clear(owner)
            assertNull(reopened.load(owner, month))
            assertFalse(reopened.reserveIfDue(owner, now + 2, interval))
            assertTrue(reopened.reserveIfDue(owner, now + interval, interval))
        } finally {
            store.clear(owner)
            prefs.edit().remove("attempt_$owner").commit()
        }
    }

    @Test fun foreignScopeAndCorruptCiphertextCannotBeReadOrOverwritten() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val owner = scope()
        val other = scope()
        val store = AndroidCampusUtilitiesSyncStore(context)
        val prefs = context.getSharedPreferences(
            "xiaoxu_campus_utilities_sync_v1", Context.MODE_PRIVATE)
        fun expectRejected(action: () -> Unit) {
            try {
                action()
                fail("Invalid ciphertext must be rejected")
            } catch (_: java.security.GeneralSecurityException) {
                // GCM authentication failure.
            }
        }
        try {
            store.save(owner, sample)
            val original = checkNotNull(prefs.getString("body_$owner", null))
            assertTrue(prefs.edit()
                .putString("body_$other", original)
                .putString("iv_$other", prefs.getString("iv_$owner", null))
                .commit())
            expectRejected { store.load(other, month) }
            assertEquals(sample, store.load(owner, month))
            val bytes = Base64.getDecoder().decode(original)
            bytes[0] = (bytes[0].toInt() xor 0x40).toByte()
            val damaged = Base64.getEncoder().encodeToString(bytes)
            assertTrue(prefs.edit().putString("body_$owner", damaged).commit())
            expectRejected { store.load(owner, month) }
            expectRejected { store.save(owner, sample.copy(fetchedAtMillis = now + 1)) }
            assertEquals(damaged, prefs.getString("body_$owner", null))
            assertEquals(original, prefs.getString("body_$other", null))
        } finally {
            store.clear(owner)
            store.clear(other)
        }
    }
}
