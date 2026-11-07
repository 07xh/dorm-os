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
import java.time.LocalDate
import java.time.ZoneId
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
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
    val sourceCourse: String? = null,
    /** Personal planning date, independent from official or source due date. */
    val personalTargetAt: String? = null
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

data class ChaoxingCourse(
    val courseId: String, val classId: String, val cpi: String, val title: String
) {
    val key: String get() = "$courseId:$classId:$cpi"
}
data class ChaoxingHomework(
    val workId: String, val title: String, val statusCode: String,
    val platformVerified: Boolean, val statusText: String
)
data class ChaoxingPageProgress(val completed: Int, val total: Int)
data class ChaoxingQuestion(
    val number: Int, val type: String, val stem: String,
    val options: List<String>, val hasImage: Boolean
)
data class ChaoxingWorkPreview(
    val workId: String, val title: String, val course: String,
    val statusCode: String, val questionsAvailable: Boolean,
    val declaredQuestionCount: Int?, val questions: List<ChaoxingQuestion>,
    val startTimeRaw: String?, val deadlineRaw: String?, val note: String
)
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
/** Opaque encrypted challenge, never a school session. In-memory only. */
data class ChaoxingQrUi(
    val status: String = "idle",
    val message: String? = null,
    val ticket: String? = null,
    val image: ByteArray? = null,
    val startedAt: Long = 0L,
    val expiresAt: Long = 0L,
    val polls: Int = 0,
    val busy: Boolean = false
)

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
    val chaoxingBusy: Boolean = false,
    val chaoxingQr: ChaoxingQrUi = ChaoxingQrUi(),
    val chaoxingBatchBusy: Boolean = false,
    val chaoxingBatchStatus: String? = null,
    /** Per-course official status evidence from the current explicit owner batch only. */
    val chaoxingBatchAudit: List<ChaoxingCourseAudit> = emptyList(),
    val chaoxingBatchCheckedAt: String? = null,
    val chaoxingBatchTaskLedgerFresh: Boolean = false,
    val chaoxingAutoSyncBusy: Boolean = false,
    val chaoxingAutoSyncError: String? = null,
    val chaoxingCourses: List<ChaoxingCourse> = emptyList(),
    val chaoxingSelectedKey: String? = null,
    val chaoxingHomework: List<ChaoxingHomework> = emptyList(),
    val chaoxingCheckedAt: String? = null,
    val chaoxingVerifiedEmpty: Boolean = false,
    val chaoxingPageProgress: ChaoxingPageProgress? = null,
    val chaoxingError: String? = null,
    val chaoxingDetailBusy: Boolean = false,
    val chaoxingPreview: ChaoxingWorkPreview? = null,
    val chaoxingDetailError: String? = null,
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
    private var qrGeneration: Long = 0L
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
        try {
            val r = withContext(Dispatchers.IO) { request("/api/notices") }
            if (r.code != 200 || !r.json.optBoolean("success")) throw IllegalStateException()
            val a = r.json.getJSONArray("items")
            val rows = buildList {
                for (i in 0 until minOf(a.length(), 45)) {
                    val item = a.optJSONObject(i) ?: continue
                    val url = item.optString("url")
                    val u = try { URL(url) } catch (_: Exception) { null }
                    if (u?.protocol != "https" || u.host != "www2.ncwu.edu.cn" ||
                        !u.path.startsWith("/jiaowu/info/")) continue
                    add(AcademicNotice(item.optString("id"), item.optString("title").take(220),
                        item.optString("category"), item.optString("dateLabel"), url))
                }
            }
            state = state.copy(notices = rows,
                noticeFetchedAt = r.json.optString("fetchedAt"),
                noticesPartial = r.json.optBoolean("partial"), publicError = null)
        } catch (_: Exception) {
            state = state.copy(publicError = "通知获取失败；显示的旧内容不代表最新信息")
        }
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
                                ChaoxingWorkSourceId.matches(it) }
                        val official = trustedChaoxingPlatformStatus(
                            provider, sourceId, o.optString("platform_status")
                        )
                        val sourceCourse = (o.opt("source_course") as? String)
                            ?.trim()?.takeIf { it.isNotBlank() && provider == "chaoxing" }
                            ?.take(80)
                        val target = (o.opt("personal_target_at") as? String)
                            ?.takeIf { it.isNotBlank() && it.length <= 40 }
                        if (target != null) Instant.parse(target)
                        add(CloudTask(o.getString("id"), o.optString("title").take(120),
                            o.optString("local_status"), o.optString("kind"), provider, due,
                            revision, if (provider == "wechat") readCloudLinks(o) else emptyList(),
                            sourceId, official, sourceCourse, target))
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
        if (provider !in setOf("chaoxing", "ncwu", "second_class")) return
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
        if (provider == "chaoxing") {
            loadChaoxingCourses()
            if (epoch != sessionEpoch) return
            val count = state.chaoxingCourses.size
            if (count > 0 && state.chaoxingError == null) {
                // Only first course, bounded by the existing owner-scoped
                // server endpoint. Never imply every course was synced.
                syncChaoxingCourse()
            }
            if (epoch != sessionEpoch) return
            // Authorization and personal course/work verification are separate.
            // Do not report a verified school sync from a stale session or a
            // course list when no actual first-course work read was confirmed.
            val connectionTrusted = state.platforms.firstOrNull { it.id == provider }
                ?.verified == true
            val workChecked = state.chaoxingCheckedAt != null &&
                state.chaoxingError == null
            state = state.copy(schoolSyncResultProvider = provider,
                schoolSyncMessage = when {
                    !connectionTrusted ->
                        "学校会话已有记录，但尚无可信本人连接与读取证明；请继续核验"
                    count == 0 ->
                        "学习通会话可用，但本人课程读取未成功；未确认接入"
                    state.chaoxingError != null ->
                        "已读取 $count 门课程；首门课程作业尚未核验，仍可在任务页手动检查"
                    !workChecked ->
                        "已读取 $count 门课程，但尚未核验真实作业；请在任务页继续检查"
                    else ->
                        "已读取 $count 门课程并核验首门课程作业；请与官方学习通逐项对照"
                })
        } else {
            // Uses existing POST /connections/{provider}/sync and its
            // trusted GET validation. No new CAS client or school parser.
            syncAuthorizedSchool(provider)
        }
    }

    private val qrTicketShape =
        Regex("""^[A-Za-z0-9_-]{16}\.[A-Za-z0-9_-]{20,23900}$""")

    /** Same-origin binary QR route; upstream school Cookies remain on the server. */
    private fun loadChaoxingQrImage(ticket: String): ByteArray {
        require(qrTicketShape.matches(ticket))
        val conn = (URL(BASE + "/api/v1/connections/chaoxing/qr/image")
            .openConnection() as HttpsURLConnection).apply {
            connectTimeout = 12000
            readTimeout = 18000
            instanceFollowRedirects = false
            useCaches = false
            requestMethod = "POST"
            setRequestProperty("Accept", "image/png, image/jpeg, application/json")
            setRequestProperty("Origin", BASE)
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Cache-Control", "no-store")
            session.load()?.let { setRequestProperty("Cookie", COOKIE_NAME + "=" + it) }
            doOutput = true
        }
        return try {
            conn.outputStream.use {
                it.write(JSONObject().put("ticket", ticket)
                    .toString().toByteArray(Charsets.UTF_8))
            }
            if (conn.responseCode != 200) throw IllegalStateException("image_unavailable")
            val type = (conn.contentType ?: "").substringBefore(';').trim().lowercase()
            if (type !in setOf("image/png", "image/jpeg", "image/svg+xml"))
                throw IllegalStateException("invalid_image_type")
            val result = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            conn.inputStream.use { source ->
                while (true) {
                    val n = source.read(buffer)
                    if (n < 0) break
                    if (result.size() + n > 220000)
                        throw IllegalStateException("image_too_large")
                    result.write(buffer, 0, n)
                }
            }
            val bytes = result.toByteArray()
            if (bytes.size < 64) throw IllegalStateException("image_too_small")
            // School sometimes returns the strictly generated fallback SVG.
            // Convert in memory without sending raw SVG to an embedded browser.
            if (type == "image/svg+xml") return NativeQrSvgRenderer.rasterize(bytes)
            val png = bytes.size >= 8 && bytes.sliceArray(0 until 8)
                .contentEquals(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10))
            val jpeg = bytes.size >= 3 && bytes[0] == (-1).toByte() &&
                bytes[1] == (-40).toByte() && bytes[2] == (-1).toByte()
            if ((type == "image/png" && !png) || (type == "image/jpeg" && !jpeg))
                throw IllegalStateException("invalid_image_magic")
            bytes
        } finally { conn.disconnect() }
    }

    suspend fun startChaoxingQr() {
        if (!state.authenticated || state.busy || state.chaoxingQr.busy) return
        val generation = ++qrGeneration
        val epoch = sessionEpoch
        state = state.copy(chaoxingQr = ChaoxingQrUi(
            status = "loading", busy = true, message = "正在生成官方登录二维码"))
        try {
            val r = withContext(Dispatchers.IO) {
                request("/api/v1/connections/chaoxing/qr/start",
                    "POST", JSONObject(), authenticated = true)
            }
            if (epoch != sessionEpoch || generation != qrGeneration) return
            if (r.code != 200 || r.json.optString("status") != "pending") {
                state = state.copy(chaoxingQr = ChaoxingQrUi(
                    status = "failed", message = when (r.json.optString("error")) {
                        "too_many_attempts" -> "二维码生成次数过多，请稍后重试"
                        "school_login_unavailable" -> "扫码服务不可用，请使用网页备用连接"
                        else -> "二维码未生成（HTTP " + r.code + "），请使用网页备用连接"
                    }))
                return
            }
            val ticket = r.json.getString("ticket")
            val imageUrl = java.net.URI(r.json.getString("qrImageUrl"))
            val expiry = Instant.parse(r.json.getString("expiresAt")).toEpochMilli()
            val now = System.currentTimeMillis()
            if (!qrTicketShape.matches(ticket) ||
                imageUrl.scheme != "https" || imageUrl.host != "passport2.chaoxing.com" ||
                imageUrl.path != "/createqr" || imageUrl.port != -1 ||
                imageUrl.rawUserInfo != null || expiry - now !in 1000L..165000L)
                throw IllegalStateException("invalid_challenge")
            val image = withContext(Dispatchers.IO) { loadChaoxingQrImage(ticket) }
            if (epoch != sessionEpoch || generation != qrGeneration) return
            state = state.copy(chaoxingQr = ChaoxingQrUi(
                status = "pending", ticket = ticket, image = image,
                startedAt = now, expiresAt = expiry,
                message = "请使用另一设备上的官方学习通 App 扫码确认"))
        } catch (e: Exception) {
            if (epoch == sessionEpoch && generation == qrGeneration)
                state = state.copy(chaoxingQr = ChaoxingQrUi(
                    status = "failed",
                    message = "二维码无法可信读取；未建立学校连接，请使用网页备用连接"))
        }
    }

    /** Strictly bounded foreground checking; the server also rate limits. */
    suspend fun pollChaoxingQr() {
        val qr = state.chaoxingQr
        if (!state.authenticated || qr.busy ||
            qr.status !in setOf("pending", "scanned") || qr.ticket == null) return
        if (System.currentTimeMillis() >= qr.expiresAt || qr.polls >= 24) {
            state = state.copy(chaoxingQr = ChaoxingQrUi(
                status = "expired", message = "二维码已过期，请重新生成"))
            return
        }
        val epoch = sessionEpoch
        val generation = qrGeneration
        state = state.copy(chaoxingQr = qr.copy(
            polls = qr.polls + 1, busy = true))
        try {
            val r = withContext(Dispatchers.IO) {
                request("/api/v1/connections/chaoxing/qr/status",
                    "POST", JSONObject().put("ticket", qr.ticket), authenticated = true)
            }
            if (epoch != sessionEpoch || generation != qrGeneration) return
            if (r.code != 200) {
                state = state.copy(chaoxingQr = ChaoxingQrUi(
                    status = "failed", message = when (r.json.optString("error")) {
                        "session_expired" -> "学习通二维码已过期，请重新生成"
                        "authorization_failed" -> "官方授权未通过，请重新扫码"
                        "too_many_attempts" -> "检查次数过多，请稍后再试"
                        else -> "登录状态无法确认（HTTP " + r.code + "）"
                    }))
                return
            }
            when (r.json.optString("status")) {
                "pending", "scanned" -> {
                    val ticket = r.json.getString("ticket")
                    if (!qrTicketShape.matches(ticket))
                        throw IllegalStateException("invalid_ticket")
                    val scanned = r.json.optString("status") == "scanned"
                    state = state.copy(chaoxingQr = qr.copy(
                        ticket = ticket, polls = qr.polls + 1, busy = false,
                        status = if (scanned) "scanned" else "pending",
                        message = if (scanned) "已扫码，请在官方学习通 App 确认"
                            else "等待官方学习通扫码"))
                }
                "connected", "authenticated_pending_sync" -> {
                    state = state.copy(chaoxingQr = ChaoxingQrUi(
                        status = "verifying", busy = true,
                        message = "官方扫码已确认，正在核验真实课程与作业"))
                    completeSchoolReturn("chaoxing")
                    if (epoch != sessionEpoch || generation != qrGeneration) return
                    val verified = state.connectionsFresh &&
                        state.platforms.firstOrNull { it.id == "chaoxing" }
                        ?.verified == true && state.chaoxingCourses.isNotEmpty() &&
                        state.chaoxingCheckedAt != null && state.chaoxingError == null
                    state = state.copy(chaoxingQr = ChaoxingQrUi(
                        status = if (verified) "verified" else "pending_sync",
                        message = if (verified)
                            "已读本人课程并检查首门课程作业；请与官网逐项核对"
                        else "扫码已确认，但本人课程或作业仍需可信核验"))
                }
                "cancelled" -> state = state.copy(chaoxingQr = ChaoxingQrUi(
                    status = "cancelled", message = "已取消扫码授权"))
                else -> throw IllegalStateException("invalid_status")
            }
        } catch (_: Exception) {
            if (epoch == sessionEpoch && generation == qrGeneration)
                state = state.copy(chaoxingQr = qr.copy(
                    polls = qr.polls + 1, busy = false,
                    message = "检查失败，尚未确认学校授权，可重试"))
        } finally {
            if (epoch == sessionEpoch && generation == qrGeneration &&
                state.chaoxingQr.busy &&
                state.chaoxingQr.status in setOf("pending", "scanned"))
                state = state.copy(chaoxingQr = state.chaoxingQr.copy(busy = false))
        }
    }

    fun cancelChaoxingQr() {
        qrGeneration++
        state = state.copy(chaoxingQr = ChaoxingQrUi())
    }

    /**
     * Reviews a real cloud candidate using the existing owner session and same-origin POST.
     * Never sends school credentials or accepts a message automatically.
     */
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

    private fun clearAccountSnapshots(): CloudUiState = emptyChaoxing().copy(
        tasksFresh = false, connectionsFresh = false,
        secondClassFresh = false, wechatCandidates = emptyList(), wechatError = null, wechatReviewingId = null,
        schoolSyncBusyProvider = null, schoolSyncResultProvider = null, schoolSyncMessage = null
    )

    private fun emptyChaoxing(): CloudUiState = state.copy(
        chaoxingCourses = emptyList(),
        chaoxingQr = ChaoxingQrUi(),
        chaoxingSelectedKey = null,
        chaoxingHomework = emptyList(),
        chaoxingCheckedAt = null,
        chaoxingVerifiedEmpty = false,
        chaoxingPageProgress = null,
        chaoxingError = null,
        chaoxingBusy = false,
        chaoxingBatchBusy = false,
        chaoxingBatchStatus = null,
        chaoxingBatchAudit = emptyList(),
        chaoxingBatchCheckedAt = null,
        chaoxingBatchTaskLedgerFresh = false,
        chaoxingAutoSyncBusy = false,
        chaoxingAutoSyncError = null,
        chaoxingPreview = null, chaoxingDetailBusy = false, chaoxingDetailError = null
    )

    private fun chaoxingFailure(code: String, http: Int): String = when (code) {
        "session_expired", "authorization_failed" ->
            "学习通会话失效，请到校序连接中心重新登录本人账号"
        "interactive_login_required" ->
            "学习通要求官方登录或人机验证，请在官方页面完成后重试"
        "courses_unverified" ->
            "尚未核实本人课程所属学校和课程列表，不显示猜测结果"
        "school_login_unavailable", "not_configured" ->
            "学习通服务端适配器未就绪"
        "work_not_available" ->
            "官方作业页未开放或无法验证，已有任务保持不变"
        "work_list_incomplete" ->
            "官网声明的作业总数与实际读取条数不一致，已阻止本次同步；旧任务未覆盖。请检查官网筛选或分页。"
        "database_unavailable" -> "同步数据库暂时不可用，旧任务保持不变"
        else -> if (http == 401) "校序云端会话过期，请重新连接"
               else "学校接口暂时无法读取（HTTP $http），旧记录保持不变"
    }

    fun chooseChaoxingCourse(key: String) {
        if (state.chaoxingBusy || state.chaoxingDetailBusy) return
        if (state.chaoxingCourses.none { it.key == key }) return
        state = state.copy(
            chaoxingSelectedKey = key, chaoxingHomework = emptyList(),
            chaoxingCheckedAt = null, chaoxingVerifiedEmpty = false,
            chaoxingPageProgress = null, chaoxingError = null,
            chaoxingPreview = null, chaoxingDetailError = null
        )
    }

    fun clearChaoxingDetail() {
        if (!state.chaoxingDetailBusy) {
            state = state.copy(chaoxingPreview = null, chaoxingDetailError = null)
        }
    }

    /**
     * Controls the EXISTING hourly Worker opt-in for an authorized owner.
     * Enabling needs an already owner-decryptable Chaoxing session.
     * Turning it off remains permitted after the school session expires.
     */
    suspend fun setChaoxingAutoSync(enabled: Boolean) {
        if (!state.authenticated || state.chaoxingAutoSyncBusy || state.busy) return
        val current = state.platforms.firstOrNull { it.id == "chaoxing" } ?: return
        if (enabled && (!current.autoSyncAvailable || current.sessionState != "active")) return
        if (!enabled && !current.autoSyncEnabled) return
        val epoch = sessionEpoch
        state = state.copy(chaoxingAutoSyncBusy = true, chaoxingAutoSyncError = null)
        try {
            val result = withContext(Dispatchers.IO) {
                request(
                    "/api/v1/connections/chaoxing/auto-sync", "POST",
                    JSONObject().put("enabled", enabled), authenticated = true
                )
            }
            if (epoch != sessionEpoch) return
            if (result.code != 200) {
                val error = when (result.json.optString("error")) {
                    "session_expired" -> "学习通授权已过期；需要在官网完成本人重新授权"
                    "autosync_not_ready" -> "云端定时同步尚未配置，仍可手动读取"
                    "forbidden_origin_or_format" -> "请求验证失败，定时设置未更改"
                    "database_unavailable" -> "云端数据库暂时不可用，定时设置未确认"
                    else -> if (result.code == 401) "校序云端登录已失效"
                        else "自动同步设置失败（HTTP " + result.code + "），原设置不变"
                }
                state = state.copy(chaoxingAutoSyncError = error)
                if (result.code == 401) refreshPrivate()
                return
            }
            if (result.json.getBoolean("enabled") != enabled ||
                result.json.getInt("intervalMinutes") != 60)
                throw IllegalStateException("invalid_autosync_result")
            // The server confirmed the opt-in, not a successful school read.
            state = state.copy(platforms = state.platforms.map {
                if (it.id == "chaoxing") it.copy(autoSyncEnabled = enabled) else it
            })
            refreshPrivate()
        } catch (_: Exception) {
            if (epoch == sessionEpoch) state = state.copy(
                chaoxingAutoSyncError = "未确认自动同步设置，请刷新校序云端状态后检查"
            )
        } finally {
            if (epoch == sessionEpoch) state = state.copy(chaoxingAutoSyncBusy = false)
        }
    }

    /** Explicit read; never poll the school's session merely because the screen opened. */
    suspend fun loadChaoxingCourses() {
        if (!state.authenticated || state.chaoxingBusy || state.chaoxingDetailBusy) return
        val epoch = sessionEpoch
        state = state.copy(chaoxingBusy = true, chaoxingError = null,
            chaoxingBatchStatus = null, chaoxingBatchAudit = emptyList(),
            chaoxingBatchCheckedAt = null, chaoxingBatchTaskLedgerFresh = false,
            chaoxingPreview = null, chaoxingDetailError = null)
        try {
            val response = withContext(Dispatchers.IO) {
                request("/api/v1/chaoxing-homework?view=courses", authenticated = true)
            }
            if (epoch != sessionEpoch) return
            if (response.code != 200) {
                val err = response.json.optString("error")
                state = emptyChaoxing().copy(
                    chaoxingBusy = true,
                    chaoxingError = chaoxingFailure(err, response.code)
                )
                return
            }
            val root = response.json
            if (root.optString("schema") != "xiaoxu/chaoxing-work-browser-v1" ||
                root.optString("view") != "courses" || !root.optBoolean("live")) {
                throw IllegalStateException("invalid_schema")
            }
            val array = root.getJSONObject("data").getJSONArray("courses")
            if (array.length() !in 1..120) throw IllegalStateException("invalid_courses")
            val ids = Regex("^\\d{1,24}$")
            val courses = buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val id = item.getString("courseId")
                    val clazz = item.getString("classId")
                    val cpi = item.getString("cpi")
                    val title = item.getString("title").trim()
                    if (!ids.matches(id) || !ids.matches(clazz) || !ids.matches(cpi) ||
                        title.isBlank() || title.length > 100) throw IllegalStateException("invalid_course")
                    add(ChaoxingCourse(id, clazz, cpi, title))
                }
            }
            if (courses.map { it.key }.toSet().size != courses.size)
                throw IllegalStateException("duplicate_course")
            state = state.copy(
                chaoxingCourses = courses,
                chaoxingSelectedKey = courses.first().key,
                chaoxingHomework = emptyList(), chaoxingCheckedAt = null,
                chaoxingVerifiedEmpty = false, chaoxingPageProgress = null,
                chaoxingError = null
            )
        } catch (_: Exception) {
            if (epoch == sessionEpoch) state = state.copy(
                chaoxingError = "官方课程读取失败或结构不可信，旧课程不会冒充最新结果",
                chaoxingCourses = emptyList(), chaoxingSelectedKey = null,
                chaoxingHomework = emptyList(), chaoxingCheckedAt = null
            )
        } finally {
            if (epoch == sessionEpoch) state = state.copy(chaoxingBusy = false)
        }
    }

    /**
     * Owner-triggered resume path after an App restart: re-read official courses
     * before using the EXISTING per-course read-only sync. No school polling on
     * app open, no parallel calls, and no new login/API/database.
     *
     * A resumed Cloudflare owner token is NOT proof of a usable school session.
     * Both calls independently verify their school-scoped responses; a failed
     * course-list read must never fall through to stale homework records.
     */
    suspend fun refreshChaoxingCoursesAndHomework() {
        if (!state.authenticated || state.busy || state.chaoxingBusy ||
            state.chaoxingBatchBusy || state.chaoxingDetailBusy ||
            state.schoolSyncBusyProvider != null ||
            state.platforms.none {
                it.id == "chaoxing" && it.sessionState == "active"
            }) return
        val epoch = sessionEpoch
        state = state.copy(
            chaoxingBatchBusy = true,
            chaoxingBatchStatus = "开始重新核验本人课程与作业；不会自动提交或代替官网验收",
            chaoxingPageProgress = null
        )
        try {
            loadChaoxingCourses()
            if (epoch != sessionEpoch) return
            if (state.chaoxingError != null || state.chaoxingCourses.isEmpty()) {
                state = state.copy(
                    chaoxingBatchStatus =
                        "本人课程列表未通过核验，因此没有继续读取作业；旧云端任务保持不变。"
                )
                return
            }
            // Already loaded authenticated course IDs, class IDs and CPI.
            // Reuse the bounded sequential owner-scoped sync unchanged.
            syncChaoxingAllCourses()
        } finally {
            if (epoch == sessionEpoch)
                state = state.copy(chaoxingBatchBusy = false)
        }
    }

    /** Existing official per-course response guard, reused by one-course and explicit batch reads. */
    private fun verifiedChaoxingWork(
        root: JSONObject, selected: ChaoxingCourse
    ): Triple<List<ChaoxingHomework>, Boolean, ChaoxingPageProgress?> {
            if (root.optString("schema") != "xiaoxu/chaoxing-work-browser-v1" ||
                root.optString("view") != "list" || !root.optBoolean("live"))
                throw IllegalStateException("invalid_schema")
            val data = root.getJSONObject("data")
            val course = data.getJSONObject("course")
            if (course.optString("courseId") != selected.courseId ||
                course.optString("classId") != selected.classId ||
                course.optString("cpi") != selected.cpi) throw IllegalStateException("wrong_course")
            val raw = data.getJSONArray("items")
            if (raw.length() > 200) throw IllegalStateException("too_many")
            val verifiedEmpty = data.getBoolean("verifiedEmpty")
            if ((raw.length() == 0) != verifiedEmpty) throw IllegalStateException("incomplete")
            val ids = Regex("^\\d{1,24}$")
            val statuses = setOf("todo","submitted","graded","returned","unknown")
            val work = buildList {
                for (i in 0 until raw.length()) {
                    val item = raw.getJSONObject(i)
                    val id = item.getString("workId")
                    val title = item.getString("title").trim()
                    val status = item.getString("statusCode")
                    if (!ids.matches(id) || title.isBlank() || title.length > 100 ||
                        status !in statuses || !item.optBoolean("platformVerified"))
                        throw IllegalStateException("unverified_work")
                    add(ChaoxingHomework(
                        id,title,status,true,item.optString("status").take(60)
                    ))
                }
            }
            if (work.map { it.workId }.toSet().size != work.size)
                throw IllegalStateException("duplicate_work")
            val rawProgress = data.opt("progress")
            val progress = when (rawProgress) {
                null, JSONObject.NULL -> null
                is JSONObject -> {
                    val done = rawProgress.opt("completed")
                    val total = rawProgress.opt("total")
                    if (rawProgress.length() != 2 || done !is Int || total !is Int ||
                        total !in 0..200 || done !in 0..total || total != work.size)
                        throw IllegalStateException("unverified_homework_total")
                    ChaoxingPageProgress(done, total)
                }
                else -> throw IllegalStateException("invalid_homework_progress")
            }
            // 200 OK does not mean that the trusted D1 connector committed.
            // The existing POST sync route returns this result only after its
            // owner-bound atomic transaction. Reject incomplete acknowledgements.
            val saved = data.getJSONObject("sync")
            if (saved.optString("provider") != "chaoxing" ||
                saved.optString("coverage") != "partial" ||
                saved.optInt("received", -1) != work.size ||
                saved.optInt("pages", -1) !in 1..11)
                throw IllegalStateException("unverified_homework_persistence")
            return Triple(work, verifiedEmpty, progress)
    }

    /** Official per-course read + server-side verified task-status persistence. */
    suspend fun syncChaoxingCourse() {
        if (!state.authenticated || state.chaoxingBusy || state.chaoxingDetailBusy) return
        val selected = state.chaoxingCourses.firstOrNull {
            it.key == state.chaoxingSelectedKey
        } ?: return
        val epoch = sessionEpoch
        state = state.copy(
            chaoxingBusy = true, chaoxingError = null,
            chaoxingBatchStatus = null,
            chaoxingBatchAudit = emptyList(), chaoxingBatchCheckedAt = null,
            chaoxingBatchTaskLedgerFresh = false,
            chaoxingCheckedAt = null, chaoxingHomework = emptyList(),
            chaoxingVerifiedEmpty = false, chaoxingPageProgress = null,
            chaoxingPreview = null, chaoxingDetailError = null
        )
        try {
            val body = JSONObject()
                .put("courseId", selected.courseId)
                .put("classId", selected.classId)
                .put("cpi", selected.cpi)
            val response = withContext(Dispatchers.IO) {
                request("/api/v1/chaoxing-homework/sync", "POST", body, authenticated = true)
            }
            if (epoch != sessionEpoch || state.chaoxingSelectedKey != selected.key) return
            if (response.code != 200) {
                state = state.copy(
                    chaoxingError = chaoxingFailure(response.json.optString("error"), response.code)
                )
                return
            }
            val (work, verifiedEmpty, progress) = verifiedChaoxingWork(response.json, selected)
            state = state.copy(
                chaoxingHomework = work,
                chaoxingCheckedAt = Instant.now().toString(),
                chaoxingVerifiedEmpty = verifiedEmpty,
                chaoxingPageProgress = progress, chaoxingError = null
            )
            // The server has saved only verified statuses; reload first-party task data.
            refreshPrivate()
        } catch (_: Exception) {
            if (epoch == sessionEpoch) state = state.copy(
                chaoxingError = "该课程作业读取或保存失败，未把未核验状态标为已完成",
                chaoxingHomework = emptyList(), chaoxingCheckedAt = null
            )
        } finally {
            if (epoch == sessionEpoch) state = state.copy(chaoxingBusy = false)
        }
    }

    /**
     * Explicit, sequential one-tap read of the current owner's verified courses.
     * Each course uses the EXISTING read-only POST endpoint and the same response
     * validation as single-course sync. No parallel logins, answer submission,
     * invented deadlines, or new credentials. Partial results are never called full sync.
     */
    suspend fun syncChaoxingAllCourses() {
        val courses = state.chaoxingCourses.toList()
        if (!state.authenticated || state.busy || state.chaoxingBusy ||
            state.chaoxingDetailBusy || state.schoolSyncBusyProvider != null ||
            courses.isEmpty()) return
        val epoch = sessionEpoch
        var checked = 0
        var successful = 0
        var failed = 0
        var consecutiveFailures = 0
        var stoppedForAuth = false
        val audit = mutableListOf<ChaoxingCourseAudit>()
        state = state.copy(chaoxingBusy = true, chaoxingBatchBusy = true,
            chaoxingBatchStatus = "正在核验第 1 / ${courses.size} 门课程；仅同步本人可验证的作业状态",
            chaoxingBatchAudit = emptyList(), chaoxingBatchCheckedAt = null,
            chaoxingBatchTaskLedgerFresh = false,
            chaoxingPageProgress = null, chaoxingCheckedAt = null,
            chaoxingHomework = emptyList(), chaoxingVerifiedEmpty = false,
            chaoxingError = null)
        try {
            for (course in courses) {
                if (epoch != sessionEpoch) return
                val result = try {
                    val body = JSONObject().put("courseId", course.courseId)
                        .put("classId", course.classId).put("cpi", course.cpi)
                    withContext(Dispatchers.IO) {
                        request("/api/v1/chaoxing-homework/sync", "POST", body,
                            authenticated = true)
                    }
                } catch (_: Exception) {
                    failed++
                    checked++
                    consecutiveFailures++
                    audit.add(ChaoxingCourseAudit(
                        course.key, course.title, ChaoxingCourseCoverage.FAILED, emptyList()
                    ))
                    state = state.copy(
                        chaoxingBatchAudit = audit.toList(),
                        chaoxingBatchStatus =
                            "已检查 $checked / ${courses.size} 门：成功 $successful，失败 $failed"
                    )
                    if (consecutiveFailures >= 2) break // Avoid hammering an offline upstream.
                    continue
                }
                if (epoch != sessionEpoch) return
                if (result.code == 401 || result.json.optString("error") in
                    setOf("session_expired", "authorization_failed", "interactive_login_required")) {
                    stoppedForAuth = true
                    break
                }
                // Record individual official statuses ONLY after the same strict
                // provider, count, pagination, row and persistence checks used
                // for one-course sync. A 200 response alone is not proof.
                val validated = if (result.code == 200) {
                    try {
                        verifiedChaoxingWork(result.json, course)
                    } catch (_: Exception) { null }
                } else null
                if (validated != null) {
                    val (work, _, progress) = validated
                    audit.add(ChaoxingCourseAudit(
                        course.key, course.title,
                        if (progress != null) ChaoxingCourseCoverage.COUNT_VERIFIED
                        else ChaoxingCourseCoverage.LIST_ONLY,
                        work
                    ))
                    successful++
                    consecutiveFailures = 0
                } else {
                    audit.add(ChaoxingCourseAudit(
                        course.key, course.title, ChaoxingCourseCoverage.FAILED, emptyList()
                    ))
                    failed++
                    consecutiveFailures++
                }
                checked++
                state = state.copy(
                    chaoxingBatchAudit = audit.toList(),
                    chaoxingBatchStatus =
                        "已检查 $checked / ${courses.size} 门：成功 $successful，失败 $failed"
                )
                if (consecutiveFailures >= 2) break
            }
            if (epoch != sessionEpoch) return
            val checkedKeys = audit.map { it.courseKey }.toSet()
            courses.filterNot { it.key in checkedKeys }.forEach { course ->
                audit.add(ChaoxingCourseAudit(
                    course.key, course.title, ChaoxingCourseCoverage.NOT_CHECKED, emptyList()
                ))
            }
            // Once, not after every course: reload the owner's actual task ledger.
            refreshPrivate()
            if (epoch != sessionEpoch || !state.authenticated) return
            val fresh = state.tasksFresh
            val summary = summarizeChaoxingAudit(audit, courses.size, fresh)
            val status = "学习通本次检查 ${summary.checkedCourses} / ${courses.size} 门：" +
                "条数已核验 ${summary.countVerifiedCourses} 门，" +
                "仅列表已核验 ${summary.listOnlyCourses} 门，" +
                "失败 ${summary.failedCourses} 门" +
                (if (summary.notCheckedCourses > 0)
                    "，未检查 ${summary.notCheckedCourses} 门" else "") +
                "；读取可信状态 ${summary.verifiedWorkRows} 条。"
            val note = when {
                stoppedForAuth -> "学校授权已失效或要求交互登录，需在官网重新授权。"
                !fresh -> "云端任务清单刷新失败；不能声称整个任务中心已同步。"
                summary.fullyCoveredAndRefreshed ->
                    "全部课程作业条数与返回记录已逐门核对；仍请与本人官网逐项对照。"
                else -> "本次未证实全部课程作业覆盖，缺失课程或列表不作为已完成。"
            }
            state = state.copy(
                chaoxingBatchAudit = audit.toList(),
                chaoxingBatchCheckedAt = Instant.now().toString(),
                chaoxingBatchTaskLedgerFresh = fresh,
                chaoxingBatchStatus = status + note
            )
        } finally {
            if (epoch == sessionEpoch) state = state.copy(
                chaoxingBusy = false, chaoxingBatchBusy = false)
        }
    }

    /** Single work detail, on demand. No answers, credentials or raw school HTML. */
    suspend fun loadChaoxingWorkDetail(workId: String) {
        if (!state.authenticated || state.chaoxingBusy || state.chaoxingDetailBusy ||
            state.chaoxingCheckedAt == null) return
        val selected = state.chaoxingCourses.firstOrNull {
            it.key == state.chaoxingSelectedKey
        } ?: return
        val work = state.chaoxingHomework.firstOrNull { it.workId == workId }
            ?: return // Never request a work outside the verified selected course list.
        val epoch = sessionEpoch
        state = state.copy(
            chaoxingDetailBusy = true, chaoxingPreview = null, chaoxingDetailError = null
        )
        try {
            val path = "/api/v1/chaoxing-homework?view=detail" +
                "&courseId=" + selected.courseId +
                "&classId=" + selected.classId +
                "&cpi=" + selected.cpi +
                "&workId=" + workId
            val response = withContext(Dispatchers.IO) {
                request(path, authenticated = true)
            }
            if (epoch != sessionEpoch || state.chaoxingSelectedKey != selected.key ||
                state.chaoxingHomework.none { it.workId == workId }) return
            if (response.code != 200) {
                state = state.copy(
                    chaoxingDetailError = chaoxingFailure(
                        response.json.optString("error"), response.code
                    )
                )
                return
            }
            val root = response.json
            if (root.optString("schema") != "xiaoxu/chaoxing-work-browser-v1" ||
                root.optString("view") != "detail" || !root.optBoolean("live")) {
                throw IllegalStateException("invalid_wrapper")
            }
            val data = root.getJSONObject("data")
            if (data.optString("schema") != "xiaoxu/chaoxing-work-preview-v1" ||
                data.optString("source") != "chaoxing" ||
                data.optString("workId") != workId ||
                data.optString("course") != selected.title ||
                data.optString("title") != work.title ||
                data.optString("statusCode") != work.statusCode) {
                throw IllegalStateException("wrong_work")
            }
            val available = data.getBoolean("questionsAvailable")
            val raw = data.getJSONArray("questions")
            if (raw.length() > 120 || (available && raw.length() == 0) ||
                (!available && raw.length() != 0)) throw IllegalStateException("invalid_count")
            val allowedTypes = setOf(
                "multiple_choice", "single_choice", "true_false", "fill_blank",
                "essay", "calculation", "unknown"
            )
            val questions = buildList {
                for (i in 0 until raw.length()) {
                    val q = raw.getJSONObject(i)
                    val number = q.getInt("number")
                    val type = q.getString("type")
                    val stem = q.getString("stem").trim()
                    val optionsRaw = q.getJSONArray("options")
                    if (number != i + 1 || type !in allowedTypes ||
                        stem.isBlank() || stem.length > 2000 || optionsRaw.length() > 30)
                        throw IllegalStateException("invalid_question")
                    val options = buildList {
                        for (j in 0 until optionsRaw.length()) {
                            val option = optionsRaw.getString(j).trim()
                            if (option.isBlank() || option.length > 1000)
                                throw IllegalStateException("invalid_option")
                            add(option)
                        }
                    }
                    add(ChaoxingQuestion(number, type, stem, options, q.getBoolean("hasImage")))
                }
            }
            val declaredCount = if (data.isNull("declaredQuestionCount")) null
                else data.optInt("declaredQuestionCount", -1)
            if (declaredCount != null && declaredCount !in 0..120)
                throw IllegalStateException("invalid_declared_count")
            // Upstream official detail gives MM-DD HH:mm without a year.
            // No year inference, automatic reminder or fake dueAt persistence.
            val timePattern = Regex("^(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01]) ([01][0-9]|2[0-3]):[0-5][0-9]$")
            fun optionalDetailTime(key: String): String? {
                val raw = data.opt(key)
                if (raw == null || raw == JSONObject.NULL) return null
                if (raw !is String || !timePattern.matches(raw))
                    throw IllegalStateException("invalid_detail_time")
                return raw
            }
            val startTime = optionalDetailTime("startTimeRaw")
            val deadline = optionalDetailTime("deadlineRaw")
            if ((startTime == null) != (deadline == null))
                throw IllegalStateException("incomplete_detail_time")
            state = state.copy(
                chaoxingPreview = ChaoxingWorkPreview(
                    workId, work.title, selected.title, work.statusCode, available,
                    declaredCount, questions, startTime, deadline,
                    data.optString("note").take(200)
                ),
                chaoxingDetailError = null
            )
        } catch (_: Exception) {
            if (epoch == sessionEpoch && state.chaoxingSelectedKey == selected.key) {
                state = state.copy(
                    chaoxingPreview = null,
                    chaoxingDetailError = "该作业页面未能可信读取，未展示猜测题目或答案"
                )
            }
        } finally {
            if (epoch == sessionEpoch) state = state.copy(chaoxingDetailBusy = false)
        }
    }

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

    /**
     * User-specified plan date on an explicitly approved WeChat task.
     * The source deadline and original chat text are immutable. We use
     * the existing owner-scoped POST compatibility endpoint, not PATCH
     * (HttpURLConnection does not reliably support that verb).
     */
    suspend fun setWechatPersonalPlanDate(
        id: String, revision: Int, date: LocalDate?
    ): Boolean {
        if (!state.authenticated || !state.tasksFresh || revision < 0 ||
            !Regex("^[A-Za-z0-9_-]{1,100}$").matches(id)) return false
        val current = state.tasks.firstOrNull {
            it.id == id && it.provider == "wechat" && it.revision == revision
        } ?: return false
        val target = date?.atTime(23, 59)
            ?.atZone(ZoneId.of("Asia/Shanghai"))?.toInstant()?.toString()
        val existingDate = current.personalTargetAt?.let {
            runCatching { Instant.parse(it).atZone(ZoneId.of("Asia/Shanghai")).toLocalDate() }.getOrNull()
        }
        if (existingDate == date) return true
        val epoch = sessionEpoch
        return try {
            val body = JSONObject().put("revision", revision)
                .put("personalTargetAt", target ?: JSONObject.NULL)
            val response = withContext(Dispatchers.IO) {
                request("/api/v1/assignments/$id", "POST", body, authenticated = true)
            }
            if (epoch != sessionEpoch) return false
            val item = response.json.optJSONObject("item")
            if (response.code != 200 || item?.optString("id") != id) {
                if (response.code in setOf(401,409)) refreshPrivate()
                if (epoch == sessionEpoch) state = state.copy(privateError = when (response.code) {
                    401 -> "登录状态已过期，请重新连接"
                    409 -> "任务已被其他设备修改，请刷新后重试"
                    else -> "个人计划日期保存失败，请重试"
                })
                return false
            }
            val newRevision = item.optLong("revision", -1L)
            if (item.optBoolean("unchanged", true) ||
                newRevision != revision.toLong() + 1L ||
                newRevision > Int.MAX_VALUE) {
                state = state.copy(privateError = "云端计划可能已变更，请刷新后核对")
                return false
            }
            if (!state.authenticated || state.tasks.none {
                it.id == id && it.provider == "wechat" && it.revision == revision
            }) return false
            state = state.copy(
                tasks = state.tasks.map {
                    if (it.id == id && it.provider == "wechat" && it.revision == revision)
                        it.copy(personalTargetAt = target, revision = newRevision.toInt())
                    else it
                },
                privateError = null
            )
            true
        } catch (_: Exception) {
            if (epoch == sessionEpoch) state = state.copy(
                privateError = "个人计划日期保存失败，请刷新后重试")
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
        qrGeneration++
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
