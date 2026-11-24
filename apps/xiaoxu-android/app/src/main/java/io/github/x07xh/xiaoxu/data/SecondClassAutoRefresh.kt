package io.github.x07xh.xiaoxu.data

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.TimeUnit
import org.json.JSONObject

/** Scheduling and local-session orchestration only; school business parsing
 * remains in the existing fixed asset and native parsers. */
object SecondClassAutoRefresh {
    private const val WORK = "secondclass-local-auto-read-v1"
    const val SETTINGS = "second_class_auto_read"
    private val lock = Mutex()
    private val releaseScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    @Volatile private var manualUsers = 0
    private data class ActiveRead(val job: Job?, val background: Boolean, val userRequested: Boolean)
    @Volatile private var activeRead: ActiveRead? = null
    private fun prefs(c: Context) = c.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE)
    fun enabled(c: Context) = prefs(c).getBoolean("enabled", true)
    fun background(c: Context) = prefs(c).getBoolean("background", false)
    fun status(c: Context) = prefs(c).getString("status", "idle").orEmpty()

    fun lastSuccess(c: Context) = prefs(c).getLong("success_at", 0)
    fun lastSuccessTransport(c: Context) = prefs(c).getString("success_transport", "").orEmpty()
    fun lastSuccessWasBackground(c: Context) = prefs(c).getBoolean("success_background", false)
    fun lastBackgroundCheck(c: Context) = prefs(c).getLong("background_check_at", 0)

    fun configure(c: Context, enabled: Boolean, background: Boolean) {
        prefs(c).edit().putBoolean("enabled", enabled).putBoolean("background", background).apply()
        val active = activeRead
        if (active != null && !active.userRequested && (!enabled || !background && active.background)) active.job?.cancel()
        reconcile(c)
    }
    fun reconcile(c: Context) {
        val work = WorkManager.getInstance(c)
        if (!enabled(c) || !background(c)) { work.cancelUniqueWork(WORK); return }
        val request = PeriodicWorkRequestBuilder<SecondClassAutoReadWorker>(SecondClassAutoReadPolicy.AUTO_SYNC_HOURS, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true).build())
            .build()
        work.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
    // Main-thread Activity callbacks reserve manual mode until pending Room
    // work has completed cancellation, even if the Activity is destroyed.
    fun manualStarted() { manualUsers++; activeRead?.job?.cancel() }
    fun manualFinishedAfter(saving: Job?) {
        releaseScope.launch { saving?.join(); manualUsers = (manualUsers - 1).coerceAtLeast(0) }
    }
    fun onManualSave(c: Context) {
        prefs(c).edit().putString("status", "updated").apply()
        reconcile(c)
    }

    internal suspend fun refreshIfDue(
        context: Context, backgroundRun: Boolean = false, userRequested: Boolean = false,
        // A provider must return an explicitly authorized, account-bound reader.
        // Null means no native session was issued, not a failed native request.
        nativeReader: suspend (Context, String, String) -> JSONObject? = { c, salt, expected ->
            readSecondClassFromAuthorizedSession(c, salt, expected)
        },
        reader: suspend (Context, String) -> JSONObject = { c, salt -> readSecondClassAutomatically(c, salt) }
    ): Boolean {
        val c = context.applicationContext
        if (!lock.tryLock()) return false
        activeRead = ActiveRead(currentCoroutineContext()[Job], backgroundRun, userRequested)
        val settings = prefs(c)
        fun state(value: String) { settings.edit().putString("status", value).apply() }
        try {
            if (settings.getInt("session_policy_version", 0) < 1) {
                // Old builds permanently paused even on a temporary 403 or
                // normal school redirect. Permit one fresh, owner-checked read;
                // never reset account changes, opt-outs or saved identity.
                settings.edit().putInt("session_policy_version", 1).apply()
                if (status(c) == "needs_reconnect") state("failed")
            }
            // Written only when a background invocation actually acquires the
            // coordinator, never when a periodic request is merely registered.
            if (backgroundRun && enabled(c) && background(c))
                settings.edit().putLong("background_check_at", System.currentTimeMillis()).apply()
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
                bound, manualUsers > 0, startedAt, saved, settings.getLong("attempt", 0),
                userRequested)) return false
            settings.edit().putLong("attempt", startedAt).apply()
            state("reading")
            val result = readSecondClassForSync(c, salt!!, requireNotNull(expected), nativeReader, reader)
            val payload = result.payload
            if (!payload.optBoolean("ok")) {
                state(if (SecondClassAutoReadPolicy.requiresReconnect(
                    payload.optInt("httpStatus"), payload.optString("stage"))) "needs_reconnect"
                    else if (payload.optString("stage") == "identity" &&
                        payload.optString("reason") != "visible_account_required") "needs_confirmation" else "failed")
                return false
            }
            if (!SecondClassAutoReadPolicy.matchesConfirmedAccount(expected, payload.optString("accountHash"))) {
                state("needs_confirmation"); return false
            }
            val scores = parseLocalSecondClassScores(payload, startedAt)
            val activities = parseLocalSecondClassActivities(payload, startedAt)
            val catalog = parseLocalSecondClassCatalogEmpty(payload, startedAt)
            val catalogActivities = parseLocalSecondClassCatalogActivities(payload, startedAt)
            // Catalog may be independently unavailable: still save verified
            // personal scores/registrations, while the DAO retains only the
            // same owner's earlier catalog and its original capture time.
            val catalogVerified = payload.optString("catalogStatus") in
                setOf("verified_empty", "verified_nonempty")
            currentCoroutineContext().ensureActive()
            if ((!enabled(c) && !userRequested) || backgroundRun && !background(c) ||
                manualUsers > 0 || identity.getString("salt", null) != salt) return false
            // A changed legacy confirmation must also invalidate this attempt.
            if (bindings.isEmpty() && identity.getString("account", null) != expected) return false
            withContext(Dispatchers.IO) {
                currentCoroutineContext().ensureActive()
                dao.replaceSecondClassSnapshot(scores, activities, catalog, catalogActivities,
                    accountHash = expected, expectedBinding = expected,
                    catalogVerified = catalogVerified)
            }
            settings.edit().putLong("success_at", System.currentTimeMillis())
                .putBoolean("success_background", backgroundRun)
                .putString("catalog_status", payload.optString("catalogStatus"))
                .putString("success_transport", result.transport)
                .putString("status", "updated").apply()
            return true
        } catch (_: TimeoutCancellationException) { state("failed"); return false }
        catch (cancelled: CancellationException) {
            if (status(c) == "reading") state("idle")
            throw cancelled
        } catch (failure: AutoReadFailure) { state(failure.status); return false }
        catch (failure: SecondClassHttpFailure) {
            state(if (failure.status == 401 && failure.endpoint == SecondClassReadEndpoint.SCORES)
                "needs_reconnect" else "failed")
            return false
        }
        catch (_: Exception) { state("failed"); return false }
        finally {
            // Invalidation can return before Room without throwing (for
            // example a changed identity salt). Never leave a finished read busy.
            if (status(c) == "reading") state("idle")
            activeRead = null
            lock.unlock()
        }
    }
}

/** No immediate retry loops on expiry, account changes or school failures. */
class SecondClassAutoReadWorker(c: Context, params: WorkerParameters) : CoroutineWorker(c, params) {
    override suspend fun doWork(): Result {
        SecondClassAutoRefresh.refreshIfDue(applicationContext, backgroundRun = true)
        return Result.success()
    }
}
