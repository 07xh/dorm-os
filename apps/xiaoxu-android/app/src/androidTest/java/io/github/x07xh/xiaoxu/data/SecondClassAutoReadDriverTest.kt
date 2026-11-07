package io.github.x07xh.xiaoxu.data

import android.content.Context
import android.webkit.WebView
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebViewClient
import android.webkit.WebResourceError
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/** Executes the production driver. Only page transport is synthetic, never school traffic. */
@RunWith(AndroidJUnit4::class)
class SecondClassAutoReadDriverTest {
    private val c = InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun requireIsolatedRuntime() { assertRuntimeIsolation(c) }
    private val html = """<html><body><h3>本人[SYNTHETIC_SELF]</h3><script>
        window.fetch=function(url){
          var row={student_code:'SYNTHETIC_SELF',school_term_index:1,score_value:0,score_plus:0};
          for(var i=1;i<=6;i++)row['activity_type'+i+'_score']=0;
          var data=url.endsWith('/score/list')?[row]:[];
          return Promise.resolve({status:200,url:url,redirected:false,
            headers:{get:function(){return 'application/json';}},
            text:function(){return Promise.resolve(JSON.stringify({data:data,totalSize:data.length,totalPage:0,startIndex:0}));}});
        };</script></body></html>"""
    private class Page(c: Context, val html: String, val destroyed: AtomicInteger,
        val loaded: CompletableDeferred<Unit> = CompletableDeferred()) : WebView(c) {
        override fun loadUrl(url: String) {
            assertEquals("https://dekt.ncwu.edu.cn/sys/stu/score", url)
            assertNull(parent)
            loadDataWithBaseURL(url, html, "text/html", "UTF-8", null)
            loaded.complete(Unit)
        }
        override fun destroy() { destroyed.incrementAndGet(); super.destroy() }
    }
    @Test fun productionDriverExecutesTheFixedReaderAndDestroysItsDetachedBrowser() = runBlocking {
        val destroyed = AtomicInteger()
        val result = withTimeout(30_000) {
            readSecondClassAutomatically(c, "a".repeat(32)) { Page(it, html, destroyed) }
        }
        assertTrue(result.toString(), result.getBoolean("ok"))
        assertTrue(result.getBoolean("identityMatched"))
        assertEquals("verified_empty", result.getString("catalogStatus"))
        assertEquals(1, destroyed.get())
    }
    @Test fun cancellationDestroysTheProductionBrowserWithoutWaitingForTimeout() = runBlocking {
        val destroyed = AtomicInteger()
        val loaded = CompletableDeferred<Unit>()
        val reading = launch {
            readSecondClassAutomatically(c, "a".repeat(32)) {
                Page(it, "<html><body>Waiting</body></html>", destroyed, loaded)
            }
        }
        try {
            withTimeout(10_000) { loaded.await() }
        } finally { reading.cancelAndJoin() }
        withContext(Dispatchers.Main) { /* drain cancellation cleanup posted to main */ }
        assertEquals(1, destroyed.get())
    }
    @Test fun aLoginRedirectStopsBeforeOpeningAnotherPage() = runBlocking {
        val destroyed = AtomicInteger()
        val redirect = "<html><script>location.replace('https://login.invalid/synthetic');</script></html>"
        val error = try {
            withTimeout(15_000) {
                readSecondClassAutomatically(c, "a".repeat(32)) { Page(it, redirect, destroyed) }
            }
            null
        } catch (failure: AutoReadFailure) { failure.status }
        assertEquals("failed", error)
        assertEquals(1, destroyed.get())
    }
    @Test fun existingOfficialSsoMayReturnToTheOwnerScorePage() = runBlocking {
        val destroyed = AtomicInteger()
        val result = withTimeout(30_000) {
            readSecondClassAutomatically(c, "a".repeat(32)) {
                object : WebView(it) {
                    override fun loadUrl(url: String) {
                        // Simulate only observed first-party redirect callbacks;
                        // the runtime APK cannot contact the school network.
                        for (hop in listOf(
                            "https://dekt.ncwu.edu.cn/rump_frontend/login/?next=%2Fsys%2Fstu%2Fscore",
                            "https://authserver.ncwu.edu.cn/authserver/login?service=https%3A%2F%2Fsec.ncwu.edu.cn%2Frump_frontend%2FloginFromCas%2F",
                            "https://sec.ncwu.edu.cn/rump_frontend/loginFromCas/?ticket=SYNTHETIC"
                        )) {
                            webViewClient.onPageStarted(this, hop, null)
                            if (destroyed.get() != 0) return
                        }
                        loadDataWithBaseURL(url, html, "text/html", "UTF-8", null)
                    }
                    override fun destroy() { destroyed.incrementAndGet(); super.destroy() }
                }
            }
        }
        assertTrue(result.toString(), result.getBoolean("ok"))
        assertTrue(result.getBoolean("identityMatched"))
        assertEquals(1, destroyed.get())
    }
    @Test fun forbiddenResponseIsRetryableWithoutClaimingTheOwnerLoggedOut() = runBlocking {
        val destroyed = AtomicInteger()
        val error = try {
            readSecondClassAutomatically(c, "a".repeat(32)) {
                object : WebView(it) {
                    override fun loadUrl(url: String) {
                        val request = object : WebResourceRequest {
                            override fun getUrl() = Uri.parse(url)
                            override fun isForMainFrame() = true
                            override fun isRedirect() = false
                            override fun hasGesture() = false
                            override fun getMethod() = "GET"
                            override fun getRequestHeaders(): Map<String, String> = emptyMap()
                        }
                        webViewClient.onReceivedHttpError(this, request,
                            WebResourceResponse("text/html", "UTF-8", 403, "Forbidden", emptyMap(), null))
                    }
                    override fun destroy() { destroyed.incrementAndGet(); super.destroy() }
                }
            }
            null
        } catch (failure: AutoReadFailure) { failure.status }
        assertEquals("failed", error)
        assertEquals(1, destroyed.get())
    }
    @Test fun schoolHoldingPageUsesOnlyTheFixedExistingCasService() = runBlocking {
        val destroyed = AtomicInteger()
        val loads = mutableListOf<String>()
        val score = "https://dekt.ncwu.edu.cn/sys/stu/score"
        val cas = "https://authserver.ncwu.edu.cn/authserver/login?service=https%3A%2F%2Fsec.ncwu.edu.cn%2Frump_frontend%2FloginFromCas%2F"
        val result = withTimeout(30_000) {
            readSecondClassAutomatically(c, "a".repeat(32)) {
                object : WebView(it) {
                    override fun loadUrl(url: String) {
                        loads.add(url)
                        when (url) {
                            score -> loadDataWithBaseURL(
                                "https://dekt.ncwu.edu.cn/rump_frontend/login/?next=%2Fsys%2Fstu%2Fscore",
                                "<html>Official login gateway</html>", "text/html", "UTF-8", null)
                            cas -> loadDataWithBaseURL(score, html, "text/html", "UTF-8", null)
                            else -> error("unapproved navigation")
                        }
                    }
                    override fun destroy() { destroyed.incrementAndGet(); super.destroy() }
                }
            }
        }
        assertTrue(result.toString(), result.getBoolean("ok"))
        assertEquals(listOf(score, cas), loads)
        assertEquals(1, destroyed.get())
    }
    private suspend fun terminalCasPage(page: String): String? {
        val destroyed = AtomicInteger()
        val error = try {
            withTimeout(15_000) {
                readSecondClassAutomatically(c, "a".repeat(32)) {
                    object : WebView(it) {
                        override fun loadUrl(url: String) {
                            loadDataWithBaseURL(
                                "https://authserver.ncwu.edu.cn/authserver/login?service=https%3A%2F%2Fsec.ncwu.edu.cn%2Frump_frontend%2FloginFromCas%2F",
                                page, "text/html", "UTF-8", null)
                        }
                        override fun destroy() { destroyed.incrementAndGet(); super.destroy() }
                    }
                }
            }
            null
        } catch (failure: AutoReadFailure) { failure.status }
        assertEquals(1, destroyed.get())
        return error
    }
    @Test fun terminalCasNeedsVisibleLoginEvidenceAndMaintenanceRemainsRetryable() = runBlocking {
        assertEquals("needs_reconnect", terminalCasPage("<form><input type='password'></form>"))
        assertEquals("needs_reconnect", terminalCasPage("<html>请使用微信扫码登录</html>"))
        assertEquals("failed", terminalCasPage("<html>School temporarily unavailable</html>"))
    }
    @Test fun aRealMainFrameFormPostIsBlockedBeforeTransport() = runBlocking {
        val destroyed = AtomicInteger()
        val interceptedStatus = AtomicInteger(-1)
        val error = try {
            withTimeout(20_000) {
                readSecondClassAutomatically(c, "a".repeat(32)) {
                    object : WebView(it) {
                        override fun setWebViewClient(client: WebViewClient) {
                            super.setWebViewClient(object : WebViewClient() {
                                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                                    val result = client.shouldInterceptRequest(view, request)
                                    if (request.isForMainFrame && request.method == "POST")
                                        interceptedStatus.set(result?.statusCode ?: 0)
                                    return result
                                }
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) =
                                    client.shouldOverrideUrlLoading(view, request)
                                override fun onPageStarted(view: WebView, url: String?, icon: android.graphics.Bitmap?) =
                                    client.onPageStarted(view, url, icon)
                                override fun onPageFinished(view: WebView, url: String?) = client.onPageFinished(view, url)
                                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) =
                                    client.onReceivedError(view, request, error)
                                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) =
                                    client.onReceivedHttpError(view, request, response)
                            })
                        }
                        override fun loadUrl(url: String) {
                            loadDataWithBaseURL(url, """<html><form method='POST'
                                action='https://authserver.ncwu.edu.cn/authserver/login?service=https%3A%2F%2Fsec.ncwu.edu.cn%2Frump_frontend%2FloginFromCas%2F'>
                                <input name='synthetic' value='fixture'></form>
                                <script>document.querySelector('form').submit();</script></html>""",
                                "text/html", "UTF-8", null)
                        }
                        override fun destroy() { destroyed.incrementAndGet(); super.destroy() }
                    }
                }
            }
            null
        } catch (failure: AutoReadFailure) { failure.status }
        assertEquals("failed", error)
        withTimeout(5_000) { while (interceptedStatus.get() == -1) delay(10) }
        assertEquals("the actual POST must receive a local blocking response", 403, interceptedStatus.get())
        assertEquals(1, destroyed.get())
    }
    @Test fun repeatedApprovedPagesStillHaveAFiniteHopLimit() = runBlocking {
        val destroyed = AtomicInteger()
        val error = try {
            readSecondClassAutomatically(c, "a".repeat(32)) {
                object : WebView(it) {
                    override fun loadUrl(url: String) {
                        repeat(11) {
                            if (destroyed.get() == 0) webViewClient.onPageStarted(this, url, null)
                        }
                    }
                    override fun destroy() { destroyed.incrementAndGet(); super.destroy() }
                }
            }
            null
        } catch (failure: AutoReadFailure) { failure.status }
        assertEquals("failed", error)
        assertEquals(1, destroyed.get())
    }
}
