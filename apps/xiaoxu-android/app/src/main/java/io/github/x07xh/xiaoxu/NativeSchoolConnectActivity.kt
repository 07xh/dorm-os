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
import androidx.lifecycle.lifecycleScope
import io.github.x07xh.xiaoxu.data.LocalTaskDatabase
import io.github.x07xh.xiaoxu.data.NativeSchoolRecord
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/**
 * A school-owned HTTPS login in an app-controlled WebView. Cookies remain
 * exclusively in WebView's own jar; no JavascriptInterface, credential
 * extractor, remote proxy or Cloudflare credential exchange.
 *
 * Only reads the VISIBLE, already loaded page after an explicit user tap.
 * Not yet an automatic school API adapter; page contracts must be verified
 * for each provider on the owner's own session.
 */
class NativeSchoolConnectActivity : ComponentActivity() {
    companion object {
        const val EXTRA_PROVIDER = "provider"
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
            "second_class" to "https://dekt.ncwu.edu.cn/"
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
    private var provider = ""
    private var loaded = false
    private var pageFailed = false
    private var captureEpoch = 0
    private var savedKind = ""
    private var savedCount = 0

    private fun tell(message: String) {
        status.text = message
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        provider = intent.getStringExtra(EXTRA_PROVIDER).orEmpty()
        val entry = entries[provider] ?: run { finish(); return }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(16, 12, 16, 12)
        }
        status = TextView(this).apply {
            textSize = 13f
            text = "请在学校或学习通官方页面正常登录，打开本人课程／作业／活动列表后，点击「读取当前页」。"
            setPadding(8, 4, 8, 8)
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
        extract = control("读取当前页") { captureVisibleRecords() }
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
                    captureEpoch++
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
                        captureEpoch++
                        pageFailed = true
                        loaded = false
                        extract.isEnabled = false
                        appendVisible?.isEnabled = false
                        tell("学校页面加载失败；请检查网络或重新打开官方页面，本次未读取数据。")
                    }
                }
                override fun onReceivedSslError(
                    view: WebView, handler: SslErrorHandler, error: SslError
                ) {
                    handler.cancel()
                    pageFailed = true
                    loaded = false
                    extract.isEnabled = false
                    appendVisible?.isEnabled = false
                    tell("学校 HTTPS 证书验证失败，已阻止继续访问。")
                }
                override fun onReceivedHttpError(
                    view: WebView, request: WebResourceRequest, response: WebResourceResponse
                ) {
                    if (request.isForMainFrame && response.statusCode >= 400) {
                        pageFailed = true
                        loaded = false
                        extract.isEnabled = false
                        appendVisible?.isEnabled = false
                        tell("学校页面返回 HTTP " + response.statusCode + "。请检查官网是否可正常访问。")
                    }
                }
            }
        }
        root.addView(controls)
        appendVisible?.let(root::addView)
        if (courseShortcut != null) root.addView(courseShortcut)
        if (homeworkShortcut != null) root.addView(homeworkShortcut)
        if (ncwuSsoShortcut != null) root.addView(ncwuSsoShortcut)
        root.addView(finishAndReturn)
        root.addView(status)
        root.addView(browser, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        browser.loadUrl(entry)
    }

    private fun captureVisibleRecords(append: Boolean = false) {
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
                    tell("当前页面超过 120 条可见记录，已阻止截断导入；旧记录保持不变。可在官网分页，逐页使用「分页追加本页」读取。")
                    return@evaluateJavascript
                }
                val kind = payload.optString("kind")
                val kinds = when (provider) {
                    "chaoxing" -> setOf("course", "homework")
                    "second_class" -> setOf("activity", "credits")
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
                lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            val dao = LocalTaskDatabase.open(applicationContext)
                                .nativeSchoolRecords()
                            if (append) dao.insertAll(accepted)
                            else dao.replaceKind(provider, kind, accepted)
                        }
                        if (epoch != captureEpoch || isFinishing) return@launch
                        // The result contract never contains raw course names,
                        // student identifiers, school credentials or HTML.
                        savedKind = kind
                        savedCount = accepted.size
                        finishAndReturn.isEnabled = true
                        tell((if (append) "已追加／更新本页 " else "已替换同类旧记录，保存 ") +
                            accepted.size + " 条本机页面记录。" +
                            (if (append) "旧页记录未删除；请核对不同页面是否有重名课程或作业。" else
                                "再次读取同类别时会替换旧记录；跨页请使用「分页追加本页」。") +
                            "这不是学校官方完整同步。")
                    } catch (_: Exception) {
                        tell("本机数据库保存失败。原有记录不会因本次操作被清空。")
                    }
                }
            } catch (_: Exception) {
                tell("当前页面没有匹配的业务列表，或页面结构已变化。没有写入猜测数据；请进入课程／作业／活动列表后再试。")
            }
        }
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
    }

    override fun onDestroy() {
        captureEpoch++
        if (::browser.isInitialized) {
            (browser.parent as? ViewGroup)?.removeView(browser)
            browser.stopLoading()
            browser.destroy()
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
  const rows = [];
  let kind = '';
  // The all-task page may also contain course/summary tables. Those are NOT
  // evidence that a homework title was read from an official work row.
  const homeworkOverview = provider === 'chaoxing' &&
    host === 'mooc1-api.chaoxing.com' &&
    location.pathname === '/mooc-ans/mooc2/work/all-task';
  for (const doc of docs) {
    for (const table of Array.from(doc.querySelectorAll('table')).slice(0,35)) {
      if (!onScreen(table)) continue;
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
