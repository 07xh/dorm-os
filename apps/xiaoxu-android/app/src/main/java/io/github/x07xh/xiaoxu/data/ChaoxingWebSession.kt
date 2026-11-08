package io.github.x07xh.xiaoxu.data

import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.CancellationException

/** Same app-controlled WebView cookie jar as official login, used only on device.
 * No cookie export, separate credential store, Cloudflare proxy or Javascript bridge.
 */
internal class ChaoxingWebSession : ChaoxingReadTransport {
    companion object {
        const val USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        private val readOnlyPosts = setOf("/pc/notice/getNoticeList", "/mooc2-ans/visit/courselistdata",
            "/userStudyPlan/getGroupData", "/userStudyPlan/getPlanDataByGroupId", "/userStudyPlan/getToStudyUrl")
    }
    @Volatile private var cancelled = false
    @Volatile private var active: HttpURLConnection? = null
    fun cancel() {cancelled=true;active?.disconnect()}
    private fun checkActive() {if(cancelled||Thread.currentThread().isInterrupted) throw CancellationException()}
    override fun request(request: ChaoxingReadRequest): ChaoxingReadPage {
        check(Looper.myLooper()!=Looper.getMainLooper()) {"network_on_main_thread"}
        var url=ChaoxingLocalReader.safeUrl(request.url) ?: throw ChaoxingReadFailure("non_official_url")
        var method=request.method
        var body=if(method=="POST") request.form.entries.joinToString("&") {
            URLEncoder.encode(it.key,"UTF-8")+"="+URLEncoder.encode(it.value,"UTF-8")
        }.toByteArray(Charsets.UTF_8) else null
        repeat(8) {
            checkActive()
            val uri=URI(url)
            if(method !in setOf("GET","POST") || (method=="POST"&&uri.path !in readOnlyPosts))
                throw ChaoxingReadFailure("non_readonly_request")
            val connection=(URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects=false;connectTimeout=15000;readTimeout=25000
                requestMethod=method
                setRequestProperty("User-Agent",USER_AGENT)
                setRequestProperty("Accept","*/*")
                setRequestProperty("Referer","https://i.chaoxing.com/")
                setRequestProperty("X-Requested-With","XMLHttpRequest")
                // CookieManager scopes each request to its actual target host.
                CookieManager.getInstance().getCookie(url)?.takeIf {it.isNotBlank()}?.let {setRequestProperty("Cookie",it)}
                if(body!=null) {doOutput=true;setRequestProperty("Content-Type","application/x-www-form-urlencoded; charset=UTF-8")}
            }
            active=connection
            try {
                body?.let {bytes->connection.outputStream.use {it.write(bytes)}}
                val code=connection.responseCode
                val cookies=connection.headerFields.filterKeys {it.equals("Set-Cookie",true)}.values.flatten()
                if(cookies.isNotEmpty()) {
                    val latch=CountDownLatch(cookies.size)
                    Handler(Looper.getMainLooper()).post {
                        for(cookie in cookies) CookieManager.getInstance().setCookie(url,cookie) {latch.countDown()}
                    }
                    if(!latch.await(5,TimeUnit.SECONDS)) throw ChaoxingReadFailure("session_persistence")
                    CookieManager.getInstance().flush()
                }
                checkActive()
                if(code in setOf(301,302,303,307,308)) {
                    val next=ChaoxingLocalReader.safeUrl(connection.getHeaderField("Location").orEmpty(),url)
                        ?: throw ChaoxingReadFailure("redirect_blocked")
                    if(method=="POST"&&code in setOf(307,308)) throw ChaoxingReadFailure("interactive_login_required")
                    url=next;method="GET";body=null
                } else {
                    if(code==401) throw ChaoxingReadFailure("login_required")
                    if(code==403) throw ChaoxingReadFailure("access_denied")
                    if(code !in 200..299) throw ChaoxingReadFailure("upstream_http_$code")
                    val out=ByteArrayOutputStream()
                    connection.inputStream.use {input->
                        val buffer=ByteArray(8192)
                        while(true) {
                            checkActive();val size=input.read(buffer);if(size<0) break
                            if(out.size()+size>6000000) throw ChaoxingReadFailure("response_limit")
                            out.write(buffer,0,size)
                        }
                    }
                    checkActive()
                    return ChaoxingReadPage(url,out.toString("UTF-8"))
                }
            } catch(e:ChaoxingReadFailure) {throw e}
            catch(e:CancellationException) {throw e}
            catch(_:Exception) {checkActive();throw ChaoxingReadFailure("network_failed")}
            finally {connection.disconnect();active=null}
        }
        throw ChaoxingReadFailure("redirect_limit")
    }
}
