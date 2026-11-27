package io.github.x07xh.xiaoxu

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.SslErrorHandler
import android.net.http.SslError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import io.github.x07xh.xiaoxu.data.ChaoxingWebNavigation
import io.github.x07xh.xiaoxu.data.ChaoxingWebSession
import io.github.x07xh.xiaoxu.data.ChaoxingLocalReader
import io.github.x07xh.xiaoxu.data.ChaoxingReadFailure
import io.github.x07xh.xiaoxu.data.LocalTaskDatabase
import io.github.x07xh.xiaoxu.data.NativeSchoolRecord
import io.github.x07xh.xiaoxu.data.nativeChaoxingVisibleStatus
import io.github.x07xh.xiaoxu.data.SecondClassAutoRefresh
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A school-owned HTTPS login in an app-controlled WebView. Cookies remain
 * exclusively in WebView's own jar; no JavascriptInterface, credential
 * extractor, remote proxy or Cloudflare credential exchange.
 *
 * Chaoxing reads after a user tap. Second-class uses this WebView for login
 * and reads and saves data through the native service after returning.
 * Each provider still requires the owner's on-device session acceptance.
 */
class NativeSchoolConnectActivity : ComponentActivity() {
    companion object {
        const val EXTRA_PROVIDER = "provider"
        const val EXTRA_RETURN_KIND = "saved_kind"
        const val EXTRA_SAVED_COUNT = "saved_count"
        const val EXTRA_LOGIN_ONLY_RESULT = "login_only_result"
        // NCWU's independently published CAS -> Chaoxing SSO service.
        // This is a SCHOOL-owned login redirect, not authorization to Xiaoxu.
        private const val CHAOXING_SCHOOL_SERVICE = "https://fysso.chaoxing.com/cassso/ncwu"
        private const val CHAOXING_SCHOOL_SSO =
            "https://authserver.ncwu.edu.cn/authserver/login" +
                "?service=https%3A%2F%2Ffysso.chaoxing.com%2Fcassso%2Fncwu"
        private val entries = mapOf(
            "chaoxing" to "https://i.chaoxing.com/base",
            "second_class" to "https://dekt.ncwu.edu.cn/sys/stu/score"
        )
        private val schools = setOf(
            "authserver.ncwu.edu.cn",
            "sec.ncwu.edu.cn", "dekt.ncwu.edu.cn"
        )
        fun navigationAllowed(provider: String, raw: String?): Boolean {
            val uri = runCatching { Uri.parse(raw ?: "") }.getOrNull() ?: return false
            if (uri.scheme != "https" || uri.userInfo != null) return false
            val host = uri.host?.lowercase() ?: return false
            return when (provider) {
                "chaoxing" -> when (host) {
                    // Only NCWU's registered Chaoxing CAS service is accepted;
                    // other school or third-party CAS service targets are blocked.
                    "authserver.ncwu.edu.cn" ->
                        uri.path == "/authserver/login" &&
                            uri.getQueryParameters("service").size == 1 &&
                            uri.getQueryParameter("service") == CHAOXING_SCHOOL_SERVICE
                    "fysso.chaoxing.com" ->
                        uri.path in setOf("/cassso/ncwu", "/cassso/ncwu/")
                    else -> ChaoxingWebNavigation.target(raw) == raw
                }
                "second_class" -> host in schools
                else -> false
            }
        }
    }

    private lateinit var browser: WebView
    private lateinit var status: TextView
    private lateinit var extract: Button
    private lateinit var finishAndReturn: Button
    private var provider = ""
    private val loginOnly get() = provider == "second_class"
    private var loaded = false
    private var pageFailed = false
    private var savedKind = ""
    private var savedCount = 0
    private var manualReservation = false

    private var chaoxingReadJob: Job? = null
    private var chaoxingWebSession: ChaoxingWebSession? = null
    private fun abortTaskDiscovery() {
        chaoxingWebSession?.cancel()
        chaoxingReadJob?.cancel()
        chaoxingWebSession = null
    }

    private fun tell(message: String) {
        status.text = message
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        provider = intent.getStringExtra(EXTRA_PROVIDER).orEmpty()
        val entry = entries[provider] ?: run { finish(); return }
        if (provider == "second_class") {
            SecondClassAutoRefresh.manualStarted()
            manualReservation = true
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            val gap = (8 * resources.displayMetrics.density).toInt()
            ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
                view.setPadding(gap + bars.left, bars.top, gap + bars.right,
                    maxOf(bars.bottom, keyboard.bottom))
                insets
            }
        }
        status = TextView(this).apply {
            textSize = 13f
            setTextIsSelectable(true)
            text = "学习通正常登录后点「读取全部」，读取通知、学习计划和课程作业。"
            setPadding(8, 4, 8, 8)
            if (loginOnly) text = "请在华水官方页面登录，返回后在第二课堂同步数据。"
        }
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun control(label: String, action: () -> Unit): Button =
            Button(this).apply {
                text = label
                setOnClickListener { action() }
                controls.addView(this, LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
        control("返回") {
            abortTaskDiscovery()
            if (browser.canGoBack()) browser.goBack() else finish()
        }
        control("刷新页面") {
            abortTaskDiscovery()
            browser.reload()
        }
        extract = control("读取全部") { startOwnerTaskDiscovery() }
        extract.isEnabled = false
        if (loginOnly) extract.visibility = View.GONE
        val ncwuSsoShortcut = if (provider == "chaoxing") Button(this).apply {
            text = "华水统一身份认证（学习通）"
            setOnClickListener { browser.loadUrl(CHAOXING_SCHOOL_SSO) }
        } else null
        finishAndReturn = Button(this).apply {
            text = if (loginOnly) "返回校序继续同步" else "返回校序查看已保存数据"
            isEnabled = false
            visibility = View.GONE
            setOnClickListener {
                val result = Intent().putExtra(EXTRA_PROVIDER, provider)
                if (loginOnly) {
                    result.putExtra(EXTRA_LOGIN_ONLY_RESULT, true)
                } else {
                    result.putExtra(EXTRA_RETURN_KIND, savedKind)
                    result.putExtra(EXTRA_SAVED_COUNT, savedCount)
                }
                setResult(Activity.RESULT_OK, result)
                finish()
            }
        }

        browser = WebView(this).apply {
            settings.javaScriptEnabled = true // Required for the official interactive login.
            settings.domStorageEnabled = true
            if (provider == "chaoxing") {
                // Match the desktop course templates used by the reference client.
                settings.userAgentString = ChaoxingWebSession.USER_AGENT
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
            }
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.allowFileAccessFromFileURLs = false
            settings.allowUniversalAccessFromFileURLs = false
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.safeBrowsingEnabled = true
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, provider == "chaoxing")
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (!request.isForMainFrame) return false
                    val raw = request.url.toString()
                    val target = if (provider == "chaoxing") ChaoxingWebNavigation.target(raw) else null
                    if (target != null && target != raw && navigationAllowed(provider, target)) {
                        view.loadUrl(target)
                        return true
                    }
                    val allowed = navigationAllowed(provider, raw)
                    if (!allowed) tell("当前链接不是受支持的官方网页，请返回继续登录。")
                    return !allowed
                }
                override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                    loaded = false
                    pageFailed = false
                    extract.isEnabled = false
                    if (loginOnly) {
                        finishAndReturn.isEnabled = false
                        finishAndReturn.visibility = View.GONE
                    }
                    if (!navigationAllowed(provider, url)) {
                        pageFailed = true
                        view.stopLoading()
                        tell("已阻止非学校或学习通白名单站点的跳转。请返回官方页面。")
                    } else {
                        tell("学校官方页面加载中。登录凭据不会发送到校序云端。")
                    }
                }
                override fun onPageFinished(view: WebView, url: String?) {
                    loaded = !pageFailed && navigationAllowed(provider, url)
                    if (loaded) CookieManager.getInstance().flush()
                    val host = Uri.parse(url ?: "").host
                    if (loginOnly) {
                        extract.isEnabled = false
                        finishAndReturn.isEnabled = loaded && host == "dekt.ncwu.edu.cn"
                        finishAndReturn.visibility = if (finishAndReturn.isEnabled) View.VISIBLE else View.GONE
                        tell(if (pageFailed) "学校官方页面加载失败，请重试。"
                            else if (finishAndReturn.isEnabled)
                                "已打开华水官方页面。请在此页面正常完成登录，然后返回校序；数据读取由第二课堂服务通过固定 HTTPS 只读接口执行。"
                            else "正在等待学校官方登录跳转…")
                        return
                    }
                    extract.isEnabled = loaded && chaoxingReadJob?.isActive != true
                    tell(if (pageFailed)
                        "当前页面访问失败，已禁止导入；请检查官方页面后重试。"
                    else if (extract.isEnabled)
                        "已打开官方站点（" + host + "）。只有进入本人真实业务列表后才能读取；登录成功不代表数据已同步。"
                    else "请在官网继续正常登录或进入本人课程／作业／活动列表；此页面不会被提取。")
                }
                override fun onReceivedError(
                    view: WebView, request: WebResourceRequest, error: WebResourceError
                ) {
                    if (request.isForMainFrame) {
                        pageFailed = true
                        if (chaoxingReadJob?.isActive == true) {
                            abortTaskDiscovery()
                            tell("课程任务扫描的官方页面加载失败；已保留之前保存的记录。")
                        }
                        loaded = false
                        extract.isEnabled = false
                        tell("学校页面加载失败；请检查网络或重新打开官方页面，本次未读取数据。")
                    }
                }
                override fun onReceivedSslError(
                    view: WebView, handler: SslErrorHandler, error: SslError
                ) {
                    handler.cancel()
                    abortTaskDiscovery()
                    pageFailed = true
                    loaded = false
                    extract.isEnabled = false
                    tell("学校 HTTPS 证书验证失败，已阻止继续访问。")
                }
                override fun onReceivedHttpError(
                    view: WebView, request: WebResourceRequest, response: WebResourceResponse
                ) {
                    if (request.isForMainFrame && response.statusCode >= 400) {
                        pageFailed = true
                        loaded = false
                        extract.isEnabled = false
                        tell("学校页面返回 HTTP " + response.statusCode + "。请检查官网是否可正常访问。")
                    }
                }
            }
        }
        root.addView(controls)
        if (provider == "chaoxing") {
            val more = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                visibility = View.GONE
            }
            ncwuSsoShortcut?.let { more.addView(it) }
            root.addView(Button(this).apply {
                text = "更多读取选项"
                setOnClickListener {
                    val expanded = more.visibility == View.VISIBLE
                    more.visibility = if (expanded) View.GONE else View.VISIBLE
                    text = if (expanded) "更多读取选项" else "收起读取选项"
                }
            })
            root.addView(more)
        }
        root.addView(finishAndReturn)
        root.addView(status)
        root.addView(browser, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        browser.loadUrl(entry)
    }

    /** The sole App Chaoxing reader uses the official login's own cookie jar. */
    private fun startOwnerTaskDiscovery() {
        if (provider != "chaoxing" || chaoxingReadJob?.isCompleted == false) return
        val session = ChaoxingWebSession()
        chaoxingWebSession = session
        extract.isEnabled = false
        tell("读取本机学习通通知、学习计划和课程作业…")
        chaoxingReadJob = lifecycleScope.launch {
            val context = currentCoroutineContext()
            try {
                val result = withContext(Dispatchers.IO) {
                    ChaoxingLocalReader(session,
                        progress = { message -> runOnUiThread {
                            if (!isFinishing && context.isActive) tell(message)
                        } }, checkCancelled = { context.ensureActive() })
                        .scan(java.util.Calendar.getInstance().get(java.util.Calendar.YEAR))
                }
                context.ensureActive()
                if (result.items.isEmpty() && !result.complete) {
                    tell("本次没有成功读取的任务来源，原记录保留：\n" + result.failures.take(8).joinToString("\n"))
                    return@launch
                }
                val now = System.currentTimeMillis()
                val rows = result.items.map { item ->
                    val status = if (item.kind == "homework") "当前网页显示的状态：" else "学习计划状态："
                    val label = if (item.kind == "homework") "官方作业身份：" else "学习任务身份："
                    val aliases = (if (item.noticeAliases.isEmpty()) "" else
                        " · 旧通知身份：" + item.noticeAliases.sorted().joinToString(",")) +
                        (if (item.peerAliases.isEmpty()) "" else
                        " · 互评通知身份：" + item.peerAliases.sorted().joinToString(","))
                    val due = item.deadline?.let {
                        " · " + (if (it.hasYear) "官网截止时间：" else "官网截止片段：") + it.displayed
                    }.orEmpty()
                    val detail = if (item.kind == "course") {
                        if(item.sourceKey.matches(Regex("""scope:\d{1,24}:\d{1,24}""")) &&
                            item.status=="作业列表已完整核对") "完整作业列表身份："+item.sourceKey
                        else "课程列表"
                    } else if(item.kind=="exam_notice") "官网考试通知状态："+item.status+aliases+due+" · 考试通知身份："+item.sourceKey
                    else status + item.status + aliases + due + " · " + label + item.sourceKey
                    val fingerprint = MessageDigest.getInstance("SHA-256")
                        .digest(("chaoxing|" + item.kind + "|" + item.sourceKey).toByteArray(Charsets.UTF_8))
                        .joinToString("") { "%02x".format(it) }
                    NativeSchoolRecord("chaoxing", item.kind, fingerprint, item.title,
                        detail, item.sourceHost, now)
                }
                val count = withContext(Dispatchers.IO) {
                    LocalTaskDatabase.open(applicationContext).nativeSchoolRecords()
                        .mergeChaoxingRead(rows, result.accountHash, result.complete, result.supersededKeys)
                }
                context.ensureActive()
                savedKind = listOf("homework", "study_task", "exam_notice", "course").firstOrNull {kind -> rows.any {it.kind == kind}} ?: "homework"
                savedCount = rows.count {it.kind == savedKind}
                finishAndReturn.isEnabled = true
                finishAndReturn.visibility = View.VISIBLE
                val observed=rows.filter {it.kind in setOf("homework","study_task")}
                val completed=observed.count {nativeChaoxingVisibleStatus(it) in setOf("submitted","graded")}
                val unknown=observed.count {nativeChaoxingVisibleStatus(it) == "unknown"}
                val removed=observed.count {nativeChaoxingVisibleStatus(it) == "removed"}
                tell("已读取通知 " + result.noticePages + " 页、课程 " + result.courseCount +
                    " 门，保存记录 " + count + " 条。\n" +
                    "本次作业 ${rows.count {it.kind == "homework"}} 条，考试通知 ${rows.count {it.kind == "exam_notice"}} 条，学习计划 ${rows.count {it.kind == "study_task"}} 条；" +
                    "已提交/完成 $completed 条，已删除/失效 $removed 条，状态待核对 $unknown 条。\n" +
                    if (result.complete) "请返回任务中心核对。"
                    else "部分来源未完成：\n" + result.failures.take(8).joinToString("\n"))
            } catch (_: CancellationException) {
                // Cancelling navigation or closing Activity never reports a new saved snapshot.
            } catch (e: ChaoxingReadFailure) {
                tell(when (e.code) {
                    "login_required" -> "请先在当前学习通官网完成登录，再点读取全部。"
                    "account_changed" -> "读取过程中账号发生变化，未保存本次结果，请重新读取。"
                    else -> "本机读取未完成（" + e.code + "），已保留原有记录。"
                })
            } catch (_: Exception) {
                tell("本机读取或保存未完成，已保留原有记录。")
            } finally {
                session.cancel()
                if (chaoxingWebSession === session) chaoxingWebSession = null
                if (!isFinishing && !isDestroyed) extract.isEnabled = loaded && !pageFailed
            }
        }
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
    }

    override fun onDestroy() {
        abortTaskDiscovery()
        if (::browser.isInitialized) {
            (browser.parent as? ViewGroup)?.removeView(browser)
            browser.stopLoading()
            browser.destroy()
        }
        if (manualReservation) {
            manualReservation = false
            SecondClassAutoRefresh.manualFinishedAfter(null)
        }
        super.onDestroy()
    }
}
