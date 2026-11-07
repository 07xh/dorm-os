package io.github.x07xh.xiaoxu

import android.content.Context
import android.net.Uri
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * One-use navigation challenge, NOT a school authorization token.
 * The app verifies the original owner session and reads the existing
 * authenticated /api/v1/connections endpoint after the browser returns.
 * No student ID, CAS ticket, owner Cookie or school Cookie enters a URL.
 */
internal class SchoolAuthReturn(context: Context) {
    // Match the build variant's declared callback host, not a production-only literal.
    private val expectedReturnHost = context.applicationContext.getString(R.string.school_auth_return_host)
    private val prefs = context.applicationContext.getSharedPreferences(
        "xiaoxu_school_auth_handoff_v1", Context.MODE_PRIVATE
    )
    private val providers = setOf("chaoxing", "ncwu", "second_class")
    private val lifetimeMs = 10 * 60 * 1000L

    fun begin(provider: String, ownerBinding: String?): Uri? {
        if (provider !in providers || ownerBinding.isNullOrBlank()) return null
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        val state = Base64.encodeToString(bytes,
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        val saved = prefs.edit().putString("provider", provider)
            .putString("state", state)
            .putString("owner", ownerBinding)
            .putLong("created", System.currentTimeMillis()).commit()
        if (!saved) return null
        // Fragment is deliberately used: the nonce is never sent as a
        // Referer or an HTTP request to school hosts.
        return Uri.parse(
            "https://dorm-os-dk9.pages.dev/#settings" +
                "?xu_app_source=$provider&xu_app_state=$state"
        )
    }

    fun consume(uri: Uri?, ownerBinding: String?): String? {
        if (uri?.scheme != "xiaoxu" || uri.host != expectedReturnHost ||
            uri.port != -1 || !uri.path.isNullOrEmpty() || uri.fragment != null ||
            uri.queryParameterNames != setOf("source", "state") ||
            uri.getQueryParameters("source").size != 1 ||
            uri.getQueryParameters("state").size != 1) return null
        val source = uri.getQueryParameter("source") ?: return null
        val nonce = uri.getQueryParameter("state") ?: return null
        val expected = prefs.getString("state", null) ?: return null
        val savedProvider = prefs.getString("provider", null)
        val savedOwner = prefs.getString("owner", null)
        val created = prefs.getLong("created", 0L)
        // Consume before further processing: the same callback cannot
        // trigger two school data reads even after a process restart.
        if (!prefs.edit().clear().commit()) return null
        val elapsed = System.currentTimeMillis() - created
        if (elapsed !in 0L..lifetimeMs || source !in providers ||
            source != savedProvider || ownerBinding.isNullOrBlank() ||
            ownerBinding != savedOwner ||
            !Regex("^[A-Za-z0-9_-]{43}$").matches(nonce)) return null
        if (!MessageDigest.isEqual(
                nonce.toByteArray(Charsets.US_ASCII),
                expected.toByteArray(Charsets.US_ASCII)
            )) return null
        return source
    }
}
