package io.github.x07xh.xiaoxu.data

import android.webkit.WebView
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.coroutines.resume

/**
 * Executes ONLY known utility read requests in the school's first-party WebView
 * origin after the student signs in there. The school page keeps accessToken
 * and sends it directly to its own /server endpoint; the token is never
 * returned to Kotlin, Intent, logs, the clipboard, or Xiaoxu's cloud.
 *
 * No JS bridge or credential extractor is registered. Android receives only
 * HTTP status and the corresponding utilities response for local decoding.
 */
class CampusUtilitiesWebViewTransport(
    private val webView: WebView,
    private val pageAllowed: () -> Boolean
) : CampusUtilitiesTransport {
    override suspend fun post(
        path: String,
        accessToken: String,
        json: JSONObject
    ): CampusUtilitiesRawResponse = withContext(Dispatchers.Main) {
        require(path in allowedPaths) { "Unsupported utilities route" }
        if (!pageAllowed() || webView.url?.let { isOfficialOrigin(it) } != true) {
            throw CampusUtilitiesException(CampusUtilitiesFailure.LOGIN_REQUIRED)
        }

        val requestId = UUID.randomUUID().toString()
        // JSONObject.quote serializes the entire JSON body as a JS literal;
        // user-provided room coordinates can never become script statements.
        val payload = JSONObject.quote(json.toString())
        val requestPath = JSONObject.quote("/server$path")
        val jsId = JSONObject.quote(requestId)
        val startScript = """
            (function() {
              'use strict';
              const id = $jsId;
              window.__xiaoxuUtilityRead = {id:id, phase:'running'};
              if (location.origin !== 'https://mobile-ykt.ncwu.edu.cn') {
                window.__xiaoxuUtilityRead = {id:id, phase:'done', code:403, body:''};
                return;
              }
              // Never return this value to Android or save it in Xiaoxu.
              const token = localStorage.getItem('accessToken') || '';
              if (!token) {
                window.__xiaoxuUtilityRead = {id:id, phase:'done', code:401, body:''};
                return;
              }
              fetch($requestPath, {
                method:'POST',
                credentials:'same-origin',
                redirect:'error',
                cache:'no-store',
                headers:{
                  'Authorization':token,
                  'Content-Type':'application/json',
                  'Accept':'application/json',
                  'V8-Language':'zh-CN'
                },
                body:$payload
              }).then(async function(response) {
                const body = await response.text();
                window.__xiaoxuUtilityRead = {
                  id:id, phase:'done', code:response.status,
                  body:body.length <= 262144 ? body : ''
                };
              }).catch(function() {
                window.__xiaoxuUtilityRead = {id:id, phase:'done', code:0, body:''};
              });
            })();
        """.trimIndent()
        webView.evaluateJavascript(startScript, null)

        repeat(120) {
            delay(250)
            if (!pageAllowed() || webView.url?.let { isOfficialOrigin(it) } != true) {
                throw CampusUtilitiesException(CampusUtilitiesFailure.LOGIN_REQUIRED)
            }
            val raw = suspendCancellableCoroutine<String> { continuation ->
                webView.evaluateJavascript(
                    "JSON.stringify(window.__xiaoxuUtilityRead || null)"
                ) { value ->
                    if (continuation.isActive) continuation.resume(value)
                }
            }
            val snapshot = runCatching {
                val jsonString = JSONTokener(raw).nextValue() as? String
                if (jsonString.isNullOrBlank() || jsonString == "null") null
                else JSONObject(jsonString)
            }.getOrNull()
            if (snapshot?.optString("id") == requestId &&
                snapshot.optString("phase") == "done"
            ) {
                webView.evaluateJavascript("window.__xiaoxuUtilityRead=null", null)
                val code = snapshot.optInt("code", 0)
                return@withContext CampusUtilitiesRawResponse(
                    code, snapshot.optString("body", "")
                )
            }
        }
        webView.evaluateJavascript("window.__xiaoxuUtilityRead=null", null)
        throw CampusUtilitiesException(CampusUtilitiesFailure.SCHOOL_UNAVAILABLE)
    }

    companion object {
        private val allowedPaths = setOf(
            "/utilities/config", "/utilities/account",
            "/utilities/usageDetails", "/utilities/usageDetailsV2"
        )

        fun isOfficialOrigin(raw: String): Boolean = runCatching {
            val uri = java.net.URI(raw)
            uri.scheme == "https" && uri.host == "mobile-ykt.ncwu.edu.cn" &&
                (uri.port == -1 || uri.port == 443) && uri.rawUserInfo == null
        }.getOrDefault(false)
    }
}
