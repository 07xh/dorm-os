package io.github.x07xh.xiaoxu.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.net.URL
import java.security.KeyStore
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val BASE = "https://dorm-os-dk9.pages.dev"
private const val COOKIE_NAME = "__Host-xiaoxu_session"
private const val KEY_ALIAS = "xiaoxu_owner_cookie_aes_v1"

data class CloudTask(
    val id: String, val title: String, val status: String,
    val kind: String, val provider: String?, val due: String?,
    val revision: Int = -1,
    val links: List<String> = emptyList(),
    /** Stable source tuple from the trusted school assignment ledger. */
    val sourceExternalId: String? = null,
    /** Last trusted platform state, independent of the user's local done flag. */
    val officialStatus: String? = null,
    /** Course label from the imported/verified source; NOT identity proof. */
    val sourceCourse: String? = null
)

/** Prevent an in-flight older GET snapshot from undoing an already confirmed owner POST.
 * Only a strictly newer locally confirmed WeChat revision wins; newer server
 * revisions still take precedence. No synthetic records are ever added.
 */
internal fun preferConfirmedWechatRevisions(
    fetched: List<CloudTask>, shown: List<CloudTask>
): List<CloudTask> {
    val confirmed = shown.filter { it.provider == "wechat" && it.revision >= 0 }
        .associateBy { it.id }
    return fetched.map { row ->
        val local = confirmed[row.id]
        if (row.provider == "wechat" && local != null && local.revision > row.revision)
            local else row
    }
}

private val ChaoxingWorkSourceId = Regex("""work:\d{1,24}:\d{1,24}:\d{1,24}""")
private val ChaoxingPlatformStatuses =
    setOf("todo", "submitted", "graded", "returned", "unknown")

/** Only trusted source-table status + exact school work tuple can be displayed. */
internal fun trustedChaoxingPlatformStatus(
    provider: String?, externalId: String?, status: String?
): String? = status?.takeIf {
    provider == "chaoxing" &&
        externalId != null && ChaoxingWorkSourceId.matches(externalId) &&
        it in ChaoxingPlatformStatuses
}

/** Parse the optional V1 link contract without making unrelated tasks fail. */
private fun readCloudLinks(row: JSONObject): List<String> {
    val values = row.optJSONArray("links") ?: return emptyList()
    val raw = (0 until minOf(values.length(), 3)).mapNotNull { values.opt(it) as? String }
    return safeCloudLinks(raw)
}

data class VerifiedLesson(
    val id: String, val term: String, val title: String, val teacher: String,
    val location: String, val weekday: Int, val startSection: Int,
    val endSection: Int, val weeks: List<Int>
)
data class AcademicNotice(
    val id: String, val title: String, val category: String,
    val dateLabel: String, val url: String
)
/** Owner-only extracted candidates; NOT verified school coursework or full WeChat chat history. */
data class WechatCandidate(
    val id: String, val kind: String, val title: String,
    val excerpt: String?, val chatLabel: String?, val sentAt: String,
    val duePrecision: String, val dueLocalDate: String?,
    val links: List<String> = emptyList()
)
data class PlatformConnection(
    val id: String, val name: String, val status: String,
    val sessionState: String, val authorization: String,
    val lastSuccessAt: String?,
    val autoSyncAvailable: Boolean = false,
    val autoSyncEnabled: Boolean = false,
    val autoSyncNextAt: String? = null,
    val autoSyncLastRunAt: String? = null,
    val autoSyncLastResult: String? = null,
    val autoSyncFailureCount: Int = 0
) {
    val verified: Boolean
        get() = authorization == "verified_session" &&
            sessionState == "active" && status in setOf("connected", "partial")
}
data class CloudUiState(
    val busy: Boolean = false, val signingIn: Boolean = false,
    val authenticated: Boolean = false,
    val authMessage: String = "云端连接可选，本地任务无需登录",
    val privateError: String? = null, val publicError: String? = null,
    val notices: List<AcademicNotice> = emptyList(),
    val noticeFetchedAt: String? = null, val noticesPartial: Boolean = false,
    val tasks: List<CloudTask> = emptyList(),
    // True only after the current owner-scoped assignments response was
    // successfully parsed, independently of WeChat or school notices.
    val tasksFresh: Boolean = false,
    // Per-source freshness of the current authenticated API read, not school login proof.
    val connectionsFresh: Boolean = false,
    val platforms: List<PlatformConnection> = emptyList(),
    val pendingWechatCount: Int? = null, val lastSyncAt: String? = null,
    val wechatCandidates: List<WechatCandidate> = emptyList(),
    val wechatError: String? = null, val wechatReviewingId: String? = null,
    val secondClass: SecondClassSnapshot? = null,
    val secondClassFresh: Boolean = false,
    val secondClassBusy: Boolean = false,
    val secondClassError: String? = null,
    val schoolSyncBusyProvider: String? = null,
    val schoolSyncResultProvider: String? = null,
    val schoolSyncMessage: String? = null
)

/** Keystore-encrypted token for the existing owner login, never a school credential. */
private class SessionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("xiaoxu_owner_v1", Context.MODE_PRIVATE)
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }
    fun save(token: String, expiresAt: String) {
        require(Regex("^[A-Za-z0-9_-]{43}$").matches(token))
        val end = Instant.parse(expiresAt).toEpochMilli()
        require(end > System.currentTimeMillis())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        prefs.edit().putString("cipher", Base64.getEncoder().encodeToString(
            cipher.doFinal(token.toByteArray(Charsets.UTF_8))))
            .putString("iv", Base64.getEncoder().encodeToString(cipher.iv))
            .putLong("expires", end).apply()
    }
    fun load(): String? {
        if (prefs.getLong("expires", 0) <= System.currentTimeMillis()) {
            clear()
            return null
        }
        return try {
            val iv = Base64.getDecoder().decode(prefs.getString("iv", "")!!)
            val data = Base64.getDecoder().decode(prefs.getString("cipher", "")!!)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(data), Charsets.UTF_8)
                .takeIf { Regex("^[A-Za-z0-9_-]{43}$").matches(it) }
        } catch (_: Exception) {
            clear()
            null
        }
    }
    fun clear() { prefs.edit().clear().apply() }
}
private data class HttpResult(val code: Int, val json: JSONObject, val cookie: String?)

/**
 * First-party only: reject redirects and non-JSON responses before parsing.
 * Never send private data to arbitrary URLs. School auth stays on existing service.
 */
class CloudClient(context: Context) {
    private val session = SessionStore(context)
    var state by mutableStateOf(CloudUiState())
        private set
    private var sessionEpoch: Long = 0L
    // Out-of-order HTTPS results must not overwrite a newer owner snapshot.
    private var privateSnapshotReadId: Long = 0L
    private var secondClassReadId: Long = 0L
    private var secondClassSnapshotReadId: Long = -1L

    /** Hash of this App's owner session, never the Cookie itself. */
    fun ownerSessionBinding(): String? = session.load()?.let { token ->
        Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8))
        )
    }

    private fun request(path: String, method: String = "GET",
                        body: JSONObject? = null, authenticated: Boolean = false): HttpResult {
        require(path.startsWith("/") && !path.startsWith("//") && !path.contains("://"))
        val conn = (URL(BASE + path).openConnection() as HttpsURLConnection).apply {
            connectTimeout = 12000
            readTimeout = 18000
            instanceFollowRedirects = false
            useCaches = false
            requestMethod = method
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Cache-Control", "no-store")
            if (authenticated) {
                val token = session.load()
                if (token != null) setRequestProperty("Cookie", COOKIE_NAME + "=" + token)
            }
            if (body != null) {
                setRequestProperty("Origin", BASE)
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                doOutput = true
            }
        }
        return try {
            if (body != null) conn.outputStream.use {
                it.write(body.toString().toByteArray(Charsets.UTF_8))
            }
            val code = conn.responseCode
            if (code in 300..399) throw IllegalStateException("redirect")
            if (!(conn.contentType ?: "").lowercase().contains("application/json"))
                throw IllegalStateException("non_json")
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val bytes = stream?.use {
                val result = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val n = it.read(buffer)
                    if (n < 0) break
                    if (result.size() + n > 524288) throw IllegalStateException("too_large")
                    result.write(buffer, 0, n)
                }
                result.toByteArray()
            } ?: ByteArray(0)
            val sessionCookie = conn.headerFields.entries
                .firstOrNull { it.key?.equals("Set-Cookie", ignoreCase = true) == true }
                ?.value?.firstOrNull { it.startsWith(COOKIE_NAME + "=") }
            HttpResult(code, JSONObject(String(bytes, Charsets.UTF_8)), sessionCookie)
        } finally { conn.disconnect() }
    }

    // Foreground reopening refreshes the existing Cloudflare snapshots only.
    // It does NOT run any school login, Chaoxing job, or autonomous CAS sync.
    private var lastForegroundRefreshMs: Long = -1L

    suspend fun refreshOnForeground() {
        val now = android.os.SystemClock.elapsedRealtime()
        if (state.busy || (lastForegroundRefreshMs >= 0L &&
                now - lastForegroundRefreshMs < 60_000L)) return
        lastForegroundRefreshMs = now
        refresh()
    }

    suspend fun refresh() {
        if (state.busy || state.schoolSyncBusyProvider != null) return
        lastForegroundRefreshMs = android.os.SystemClock.elapsedRealtime()
        state = state.copy(busy = true)
        try {
            // Public announcement latency must not block owner task refresh.
            // Each coroutine updates its distinct CloudUiState fields on main.
            coroutineScope {
                launch { refreshNotices() }
                launch { refreshPrivate() }
            }
        } finally { state = state.copy(busy = false) }
    }

    private suspend fun refreshNotices() {
        // Read the existing live API and the already-published archive concurrently.
        // Neither request uses school account credentials or adds a second UI.
        val (liveResult, archiveResult) = coroutineScope {
            val live = async(Dispatchers.IO) { runCatching { request("/api/notices") } }
            val archive = async(Dispatchers.IO) { runCatching { request("/data/notices.json") } }
            live.await() to archive.await()
        }
        val live = liveResult.getOrNull()?.takeIf {
            it.code == 200 && it.json.optBoolean("success")
        }
        val archive = archiveResult.getOrNull()?.takeIf { it.code == 200 }?.let {
            runCatching { PublicNoticeArchive.parse(it.json) }.getOrNull()
        }
        if (live == null && archive == null) {
            state = state.copy(publicError = "通知获取失败；显示的旧内容不代表最新信息")
            return
        }
        val byUrl = LinkedHashMap<String, AcademicNotice>()
        if (live != null) {
            val rows = live.json.optJSONArray("items")
            if (rows != null) {
                for (i in 0 until minOf(rows.length(), 45)) {
                    val item = rows.optJSONObject(i) ?: continue
                    val url = item.optString("url")
                    val u = try { URL(url) } catch (_: Exception) { null }
                    if (u?.protocol != "https" || u.host != "www2.ncwu.edu.cn" ||
                        !u.path.startsWith("/jiaowu/info/")) continue
                    val title = item.optString("title").take(220)
                    if (title.isBlank()) continue
                    byUrl.putIfAbsent(url, AcademicNotice(item.optString("id"),
                        title, item.optString("category"), item.optString("dateLabel"), url))
                }
            }
        }
        // Live official notices take priority; archived sources fill historical and
        // departmental gaps. Identical article URLs appear only once.
        for (item in archive?.items.orEmpty()) byUrl.putIfAbsent(item.url, item)
        val archiveOutdated = archive?.let {
            runCatching { Instant.parse(it.fetchedAt).isBefore(Instant.now().minusSeconds(12 * 3600)) }
                .getOrDefault(true)
        } ?: true
        state = state.copy(
            notices = byUrl.values.take(240),
            noticeFetchedAt = live?.json?.optString("fetchedAt")?.takeIf { it.isNotBlank() }
                ?: archive?.fetchedAt,
            noticesPartial = live == null || archive == null || archiveOutdated ||
                live.json.optBoolean("partial"),
            publicError = if (live == null) "实时通知暂不可用，显示最近一次有效归档" else null
        )
    }

    suspend fun refreshPrivate() {
        val epoch = sessionEpoch
        val snapshotRead = ++privateSnapshotReadId
        // The previous snapshot remains visible, but a failed current read
        // must never certify that a school session is still active.
        state = state.copy(tasksFresh = false, connectionsFresh = false,
            secondClassFresh = false)
        // Android Keystore decryption may block; keep it off main.
        if (withContext(Dispatchers.IO) { session.load() } == null) {
            state = clearAccountSnapshots().copy(authenticated = false, authMessage = "云端未连接",
                tasks = emptyList(), platforms = emptyList(), pendingWechatCount = null,
                privateError = null, lastSyncAt = null,
                secondClass = null, secondClassError = null, secondClassBusy = false)
            return
        }
        try {
            val auth = withContext(Dispatchers.IO) {
                request("/api/v1/auth/me", authenticated = true)
            }
            if (epoch != sessionEpoch || snapshotRead != privateSnapshotReadId) return
            if (auth.code == 401) {
                sessionEpoch++
                session.clear()
                state = clearAccountSnapshots().copy(authenticated = false, authMessage = "会话过期，请重新连接",
                    tasks = emptyList(), platforms = emptyList(), pendingWechatCount = null,
                    secondClass = null, secondClassError = null, secondClassBusy = false)
                return
            }
            if (auth.code != 200 || !auth.json.optBoolean("authenticated")) {
                // An explicit authorization denial must not leave private snapshots
                // visible under a falsely disconnected account.
                sessionEpoch++
                state = clearAccountSnapshots().copy(
                    authenticated = false, privateError = "云端认证不可用",
                    tasks = emptyList(), platforms = emptyList(), pendingWechatCount = null,
                    secondClass = null,
                    secondClassBusy = false, secondClassError = null
                )
                return
            }
        } catch (_: Exception) {
            if (epoch == sessionEpoch && snapshotRead == privateSnapshotReadId)
                state = state.copy(privateError = "网络或登录服务异常，无法验证云端会话")
            return
        }
        if (epoch != sessionEpoch || snapshotRead != privateSnapshotReadId) return
        state = state.copy(authenticated = true, authMessage = "云端登录有效")
        val failed = mutableListOf<String>()
        var tasks: List<CloudTask>? = null
        var platforms: List<PlatformConnection>? = null
        var candidates: List<WechatCandidate>? = null
        var candidateLoadFailed = false
        // The owner task ledger can contain more than 100 records after a
        // Chaoxing multi-course sync. Read every bounded API page before
        // certifying tasksFresh; a partial response never replaces the old list.
        try {
            val pages = CloudAssignmentPages()
            while (!pages.complete) {
                // Account changes or a newer refresh invalidate all remaining pages.
                if (epoch != sessionEpoch || snapshotRead != privateSnapshotReadId) return
                val offset = pages.nextOffset
                val r = withContext(Dispatchers.IO) {
                    request("/api/v1/assignments?limit=100&offset=$offset",
                        authenticated = true)
                }
                if (epoch != sessionEpoch || snapshotRead != privateSnapshotReadId) return
                if (r.code != 200) throw IllegalStateException("assignments_page_failed")
                val array = r.json.getJSONArray("items")
                if (array.length() > 100) throw IllegalStateException("oversized_page")
                val rows = buildList {
                    for (i in 0 until array.length()) {
                        val o = array.getJSONObject(i)
                        val provider = o.optString("source_provider")
                            .takeIf { it.isNotBlank() && it != "null" }
                        val due = o.optString("due_at_utc")
                            .takeIf { it.isNotBlank() && it != "null" }
                            ?: o.optString("due_local_date")
                                .takeIf { it.isNotBlank() && it != "null" }
                        val revision = o.optLong("revision", -1L)
                            .takeIf { it in 0..Int.MAX_VALUE.toLong() }?.toInt() ?: -1
                        val sourceId = o.optString("source_external_id")
                            .takeIf { provider == "chaoxing" &&
                                (ChaoxingWorkSourceId.matches(it) ||
                                    it.matches(Regex("cxn:[a-f0-9]{40}"))) }
                        val official = trustedChaoxingPlatformStatus(
                            provider, sourceId, o.optString("platform_status")
                        )
                        val sourceCourse = (o.opt("source_course") as? String)
                            ?.trim()?.takeIf { it.isNotBlank() && provider == "chaoxing" }
                            ?.take(80)
                        add(CloudTask(o.getString("id"), o.optString("title").take(120),
                            o.optString("local_status"), o.optString("kind"), provider, due,
                            revision, if (provider == "wechat") readCloudLinks(o) else emptyList(),
                            sourceId, official, sourceCourse))
                    }
                }
                pages.append(rows, r.json.getInt("limit"), r.json.getInt("offset"))
            }
            tasks = pages.verifiedRows()
        } catch (_: Exception) {
            // Preserve prior snapshots without falsely marking the current
            // school-linked tasks as freshly and completely loaded.
            failed.add("云端任务完整分页读取")
        }
        try {
            val r = withContext(Dispatchers.IO) {
                request("/api/v1/connections", authenticated = true)
            }
            if (r.code != 200) throw IllegalStateException()
            val array = r.json.getJSONArray("platforms")
            platforms = buildList {
                for (i in 0 until array.length()) {
                    val o = array.optJSONObject(i) ?: continue
                    val id = o.optString("id")
                    val auto = o.optJSONObject("autoSync")
                    val isChaoxingAuto = id == "chaoxing" && auto != null
                    val trustedAuto = isChaoxingAuto && o.optBoolean("autoSyncAvailable")
                    add(PlatformConnection(
                        id = id, name = o.optString("name"),
                        status = o.optString("status"),
                        sessionState = o.optString("sessionState"),
                        authorization = o.optString("accountAuthorization"),
                        lastSuccessAt = o.optString("lastSuccessAt")
                            .takeIf { it.isNotBlank() && it != "null" },
                        autoSyncAvailable = trustedAuto,
                        autoSyncEnabled = isChaoxingAuto && auto!!.optBoolean("enabled"),
                        autoSyncNextAt = if (isChaoxingAuto) auto!!.optString("nextAt")
                            .takeIf { it.isNotBlank() && it != "null" } else null,
                        autoSyncLastRunAt = if (isChaoxingAuto) auto!!.optString("lastRunAt")
                            .takeIf { it.isNotBlank() && it != "null" } else null,
                        autoSyncLastResult = if (isChaoxingAuto) auto!!.optString("lastResult")
                            .takeIf { it in setOf("success","partial","failed","expired","idle") }
                            else null,
                        autoSyncFailureCount = if (isChaoxingAuto)
                            auto!!.optInt("failureCount", 0).coerceIn(0, 100) else 0
                    ))
                }
            }
        } catch (_: Exception) { failed.add("连接状态") }
        // Local-first timetable: do not poll or hydrate a server timetable
        // during cloud refresh. Grades/tasks/connections keep their own remote flow.
        // Explicit local import is the only source of displayed lessons.
        try {
            val r = withContext(Dispatchers.IO) {
                request("/api/v1/wechat/candidates?limit=30&status=needs_review", authenticated = true)
            }
            if (r.code != 200 || r.json.optString("status") != "personal_candidates")
                throw IllegalStateException("invalid_candidates_response")
            val rows = r.json.getJSONArray("items")
            if (rows.length() > 30) throw IllegalStateException("too_many_candidates")
            val validId = Regex("^[A-Za-z0-9_-]{12,128}$")
            val seen = mutableSetOf<String>()
            candidates = buildList {
                for (i in 0 until rows.length()) {
                    val row = rows.getJSONObject(i)
                    val id = row.getString("id")
                    val title = row.getString("title")
                    val kind = row.getString("kind")
                    val at = row.getString("sentAt")
                    val precision = row.getString("duePrecision")
                    val due = row.optString("dueLocalDate").takeIf { it.isNotBlank() && it != "null" }
                    if (!validId.matches(id) || !seen.add(id) ||
                        title.isBlank() || title.length > 120 ||
                        kind !in setOf("homework", "exam", "activity", "signup", "other") ||
                        row.optString("reviewStatus") != "needs_review" ||
                        precision !in setOf("unknown", "date", "exact") ||
                        (precision != "unknown" && due.isNullOrBlank()) ||
                        (due != null && !Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(due)))
                        throw IllegalStateException("invalid_candidate")
                    Instant.parse(at)
                    val excerpt = row.optString("excerpt")
                        .takeIf { it.isNotBlank() && it != "null" }?.take(240)
                    val chat = row.optString("chatLabel")
                        .takeIf { it.isNotBlank() && it != "null" }?.take(80)
                    add(WechatCandidate(id, kind, title, excerpt, chat, at, precision, due,
                        readCloudLinks(row)))
                }
            }
        } catch (_: Exception) {
            candidateLoadFailed = true
            failed.add("微信候选")
        }

        // Logout AND newer overlapping refreshes invalidate this result.
        // Only the newest request can certify school-connection freshness.
        if (epoch != sessionEpoch || snapshotRead != privateSnapshotReadId) return
        state = state.copy(
            tasks = tasks?.let { preferConfirmedWechatRevisions(it, state.tasks) }
                ?: state.tasks,
            tasksFresh = tasks != null,
            platforms = platforms ?: state.platforms,
            connectionsFresh = platforms != null,
            wechatCandidates = candidates ?: state.wechatCandidates,
            pendingWechatCount = candidates?.size ?: state.pendingWechatCount,
            wechatError = if (candidateLoadFailed) "微信候选读取失败，列表可能过期，请重试" else null,
            privateError = if (failed.isEmpty()) null else failed.joinToString("、") + "刷新失败，部分结果可能过期",
            lastSyncAt = if (failed.isEmpty()) Instant.now().toString() else state.lastSyncAt
        )
        loadSecondClass()
    }

    /**
     * Trigger the existing authenticated, owner-scoped readonly school refresh.
     * This is NOT a school login, CAS bridge or a browser Cookie extractor.
     * Never mark success until the server confirms refreshed=true.
     */
    suspend fun syncAuthorizedSchool(provider: String): Boolean {
        // The legacy NCWU POST refreshes timetable records, not grades.
        // Never call it for the new local-only timetable workflow.
        if (provider != "second_class" ||
            !state.authenticated || state.busy || state.schoolSyncBusyProvider != null) return false
        val linked = state.platforms.firstOrNull { it.id == provider }
        if (linked?.sessionState != "active") {
            state = state.copy(
                schoolSyncResultProvider = provider,
                schoolSyncMessage = "学校会话未生效；请先在官方连接中心完成本人授权")
            return false
        }
        val epoch = sessionEpoch
        val label = if (provider == "ncwu") "青果教务" else "第二课堂"
        state = state.copy(schoolSyncBusyProvider = provider, schoolSyncResultProvider = provider,
            schoolSyncMessage = null)
        try {
            val result = withContext(Dispatchers.IO) {
                request("/api/v1/connections/$provider/sync", "POST", JSONObject(),
                    authenticated = true)
            }
            if (epoch != sessionEpoch) return false
            if (result.code != 200 || !result.json.optBoolean("refreshed") ||
                result.json.optString("provider") != provider) {
                val code = result.json.optString("error")
                state = state.copy(schoolSyncMessage = when (code) {
                    "session_expired", "authorization_failed" ->
                        "$label 学校会话过期，请在官方连接中心重新授权"
                    "review_pending" ->
                        "青果课表仍等待本人核对，请先完成课表预览确认"
                    "interactive_login_required" ->
                        "$label 需要在学校官方页面完成人机验证或登录"
                    "school_login_unavailable", "not_configured" ->
                        "$label 连接器或安全会话配置不可用"
                    "database_unavailable" ->
                        "校序存储服务暂时不可用；未覆盖旧数据"
                    "sync_failed", "upstream_unavailable" ->
                        "$label 上游数据读取失败；旧记录保持不变"
                    else -> "未确认 $label 同步成功（HTTP ${result.code}），请核查连接状态"
                })
                if (result.code == 401) refreshPrivate()
                return false
            }
            // Server claims a successful owner-scoped read, but the UI still needs
            // to verify fresh trusted snapshots through the existing GET endpoints.
            state = state.copy(schoolSyncMessage = "$label 已返回只读刷新成功；正在核对可信快照")
            refreshPrivate()
            if (epoch != sessionEpoch) return false
            // Do not let an unrelated WeChat/task API outage reject a fresh
            // trusted school read, or let old cached school rows prove success.
            val verified = state.connectionsFresh &&
                state.platforms.firstOrNull { it.id == provider }?.verified == true &&
                state.secondClassFresh &&
                state.secondClass?.status == "ready" &&
                state.secondClass?.provenance == "trusted_readonly"
            state = state.copy(schoolSyncResultProvider = provider,
                schoolSyncMessage = if (verified)
                    "$label 已取得可信连接状态；请与学校官网逐项对照"
                else
                    "$label 上游刷新已返回，但可信数据状态尚未确认；请核查连接中心")
            return verified
        } catch (_: Exception) {
            if (epoch == sessionEpoch) state = state.copy(schoolSyncResultProvider = provider,
                schoolSyncMessage = "$label 网络或接口响应异常；不能确认同步成功，旧数据不受影响")
            return false
        } finally {
            if (epoch == sessionEpoch) state = state.copy(schoolSyncBusyProvider = null)
        }
    }


    /**
     * One-time browser return is only a navigation event, never a grant.
     * Reuse the existing encrypted owner session + verified school API to
     * read actual records. Do not trust the browser or return URI as proof.
     */
    suspend fun completeSchoolReturn(provider: String) {
        if (provider == "chaoxing") {
            state = state.copy(schoolSyncResultProvider = provider,
                schoolSyncMessage = "学习通使用本机官方登录与读取，请在数据连接中打开。")
            return
        }
        if (provider !in setOf("ncwu", "second_class")) return
        val epoch = sessionEpoch
        state = state.copy(schoolSyncResultProvider = provider,
            schoolSyncMessage = "已返回校序；正在通过本人会话核验学校授权和真实数据")
        // Read the current owner /auth/me and fresh provider connection state.
        refreshPrivate()
        if (epoch != sessionEpoch) return
        if (!state.authenticated) {
            state = state.copy(schoolSyncResultProvider = provider,
                schoolSyncMessage = "校序本人会话失效；请在 App 内重新连接后再尝试")
            return
        }
        if (!state.connectionsFresh) {
            state = state.copy(schoolSyncResultProvider = provider,
                schoolSyncMessage = "无法核验最新学校连接状态，暂不显示授权成功")
            return
        }
        val platform = state.platforms.firstOrNull { it.id == provider }
        if (platform?.sessionState != "active") {
            state = state.copy(schoolSyncResultProvider = provider,
                schoolSyncMessage = if (platform?.sessionState == "review_pending")
                    "青果课表仍需要在学校连接中心确认预览；未标记已连接"
                else "尚未证实学校本人授权；请完成官方登录后再返回")
            return
        }
        syncAuthorizedSchool(provider)

    }

    suspend fun reviewWechatCandidate(candidateId: String, decision: String): Boolean {
        if (!state.authenticated || state.wechatReviewingId != null ||
            decision !in setOf("accept", "dismiss") ||
            !Regex("^[A-Za-z0-9_-]{12,128}$").matches(candidateId) ||
            state.wechatCandidates.none { it.id == candidateId }) return false
        val epoch = sessionEpoch
        state = state.copy(wechatReviewingId = candidateId, wechatError = null)
        try {
            val result = withContext(Dispatchers.IO) {
                request("/api/v1/wechat/candidates/$candidateId", "POST",
                    JSONObject().put("decision", decision), authenticated = true)
            }
            if (epoch != sessionEpoch) return false
            if (result.code != 200 ||
                result.json.optString("id") != candidateId ||
                result.json.optString("status") != if (decision == "accept") "accepted" else "dismissed") {
                state = state.copy(wechatError = when (result.code) {
                    401 -> "校序登录状态已过期，请在「我的」重新连接"
                    409 -> "该候选已在别处审核，请刷新列表"
                    404 -> "候选已不存在，请刷新列表"
                    403 -> "没有审核权限，请核查校序登录状态"
                    else -> "审核未成功（HTTP ${result.code}），请重试"
                })
                if (result.code == 401) refreshPrivate()
                return false
            }
            // A confirmed server mutation may disappear from the current list. Refetch
            // both the queue and task ledger; if that fails the UI labels snapshots stale.
            state = state.copy(
                wechatCandidates = state.wechatCandidates.filterNot { it.id == candidateId },
                pendingWechatCount = state.pendingWechatCount?.let { (it - 1).coerceAtLeast(0) }
            )
            refreshPrivate()
            return true
        } catch (_: Exception) {
            if (epoch == sessionEpoch)
                state = state.copy(wechatError = "审核请求失败，状态未确认；请先刷新以免重复操作")
            return false
        } finally {
            if (epoch == sessionEpoch) state = state.copy(wechatReviewingId = null)
        }
    }

    /**
     * Reads the existing owner-protected D1 snapshot only.
     * No campus login, school API probe, background polling or status mutation.
     */
    suspend fun loadSecondClass() {
        if (!state.authenticated ||
            (state.secondClassBusy && secondClassSnapshotReadId == privateSnapshotReadId)) return
        val epoch = sessionEpoch
        val snapshotRead = privateSnapshotReadId
        val readId = ++secondClassReadId
        secondClassSnapshotReadId = snapshotRead
        state = state.copy(secondClassBusy = true, secondClassFresh = false,
            secondClassError = null)
        try {
            val response = withContext(Dispatchers.IO) {
                request("/api/v1/second-class", authenticated = true)
            }
            if (epoch != sessionEpoch || readId != secondClassReadId ||
                snapshotRead != privateSnapshotReadId) return
            if (response.code == 401) {
                sessionEpoch++
                session.clear()
                state = clearAccountSnapshots().copy(
                    authenticated = false,
                    authMessage = "校序会话已过期，请重新连接",
                    tasks = emptyList(), platforms = emptyList(), pendingWechatCount = null,
                    secondClass = null, secondClassError = null, secondClassBusy = false
                )
                return
            }
            if (response.code != 200) throw IllegalStateException("secondclass_http_error")
            val snapshot = parseSecondClassSnapshot(response.json)
            state = state.copy(secondClass = snapshot, secondClassFresh = true,
                secondClassError = null)
        } catch (_: Exception) {
            if (epoch == sessionEpoch && readId == secondClassReadId &&
                snapshotRead == privateSnapshotReadId) {
                state = state.copy(
                    secondClassError = if (state.secondClass == null)
                        "第二课堂个人记录读取失败，无法确认是否已连接"
                        else "第二课堂刷新失败；展示的是上次读取的数据，不代表实时信息"
                )
            }
        } finally {
            if (epoch == sessionEpoch && readId == secondClassReadId)
                state = state.copy(secondClassBusy = false)
        }
    }

    private fun clearAccountSnapshots(): CloudUiState = state.copy(
        tasksFresh = false, connectionsFresh = false,
        secondClassFresh = false, wechatCandidates = emptyList(), wechatError = null, wechatReviewingId = null,
        schoolSyncBusyProvider = null, schoolSyncResultProvider = null, schoolSyncMessage = null
    )

    suspend fun signIn(passphrase: String): Boolean {
        if (passphrase.isBlank() || state.signingIn) return false
        state = state.copy(signingIn = true)
        return try {
            val r = withContext(Dispatchers.IO) {
                request("/api/v1/auth/login", "POST",
                    JSONObject().put("passphrase", passphrase).put("durationDays", 7))
            }
            if (r.code != 200 || !r.json.optBoolean("authenticated")) {
                state = state.copy(authMessage = when (r.code) {
                    401 -> "校序口令不正确"
                    429 -> "尝试次数过多，请稍后重试"
                    503 -> "云端登录服务不可用"
                    else -> "登录失败，HTTP " + r.code
                })
                false
            } else {
                val token = Regex("^" + COOKIE_NAME + "=([A-Za-z0-9_-]{43})(?:;|$)")
                    .find(r.cookie ?: "")?.groupValues?.get(1)
                    ?: throw IllegalStateException("missing_cookie")
                withContext(Dispatchers.IO) {
                    session.save(token, r.json.getString("expiresAt"))
                }
                sessionEpoch++
                state = clearAccountSnapshots().copy(
                    authenticated = true, authMessage = "校序云端连接成功",
                    secondClass = null, secondClassError = null, secondClassBusy = false
                )
                refreshPrivate()
                true
            }
        } catch (_: Exception) {
            state = state.copy(authMessage = "连接失败，请检查网络、登录配置或登录响应")
            false
        } finally { state = state.copy(signingIn = false) }
    }

    /** Only a personally approved WeChat task may change its own completion status.
     * Owner-scoped status-only POST accepts a revision and never touches school submissions.
     */
    suspend fun setWechatTaskCompleted(id: String, revision: Int, completed: Boolean): Boolean {
        if (!state.authenticated || !state.tasksFresh || revision < 0 ||
            !Regex("^[A-Za-z0-9_-]{1,100}$").matches(id)) return false
        val current = state.tasks.firstOrNull {
            it.id == id && it.provider == "wechat" && it.revision == revision
        } ?: return false
        if (current.status == "done" && completed ||
            current.status != "done" && !completed) return true
        val epoch = sessionEpoch
        return try {
            val body = JSONObject().put("revision", revision)
                .put("localStatus", if (completed) "done" else "todo")
            val response = withContext(Dispatchers.IO) {
                request("/api/v1/assignments/$id", "POST", body, authenticated = true)
            }
            if (epoch != sessionEpoch) return false
            if (response.code != 200 ||
                response.json.optJSONObject("item")?.optString("id") != id) {
                if (response.code in setOf(401, 409)) refreshPrivate()
                if (epoch == sessionEpoch) state = state.copy(privateError = when (response.code) {
                    401 -> "登录状态已过期，请重新连接"
                    409 -> "任务已被其他设备更新，请刷新后重试"
                    else -> "微信任务状态更新失败，请重试"
                })
                return false
            }
            // The owner-scoped POST only mutates localStatus and returns the
            // committed revision. Updating this one row avoids a second full
            // refresh (auth + paged ledger + connections + candidates + school).
            val item = response.json.getJSONObject("item")
            val committedRevision = item.optLong("revision", -1L)
            if (item.optBoolean("unchanged", true) ||
                committedRevision != revision.toLong() + 1L ||
                committedRevision > Int.MAX_VALUE) {
                state = state.copy(privateError =
                    "云端可能已更新，但版本未能确认，请手动刷新")
                return false
            }
            val currentRow = state.tasks.firstOrNull {
                it.id == id && it.provider == "wechat" && it.revision == revision
            }
            if (currentRow == null || !state.authenticated) {
                state = state.copy(privateError = "任务状态发生变化，请刷新后重试")
                return false
            }
            // Present the owner-confirmed mutation immediately. An older GET
            // is reconciled by revision in refreshPrivate, without cancelling
            // unrelated school connection and candidate refreshes.
            state = state.copy(
                tasks = state.tasks.map {
                    if (it.id == id && it.provider == "wechat" && it.revision == revision)
                        it.copy(status = if (completed) "done" else "todo",
                            revision = committedRevision.toInt())
                    else it
                },
                privateError = state.privateError?.takeUnless {
                    it.startsWith("微信任务状态更新失败") ||
                        it.startsWith("任务状态发生变化") ||
                        it.startsWith("云端可能已更新")
                }
            )
            true
        } catch (_: Exception) {
            if (epoch == sessionEpoch)
                state = state.copy(privateError = "微信任务状态更新失败，请刷新后重试")
            false
        }
    }

    suspend fun addCloudTask(title: String): Boolean {
        if (!state.authenticated || title.isBlank() || title.trim().length > 120) return false
        return try {
            val body = JSONObject().put("title", title.trim())
                .put("localStatus", "todo").put("deadline", JSONObject().put("precision", "unknown"))
            val r = withContext(Dispatchers.IO) {
                request("/api/v1/assignments", "POST", body, authenticated = true)
            }
            if (r.code != 201) throw IllegalStateException()
            refreshPrivate()
            true
        } catch (_: Exception) {
            state = state.copy(privateError = "新增云端任务失败；没有写入本地")
            false
        }
    }

    suspend fun signOut() {
        sessionEpoch++
        state = clearAccountSnapshots()
        try {
            withContext(Dispatchers.IO) {
                request("/api/v1/auth/logout", "POST", JSONObject(), authenticated = true)
            }
        } catch (_: Exception) { /* Always clear local token even if offline. */ }
        session.clear()
        state = state.copy(authenticated = false, authMessage = "已退出校序云端",
            tasks = emptyList(), platforms = emptyList(), pendingWechatCount = null,
            wechatCandidates = emptyList(), wechatError = null, wechatReviewingId = null,
            privateError = null, lastSyncAt = null,
            secondClass = null, secondClassError = null, secondClassBusy = false)
    }
}
