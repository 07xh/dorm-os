package io.github.x07xh.xiaoxu.data

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject

/** In-memory confirmation payload. Only sanitized rows and a salted account hash are retained. */
internal class SecondClassPendingRead(
    internal val payload: JSONObject,
    internal val salt: String,
    internal val accountHash: String,
    internal val capturedAt: Long,
    internal val previousBindingPresent: Boolean,
    internal val previousBindingHash: String?,
    internal val previousBindingCapturedAt: Long?,
    internal val legacyAccount: String?,
    val scoreCount: Int,
    val activityCount: Int,
    val aggregateScore: String?,
    val catalogStatus: String
)

internal sealed class SecondClassReadOutcome {
    object Skipped : SecondClassReadOutcome()
    data class Saved(
        val capturedAt: Long,
        val scoreCount: Int,
        val activityCount: Int,
        val catalogStatus: String
    ) : SecondClassReadOutcome()
    data class NeedsConfirmation(val pending: SecondClassPendingRead) : SecondClassReadOutcome()
    data class Failed(val status: String) : SecondClassReadOutcome()
}

/** Scheduling, account confirmation and local persistence for fixed read-only HTTP endpoints. */
object SecondClassAutoRefresh {
    private const val WORK = "secondclass-local-auto-read-v1"
    const val SETTINGS = "second_class_auto_read"
    private const val IDENTITY = "second_class_local_identity"
    private const val HOST = "dekt.ncwu.edu.cn"
    private val lock = Mutex()
    private val releaseScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    @Volatile private var manualUsers = 0
    private data class ActiveRead(val job: Job?, val background: Boolean)
    @Volatile private var activeRead: ActiveRead? = null

    private fun prefs(c: Context) = c.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE)
    private fun identity(c: Context) = c.getSharedPreferences(IDENTITY, Context.MODE_PRIVATE)
    fun enabled(c: Context) = prefs(c).getBoolean("enabled", true)
    fun background(c: Context) = prefs(c).getBoolean("background", false)
    fun status(c: Context) = prefs(c).getString("status", "idle").orEmpty()
    fun lastSuccess(c: Context) = prefs(c).getLong("success_at", 0)
    fun lastSuccessWasBackground(c: Context) = prefs(c).getBoolean("success_background", false)
    fun lastBackgroundCheck(c: Context) = prefs(c).getLong("background_check_at", 0)

    fun configure(c: Context, enabled: Boolean, background: Boolean) {
        prefs(c).edit().putBoolean("enabled", enabled).putBoolean("background", background).apply()
        val active = activeRead
        if (!enabled || !background && active?.background == true) active?.job?.cancel()
        reconcile(c)
    }

    fun reconcile(c: Context) {
        val work = WorkManager.getInstance(c)
        if (!enabled(c) || !background(c)) {
            work.cancelUniqueWork(WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<SecondClassAutoReadWorker>(30, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true).build())
            .build()
        work.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    // Keep a login WebView from racing an already scheduled native HTTP read.
    fun manualStarted() { manualUsers++; activeRead?.job?.cancel() }
    fun manualFinishedAfter(saving: Job?) {
        releaseScope.launch { saving?.join(); manualUsers = (manualUsers - 1).coerceAtLeast(0) }
    }

    fun onManualSave(c: Context) {
        val now = System.currentTimeMillis()
        prefs(c).edit().putLong("success_at", now)
            .putBoolean("success_background", false).putString("status", "updated").apply()
        reconcile(c)
    }

    internal suspend fun refreshIfDue(
        context: Context,
        backgroundRun: Boolean = false,
        userRequested: Boolean = false,
        reader: suspend (Context, String, String?) -> JSONObject = { _, salt, expected ->
            readSecondClassNativeHttp(salt, expected)
        }
    ): SecondClassReadOutcome {
        val c = context.applicationContext
        if (!lock.tryLock()) return SecondClassReadOutcome.Skipped
        activeRead = ActiveRead(currentCoroutineContext()[Job], backgroundRun)
        val settings = prefs(c)
        fun setStatus(value: String) { settings.edit().putString("status", value).apply() }
        try {
            if (settings.getInt("session_policy_version", 0) < 2) {
                settings.edit().putInt("session_policy_version", 2).apply()
            }
            if (backgroundRun && enabled(c) && background(c))
                settings.edit().putLong("background_check_at", System.currentTimeMillis()).apply()
            if (status(c) == "reading") setStatus("idle")

            val dao = LocalTaskDatabase.open(c).nativeSchoolRecords()
            val records = withContext(Dispatchers.IO) { dao.snapshot() }
            val credits = records.filter { it.provider == "second_class" && it.kind == "credits" }
            val savedAt = credits.map { it.capturedAt }.distinct().singleOrNull() ?: 0L
            val bindingRows = records.filter {
                it.provider == "second_class" && it.kind == "account_binding"
            }
            if (bindingRows.size > 1) {
                setStatus("needs_confirmation")
                return SecondClassReadOutcome.Failed("needs_confirmation")
            }
            val previousBinding = bindingRows.singleOrNull()
            val identityPrefs = identity(c)
            val legacyAccount = if (previousBinding == null)
                identityPrefs.getString("account", null) else null
            val expected = if (previousBinding == null) legacyAccount else
                previousBinding.details.takeIf {
                    previousBinding.sourceHost == HOST && previousBinding.capturedAt == savedAt
                }

            var salt = identityPrefs.getString("salt", null)
            if (salt?.matches(Regex("^[a-f0-9]{32}$")) != true && userRequested) {
                salt = UUID.randomUUID().toString().replace("-", "")
                if (!identityPrefs.edit().putString("salt", salt).commit())
                    throw AutoReadFailure("failed")
            }
            if (salt?.matches(Regex("^[a-f0-9]{32}$")) != true || salt == null)
                return SecondClassReadOutcome.Skipped

            val identityMatches = SecondClassAutoReadPolicy.matchesConfirmedAccount(expected, expected) &&
                credits.all { it.sourceHost == HOST } &&
                (previousBinding == null ||
                    previousBinding.sourceHost == HOST && previousBinding.capturedAt == savedAt)
            val autoBound = identityMatches &&
                status(c) !in setOf("needs_reconnect", "needs_confirmation")
            val startedAt = System.currentTimeMillis()
            if (!SecondClassAutoReadPolicy.shouldRead(
                    enabled(c), background(c), backgroundRun, autoBound, manualUsers > 0,
                    startedAt, savedAt, settings.getLong("attempt", 0),
                    userRequested = userRequested
                )) return SecondClassReadOutcome.Skipped

            settings.edit().putLong("attempt", startedAt).apply()
            setStatus("reading")
            // Manual reads discover the current salted hash first; they never
            // write a new binding until the owner confirms the summary.
            val payload = reader(c, salt, if (userRequested) null else expected)
            if (!payload.optBoolean("ok")) {
                val statusValue = if (SecondClassAutoReadPolicy.requiresReconnect(
                        payload.optInt("httpStatus"), payload.optString("stage")))
                    "needs_reconnect" else "failed"
                setStatus(statusValue)
                return SecondClassReadOutcome.Failed(statusValue)
            }
            val actualHash = payload.optString("accountHash")
            if (!actualHash.matches(Regex("^[a-f0-9]{64}$"))) {
                setStatus("needs_confirmation")
                return SecondClassReadOutcome.Failed("needs_confirmation")
            }
            if (!userRequested && !SecondClassAutoReadPolicy.matchesConfirmedAccount(expected, actualHash)) {
                setStatus("needs_confirmation")
                return SecondClassReadOutcome.Failed("needs_confirmation")
            }

            val rows = payload.optJSONArray("rows") ?: JSONArray()
            val aggregateScore = (0 until rows.length()).mapNotNull { index ->
                rows.optJSONObject(index)?.takeIf { it.optInt("termIndex", -1) == 100 }
                    ?.opt("qualityScore")?.toString()
            }.singleOrNull()
            val pending = SecondClassPendingRead(
                payload = payload,
                salt = salt,
                accountHash = actualHash,
                capturedAt = startedAt,
                previousBindingPresent = previousBinding != null,
                previousBindingHash = previousBinding?.details,
                previousBindingCapturedAt = previousBinding?.capturedAt,
                legacyAccount = legacyAccount,
                scoreCount = rows.length(),
                activityCount = payload.optJSONArray("activities")?.length() ?: 0,
                aggregateScore = aggregateScore,
                catalogStatus = payload.optString("catalogStatus", "unavailable")
            )
            if (userRequested && (!identityMatches || actualHash != expected)) {
                setStatus("needs_confirmation")
                return SecondClassReadOutcome.NeedsConfirmation(pending)
            }
            return persist(c, pending, backgroundRun)
        } catch (cancelled: CancellationException) {
            if (status(c) == "reading") setStatus("idle")
            throw cancelled
        } catch (failure: AutoReadFailure) {
            setStatus(failure.status)
            return SecondClassReadOutcome.Failed(failure.status)
        } catch (_: Exception) {
            setStatus("failed")
            return SecondClassReadOutcome.Failed("failed")
        } finally {
            if (status(c) == "reading") setStatus("idle")
            activeRead = null
            lock.unlock()
        }
    }

    internal suspend fun confirmPending(
        context: Context,
        pending: SecondClassPendingRead
    ): SecondClassReadOutcome {
        val c = context.applicationContext
        if (!lock.tryLock()) return SecondClassReadOutcome.Skipped
        activeRead = ActiveRead(currentCoroutineContext()[Job], background = false)
        try {
            val identityPrefs = identity(c)
            if (identityPrefs.getString("salt", null) != pending.salt)
                throw AutoReadFailure("needs_confirmation")
            if (!pending.previousBindingPresent &&
                identityPrefs.getString("account", null) != pending.legacyAccount)
                throw AutoReadFailure("needs_confirmation")
            prefs(c).edit().putString("status", "reading").apply()
            return persist(c, pending, backgroundRun = false)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: AutoReadFailure) {
            prefs(c).edit().putString("status", failure.status).apply()
            return SecondClassReadOutcome.Failed(failure.status)
        } catch (_: Exception) {
            prefs(c).edit().putString("status", "failed").apply()
            return SecondClassReadOutcome.Failed("failed")
        } finally {
            activeRead = null
            lock.unlock()
        }
    }

    private suspend fun persist(
        context: Context,
        pending: SecondClassPendingRead,
        backgroundRun: Boolean
    ): SecondClassReadOutcome {
        val c = context.applicationContext
        val payload = pending.payload
        if (payload.optString("accountHash") != pending.accountHash ||
            identity(c).getString("salt", null) != pending.salt)
            throw AutoReadFailure("needs_confirmation")
        val scores = parseLocalSecondClassScores(payload, pending.capturedAt)
        val activities = parseLocalSecondClassActivities(payload, pending.capturedAt)
        val catalog = parseLocalSecondClassCatalogEmpty(payload, pending.capturedAt)
        val catalogActivities = parseLocalSecondClassCatalogActivities(payload, pending.capturedAt)
        val catalogVerified = payload.optString("catalogStatus") in
            setOf("verified_empty", "verified_nonempty")
        currentCoroutineContext().ensureActive()
        if (!pending.previousBindingPresent &&
            identity(c).getString("account", null) != pending.legacyAccount)
            throw AutoReadFailure("needs_confirmation")
        val dao = LocalTaskDatabase.open(c).nativeSchoolRecords()
        withContext(Dispatchers.IO) {
            currentCoroutineContext().ensureActive()
            dao.replaceSecondClassSnapshot(
                scores, activities, catalog, catalogActivities,
                accountHash = pending.accountHash,
                catalogVerified = catalogVerified,
                requirePreviousBindingMatch = true,
                expectedPreviousBinding = pending.previousBindingHash,
                expectedPreviousBindingCapturedAt = pending.previousBindingCapturedAt
            )
        }
        // The Room account_binding row is authoritative; this preference is a
        // compatibility mirror for earlier builds.
        identity(c).edit().putString("account", pending.accountHash).apply()
        val now = System.currentTimeMillis()
        prefs(c).edit().putLong("success_at", now)
            .putBoolean("success_background", backgroundRun)
            .putString("catalog_status", pending.catalogStatus)
            .putString("status", "updated").apply()
        reconcile(c)
        return SecondClassReadOutcome.Saved(
            now, pending.scoreCount, pending.activityCount, pending.catalogStatus
        )
    }
}

/** WorkManager may wake periodically; the policy only permits a real read every 12 hours. */
class SecondClassAutoReadWorker(c: Context, params: WorkerParameters) : CoroutineWorker(c, params) {
    override suspend fun doWork(): Result {
        SecondClassAutoRefresh.refreshIfDue(applicationContext, backgroundRun = true)
        return Result.success()
    }
}
