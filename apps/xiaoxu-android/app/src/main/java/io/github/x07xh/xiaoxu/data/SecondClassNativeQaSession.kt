package io.github.x07xh.xiaoxu.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.webkit.CookieManager
import java.net.URI
import javax.net.ssl.HttpsURLConnection

/**
 * Explicit, isolated QA only. Use the same application's official school
 * session, never another app's cookie jar, passwords, CAS tickets or exports.
 * No authorization material is stored in preferences or returned to UI.
 */
internal object SecondClassNativeQaSession {
    private const val QA_PACKAGE = "io.github.x07xh.xiaoxu.unifiedqa"
    private const val CONSENT = "native_qa_confirmed_account"
    private const val SCHOOL = "dekt.ncwu.edu.cn"

    fun available(context: Context): Boolean =
        context.packageName == QA_PACKAGE &&
            context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    private fun settings(c: Context) =
        c.getSharedPreferences(SecondClassAutoRefresh.SETTINGS, Context.MODE_PRIVATE)

    private fun confirmedAccount(c: Context): String? =
        c.getSharedPreferences("second_class_local_identity", Context.MODE_PRIVATE)
            .getString("account", null)
            ?.takeIf { SecondClassAutoReadPolicy.matchesConfirmedAccount(it, it) }

    fun enabled(c: Context): Boolean {
        val account = confirmedAccount(c)
        return available(c) && account != null && settings(c).getString(CONSENT, null) == account
    }

    fun configure(c: Context, enabled: Boolean) {
        if (!available(c)) return
        val account = confirmedAccount(c)
        val edit = settings(c).edit()
        if (enabled && account != null) edit.putString(CONSENT, account)
        else edit.remove(CONSENT)
        edit.apply()
    }

    fun clear(c: Context) { settings(c).edit().remove(CONSENT).apply() }

    internal fun acquire(c: Context, expected: String): SecondClassAuthorizedHttpSession? {
        if (!enabled(c)) return null
        if (confirmedAccount(c) != expected) throw AutoReadFailure("needs_confirmation")
        return object : SecondClassAuthorizedHttpSession {
            private fun checkedUrl(connection: HttpsURLConnection): String {
                val uri = connection.url.toURI()
                require(uri.scheme == "https" && uri.host == SCHOOL &&
                    uri.port in setOf(-1, 443) && uri.rawUserInfo == null &&
                    uri.rawQuery == null && uri.rawFragment == null &&
                    SecondClassReadEndpoint.entries.any { it.path == uri.rawPath })
                if (!enabled(c) || confirmedAccount(c) != expected)
                    throw AutoReadFailure("needs_confirmation")
                return uri.toString()
            }

            override fun authorize(connection: HttpsURLConnection) {
                val url = checkedUrl(connection)
                // Android WebView's own app-private cookie manager; no copy to
                // disk, clipboard, diagnostics, Intent, other host or service.
                val header = CookieManager.getInstance().getCookie(url)
                val scoped = selectSecondClassSessionCookie(header)
                    ?: throw AutoReadFailure("needs_reconnect")
                connection.setRequestProperty("Cookie", scoped)
            }

            override fun acceptResponse(connection: HttpsURLConnection) {
                val url = checkedUrl(connection)
                // Permit only a scoped sid rotation from the fixed official origin.
                val updates = connection.headerFields.entries
                    .filter { it.key?.equals("Set-Cookie", ignoreCase = true) == true }
                    .flatMap { it.value.orEmpty() }
                    .filter(::isScopedSecondClassSessionUpdate)
                if (updates.isNotEmpty()) {
                    val manager = CookieManager.getInstance()
                    updates.forEach { manager.setCookie(url, it) }
                    manager.flush()
                }
            }
        }
    }
}

/** Keep only the documented session cookie; ambiguous or malformed values fail closed. */
internal fun selectSecondClassSessionCookie(header: String?): String? {
    val matches = header.orEmpty().split(';').map { it.trim() }
        .filter { it.substringBefore('=') == "sid" }
    return matches.singleOrNull()?.takeIf {
        it.matches(Regex("sid=[A-Fa-f0-9]{8}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{12}"))
    }
}

/** Never broaden the jar to another host or accept unrelated cookies. */
internal fun isScopedSecondClassSessionUpdate(header: String): Boolean {
    if (header.any { it == '\r' || it == '\n' }) return false
    val fields = header.split(';').map { it.trim() }
    if (fields.firstOrNull()?.substringBefore('=') != "sid") return false
    val value = fields.first().substringAfter('=', "")
    if (value.isNotEmpty() && selectSecondClassSessionCookie(fields.first()) == null) return false
    val domains = fields.drop(1).filter { it.substringBefore('=').equals("domain", true) }
    if (domains.size > 1 || domains.any {
        it.substringAfter('=', "").trim().trimStart('.').lowercase() != "dekt.ncwu.edu.cn"
    }) return false
    return true
}
