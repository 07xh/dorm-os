package io.github.x07xh.xiaoxu.data

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.TimeUnit

/** Scheduling and local-session orchestration only; school business parsing
 * remains in the existing fixed asset and native parsers. */
object SecondClassAutoRefresh {
    private const val WORK = "secondclass-local-auto-read-v1"
    const val SETTINGS = "second_class_auto_read"
    private val lock = Mutex()
    private val releaseScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    @Volatile private var manualUsers = 0
    @Volatile private var activeJob: Job? = null
    private fun prefs(c: Context) = c.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE)
    fun enabled(c: Context) = prefs(c).getBoolean("enabled", true)
    fun background(c: Context) = prefs(c).getBoolean("background", false)
    fun status(c: Context) = prefs(c).getString("status", "idle").orEmpty()

    fun configure(c: Context, enabled: Boolean, background: Boolean) {
        prefs(c).edit().putBoolean("enabled", enabled).putBoolean("background", background).apply()
        if (!enabled) activeJob?.cancel()
        reconcile(c)
    }
    fun reconcile(c: Context) {
        val work = WorkManager.getInstance(c)
        if (!enabled(c) || !background(c)) { work.cancelUniqueWork(WORK); return }
        val request = PeriodicWorkRequestBuilder<SecondClassAutoReadWorker>(30, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true).build())
            .build()
        work.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }
    // Main-thread Activity callbacks reserve manual mode until pending Room
    // work has completed cancellation, even if the Activity is destroyed.
    fun manualStarted() { manualUsers++; activeJob?.cancel() }
    fun manualFinishedAfter(saving: Job?) {
        releaseScope.launch { saving?.join(); manualUsers = (manualUsers - 1).coerceAtLeast(0) }
    }
    fun onManualSave(c: Context) {
        prefs(c).edit().putString("status", "updated").apply()
        reconcile(c)
    }

    suspend fun refreshIfDue(context: Context, backgroundRun: Boolean = false) {
        val c = context.applicationContext
        if (!lock.tryLock()) return
        activeJob = currentCoroutineContext()[Job]
        val settings = prefs(c)
        fun state(value: String) { settings.edit().putString("status", value).apply() }
        try {
            if (status(c) == "reading") state("idle") // A killed prior process cannot still be reading.
            val dao = LocalTaskDatabase.open(c).nativeSchoolRecords()
            val records = withContext(Dispatchers.IO) { dao.snapshot() }
            val credits = records.filter { it.provider == "second_class" && it.kind == "credits" }
            val saved = credits.map { it.capturedAt }.distinct().singleOrNull() ?: 0
            val identity = c.getSharedPreferences("second_class_local_identity", Context.MODE_PRIVATE)
            val bindings = records.filter { it.provider == "second_class" && it.kind == "account_binding" }
            // Legacy upgrade: a previously confirmed preference only authorizes
            // a NEW identity-checked read. It never identifies cached records.
            val expected = if (bindings.isEmpty()) identity.getString("account", null)
                else bindings.singleOrNull()?.takeIf {
                    it.sourceHost == "dekt.ncwu.edu.cn" && it.capturedAt == saved
                }?.details
            val salt = identity.getString("salt", null)
            val bound = SecondClassAutoReadPolicy.matchesConfirmedAccount(expected, expected) &&
                salt?.matches(Regex("^[a-f0-9]{32}$")) == true &&
                status(c) !in setOf("needs_reconnect", "needs_confirmation") &&
                credits.all { it.sourceHost == "dekt.ncwu.edu.cn" }
            val startedAt = System.currentTimeMillis()
            if (!SecondClassAutoReadPolicy.shouldRead(enabled(c), background(c), backgroundRun,
                bound, manualUsers > 0, startedAt, saved, settings.getLong("attempt", 0))) return
            settings.edit().putLong("attempt", startedAt).apply()
            state("reading")
            val payload = readSecondClassAutomatically(c, salt!!)
            if (!payload.optBoolean("ok")) {
                state(if (payload.optInt("httpStatus") in setOf(401, 403)) "needs_reconnect"
                    else if (payload.optString("stage") == "identity" &&
                        payload.optString("reason") != "visible_account_required") "needs_confirmation" else "failed")
                return
            }
            if (!SecondClassAutoReadPolicy.matchesConfirmedAccount(expected, payload.optString("accountHash"))) {
                state("needs_confirmation"); return
            }
            val scores = parseLocalSecondClassScores(payload, startedAt)
            val activities = parseLocalSecondClassActivities(payload, startedAt)
            val catalog = parseLocalSecondClassCatalogEmpty(payload, startedAt)
            currentCoroutineContext().ensureActive()
            if (!enabled(c) || backgroundRun && !background(c) || manualUsers > 0 ||
                identity.getString("salt", null) != salt) return
            // A changed legacy confirmation must also invalidate this attempt.
            if (bindings.isEmpty() && identity.getString("account", null) != expected) return
            withContext(Dispatchers.IO) {
                currentCoroutineContext().ensureActive()
                dao.replaceSecondClassSnapshot(scores, activities, catalog,
                    accountHash = expected, expectedBinding = expected)
            }
            state("updated")
        } catch (_: TimeoutCancellationException) { state("failed") }
        catch (cancelled: CancellationException) {
            if (status(c) == "reading") state("idle")
            throw cancelled
        } catch (failure: AutoReadFailure) { state(failure.status) }
        catch (_: Exception) { state("failed") }
        finally { activeJob = null; lock.unlock() }
    }
}

/** No immediate retry loops on expiry, account changes or school failures. */
class SecondClassAutoReadWorker(c: Context, params: WorkerParameters) : CoroutineWorker(c, params) {
    override suspend fun doWork(): Result {
        SecondClassAutoRefresh.refreshIfDue(applicationContext, backgroundRun = true)
        return Result.success()
    }
}
