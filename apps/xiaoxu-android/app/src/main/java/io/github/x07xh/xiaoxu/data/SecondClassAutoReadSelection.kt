package io.github.x07xh.xiaoxu.data

import android.content.Context
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

internal data class SecondClassReadResult(val payload: JSONObject, val transport: String)

/** There is no verified school-issued native session in the production app yet.
 * Only the isolated owner-authorized QA build may attempt the same-app
 * session adapter; normal builds keep the official authorization route. */
internal suspend fun readSecondClassFromAuthorizedSession(
    context: Context, salt: String, expected: String,
    acquire: suspend (Context, String) -> SecondClassAuthorizedHttpSession? = { c, account -> SecondClassNativeQaSession.acquire(c, account) }
): JSONObject? {
    val session = acquire(context.applicationContext, expected) ?: return null
    return withTimeout(90_000) {
        val transport = SecondClassNativeHttpTransport(session)
        readSecondClassNativeHttp(salt, expected, transport::post)
    }
}

/** Select once: if an authorized native read was attempted, errors propagate.
 * Never run an extra WebView read after a native failure or invalid payload. */
internal suspend fun readSecondClassForSync(
    context: Context, salt: String, expected: String,
    nativeReader: suspend (Context, String, String) -> JSONObject?,
    officialReader: suspend (Context, String) -> JSONObject
): SecondClassReadResult {
    require(SecondClassAutoReadPolicy.matchesConfirmedAccount(expected, expected))
    val native = nativeReader(context, salt, expected)
    if (native != null) return SecondClassReadResult(native, "native_http")
    return SecondClassReadResult(officialReader(context, salt), "official_webview")
}
