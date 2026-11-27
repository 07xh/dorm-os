package io.github.x07xh.xiaoxu.data

import android.webkit.CookieManager
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * In-memory bridge to the app's existing official WebView cookie jar.
 *
 * This does not create a login flow, read credential fields, export cookies,
 * persist a second session store, or access any host outside the fixed NCWU
 * second-class read endpoints. The cookie is attached only to the current
 * HTTPS request and response cookies return to the same WebView cookie jar.
 */
internal fun isAllowedSecondClassHttpRequest(url: URL, method: String): Boolean =
    url.protocol == "https" &&
        url.host.equals("dekt.ncwu.edu.cn", ignoreCase = true) &&
        url.port in setOf(-1, 443) &&
        method == "POST" &&
        url.path in SecondClassReadEndpoint.entries.map { it.path }

internal class AndroidSecondClassAuthorizedHttpSession :
    SecondClassAuthorizedHttpSession {

    override fun authorize(connection: HttpsURLConnection) {
        require(isAllowedSecondClassHttpRequest(connection.url, connection.requestMethod))
        val cookie = CookieManager.getInstance().getCookie(connection.url.toString())
            ?.takeIf { it.isNotBlank() }
            ?: throw AutoReadFailure("needs_reconnect")
        // Never log, persist, or return this value to UI / analytics.
        connection.setRequestProperty("Cookie", cookie)
    }

    override fun acceptResponse(connection: HttpsURLConnection) {
        require(connection.url.protocol == "https" &&
            connection.url.host.equals("dekt.ncwu.edu.cn", ignoreCase = true) &&
            connection.url.port in setOf(-1, 443))
        val manager = CookieManager.getInstance()
        connection.headerFields.entries
            .filter { it.key.equals("Set-Cookie", ignoreCase = true) }
            .flatMap { it.value.orEmpty() }
            .forEach { manager.setCookie("https://dekt.ncwu.edu.cn/", it) }
        manager.flush()
    }
}
