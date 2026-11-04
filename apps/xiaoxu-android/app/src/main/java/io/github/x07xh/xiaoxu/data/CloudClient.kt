package io.github.x07xh.xiaoxu.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.net.URL
import java.security.KeyStore
import java.time.Instant
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val BASE = "https://dorm-os-dk9.pages.dev"
private const val COOKIE_NAME = "__Host-xiaoxu_session"
private const val KEY_ALIAS = "xiaoxu_owner_cookie_aes_v1"

data class CloudTask(
    val id: String, val title: String, val status: String,
    val kind: String, val provider: String?, val due: String?
)
data class ChaoxingCourse(
    val courseId: String, val classId: String, val cpi: String, val title: String
) {
    val key: String get() = "$courseId:$classId:$cpi"
}
data class ChaoxingHomework(
    val workId: String, val title: String, val statusCode: String,
    val platformVerified: Boolean, val statusText: String
)
data class ChaoxingQuestion(
    val number: Int, val type: String, val stem: String,
    val options: List<String>, val hasImage: Boolean
)
data class ChaoxingWorkPreview(
    val workId: String, val title: String, val course: String,
    val statusCode: String, val questionsAvailable: Boolean,
    val declaredQuestionCount: Int?, val questions: List<ChaoxingQuestion>,
    val note: String
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
data class PlatformConnection(
    val id: String, val name: String, val status: String,
    val sessionState: String, val authorization: String,
    val lastSuccessAt: String?
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
    val lessons: List<VerifiedLesson> = emptyList(),
    val scheduleStatus: String = "not_connected",
    val scheduleFetchedAt: String? = null,
    val platforms: List<PlatformConnection> = emptyList(),
    val pendingWechatCount: Int? = null, val lastSyncAt: String? = null,
    val chaoxingBusy: Boolean = false,
    val chaoxingCourses: List<ChaoxingCourse> = emptyList(),
    val chaoxingSelectedKey: String? = null,
    val chaoxingHomework: List<ChaoxingHomework> = emptyList(),
    val chaoxingCheckedAt: String? = null,
    val chaoxingVerifiedEmpty: Boolean = false,
    val chaoxingError: String? = null,
    val chaoxingDetailBusy: Boolean = false,
    val chaoxingPreview: ChaoxingWorkPreview? = null,
    val chaoxingDetailError: String? = null
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

    suspend fun refresh() {
        if (state.busy) return
        state = state.copy(busy = true)
        try {
            refreshNotices()
            refreshPrivate()
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
        if (session.load() == null) {
            state = emptyChaoxing().copy(authenticated = false, authMessage = "云端未连接",
                tasks = emptyList(), platforms = emptyList(), pendingWechatCount = null,
                lessons = emptyList(), scheduleStatus = "not_connected", scheduleFetchedAt = null,
                privateError = null, lastSyncAt = null)
            return
        }
        try {
            val auth = withContext(Dispatchers.IO) {
                request("/api/v1/auth/me", authenticated = true)
            }
            if (epoch != sessionEpoch) return
            if (auth.code == 401) {
                session.clear()
                state = emptyChaoxing().copy(authenticated = false, authMessage = "会话过期，请重新连接",
                    tasks = emptyList(), platforms = emptyList(), pendingWechatCount = null,
                    lessons = emptyList(), scheduleStatus = "not_connected", scheduleFetchedAt = null)
                return
            }
            if (auth.code != 200 || !auth.json.optBoolean("authenticated")) {
                state = state.copy(authenticated = false, privateError = "云端认证不可用")
                return
            }
        } catch (_: Exception) {
            state = state.copy(privateError = "网络或登录服务异常，无法验证云端会话")
            return
        }
        state = state.copy(authenticated = true, authMessage = "云端登录有效")
        val failed = mutableListOf<String>()
        var tasks: List<CloudTask>? = null
        var platforms: List<PlatformConnection>? = null
        var candidates: Int? = null
        var lessons: List<VerifiedLesson>? = null
        var scheduleStatus: String? = null
        var scheduleFetchedAt: String? = null
        try {
            val r = withContext(Dispatchers.IO) {
                request("/api/v1/assignments?limit=100", authenticated = true)
            }
            if (r.code != 200) throw IllegalStateException()
            val array = r.json.getJSONArray("items")
            tasks = buildList {
                for (i in 0 until array.length()) {
                    val o = array.optJSONObject(i) ?: continue
                    val provider = o.optString("source_provider").takeIf { it.isNotBlank() && it != "null" }
                    val due = o.optString("due_at_utc").takeIf { it.isNotBlank() && it != "null" }
                        ?: o.optString("due_local_date").takeIf { it.isNotBlank() && it != "null" }
                    add(CloudTask(o.optString("id"), o.optString("title").take(120),
                        o.optString("local_status"), o.optString("kind"), provider, due))
                }
            }
        } catch (_: Exception) { failed.add("云端任务") }
        try {
            val r = withContext(Dispatchers.IO) {
                request("/api/v1/connections", authenticated = true)
            }
            if (r.code != 200) throw IllegalStateException()
            val array = r.json.getJSONArray("platforms")
            platforms = buildList {
                for (i in 0 until array.length()) {
                    val o = array.optJSONObject(i) ?: continue
                    add(PlatformConnection(o.optString("id"), o.optString("name"),
                        o.optString("status"), o.optString("sessionState"),
                        o.optString("accountAuthorization"),
                        o.optString("lastSuccessAt").takeIf { it.isNotBlank() && it != "null" }))
                }
            }
        } catch (_: Exception) { failed.add("连接状态") }
        try {
            val r = withContext(Dispatchers.IO) {
                request("/api/v1/schedule", authenticated = true)
            }
            if (r.code != 200) throw IllegalStateException()
            scheduleStatus = r.json.optString("status")
            scheduleFetchedAt = r.json.optString("lastSuccessAt")
                .takeIf { it.isNotBlank() && it != "null" }
            if (scheduleStatus == "ready" || scheduleStatus == "stale") {
                if (r.json.optString("provenance") != "trusted_readonly") {
                    throw IllegalStateException()
                }
                val array = r.json.getJSONArray("records")
                lessons = buildList {
                    for (i in 0 until array.length()) {
                        val row = array.optJSONObject(i) ?: continue
                        val wd = row.optInt("weekday")
                        val start = row.optInt("startSection")
                        val end = row.optInt("endSection")
                        val weeksJson = row.optJSONArray("weeks")
                        if (wd !in 1..7 || start !in 1..24 || end !in start..24 ||
                            weeksJson == null || weeksJson.length() == 0) continue
                        val weeks = (0 until weeksJson.length()).map { weeksJson.optInt(it) }
                        add(VerifiedLesson(
                            row.optString("externalId"), row.optString("term"),
                            row.optString("title"), row.optString("teacher"),
                            row.optString("location"), wd, start, end, weeks
                        ))
                    }
                }
            } else {
                lessons = emptyList()
            }
        } catch (_: Exception) { failed.add("可信课表") }
        try {
            val r = withContext(Dispatchers.IO) {
                request("/api/v1/wechat/candidates?limit=30&status=needs_review", authenticated = true)
            }
            if (r.code != 200) throw IllegalStateException()
            candidates = r.json.getJSONArray("items").length()
        } catch (_: Exception) { failed.add("微信候选") }

        // A logout or new login invalidates any in-flight older read.
        if (epoch != sessionEpoch) return
        state = state.copy(
            tasks = tasks ?: state.tasks,
            lessons = lessons ?: state.lessons,
            scheduleStatus = scheduleStatus ?: state.scheduleStatus,
            scheduleFetchedAt = scheduleFetchedAt ?: state.scheduleFetchedAt,
            platforms = platforms ?: state.platforms,
            pendingWechatCount = candidates ?: state.pendingWechatCount,
            privateError = if (failed.isEmpty()) null else failed.joinToString("、") + "刷新失败，部分结果可能过期",
            lastSyncAt = if (failed.isEmpty()) Instant.now().toString() else state.lastSyncAt
        )
    }

    private fun emptyChaoxing(): CloudUiState = state.copy(
        chaoxingCourses = emptyList(),
        chaoxingSelectedKey = null,
        chaoxingHomework = emptyList(),
        chaoxingCheckedAt = null,
        chaoxingVerifiedEmpty = false,
        chaoxingError = null,
        chaoxingBusy = false,
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
        "database_unavailable" -> "同步数据库暂时不可用，旧任务保持不变"
        else -> if (http == 401) "校序云端会话过期，请重新连接"
               else "学校接口暂时无法读取（HTTP $http），旧记录保持不变"
    }

    fun chooseChaoxingCourse(key: String) {
        if (state.chaoxingBusy || state.chaoxingDetailBusy) return
        if (state.chaoxingCourses.none { it.key == key }) return
        state = state.copy(
            chaoxingSelectedKey = key, chaoxingHomework = emptyList(),
            chaoxingCheckedAt = null, chaoxingVerifiedEmpty = false, chaoxingError = null,
            chaoxingPreview = null, chaoxingDetailError = null
        )
    }

    fun clearChaoxingDetail() {
        if (!state.chaoxingDetailBusy) {
            state = state.copy(chaoxingPreview = null, chaoxingDetailError = null)
        }
    }

    /** Explicit read; never poll the school's session merely because the screen opened. */
    suspend fun loadChaoxingCourses() {
        if (!state.authenticated || state.chaoxingBusy) return
        val epoch = sessionEpoch
        state = state.copy(chaoxingBusy = true, chaoxingError = null,
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
                chaoxingVerifiedEmpty = false, chaoxingError = null
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

    /** Official per-course read + server-side verified task-status persistence. */
    suspend fun syncChaoxingCourse() {
        if (!state.authenticated || state.chaoxingBusy) return
        val selected = state.chaoxingCourses.firstOrNull {
            it.key == state.chaoxingSelectedKey
        } ?: return
        val epoch = sessionEpoch
        state = state.copy(
            chaoxingBusy = true, chaoxingError = null,
            chaoxingCheckedAt = null, chaoxingHomework = emptyList(),
            chaoxingVerifiedEmpty = false,
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
            val root = response.json
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
            state = state.copy(
                chaoxingHomework = work,
                chaoxingCheckedAt = Instant.now().toString(),
                chaoxingVerifiedEmpty = verifiedEmpty,
                chaoxingError = null
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
            state = state.copy(
                chaoxingPreview = ChaoxingWorkPreview(
                    workId, work.title, selected.title, work.statusCode, available,
                    declaredCount, questions, data.optString("note").take(200)
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
                state = emptyChaoxing().copy(authenticated = true, authMessage = "校序云端连接成功")
                refreshPrivate()
                true
            }
        } catch (_: Exception) {
            state = state.copy(authMessage = "连接失败，请检查网络、登录配置或登录响应")
            false
        } finally { state = state.copy(signingIn = false) }
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
        state = emptyChaoxing()
        try {
            withContext(Dispatchers.IO) {
                request("/api/v1/auth/logout", "POST", JSONObject(), authenticated = true)
            }
        } catch (_: Exception) { /* Always clear local token even if offline. */ }
        session.clear()
        state = state.copy(authenticated = false, authMessage = "已退出校序云端",
            tasks = emptyList(), platforms = emptyList(), pendingWechatCount = null,
            lessons = emptyList(), scheduleStatus = "not_connected", scheduleFetchedAt = null,
            privateError = null, lastSyncAt = null)
    }
}
