package io.github.x07xh.xiaoxu.data

import java.net.URI
import java.net.URLDecoder

/** Only the existing official GET SSO route may reuse WebView's private jar.
 * Business requests and owner verification remain in the fixed score reader. */
internal object SecondClassAutoSessionNavigation {
    const val SCORE = "https://dekt.ncwu.edu.cn/sys/stu/score"
    const val CAS = "https://authserver.ncwu.edu.cn/authserver/login?service=https%3A%2F%2Fsec.ncwu.edu.cn%2Frump_frontend%2FloginFromCas%2F"
    private const val SERVICE = "https://sec.ncwu.edu.cn/rump_frontend/loginFromCas/"

    enum class Page { SCORE, GATEWAY, CAS, CALLBACK, LANDING }

    fun page(raw: String?, method: String = "GET"): Page? = runCatching {
        if (method != "GET" || raw.isNullOrBlank()) return null
        val u = URI(raw)
        if (u.scheme != "https" || u.rawUserInfo != null ||
            u.port !in setOf(-1, 443) || u.rawFragment != null) return null
        val fields = u.rawQuery?.split('&')?.map {
            val pair = it.split('=', limit = 2)
            URLDecoder.decode(pair[0], "UTF-8") to URLDecoder.decode(pair.getOrElse(1) { "" }, "UTF-8")
        }.orEmpty()
        if (fields.map { it.first }.distinct().size != fields.size) return null
        val query = fields.toMap()
        when (u.host?.lowercase()) {
            "dekt.ncwu.edu.cn", "sec.ncwu.edu.cn" -> when (u.rawPath) {
                "/sys/stu/score" -> if (u.host == "dekt.ncwu.edu.cn" && query.isEmpty()) Page.SCORE else null
                "/rump_frontend/login/" -> if (query.keys.all { it == "next" } &&
                    query.values.all { it == "/sys/stu/score" || it == "/" }) Page.GATEWAY else null
                "/rump_frontend/loginFromCas/" -> if (u.host == "sec.ncwu.edu.cn" &&
                    query.keys.all { it == "ticket" } &&
                    query.values.all { it.matches(Regex("^[A-Za-z0-9._~-]{1,512}$")) }) Page.CALLBACK else null
                "/" -> if (u.host == "dekt.ncwu.edu.cn" && query.isEmpty()) Page.LANDING else null
                else -> null
            }
            "authserver.ncwu.edu.cn" -> if (u.rawPath == "/authserver/login" &&
                query.keys == setOf("service") && query["service"] == SERVICE) Page.CAS else null
            else -> null
        }
    }.getOrNull()
}
