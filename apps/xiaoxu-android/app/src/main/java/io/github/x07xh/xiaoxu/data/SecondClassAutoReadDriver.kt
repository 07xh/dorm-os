package io.github.x07xh.xiaoxu.data

import android.annotation.SuppressLint
import android.content.Context
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.json.JSONTokener
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class AutoReadFailure(val status: String) : Exception(status)

/** Supplies only the existing fixed reader's official same-origin context.
 * No Activity, login automation, credential extraction or session transport. */
@SuppressLint("SetJavaScriptEnabled")
internal suspend fun readSecondClassAutomatically(context: Context, salt: String): JSONObject =
    withContext(Dispatchers.Main.immediate) {
        withTimeout(90_000) {
            val script = context.assets.open("ncwu_secondclass_local.js").bufferedReader().use { it.readText() }
            suspendCancellableCoroutine { continuation ->
                val handler = Handler(Looper.getMainLooper())
                val browser = WebView(context.applicationContext)
                var cleaned = false
                var started = false
                val scoreUrl = "https://dekt.ncwu.edu.cn/sys/stu/score"
                val key = JSONObject.quote("xuAuto" + UUID.randomUUID().toString().replace("-", ""))
                fun cleanup() {
                    if (cleaned) return
                    cleaned = true
                    handler.removeCallbacksAndMessages(null)
                    runCatching { browser.webViewClient = WebViewClient() }
                    runCatching { browser.stopLoading() }
                    runCatching { browser.destroy() }
                }
                fun fail(status: String) {
                    if (cleaned || !continuation.isActive) return
                    cleanup()
                    continuation.resumeWithException(AutoReadFailure(status))
                }
                fun poll(remaining: Int) {
                    if (!continuation.isActive || cleaned) return
                    if (remaining <= 0) { fail("failed"); return }
                    browser.evaluateJavascript("window[$key]==null?null:window[$key]") { encoded ->
                        if (!continuation.isActive || cleaned) return@evaluateJavascript
                        val text = runCatching { JSONTokener(encoded).nextValue() as? String }.getOrNull()
                        if (text == null) handler.postDelayed({ poll(remaining - 1) }, 250)
                        else {
                            val payload = runCatching { JSONObject(text) }.getOrNull()
                            if (payload == null) fail("failed")
                            else {
                                // Preserve normal WebView session rotation for
                                // the next cold start; never read/export cookies.
                                runCatching { CookieManager.getInstance().flush() }
                                cleanup(); continuation.resume(payload)
                            }
                        }
                    }
                }
                fun begin(remaining: Int) {
                    if (!continuation.isActive || cleaned) return
                    // Readiness only: the unchanged reader still performs all
                    // visible account checks before/after business requests.
                    browser.evaluateJavascript("Array.from(document.querySelectorAll('h3,h4')).some(function(e){return e.getClientRects().length && /\\[[A-Za-z0-9_-]{1,50}\\]/.test(e.innerText||'');})") { ready ->
                        if (!continuation.isActive || cleaned) return@evaluateJavascript
                        if (ready != "true") {
                            if (remaining > 0) handler.postDelayed({ begin(remaining - 1) }, 250)
                            else fail("failed")
                        } else browser.evaluateJavascript(
                            "window[$key]=null;($script)(${JSONObject.quote(salt)},true,true).then(function(r){window[$key]=JSON.stringify(r);},function(){window[$key]=JSON.stringify({ok:false});});"
                        ) { if (continuation.isActive && !cleaned) poll(280) }
                    }
                }
                continuation.invokeOnCancellation { handler.post { cleanup() } }
                try {
                    browser.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
                    browser.settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        allowFileAccess = false
                        allowContentAccess = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        javaScriptCanOpenWindowsAutomatically = false
                        setSupportMultipleWindows(false)
                        safeBrowsingEnabled = true
                    }
                    CookieManager.getInstance().setAcceptThirdPartyCookies(browser, false)
                    browser.webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            if (!request.isForMainFrame) return false
                            if (request.url.toString() == scoreUrl) return false
                            fail("needs_reconnect"); return true
                        }
                        override fun onPageStarted(view: WebView, url: String?, icon: android.graphics.Bitmap?) {
                            if (url != scoreUrl) fail("needs_reconnect")
                        }
                        override fun onPageFinished(view: WebView, url: String?) {
                            if (cleaned || !continuation.isActive) return
                            if (url != scoreUrl) { fail("needs_reconnect"); return }
                            if (!started) { started = true; begin(12) }
                        }
                        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                            if (request.isForMainFrame) fail("failed")
                        }
                        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                            if (request.isForMainFrame) fail(if (response.statusCode in setOf(401, 403)) "needs_reconnect" else "failed")
                        }
                        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                            handler.cancel(); fail("failed")
                        }
                        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                            fail("failed"); return true
                        }
                    }
                    browser.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY))
                    browser.layout(0, 0, 1080, 1920)
                    browser.loadUrl(scoreUrl)
                } catch (_: Exception) { fail("failed") }
            }
        }
    }
