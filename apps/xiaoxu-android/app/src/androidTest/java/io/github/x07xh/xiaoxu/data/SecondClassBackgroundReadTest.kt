package io.github.x07xh.xiaoxu.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import androidx.work.WorkInfo
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/** Disposable emulator storage; synthetic identity and data, no school requests. */
@RunWith(AndroidJUnit4::class)
class SecondClassBackgroundReadTest {
    private val c = InstrumentationRegistry.getInstrumentation().targetContext
    private val settings get() = c.getSharedPreferences(SecondClassAutoRefresh.SETTINGS, Context.MODE_PRIVATE)
    private val identity get() = c.getSharedPreferences("second_class_local_identity", Context.MODE_PRIVATE)
    private val dao get() = LocalTaskDatabase.open(c).nativeSchoolRecords()
    private val hash = "a".repeat(64)
    private val savedAt = System.currentTimeMillis() - 1_000_000
    private fun payload() = JSONObject("""{"schema":"xiaoxu/local-secondclass-scores-v1","ok":true,
        "identityMatched":true,"httpStatus":200,"accountHash":"$hash",
        "rows":[{"termIndex":1,"qualityScore":0,"volunteerHours":0,"categories":[0,0,0,0,0,0]}],
        "activitiesComplete":true,"activities":[],"catalogStatus":"unavailable","catalogEmptyFilters":[]}""")

    @Before fun prepare() = runBlocking {
        settings.edit().clear().putBoolean("enabled", true).putBoolean("background", true).commit()
        identity.edit().clear().putString("salt", "b".repeat(32)).putString("account", hash).commit()
        for (kind in listOf("credits", "activity", "activity_summary", "catalog_empty", "account_binding"))
            dao.deleteKind("second_class", kind)
        dao.replaceSecondClassSnapshot(parseLocalSecondClassScores(payload(), savedAt),
            parseLocalSecondClassActivities(payload(), savedAt), accountHash = hash)
    }
    @After fun clean() = runBlocking {
        SecondClassAutoRefresh.configure(c, false, false)
        awaitCancellationOfScheduledWork()
        settings.edit().clear().commit()
        identity.edit().clear().commit()
        for (kind in listOf("credits", "activity", "activity_summary", "catalog_empty", "account_binding"))
            dao.deleteKind("second_class", kind)
    }
    @Test fun disablingBackgroundCancelsTheActualReadAndPreservesRoom() = runBlocking {
        val before = dao.snapshot()
        val entered = CompletableDeferred<Unit>()
        val reading = launch {
            SecondClassAutoRefresh.refreshIfDue(c, true) { _, _ ->
                entered.complete(Unit)
                awaitCancellation()
            }
        }
        try {
            withTimeout(10_000) { entered.await() }
            SecondClassAutoRefresh.configure(c, true, false)
            withTimeout(5_000) { reading.join() }
            assertTrue("opt-out must cancel the in-flight background job", reading.isCancelled)
        } finally { reading.cancelAndJoin() }
        assertEquals("idle", SecondClassAutoRefresh.status(c))
        assertEquals(before, dao.snapshot())
        assertEquals(0L, SecondClassAutoRefresh.lastSuccess(c))
    }
    @Test fun foregroundIsNotCancelledWhenOnlyBackgroundIsDisabled() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val reading = launch {
            SecondClassAutoRefresh.refreshIfDue(c, false) { _, _ ->
                entered.complete(Unit); finish.await(); payload()
            }
        }
        withTimeout(10_000) { entered.await() }
        SecondClassAutoRefresh.configure(c, true, false)
        assertFalse(reading.isCancelled)
        finish.complete(Unit)
        withTimeout(10_000) { reading.join() }
        assertEquals("updated", SecondClassAutoRefresh.status(c))
        assertTrue(SecondClassAutoRefresh.lastSuccess(c) >= savedAt)
        assertFalse(SecondClassAutoRefresh.lastSuccessWasBackground(c))
    }
    @Test fun automaticSuccessIsRecordedOnlyAfterRoomSaveAndManualSaveDoesNotChangeIt() = runBlocking {
        SecondClassAutoRefresh.refreshIfDue(c, true) { _, _ -> payload() }
        assertTrue(SecondClassAutoRefresh.lastBackgroundCheck(c) > 0)
        val success = SecondClassAutoRefresh.lastSuccess(c)
        assertTrue(success > savedAt)
        assertTrue(SecondClassAutoRefresh.lastSuccessWasBackground(c))
        assertEquals(settings.getLong("attempt", 0), dao.snapshot().first { it.kind == "credits" }.capturedAt)
        SecondClassAutoRefresh.onManualSave(c)
        assertEquals(success, SecondClassAutoRefresh.lastSuccess(c))
    }
    @Test fun failureAndAccountChangeNeverBecomeASuccessOrReplaceOldData() = runBlocking {
        val before = dao.snapshot()
        SecondClassAutoRefresh.refreshIfDue(c, true) { _, _ -> JSONObject("{\"ok\":false,\"httpStatus\":401}") }
        assertEquals("needs_reconnect", SecondClassAutoRefresh.status(c))
        assertEquals(0L, SecondClassAutoRefresh.lastSuccess(c))
        assertEquals(before, dao.snapshot())
        settings.edit().putString("status", "idle").putLong("attempt", 0).commit()
        SecondClassAutoRefresh.refreshIfDue(c, true) { _, _ -> payload().put("accountHash", "c".repeat(64)) }
        assertEquals("needs_confirmation", SecondClassAutoRefresh.status(c))
        assertEquals(before, dao.snapshot())
        assertEquals(0L, SecondClassAutoRefresh.lastSuccess(c))
    }
    @Test fun identityInvalidationDoesNotLeaveAReadStuckOrOverwriteData() = runBlocking {
        val before = dao.snapshot()
        SecondClassAutoRefresh.refreshIfDue(c, true) { _, _ ->
            identity.edit().putString("salt", "c".repeat(32)).commit()
            payload()
        }
        assertEquals("idle", SecondClassAutoRefresh.status(c))
        assertEquals(before, dao.snapshot())
        assertEquals(0L, SecondClassAutoRefresh.lastSuccess(c))
    }
    private suspend fun awaitCancellationOfScheduledWork() = withTimeout(10_000) {
        val work = WorkManager.getInstance(c)
        while (work.getWorkInfosForUniqueWork("secondclass-local-auto-read-v1").get(10, TimeUnit.SECONDS)
                .any { !it.state.isFinished }) delay(50)
    }
    @Test fun scheduleIsUniqueAndOptOutCancelsIt() = runBlocking {
        // A fresh snapshot ensures a real Worker cannot contact the school.
        settings.edit().putLong("attempt", System.currentTimeMillis()).commit()
        SecondClassAutoRefresh.reconcile(c)
        SecondClassAutoRefresh.reconcile(c)
        val work = WorkManager.getInstance(c)
        val jobs = work.getWorkInfosForUniqueWork("secondclass-local-auto-read-v1").get(10, TimeUnit.SECONDS)
        assertEquals(1, jobs.count { !it.state.isFinished })
        SecondClassAutoRefresh.configure(c, true, false)
        awaitCancellationOfScheduledWork()
        val cancelled = work.getWorkInfosForUniqueWork("secondclass-local-auto-read-v1").get(10, TimeUnit.SECONDS)
        assertTrue(cancelled.all { it.state == WorkInfo.State.CANCELLED || it.state.isFinished })
    }
}
