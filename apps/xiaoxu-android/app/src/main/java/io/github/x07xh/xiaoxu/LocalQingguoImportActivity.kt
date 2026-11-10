package io.github.x07xh.xiaoxu

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.view.ViewGroup
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceResponse
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import io.github.x07xh.xiaoxu.data.LocalScheduleStore
import org.json.JSONObject
import org.json.JSONTokener

/**
 * User-initiated local NCWU timetable import. The official login runs inside
 * Android WebView. No JavaScriptInterface is installed; no password, Cookie,
 * student ID, or original HTML is transferred to the app's Cloudflare backend.
 *
 * The resulting course meetings are explicitly self-imported, not verified.
 */
class LocalQingguoImportActivity : ComponentActivity() {
    private lateinit var browser: WebView
    private lateinit var status: TextView
    private lateinit var extractButton: Button
    private lateinit var inspectButton: Button
    private var extracting = false
    private var pageFailed = false
    private var pageLoaded = false
    private var pageEpoch = 0

    private fun isOfficialPage(url: String?): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        return uri.scheme == "https" && uri.userInfo == null &&
            uri.host?.lowercase() == "jwmis.ncwu.edu.cn"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(12, 12, 12, 8)
        }
        val back = Button(this).apply { text = "返回" }
        extractButton = Button(this).apply {
            text = "提取本页课表"
            isEnabled = false
        }
        inspectButton = Button(this).apply {
            text = "检查选课页面结构"
            isEnabled = false
        }
        controls.addView(back, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        controls.addView(extractButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f))
        controls.addView(inspectButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f))
        status = TextView(this).apply {
            text = "请在华水官方页面本人登录，打开个人课表，再点击提取。课程仅保存在本机。"
            setTextColor(Color.DKGRAY)
            setPadding(18, 8, 18, 12)
        }
        browser = WebView(this).apply {
            settings.javaScriptEnabled = true // Required by the official CAS pages.
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.safeBrowsingEnabled = true
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (!request.isForMainFrame) return false
                    val uri = request.url
                    val host = uri.host?.lowercase() ?: ""
                    if (uri.scheme == "https" &&
                        (host == "ncwu.edu.cn" || host.endsWith(".ncwu.edu.cn"))) return false
                    status.text = "安全限制：仅允许在华水官方 HTTPS 页面内完成导入。"
                    return true
                }
                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    pageEpoch++
                    pageFailed = false
                    pageLoaded = false
                    extractButton.isEnabled = false
                    inspectButton.isEnabled = false
                    status.text = "学校官方页面加载中…"
                }
                override fun onPageFinished(view: WebView, url: String) {
                    pageLoaded = !pageFailed && isOfficialPage(url)
                    extractButton.isEnabled = pageLoaded && !extracting
                    inspectButton.isEnabled = pageLoaded && !extracting
                    if (!pageFailed) {
                        status.text = if (pageLoaded)
                            "请先在华水官方页面打开个人课表，再点击提取；本机导入不需要云端登录。"
                        else "请继续在华水官方认证页面登录，返回教务课表后才可提取。"
                    }
                }
                override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                    handler.cancel()
                    pageFailed = true
                    pageLoaded = false
                    extractButton.isEnabled = false
                    inspectButton.isEnabled = false
                    status.text = "学校 HTTPS 证书验证失败，已禁止在此页面读取课程。"
                }
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) {
                        pageFailed = true
                        pageLoaded = false
                        extractButton.isEnabled = false
                        inspectButton.isEnabled = false
                        status.text = "学校页面加载失败，请检查网络或稍后重新打开教务网站。"
                    }
                }
                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                    if (request.isForMainFrame && response.statusCode >= 400) {
                        pageFailed = true
                        pageLoaded = false
                        extractButton.isEnabled = false
                        inspectButton.isEnabled = false
                        status.text = "学校页面返回 HTTP " + response.statusCode + "，未读取任何课程。"
                    }
                }
            }
        }
        root.addView(controls)
        root.addView(status)
        root.addView(browser, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        back.setOnClickListener { if (browser.canGoBack()) browser.goBack() else finish() }
        extractButton.setOnClickListener { extractVisibleTimetable() }
        inspectButton.setOnClickListener { inspectVisibleElectiveSource() }
        browser.loadUrl("https://jwmis.ncwu.edu.cn/")
    }

    private fun extractVisibleTimetable() {
        if (extracting) return
        val currentUrl = browser.url
        if (!pageLoaded || pageFailed || !isOfficialPage(currentUrl)) {
            status.text = "请先完成官方登录，进入华水个人课表页面。"
            return
        }
        val epoch = pageEpoch
        extracting = true
        extractButton.isEnabled = false
        inspectButton.isEnabled = false
        status.text = "正在本机读取当前页面的课表网格…"
        try {
            val script = assets.open("ncwu_qingguo_local.js").bufferedReader().use { it.readText() }
            browser.evaluateJavascript(script + "\nJSON.stringify(window.__xiaoxuExtract())") { encoded ->
                extracting = false
                if (epoch != pageEpoch || browser.url != currentUrl || isFinishing) {
                    status.text = "读取期间网页发生变化；请重新打开个人课表再试。"
                    return@evaluateJavascript
                }
                extractButton.isEnabled = pageLoaded && !pageFailed
                inspectButton.isEnabled = pageLoaded && !pageFailed
                try {
                    val jsonText = JSONTokener(encoded).nextValue() as? String
                        ?: throw IllegalArgumentException("页面没有返回课表")
                    val result = JSONObject(jsonText)
                    val error = result.optString("error")
                    if (error.isNotBlank()) throw IllegalArgumentException(error)
                    val items = result.optJSONArray("items")
                        ?: throw IllegalArgumentException("课表数据为空")
                    val count = LocalScheduleStore.parseCourses(items.toString()).size
                    val previous = LocalScheduleStore.load(this).size
                    val fewer = previous > 0 && count < previous
                    val summary = "本次识别到 ${count} 个课程课块" +
                        (if (previous > 0) "，本机原有 ${previous} 个" else "") +
                        "。请与官网逐项核对星期、周次、节次、教师、教室。\n\n" +
                        (if (fewer)
                            "注意：本次课块数量比原本少。可能是本学期课程调整，也可能是网站课表识别不全；未确认前建议保留旧课表。\n\n"
                         else "") +
                        "这是本机页面导入，不等于学校官方实时同步。"
                    val commitImport = {
                        try {
                            val saved = LocalScheduleStore.save(this, items.toString())
                            Toast.makeText(this, "已保存 ${saved} 个课块", Toast.LENGTH_LONG).show()
                            finish()
                        } catch (e: Exception) {
                            status.text = e.message ?: "本机保存失败，旧课表未修改"
                        }
                    }
                    AlertDialog.Builder(this)
                        .setTitle(if (fewer) "本次课表可能不完整" else "确认本机导入")
                        .setMessage(summary)
                        .setNegativeButton("保留旧课表", null)
                        .setPositiveButton(if (fewer) "继续核对" else "保存到本机") { _, _ ->
                            if (fewer) {
                                AlertDialog.Builder(this)
                                    .setTitle("确认替换已有课表")
                                    .setMessage("本机原有 ${previous} 个课块，本次仅识别 ${count} 个。请确认你已经与学校官方课表逐项核对；否则选择保留旧课表。")
                                    .setNegativeButton("保留旧课表", null)
                                    .setPositiveButton("已核对，仍要替换") { _, _ ->
                                        commitImport()
                                    }.show()
                            } else {
                                commitImport()
                            }
                        }.show()
                    status.text = "已生成课程数对照预览，等待本人确认；保存前旧课表不变。"
                } catch (e: Exception) {
                    status.text = (e.message ?: "无法解析当前页面") + "；旧课表保持不变。"
                }
            }
        } catch (e: Exception) {
            extracting = false
            extractButton.isEnabled = pageLoaded && !pageFailed
            inspectButton.isEnabled = pageLoaded && !pageFailed
            status.text = "课表提取组件加载失败，旧课表保持不变。"
        }
    }


    /**
     * Inspect only the *structure* of the already-open official elective page.
     * No school request, session transfer, personal record or timetable write.
     */
    private fun inspectVisibleElectiveSource() {
        if (extracting) return
        val currentUrl = browser.url
        if (!pageLoaded || pageFailed || !isOfficialPage(currentUrl)) {
            status.text = "请先通过华水官网进入本人选课页面，再检查页面结构。"
            return
        }
        val epoch = pageEpoch
        extracting = true
        extractButton.isEnabled = false
        inspectButton.isEnabled = false
        status.text = "正在本机识别表格列名与数量，不读取具体课程。"
        try {
            val script = assets.open("ncwu_elective_source_probe.js")
                .bufferedReader().use { it.readText() }
            browser.evaluateJavascript(
                script + "\nJSON.stringify(window.__xiaoxuInspectElectiveSource())"
            ) { encoded ->
                extracting = false
                if (epoch != pageEpoch || browser.url != currentUrl || isFinishing) {
                    status.text = "检查时学校页面已切换，请回到选课页重新检查。"
                    return@evaluateJavascript
                }
                extractButton.isEnabled = pageLoaded && !pageFailed
                inspectButton.isEnabled = pageLoaded && !pageFailed
                try {
                    val jsonText = JSONTokener(encoded).nextValue() as? String
                        ?: throw IllegalArgumentException("未识别到页面表格结构")
                    val result = JSONObject(jsonText)
                    if (result.optString("schema") !=
                        "xiaoxu/ncwu-elective-source-fingerprint-v1")
                        throw IllegalArgumentException("页面结构结果版本不匹配")
                    val tables = result.optJSONArray("candidateTables")
                        ?: throw IllegalArgumentException("页面结构结果不完整")
                    val columns = mapOf(
                        "courseName" to "课程名称", "courseCode" to "课程代码",
                        "classCode" to "教学班", "teacher" to "任课教师",
                        "campus" to "校区", "capacity" to "容量",
                        "remaining" to "剩余名额", "meeting" to "上课时间",
                        "status" to "选课状态", "courseType" to "课程类别",
                        "credit" to "学分"
                    )
                    val recognized = linkedSetOf<String>()
                    var likely = 0
                    for (i in 0 until minOf(tables.length(), 20)) {
                        val item = tables.optJSONObject(i) ?: continue
                        if (item.optBoolean("likelyCatalog")) likely++
                        val labels = item.optJSONArray("columns") ?: continue
                        for (j in 0 until minOf(labels.length(), 20)) {
                            val name = columns[labels.optString(j)]
                            if (name != null) recognized += name
                        }
                    }
                    val details = if (recognized.isEmpty()) "未匹配到标准课程列名"
                        else recognized.joinToString("、")
                    val message = "只读结构检查：识别到 " + tables.length() +
                        " 个含课程字段的表格，其中 " + likely +
                        " 个可能是教学班列表。\\n识别列：$details\\n\\n" +
                        "本次没有读取任何课程行、个人信息或实时余量，" +
                        "也没有向学校发送新请求。仅凭列名不能证明选课接口兼容，" +
                        "正式课程读取仍需要本人按官网页面逐项核验。"
                    AlertDialog.Builder(this)
                        .setTitle("选课页面结构检查")
                        .setMessage(message)
                        .setPositiveButton("知道了", null)
                        .show()
                    status.text = if (likely > 0)
                        "已识别教学班列表结构候选；尚未接入课程读取。"
                    else "尚未识别教学班列表。可在官网进入选课列表后再检查。"
                } catch (_: Exception) {
                    status.text = "当前页面结构无法确认；未读取或保存任何课程。"
                }
            }
        } catch (_: Exception) {
            extracting = false
            extractButton.isEnabled = pageLoaded && !pageFailed
            inspectButton.isEnabled = pageLoaded && !pageFailed
            status.text = "只读选课结构检查组件未安装，原有课表保持不变。"
        }
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
    }

    override fun onDestroy() {
        pageEpoch++
        (browser.parent as? ViewGroup)?.removeView(browser)
        browser.stopLoading()
        browser.destroy()
        super.onDestroy()
    }
}
