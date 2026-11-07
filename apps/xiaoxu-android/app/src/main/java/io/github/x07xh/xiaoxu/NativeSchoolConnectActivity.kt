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
import androidx.lifecycle.lifecycleScope
import io.github.x07xh.xiaoxu.data.LocalTaskDatabase
import io.github.x07xh.xiaoxu.data.NativeSchoolRecord
import io.github.x07xh.xiaoxu.data.SecondClassAutoRefresh
import io.github.x07xh.xiaoxu.data.parseLocalSecondClassScores
import io.github.x07xh.xiaoxu.data.parseLocalSecondClassActivities
import io.github.x07xh.xiaoxu.data.parseLocalSecondClassCatalogEmpty
import io.github.x07xh.xiaoxu.data.parseLocalSecondClassCatalogActivities
import java.util.UUID
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
        // Exactly the existing first-party course-list endpoint used by the
        // trusted server adapter; this shortcut does not transfer WebView cookies.
        private const val CHAOXING_COURSES =
            "https://mooc2-ans.chaoxing.com/mooc2-ans/visit/courselistdata"
        // Official personal homework overview; still requires the owner's normal login.
        private const val CHAOXING_HOMEWORK =
            "https://mooc1-api.chaoxing.com/mooc-ans/mooc2/work/all-task"
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
        private val chaoxing = setOf(
            "i.chaoxing.com", "passport2.chaoxing.com",
            "mooc2-ans.chaoxing.com", "mooc1.chaoxing.com",
            "mooc1-api.chaoxing.com", "mooc2.chaoxing.com",
            "passport2-app.chaoxing.com", "kb.chaoxing.com"
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
                    else -> host in chaoxing
                }
                "second_class" -> host in schools
                else -> false
            }
        }
        fun extractionAllowed(provider: String, url: String?): Boolean {
            if (!navigationAllowed(provider, url)) return false
            val uri = Uri.parse(url ?: "")
            val host = uri.host?.lowercase() ?: return false
            val path = uri.path.orEmpty()
            return when (provider) {
                "second_class" -> host == "dekt.ncwu.edu.cn"
                "chaoxing" ->
                    (host == "mooc1-api.chaoxing.com" &&
                        path == "/mooc-ans/mooc2/work/all-task") ||
                    (host == "mooc1.chaoxing.com" &&
                        (path.startsWith("/mooc2/work/") ||
                         path.startsWith("/mooc-ans/mooc2/work/") ||
                         path.startsWith("/visit/"))) ||
                    (host == "mooc2-ans.chaoxing.com" &&
                        path.startsWith("/mooc2-ans/visit/")) ||
                    (host == "kb.chaoxing.com" &&
                        path.startsWith("/res/pc/curriculum/"))
                else -> false
            }
        }
    }

    private lateinit var browser: WebView
    private lateinit var status: TextView
    private lateinit var extract: Button
    private var appendVisible: Button? = null
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
            setPadding(16, 12, 16, 12)
        }
        status = TextView(this).apply {
            textSize = 13f
            text = "请在学校或学习通官方页面正常登录，打开本人课程／作业／活动列表后，点击「读取当前页」。"
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
            if (browser.canGoBack()) browser.goBack() else finish()
        }
        control("刷新页面") { browser.reload() }
        extract = control("读取并替换同类记录") { captureVisibleRecords() }
        if (provider == "second_class") extract.text = "读取／刷新本人积分"
        extract.isEnabled = false
        // Explicit paginated-page action. The default still replaces the old
        // snapshot; only deliberate appends preserve previously read pages.
        appendVisible = if (provider == "chaoxing") Button(this).apply {
            text = "分页追加本页（保留已保存记录）"
            isEnabled = false
            setOnClickListener { captureVisibleRecords(append = true) }
        } else null
        val courseShortcut = if (provider == "chaoxing") Button(this).apply {
            text = "学习通 · 打开我的课程"
            setOnClickListener { browser.loadUrl(CHAOXING_COURSES) }
        } else null
        val homeworkShortcut = if (provider == "chaoxing") Button(this).apply {
            text = "学习通 · 打开我的作业"
            setOnClickListener { browser.loadUrl(CHAOXING_HOMEWORK) }
        } else null
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
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.allowFileAccessFromFileURLs = false
            settings.allowUniversalAccessFromFileURLs = false
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.safeBrowsingEnabled = true
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (!request.isForMainFrame) return false
                    val allowed = navigationAllowed(provider, request.url.toString())
                    if (!allowed) tell("已拦截非指定学校／学习通主站的页面跳转。")
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
                    appendVisible?.isEnabled = false
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
                    val host = Uri.parse(url ?: "").host
                    extract.isEnabled = loaded && extractionAllowed(provider, url)
                    appendVisible?.isEnabled = extract.isEnabled
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
                        loaded = false
                        extract.isEnabled = false
                        appendVisible?.isEnabled = false
                        catalogProbe.isEnabled = false
                        tell("学校页面加载失败；请检查网络或重新打开官方页面，本次未读取数据。")
                    }
                }
                override fun onReceivedSslError(
                    view: WebView, handler: SslErrorHandler, error: SslError
                ) {
                    handler.cancel()
                    invalidateCapture()
                    pendingOfficialCatalogRead = false
                    pageFailed = true
                    loaded = false
                    extract.isEnabled = false
                    appendVisible?.isEnabled = false
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
                        appendVisible?.isEnabled = false
                        catalogProbe.isEnabled = false
                        tell("学校页面返回 HTTP " + response.statusCode + "。请检查官网是否可正常访问。")
                    }
                }
            }
        }
        root.addView(controls)
        appendVisible?.let { root.addView(it) }
        if (courseShortcut != null) root.addView(courseShortcut)
        if (homeworkShortcut != null) root.addView(homeworkShortcut)
        if (ncwuSsoShortcut != null) root.addView(ncwuSsoShortcut)
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

    private fun captureVisibleRecords(append: Boolean = false) {
        if (provider == "second_class") {
            val uri = Uri.parse(browser.url ?: "")
            if (!loaded || pageFailed || uri.host != "dekt.ncwu.edu.cn") {
                tell("请先在学校官网正常登录，再读取活动。")
                return
            }
            if (uri.path == "/sys/stu/score") {
                captureSecondClassScores()
            } else if (uri.path.orEmpty().startsWith("/sys/")) {
                // The official catalog uses card layout, not the generic HTML
                // table parser. Reuse the already audited owner-bound score +
                // same-origin catalog API, instead of overwriting personal
                // signup records from a brittle visible-page scrape.
                pendingOfficialCatalogRead = true
                tell("正在核验本人学校会话，并读取积分与活动广场…")
                browser.loadUrl(entries.getValue("second_class"))
            } else {
                tell("请先进入学校本人业务页面，登录后可刷新活动。")
            }
            return
        }
        if (append && provider != "chaoxing") return
        val currentUrl = browser.url ?: return
        val host = Uri.parse(currentUrl).host
        if (!loaded || pageFailed || !navigationAllowed(provider, currentUrl) ||
            !extractionAllowed(provider, currentUrl)) {
            tell("请先在指定官方业务页面打开本人列表，登录页不能导入。")
            return
        }
        val epoch = ++captureEpoch
        extract.isEnabled = false
        appendVisible?.isEnabled = false
        tell("正在从当前已显示的官方页面读取记录（不会获取密码和 Cookie）…")
        // Fixed read-only script, never interpolates caller-controlled values or
        // executes arbitrary downloaded adapters. No form submit or hidden data.
        browser.evaluateJavascript(READ_VISIBLE_PAGE) { encoded ->
            if (epoch != captureEpoch || isFinishing) return@evaluateJavascript
            extract.isEnabled = loaded && !pageFailed &&
                extractionAllowed(provider, browser.url)
            appendVisible?.isEnabled = extract.isEnabled
            if (!extract.isEnabled || browser.url != currentUrl) {
                tell("读取时页面已变化，请重新打开需要的列表。")
                return@evaluateJavascript
            }
            try {
                val decoded = JSONTokener(encoded).nextValue() as? String
                    ?: throw IllegalArgumentException("empty")
                val payload = JSONObject(decoded)
                if (payload.optString("host") != host) throw IllegalArgumentException("origin changed")
                if (payload.optBoolean("truncated")) {
                    tell("当前页面超过 120 条可见记录，或表格数量超出安全扫描上限；已阻止不完整导入，旧记录保持不变。可在官网分页后逐页使用「分页追加本页」。")
                    return@evaluateJavascript
                }
                val kind = payload.optString("kind")
                val kinds = when (provider) {
                    "chaoxing" -> setOf("course", "homework")
                    "second_class" -> setOf("activity")
                    else -> emptySet()
                }
                if (kind !in kinds) throw IllegalArgumentException("unknown kind")
                val items = payload.getJSONArray("records")
                if (items.length() !in 1..120) throw IllegalArgumentException("no trusted rows")
                val now = System.currentTimeMillis()
                val accepted = mutableListOf<NativeSchoolRecord>()
                val keys = mutableSetOf<String>()
                for (i in 0 until items.length()) {
                    val row = items.getJSONObject(i)
                    val title = row.optString("title").trim()
                    val details = row.optString("details").trim()
                    if (title.isBlank() || title.length > 100 ||
                        details.length > 350) throw IllegalArgumentException("invalid data")
                    val fingerprint = MessageDigest.getInstance("SHA-256")
                        .digest((provider + "|" + kind + "|" + title + "|" + details)
                            .toByteArray(Charsets.UTF_8))
                        .joinToString("") { "%02x".format(it) }
                    if (keys.add(fingerprint)) accepted.add(NativeSchoolRecord(
                        provider = provider, kind = kind, fingerprint = fingerprint,
                        title = title, details = details, sourceHost = host ?: "",
                        capturedAt = now
                    ))
                }
                if (accepted.isEmpty()) throw IllegalArgumentException("no records")
                // Prevent two overlapping writes from changing the same kind.
                extract.isEnabled = false
                appendVisible?.isEnabled = false
                lifecycleScope.launch {
                    try {
                        val total = withContext(Dispatchers.IO) {
                            val dao = LocalTaskDatabase.open(applicationContext)
                                .nativeSchoolRecords()
                            if (append) dao.appendKind(provider, kind, accepted)
                            else {
                                dao.replaceKind(provider, kind, accepted)
                                accepted.size
                            }
                        }
                        if (epoch != captureEpoch || isFinishing) return@launch
                        // This result only includes a provider, kind and actual
                        // total saved count. School titles and HTML stay in Room.
                        savedKind = kind
                        savedCount = total
                        finishAndReturn.isEnabled = true
                        tell((if (append) "本页已追加或去重 " else "本页已替换旧记录，保存 ") +
                            accepted.size + " 条；当前本机同类共 " + total + " 条。" +
                            (if (append) "旧页保留，作业状态更新可能产生重复，请按课程核对。" else
                                "下次跨页读取请选择「分页追加本页」。") +
                            "这不是学校官方完整同步，也未核实全部提交状态。")
                    } catch (_: IllegalArgumentException) {
                        tell("合并后超出 500 条上限，已拒绝写入，旧记录保持不变。请核对本机记录或重新开始本类采集。")
                    } catch (_: Exception) {
                        tell("本机数据库保存失败。本次操作没有清空已有记录。")
                    } finally {
                        if (epoch == captureEpoch && !isFinishing) {
                            extract.isEnabled = loaded && !pageFailed &&
                                extractionAllowed(provider, browser.url)
                            appendVisible?.isEnabled = extract.isEnabled
                        }
                    }
                }
            } catch (_: Exception) {
                tell("当前页面没有匹配的业务列表，或页面结构已变化。没有写入猜测数据；请进入课程／作业／活动列表后再试。")
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
                tell((if (code == 401 || code == 403) "登录已失效，请在官网重新登录。"
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
            val catalogMessage = when {
                catalogActivities.isNotEmpty() ->
                    "活动广场已读取 ${catalogActivities.size} 条已验证的活动名称（其他字段未核对）"
                catalog.size == 3 ->
                    "活动广场三个筛选本次均返回0条"
                payload.optString("catalogStatus") == "nonempty_unverified" ->
                    "活动广场有非空结果，但字段或分页尚未核验；不保存猜测记录"
                else -> "活动广场暂无可用的已验证快照"
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
                                    .replaceSecondClassSnapshot(rows, activities, catalog, catalogActivities, accountHash = accountHash)
                            }
                            if (!valid()) return@launch
                            scoreIdentity.edit().putString("account", accountHash).commit()
                            SecondClassAutoRefresh.onManualSave(applicationContext)
                            savedKind = "credits"
                            savedCount = rows.size
                            finishAndReturn.isEnabled = true
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

/** The current page must have visible structured records. Unknown layouts fail closed. */
private const val READ_VISIBLE_PAGE = """
(function () {
  const host = location.hostname;
  const clean = x => String(x || '').replace(/\s+/g, ' ').trim().slice(0,350);
  const docs = [document];
  document.querySelectorAll('iframe').forEach(frame => {
    try { if (frame.contentDocument && frame.contentDocument.location.origin === location.origin)
      docs.push(frame.contentDocument); } catch (_) {}
  });
  const onScreen = node => !!(node && node.getClientRects().length);
  const provider = host === 'dekt.ncwu.edu.cn' ? 'second_class' : 'chaoxing';
  // A partial snapshot must never replace previously saved personal records.
  const MAX_RECORDS = 120;
  // Bound DOM traversal, but never silently drop tables beyond an arbitrary prefix.
  const MAX_TABLES = 200;
  let visibleTables = 0;
  const rows = [];
  let kind = '';
  // The all-task page may also contain course/summary tables. Those are NOT
  // evidence that a homework title was read from an official work row.
  const homeworkOverview = provider === 'chaoxing' &&
    host === 'mooc1-api.chaoxing.com' &&
    location.pathname === '/mooc-ans/mooc2/work/all-task';
  for (const doc of docs) {
    for (const table of Array.from(doc.querySelectorAll('table'))) {
      if (!onScreen(table)) continue;
      if (++visibleTables > MAX_TABLES)
        return JSON.stringify({host,kind,records:[],truncated:true});
      const trs = Array.from(table.querySelectorAll('tr')).filter(onScreen);
      if (trs.length < 2) continue;
      const header = Array.from(trs[0].querySelectorAll('th,td')).map(el => clean(el.innerText));
      if (header.length < 2 || header.length > 18) continue;
      const compact = header.map(s => s.replace(/\s/g,''));
      const matching = provider === 'second_class'
        ? compact.some(h => /活动名称|项目名称|积分|学时|第二课堂/.test(h))
        : compact.some(h => /作业名称|课程名称|课程名|作业|任务名称/.test(h));
      if (!matching) continue;
      // An activity table can have an "积分" column. That does not make its
      // rows an official cumulative credit statement.
      const foundKind = provider === 'second_class'
        ? (compact.some(h => /^(活动名称|活动项目|报名活动)$/.test(h))
            ? 'activity'
            : compact.some(h => /^(积分项目|积分项|学时项目|积分来源|学时汇总)$/.test(h))
              ? 'credits'
              : 'activity')
        : (compact.some(h => /作业|任务名称/.test(h)) ? 'homework' : 'course');
      if (kind && foundKind !== kind) continue;
      const titleIndex = foundKind === 'homework'
        ? compact.findIndex(h => /^(作业名称|作业名|任务名称|作业标题|作业)$/.test(h))
        : compact.findIndex(h => /^(课程名称|课程名|活动名称|项目名称|积分项目|学时项目)$/.test(h));
      // Never substitute course name or a status column for the work title.
      // On the official all-task page, ignore course-only summary tables.
      const first = foundKind === 'homework' ? titleIndex :
        titleIndex >= 0 ? titleIndex :
        compact.findIndex(h => /^(课程|活动|名称|项目|类别|类型|分类|积分项目|项目类别)$/.test(h));
      if (first < 0 || (homeworkOverview && foundKind !== 'homework')) continue;
      let appended = false;
      for (const tr of trs.slice(1)) {
        const cells = Array.from(tr.querySelectorAll(':scope > th,:scope > td')).map(el => clean(el.innerText));
        if (cells.length !== header.length || !cells[first]) continue;
        const title = cells[first].slice(0,100);
        const details = cells.map((value,i) =>
          i !== first && value ? compact[i] + '：' + value.slice(0,90) : ''
        ).filter(Boolean).join(' · ').slice(0,350);
        if (title.length >= 2 && !/登录|验证码|退出系统/.test(title)) {
          rows.push({title,details});
          appended = true;
        }
        if (rows.length > MAX_RECORDS) break;
      }
      // A header without valid visible records must not block another table.
      if (appended) kind = foundKind;
    }
  }
  if (!rows.length && provider === 'chaoxing' &&
      host === 'mooc1.chaoxing.com' && /\/work\/list/.test(location.pathname)) {
    // Mirror the already-tested FIRST-PARTY backend list evidence: each
    // visible li[onclick*=goTask] has title p.overHidden2 and status p.status.
    // Never read the li "data" attribute: it may contain an auth token.
    for (const doc of docs) {
      const tasks = doc.querySelectorAll('li[onclick*="goTask"]');
      for (const li of Array.from(tasks)) {
        if (!onScreen(li)) continue;
        const title = clean(li.querySelector('p.overHidden2.fl')?.innerText ||
          li.querySelector('p.overHidden2')?.innerText).slice(0,100);
        if (!title) continue;
        const status = clean(li.querySelector('p.status.fl')?.innerText ||
          li.querySelector('p.status')?.innerText).slice(0,60);
        rows.push({title,details: status ?
          '当前网页显示的状态：' + status : '网页未展示提交状态'});
        if (rows.length > MAX_RECORDS) break;
      }
    }
    if (rows.length) kind = 'homework';
  }
  if (!rows.length && provider === 'chaoxing' &&
      host === 'mooc2-ans.chaoxing.com' &&
      location.pathname.includes('/visit/courselistdata')) {
    // MIT xuexitong-cli shows course-name and courseid links on the
    // authenticated official course-list page. Never infer course titles
    // from unrelated links or sign-in screens.
    for (const doc of docs) {
      const nodes = doc.querySelectorAll('.course-name[title],a[href*="courseid="] .course-name');
      for (const el of Array.from(nodes)) {
        if (!onScreen(el)) continue;
        const title = clean(el.getAttribute('title') || el.innerText).slice(0,100);
        if (title.length > 1) rows.push({title,details:'课程列表页面'});
        if (rows.length > MAX_RECORDS) break;
      }
    }
    if (rows.length) kind = 'course';
  }
  if (rows.length > MAX_RECORDS)
    return JSON.stringify({host,kind,records:[],truncated:true});
  return JSON.stringify({host,kind,records:rows});
})()
""";
