package io.github.x07xh.xiaoxu

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import io.github.x07xh.xiaoxu.data.SecondClassAutoReadPolicy
import io.github.x07xh.xiaoxu.data.parseLocalSecondClassScores
import io.github.x07xh.xiaoxu.data.parseLocalSecondClassActivities
import io.github.x07xh.xiaoxu.data.parseLocalSecondClassCatalogEmpty
import io.github.x07xh.xiaoxu.data.parseLocalSecondClassCatalogActivities
import java.util.UUID
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONTokener

/**
 * A school-owned HTTPS login in an app-controlled WebView. Cookies remain
 * exclusively in WebView's own jar; no JavascriptInterface, credential
 * extractor, remote proxy or Cloudflare credential exchange.
 *
 * Chaoxing reads the visible page after a user tap. Second-class uses a fixed
 * same-origin score query after a tap, and confirms the sanitized local save.
 * Each provider still requires the owner's on-device session acceptance.
 */
class NativeSchoolConnectActivity : ComponentActivity() {
    companion object {
        const val EXTRA_PROVIDER = "provider"
        const val EXTRA_AUTO_SCORE = "auto_score"
        const val EXTRA_RETURN_KIND = "saved_kind"
        const val EXTRA_SAVED_COUNT = "saved_count"
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
        fun extractionAllowed(provider: String, url: String?): Boolean {
            if (!navigationAllowed(provider, url)) return false
            val uri = Uri.parse(url ?: "")
            val host = uri.host?.lowercase() ?: return false
            return when (provider) {
                "second_class" -> host == "dekt.ncwu.edu.cn"
                else -> false
            }
        }
    }

    private lateinit var browser: WebView
    private lateinit var status: TextView
    private lateinit var extract: Button
    private lateinit var finishAndReturn: Button
    private lateinit var catalogProbe: Button
    private var catalogProbeOn = false
    private var provider = ""
    private var loaded = false
    private var pageFailed = false
    private var captureEpoch = 0
    private var savedKind = ""
    private var savedCount = 0
    private val scoreHandler = Handler(Looper.getMainLooper())
    private var scoreBusy = false
    private var autoAttempted = false
    private var redirectedToScore = false
    private var pendingOfficialCatalogRead = false
    private val scoreIdentity by lazy { getSharedPreferences("second_class_local_identity", MODE_PRIVATE) }
    private var scoreSaveJob: Job? = null
    private var manualReservation = false

    private var chaoxingReadJob: Job? = null
    private var chaoxingWebSession: ChaoxingWebSession? = null
    private fun abortTaskDiscovery() {
        chaoxingWebSession?.cancel()
        chaoxingReadJob?.cancel()
        chaoxingWebSession = null
    }

    private fun invalidateCapture() {
        captureEpoch++
        scoreSaveJob?.cancel()
        // Retain the cancelled job until it completes, so destruction can
        // keep automatic reads reserved until Room cancellation has finished.
        scoreBusy = false
        scoreHandler.removeCallbacksAndMessages(null)
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
            if (provider == "second_class") text =
                "首次在学校官网正常登录；会话有效时可直接读取积分，无需每次登录。积分仅保存在此设备。"
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
        extract = control("读取全部") { captureVisibleRecords() }
        if (provider == "second_class") extract.text = "读取／刷新本人积分"
        extract.isEnabled = false
        val ncwuSsoShortcut = if (provider == "chaoxing") Button(this).apply {
            text = "华水统一身份认证（学习通）"
            setOnClickListener { browser.loadUrl(CHAOXING_SCHOOL_SSO) }
        } else null
        val scoreShortcut = if (provider == "second_class") Button(this).apply {
            text = "第二课堂 · 打开积分成绩单"
            setOnClickListener { browser.loadUrl(entries.getValue("second_class")) }
        } else null
        catalogProbe = Button(this).apply {
            text = "观测活动列表请求（手动）"
            isEnabled = false
            setOnClickListener { toggleCatalogObserver() }
        }
        finishAndReturn = Button(this).apply {
            text = "返回校序查看已保存数据"
            isEnabled = false
            if (provider == "chaoxing") visibility = View.GONE
            setOnClickListener {
                // Only a committed Room write enables this button.
                setResult(Activity.RESULT_OK, Intent()
                    .putExtra(EXTRA_PROVIDER, provider)
                    .putExtra(EXTRA_RETURN_KIND, savedKind)
                    .putExtra(EXTRA_SAVED_COUNT, savedCount))
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
                    invalidateCapture()
                    catalogProbeOn = false // A new document loses the in-page observer.
                    catalogProbe.text = "观测活动列表请求（手动）"
                    catalogProbe.isEnabled = false
                    extract.isEnabled = false
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
                    extract.isEnabled = if (provider == "chaoxing") loaded && chaoxingReadJob?.isActive != true
                        else loaded && extractionAllowed(provider, url)
                    extract.text = if (provider == "second_class") "读取／刷新本人积分"
                        else "读取全部"
                    catalogProbe.isEnabled = provider == "second_class" && loaded &&
                        host == "dekt.ncwu.edu.cn" &&
                        Uri.parse(url ?: "").path.orEmpty().startsWith("/sys/")
                    tell(if (pageFailed)
                        "当前页面访问失败，已禁止导入；请检查官方页面后重试。"
                    else if (extract.isEnabled)
                        "已打开官方站点（" + host + "）。只有进入本人真实业务列表后才能读取；登录成功不代表数据已同步。"
                    else "请在官网继续正常登录或进入本人课程／作业／活动列表；此页面不会被提取。")
                    if (provider == "second_class" && loaded && intent.getBooleanExtra(EXTRA_AUTO_SCORE, false) &&
                        host == "dekt.ncwu.edu.cn" && Uri.parse(url ?: "").path != "/sys/stu/score" && !redirectedToScore) {
                        redirectedToScore = true
                        browser.loadUrl(entries.getValue("second_class"))
                        return
                    }
                    if (provider == "second_class") {
                        val isScore = Uri.parse(url ?: "").path == "/sys/stu/score"
                        extract.text = if (isScore) "读取／刷新本人积分" else "读取活动与积分"
                        if (extract.isEnabled && isScore) tell(
                            "已打开官方成绩单。点击「读取／刷新本人积分」核验当前账号并读取；登录态失效时需重新登录。")
                        val initialAuto = intent.getBooleanExtra(EXTRA_AUTO_SCORE, false) && !autoAttempted
                        if (isScore && extract.isEnabled && (initialAuto || pendingOfficialCatalogRead)) {
                            if (initialAuto) autoAttempted = true
                            pendingOfficialCatalogRead = false
                            scoreHandler.postDelayed({
                                if (loaded && !pageFailed) captureSecondClassScores()
                            }, 600)
                        }
                    }
                }
                override fun onReceivedError(
                    view: WebView, request: WebResourceRequest, error: WebResourceError
                ) {
                    if (request.isForMainFrame) {
                        invalidateCapture()
                        pendingOfficialCatalogRead = false
                        pageFailed = true
                        if (chaoxingReadJob?.isActive == true) {
                            abortTaskDiscovery()
                            tell("课程任务扫描的官方页面加载失败；已保留之前保存的记录。")
                        }
                        loaded = false
                        extract.isEnabled = false
                        catalogProbe.isEnabled = false
                        tell("学校页面加载失败；请检查网络或重新打开官方页面，本次未读取数据。")
                    }
                }
                override fun onReceivedSslError(
                    view: WebView, handler: SslErrorHandler, error: SslError
                ) {
                    handler.cancel()
                    abortTaskDiscovery()
                    invalidateCapture()
                    pendingOfficialCatalogRead = false
                    pageFailed = true
                    loaded = false
                    extract.isEnabled = false
                    catalogProbe.isEnabled = false
                    tell("学校 HTTPS 证书验证失败，已阻止继续访问。")
                }
                override fun onReceivedHttpError(
                    view: WebView, request: WebResourceRequest, response: WebResourceResponse
                ) {
                    if (request.isForMainFrame && response.statusCode >= 400) {
                        invalidateCapture()
                        pageFailed = true
                        loaded = false
                        extract.isEnabled = false
                        catalogProbe.isEnabled = false
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
        if (scoreShortcut != null) root.addView(scoreShortcut)
        if (provider == "second_class") root.addView(catalogProbe)
        root.addView(finishAndReturn)
        root.addView(status)
        root.addView(browser, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        browser.loadUrl(entry)
    }

    /**
     * One-tap opt-in source discovery for an owner-visible official activity page.
     * The bundled script observes ONLY pathname + GET/POST metadata from the
     * current document's own fetch/XHR calls. No credentials/response content,
     * no new school request, no cloud report and no long-lived data collection.
     * A candidate is not a verified activity catalog API.
     */
    private fun toggleCatalogObserver() {
        val url = browser.url ?: return
        if (provider != "second_class" || !loaded || pageFailed ||
            Uri.parse(url).host != "dekt.ncwu.edu.cn" ||
            !Uri.parse(url).path.orEmpty().startsWith("/sys/")) {
            tell("请先打开学校官方活动页面；登录页面不可观测。")
            return
        }
        if (!catalogProbeOn) {
            val script = try {
                assets.open("ncwu_secondclass_source_observer.js")
                    .bufferedReader().use { it.readText() }
            } catch (_: Exception) {
                tell("活动来源诊断组件未安装，无法启动。")
                return
            }
            catalogProbe.isEnabled = false
            browser.evaluateJavascript(script) { encoded ->
                catalogProbe.isEnabled = loaded && !pageFailed
                val response = runCatching {
                    JSONObject(JSONTokener(encoded).nextValue() as String)
                }.getOrNull()
                if (response?.optBoolean("ok") == true && browser.url == url && !pageFailed) {
                    catalogProbeOn = true
                    catalogProbe.text = "停止并查看请求路径"
                    tell("已在本机开启只读路径观测。请在当前官方活动页正常切换筛选／分页，" +
                        "然后点击“停止并查看请求路径”。不采集请求正文、Cookie 或账号。")
                } else {
                    tell("观测未启动；请打开学校已登录的业务页面后重试。")
                }
            }
            return
        }
        catalogProbe.isEnabled = false
        // Do NOT read window storage, page DOM, school cookies, request bodies,
        // headers or responses. The in-page observer exposes sanitized paths only.
        browser.evaluateJavascript(
            "(function(){var o=window.__xiaoxuSecondClassSourceObserverV1;" +
                "return o?o.stop():JSON.stringify({ok:false});})()"
        ) { encoded ->
            catalogProbeOn = false
            catalogProbe.text = "观测活动列表请求（手动）"
            catalogProbe.isEnabled = loaded && !pageFailed &&
                Uri.parse(browser.url ?: "").host == "dekt.ncwu.edu.cn"
            val response = runCatching {
                JSONObject(JSONTokener(encoded).nextValue() as String)
            }.getOrNull()
            if (response?.optBoolean("ok") != true) {
                tell("页面已更换或观测中断，没有确认活动数据来源。")
                return@evaluateJavascript
            }
            val observed = response.optJSONArray("observed")
            if (observed == null || observed.length() == 0) {
                tell("没有观察到符合过滤规则的业务请求；这不证明学校没有活动接口。" +
                    "可以在官方活动页打开观测后主动切换筛选或翻页。")
                return@evaluateJavascript
            }
            val lines = (0 until minOf(observed.length(), 12)).mapNotNull { i ->
                val item = observed.optJSONObject(i) ?: return@mapNotNull null
                val method = item.optString("method")
                val path = item.optString("path")
                // Second independent Android-side validation for display.
                if (method !in setOf("GET", "POST") ||
                    !Regex("^/sys/(?:[A-Za-z_-]+/){1,7}[A-Za-z_-]+/?$").matches(path))
                    return@mapNotNull null
                val kind = if (item.optString("kind") == "known_personal")
                    "已知个人接口" else "未验证候选"
                // The only returned response details are HTTP status and a
                // categorical MIME family. No response body or school headers.
                val outcomes = item.optJSONArray("outcomes")
                val outcomesText = if (outcomes == null) "" else
                    (0 until minOf(outcomes.length(), 6)).mapNotNull { j ->
                        val meta = outcomes.optJSONObject(j) ?: return@mapNotNull null
                        val statusCode = meta.optInt("status", -1)
                        val family = meta.optString("type")
                        if (statusCode !in 100..599 ||
                            family !in setOf("json", "html", "other", "unknown"))
                            return@mapNotNull null
                        val familyName = when (family) {
                            "json" -> "JSON"
                            "html" -> "HTML"
                            "other" -> "其他"
                            else -> "未知类型"
                        }
                        "HTTP $statusCode/$familyName ×" +
                            meta.optInt("count", 1).coerceIn(1, 99)
                    }.joinToString("，")
                "$method $path · $kind · " + item.optInt("count", 1).coerceIn(1, 99) +
                    "次" + if (outcomesText.isBlank()) " · 未获得响应元信息"
                    else " · " + outcomesText
            }
            tell("仅本机观察到的请求路径与响应状态（非接口验收）：\n" +
                lines.joinToString("\n") +
                if (response.optBoolean("overflow") || observed.length() > 12)
                    "\n部分路径未展示；本次观测未保存任何请求内容。" else
                    "\n请勿将候选路径误认为已验证的活动接口。")
        }
    }

    private fun captureVisibleRecords() {
        if (provider == "chaoxing") {
            startOwnerTaskDiscovery()
        } else if (provider == "second_class") {
            val uri = Uri.parse(browser.url ?: "")
            if (!loaded || pageFailed || uri.host != "dekt.ncwu.edu.cn") {
                tell("请先在学校官网正常登录，再读取活动。")
            } else if (uri.path == "/sys/stu/score") {
                captureSecondClassScores()
            } else if (uri.path.orEmpty().startsWith("/sys/")) {
                pendingOfficialCatalogRead = true
                browser.loadUrl(entries.getValue("second_class"))
            }
        }
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

    /** Fetch stays inside the official WebView origin; only sanitized data returns. */
    private fun captureSecondClassScores() {
        if (scoreSaveJob?.isCompleted == false) return
        val capturedAt = System.currentTimeMillis()
        val currentUrl = browser.url ?: return
        if (scoreBusy || !loaded || pageFailed ||
            !extractionAllowed("second_class", currentUrl) ||
            Uri.parse(currentUrl).path != "/sys/stu/score") return
        val epoch = ++captureEpoch
        val key = JSONObject.quote("xiaoxuScore" + UUID.randomUUID().toString().replace("-", ""))
        val salt = scoreIdentity.getString("salt", null) ?: UUID.randomUUID().toString().replace("-", "").also {
            scoreIdentity.edit().putString("salt", it).commit()
        }
        val script = assets.open("ncwu_secondclass_local.js").bufferedReader().use { it.readText() }
        scoreBusy = true
        extract.isEnabled = false
        tell("正在核对本人账号，读取积分、已报名活动与活动广场…")
        fun valid() = epoch == captureEpoch && !isFinishing && !isDestroyed &&
            loaded && !pageFailed && browser.url == currentUrl &&
            extractionAllowed("second_class", browser.url)
        fun unlock() {
            scoreBusy = false
            extract.isEnabled = loaded && !pageFailed && extractionAllowed(provider, browser.url)
        }
        fun accept(payload: JSONObject) {
            if (!valid()) return
            if (!payload.optBoolean("ok")) {
                val code = payload.optInt("httpStatus", 0)
                val stage = payload.optString("stage").takeIf {
                    it in setOf("context", "identity", "score_http", "score_request", "score_response", "score_record", "activity_http", "activity_request", "activity_response", "activity_record", "catalog_request", "catalog_response")
                } ?: "score_response"
                tell((if (SecondClassAutoReadPolicy.requiresReconnect(code, stage)) "登录已失效，请在官网重新登录。"
                      else "积分读取失败，请检查官网或稍后重试。") +
                    " 阶段：$stage" + (if (code in 100..599) "，HTTP $code。" else "。") +
                    "已保存的旧积分保持不变。")
                unlock()
                return
            }
            val rows = try { parseLocalSecondClassScores(payload, capturedAt) }
                catch (_: Exception) { tell("积分字段校验失败，未更改旧数据。"); unlock(); return }
            val activities = try { parseLocalSecondClassActivities(payload, rows.first().capturedAt) }
                catch (_: Exception) { tell("活动分页或字段校验失败，积分与活动旧记录均保持不变。"); unlock(); return }
            val catalog = try { parseLocalSecondClassCatalogEmpty(payload, rows.first().capturedAt) }
                catch (_: Exception) { tell("活动广场空结果校验失败，未更改旧记录。"); unlock(); return }
            val catalogActivities = try {
                parseLocalSecondClassCatalogActivities(payload, rows.first().capturedAt)
            } catch (_: Exception) {
                tell("活动广场名称字段校验失败，旧记录保持不变。"); unlock(); return
            }
            val catalogVerified = payload.optString("catalogStatus") in
                setOf("verified_empty", "verified_nonempty")
            val catalogMessage = when {
                catalogActivities.isNotEmpty() ->
                    "活动广场已读取 ${catalogActivities.size} 条已验证的活动名称（其他字段未核对）"
                catalog.size == 3 ->
                    "活动广场三个筛选本次均返回0条"
                payload.optString("catalogStatus") == "nonempty_unverified" ->
                    "活动广场有非空结果，但字段或分页尚未核验；不保存猜测记录"
                else -> "活动广场本次未核验；旧目录仅在同账号时保留"
            }
            val summary = rows.joinToString("\n") { it.title + " · " + it.details.substringBefore('\n') }
            val accountHash = payload.optString("accountHash")
            if (!accountHash.matches(Regex("^[a-f0-9]{64}$"))) {
                tell("本机账号绑定校验失败，未更改旧数据。"); unlock(); return
            }
            fun saveScores() {
                if (!valid()) { unlock(); return }
                    scoreSaveJob = lifecycleScope.launch {
                        try {
                            // Recheck on the UI thread immediately before starting the transaction.
                            if (!valid()) return@launch
                            withContext(Dispatchers.IO) {
                                currentCoroutineContext().ensureActive()
                                LocalTaskDatabase.open(applicationContext).nativeSchoolRecords()
                                    .replaceSecondClassSnapshot(rows, activities, catalog, catalogActivities,
                                        accountHash = accountHash, catalogVerified = catalogVerified)
                            }
                            if (!valid()) return@launch
                            scoreIdentity.edit().putString("account", accountHash).commit()
                            SecondClassAutoRefresh.onManualSave(applicationContext)
                            savedKind = "credits"
                            savedCount = rows.size
                            finishAndReturn.isEnabled = true
                            finishAndReturn.visibility = View.VISIBLE
                            if (intent.getBooleanExtra(EXTRA_AUTO_SCORE, false)) {
                                setResult(Activity.RESULT_OK, Intent()
                                    .putExtra(EXTRA_PROVIDER, provider).putExtra(EXTRA_RETURN_KIND, savedKind)
                                    .putExtra(EXTRA_SAVED_COUNT, savedCount))
                                finish()
                            }
                            tell("本人积分已保存到手机，共 ${rows.size} 个汇总／学期记录。$catalogMessage。返回校序查看；会话过期时重新登录。")
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            tell("本机保存失败，请重试；旧积分未被清空。")
                        } finally { if (epoch == captureEpoch && !isFinishing) unlock() }
                    }
            }
            val autoMode = intent.getBooleanExtra(EXTRA_AUTO_SCORE, false)
            if (autoMode && scoreIdentity.getString("account", null) == accountHash) saveScores()
            else AlertDialog.Builder(this)
                .setTitle("确认保存本人积分")
                .setMessage("已核对官网当前账号。\n$summary\n本人报名活动：${activities.size - 1} 条，已完整分页读取。\n$catalogMessage。\n保存后，同一账号可在进入第二课堂时刷新；切换账号将再次询问。")
                .setNegativeButton("取消") { _, _ -> unlock() }
                .setOnCancelListener { unlock() }
                .setPositiveButton("保存到校序") { _, _ -> saveScores() }.show()
        }
        fun poll(remaining: Int) {
            if (!valid()) return
            if (remaining <= 0) {
                browser.evaluateJavascript("delete window[$key]", null)
                tell("学校积分查询超时，旧数据保持不变。请检查网络后重试。")
                unlock()
                return
            }
            browser.evaluateJavascript(
                "(function(){var value=window[$key];if(value){delete window[$key];}return value;})()"
            ) { encoded ->
                if (!valid()) return@evaluateJavascript
                val decoded = runCatching { JSONTokener(encoded).nextValue() as? String }.getOrNull()
                if (decoded == null) scoreHandler.postDelayed({ poll(remaining - 1) }, 250)
                else try { accept(JSONObject(decoded)) }
                    catch (_: Exception) { tell("学校响应无法校验，旧数据保持不变。"); unlock() }
            }
        }
        browser.evaluateJavascript(
            "window[$key]=null;($script)('" + salt + "',true,true).then(function(result){window[$key]=JSON.stringify(result);}," +
                "function(){window[$key]=JSON.stringify({ok:false,stage:'score_request'});});"
        ) { if (valid()) poll(280) }
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
    }

    override fun onDestroy() {
        val pendingSave = scoreSaveJob
        abortTaskDiscovery()
        invalidateCapture()
        if (::browser.isInitialized) {
            (browser.parent as? ViewGroup)?.removeView(browser)
            browser.stopLoading()
            browser.destroy()
        }
        if (manualReservation) {
            manualReservation = false
            SecondClassAutoRefresh.manualFinishedAfter(pendingSave)
        }
        super.onDestroy()
    }
}
