package io.github.x07xh.xiaoxu.data

import java.net.URI

/** Navigation only. Business extraction retains its separate exact-path gate. */
object ChaoxingWebNavigation {
    fun target(raw: String?): String? = runCatching {
        val uri = URI(raw ?: return null)
        val host = uri.host?.lowercase() ?: return null
        if (host != "chaoxing.com" && !host.endsWith(".chaoxing.com")) return null
        if (uri.rawUserInfo != null || uri.port !in listOf(-1, 80, 443)) return null
        when (uri.scheme?.lowercase()) {
            "https" -> if (uri.port == 80) null else raw
            "http" -> if (uri.port == 443) null else
                "https://" + host + (uri.rawPath ?: "") +
                    (uri.rawQuery?.let { "?$it" } ?: "") +
                    (uri.rawFragment?.let { "#$it" } ?: "")
            else -> null
        }
    }.getOrNull()
}
