package io.github.x07xh.xiaoxu.data

import android.content.Context
import android.webkit.WebView
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
        assertEquals("needs_reconnect", error)
        assertEquals(1, destroyed.get())
    }
}
