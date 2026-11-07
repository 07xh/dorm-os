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
internal suspend fun readSecondClassAutomatically(
    context: Context, salt: String,
    createBrowser: (Context) -> WebView = { WebView(it) }
): JSONObject =
    withContext(Dispatchers.Main.immediate) {
        withTimeout(90_000) {
            val script = context.assets.open("ncwu_secondclass_local.js").bufferedReader().use { it.readText() }
            suspendCancellableCoroutine { continuation ->
                val handler = Handler(Looper.getMainLooper())
                val browser = createBrowser(context.applicationContext)
                var cleaned = false
                var started = false
                var navigations = 0
                var pageEpoch = 0
                var currentPageUrl: String? = null
                var ssoAttempted = false
                var returnedToScore = false
                val scoreUrl = SecondClassAutoSessionNavigation.SCORE
                val key = JSONObject.quote("xuAuto" + UUID.randomUUID().toString().replace("-", ""))
                fun cleanup() {
                    if (cleaned) return
                    cleaned = true
                    handler.removeCallbacksAndMessages(null)
                    // A redirect or failed business request may also rotate a
                    // session. Persist the normal jar before destroying WebView.
                    runCatching { CookieManager.getInstance().flush() }
                    runCatching { browser.webViewClient = WebViewClient() }
                    runCatching { browser.stopLoading() }
                    runCatching { browser.destroy() }
                }
                fun fail(status: String) {
                    if (cleaned || !continuation.isActive) return
                    cleanup()
                    continuation.resumeWithException(AutoReadFailure(status))
                }
                fun poll(remaining: Int, epoch: Int) {
                    if (!continuation.isActive || cleaned || epoch != pageEpoch) return
                    if (remaining <= 0) { fail("failed"); return }
                    browser.evaluateJavascript("window[$key]==null?null:window[$key]") { encoded ->
                        if (!continuation.isActive || cleaned || epoch != pageEpoch) return@evaluateJavascript
                        val text = runCatching { JSONTokener(encoded).nextValue() as? String }.getOrNull()
                        if (text == null) handler.postDelayed({ poll(remaining - 1, epoch) }, 250)
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
                fun begin(remaining: Int, epoch: Int) {
                    if (!continuation.isActive || cleaned || epoch != pageEpoch) return
                    // Readiness only: the unchanged reader still performs all
                    // visible account checks before/after business requests.
                    browser.evaluateJavascript("Array.from(document.querySelectorAll('h3,h4')).some(function(e){return e.getClientRects().length && /\\[[A-Za-z0-9_-]{1,50}\\]/.test(e.innerText||'');})") { ready ->
                        if (!continuation.isActive || cleaned || epoch != pageEpoch) return@evaluateJavascript
                        if (ready != "true") {
                            if (remaining > 0) handler.postDelayed({ begin(remaining - 1, epoch) }, 250)
                            else fail("failed")
                        } else browser.evaluateJavascript(
                            "window[$key]=null;($script)(${JSONObject.quote(salt)},true,true).then(function(r){window[$key]=JSON.stringify(r);},function(){window[$key]=JSON.stringify({ok:false});});"
                        ) { if (continuation.isActive && !cleaned && epoch == pageEpoch) poll(280, epoch) }
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
                            if (SecondClassAutoSessionNavigation.page(request.url.toString(), request.method) != null) return false
                            fail("failed"); return true
                        }
                        override fun onPageStarted(view: WebView, url: String?, icon: android.graphics.Bitmap?) {
                            if (cleaned || !continuation.isActive) return
                            pageEpoch++
                            currentPageUrl = url
                            started = false
                            if (++navigations > 10 || SecondClassAutoSessionNavigation.page(url) == null) fail("failed")
                        }
                        override fun onPageFinished(view: WebView, url: String?) {
                            if (cleaned || !continuation.isActive || url != currentPageUrl) return
                            when (SecondClassAutoSessionNavigation.page(url)) {
                                SecondClassAutoSessionNavigation.Page.SCORE ->
                                    if (!started) { started = true; begin(12, pageEpoch) }
                                SecondClassAutoSessionNavigation.Page.GATEWAY -> {
                                    if (ssoAttempted) { fail("needs_reconnect"); return }
                                    ssoAttempted = true
                                    // The known school holding page requires a
                                    // GET to its registered CAS service. Never
                                    // inspect fields or submit a login form.
                                    view.loadUrl(SecondClassAutoSessionNavigation.CAS)
                                }
                                SecondClassAutoSessionNavigation.Page.CALLBACK,
                                SecondClassAutoSessionNavigation.Page.LANDING -> {
                                    if (returnedToScore) { fail("failed"); return }
                                    returnedToScore = true
                                    view.loadUrl(scoreUrl)
                                }
                                SecondClassAutoSessionNavigation.Page.CAS -> {
                                    // Give the school's existing session its
                                    // normal redirect opportunity. A login page
                                    // that stays here requires the owner.
                                    val epoch = pageEpoch
                                    handler.postDelayed({
                                        if (!cleaned && continuation.isActive && epoch == pageEpoch) fail("needs_reconnect")
                                    }, 3_000)
                                }
                                null -> fail("failed")
                            }
                        }
                        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                            if (request.isForMainFrame) fail("failed")
                        }
                        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                            if (request.isForMainFrame) fail(if (response.statusCode == 401) "needs_reconnect" else "failed")
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
