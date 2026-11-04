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
    val platforms: List<PlatformConnection> = emptyList(),
    val pendingWechatCount: Int? = null, val lastSyncAt: String? = null
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
            HttpResult(code, JSONObject(String(bytes, Charsets.UTF_8)),
                conn.getHeaderField("Set-Cookie"))
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
        if (session.load() == null) {
            state = state.copy(authenticated = false, authMessage = "云端未连接",
                tasks = emptyList(), platforms = emptyList(), pendingWechatCount = null,
                privateError = null, lastSyncAt = null)
            return
        }
        try {
            val auth = withContext(Dispatchers.IO) {
                request("/api/v1/auth/me", authenticated = true)
            }
            if (auth.code == 401) {
                session.clear()
                state = state.copy(authenticated = false, authMessage = "会话过期，请重新连接",
                    tasks = emptyList(), platforms = emptyList(), pendingWechatCount = null)
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
                request("/api/v1/wechat/candidates?limit=30&status=needs_review", authenticated = true)
            }
            if (r.code != 200) throw IllegalStateException()
            candidates = r.json.getJSONArray("items").length()
        } catch (_: Exception) { failed.add("微信候选") }

        state = state.copy(
            tasks = tasks ?: state.tasks,
            platforms = platforms ?: state.platforms,
            pendingWechatCount = candidates ?: state.pendingWechatCount,
            privateError = if (failed.isEmpty()) null else failed.joinToString("、") + "刷新失败，部分结果可能过期",
            lastSyncAt = if (failed.isEmpty()) Instant.now().toString() else state.lastSyncAt
        )
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
                state = state.copy(authenticated = true, authMessage = "校序云端连接成功")
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
        try {
            withContext(Dispatchers.IO) {
                request("/api/v1/auth/logout", "POST", JSONObject(), authenticated = true)
            }
        } catch (_: Exception) { /* Always clear local token even if offline. */ }
        session.clear()
        state = state.copy(authenticated = false, authMessage = "已退出校序云端",
            tasks = emptyList(), platforms = emptyList(), pendingWechatCount = null,
            privateError = null, lastSyncAt = null)
    }
}
