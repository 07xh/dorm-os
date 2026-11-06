package io.github.x07xh.xiaoxu.data

import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real Chromium execution, synthetic local page and business replies only.
 * Never contacts a school account or weakens the asset's visibility checks. */
@RunWith(AndroidJUnit4::class)
class SecondClassDetachedWebViewTest {
    private fun read(visible: Boolean): JSONObject {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val c = instrumentation.targetContext
        val done = CountDownLatch(1)
        var web: WebView? = null
        var result: JSONObject? = null
        val handler = Handler(Looper.getMainLooper())
        val script = c.assets.open("ncwu_secondclass_local.js").bufferedReader().use { it.readText() }
        val salt = "a".repeat(32)
        try {
            instrumentation.runOnMainSync {
                val view = WebView(c)
                web = view
                view.settings.javaScriptEnabled = true
                view.settings.domStorageEnabled = true
                view.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY))
                view.layout(0, 0, 1080, 1920)
                fun poll(left: Int) {
                    view.evaluateJavascript("window.__syntheticResult||null") { encoded ->
                        val text = runCatching { JSONTokener(encoded).nextValue() as? String }.getOrNull()
                        if (text == null && left > 0) handler.postDelayed({ poll(left - 1) }, 100)
                        else { result = text?.let { JSONObject(it) }; done.countDown() }
                    }
                }
                var started = false
                view.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest) =
                        WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(byteArrayOf()))
                    override fun onPageFinished(v: WebView, url: String?) {
                        if (started) return
                        started = true
                        view.evaluateJavascript("($script)('$salt',true,true).then(function(p){window.__syntheticResult=JSON.stringify(p);});") { poll(200) }
                    }
                }
                val heading = if (visible) "<h3>本人[SYNTHETIC_SELF]</h3>" else ""
                val html = """<html><body>$heading<script>
                    window.fetch=function(url){
                      var row={student_code:'SYNTHETIC_SELF',school_term_index:1,score_value:0,score_plus:0};
                      for(var i=1;i<=6;i++)row['activity_type'+i+'_score']=0;
                      var data=url.endsWith('/score/list')?[row]:[];
                      return Promise.resolve({status:200,url:url,redirected:false,
                        headers:{get:function(){return 'application/json';}},
                        text:function(){return Promise.resolve(JSON.stringify({data:data,totalSize:data.length,totalPage:0,startIndex:0}));}});
                    };</script></body></html>"""
                view.loadDataWithBaseURL("https://dekt.ncwu.edu.cn/sys/stu/score", html, "text/html", "UTF-8", null)
            }
            assertTrue("detached WebView must finish the existing reader", done.await(30, TimeUnit.SECONDS))
            return requireNotNull(result)
        } finally {
            instrumentation.runOnMainSync {
                handler.removeCallbacksAndMessages(null)
                web?.stopLoading(); web?.destroy()
            }
        }
    }
    @Test fun detachedPageCanRunTheUnchangedIdentityCheckedReader() {
        val payload = read(true)
        assertTrue(payload.toString(), payload.getBoolean("ok"))
        assertTrue(payload.getBoolean("identityMatched"))
        assertEquals("verified_empty", payload.getString("catalogStatus"))
        val expected = MessageDigest.getInstance("SHA-256")
            .digest(("a".repeat(32) + ":SYNTHETIC_SELF").toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertEquals(expected, payload.getString("accountHash"))
        assertFalse(payload.getBoolean("fullSyncVerified"))
    }
    @Test fun missingVisibleIdentityStillRejectsTheRead() {
        val payload = read(false)
        assertFalse(payload.getBoolean("ok"))
        assertEquals("identity", payload.getString("stage"))
        assertFalse(payload.has("rows"))
    }
}
