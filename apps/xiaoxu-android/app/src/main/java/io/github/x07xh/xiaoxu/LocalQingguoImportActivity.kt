package io.github.x07xh.xiaoxu

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
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
    private var extracting = false

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
        val extract = Button(this).apply { text = "提取本页课表" }
        controls.addView(back, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        controls.addView(extract, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f))
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
                override fun onPageFinished(view: WebView, url: String) {
                    status.text = "打开个人课表页面后，点击“提取本页课表”。"
                }
            }
        }
        root.addView(controls)
        root.addView(status)
        root.addView(browser, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        back.setOnClickListener { if (browser.canGoBack()) browser.goBack() else finish() }
        extract.setOnClickListener { extractVisibleTimetable() }
        browser.loadUrl("https://jwmis.ncwu.edu.cn/")
    }

    private fun extractVisibleTimetable() {
        if (extracting) return
        val uri = android.net.Uri.parse(browser.url ?: "")
        val host = uri.host?.lowercase() ?: ""
        if (uri.scheme != "https" ||
            (host != "ncwu.edu.cn" && !host.endsWith(".ncwu.edu.cn"))) {
            status.text = "请先打开华水官方 HTTPS 教务课表页面。"
            return
        }
        extracting = true
        status.text = "正在本机读取当前页面的课表网格…"
        try {
            val script = assets.open("ncwu_qingguo_local.js").bufferedReader().use { it.readText() }
            browser.evaluateJavascript(script + "\nJSON.stringify(window.__xiaoxuExtract())") { encoded ->
                extracting = false
                try {
                    val jsonText = JSONTokener(encoded).nextValue() as? String
                        ?: throw IllegalArgumentException("页面没有返回课表")
                    val result = JSONObject(jsonText)
                    val error = result.optString("error")
                    if (error.isNotBlank()) throw IllegalArgumentException(error)
                    val items = result.optJSONArray("items")
                        ?: throw IllegalArgumentException("课表数据为空")
                    val count = LocalScheduleStore.parseCourses(items.toString()).size
                    AlertDialog.Builder(this)
                        .setTitle("确认本机导入")
                        .setMessage("读取到 ${count} 个课程课块。请与官方课表逐项核对星期、周次、节次、教师和教室。此操作不会上传到云端，也不代表官方认证。\n\n保存后可在校序“课表”页直接查看。")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("保存到本机") { _, _ ->
                            try {
                                val saved = LocalScheduleStore.save(this, items.toString())
                                Toast.makeText(this, "已保存 ${saved} 个课块", Toast.LENGTH_LONG).show()
                                finish()
                            } catch (e: Exception) {
                                status.text = e.message ?: "本机保存失败，旧课表未修改"
                            }
                        }.show()
                    status.text = "已生成导入预览，等待本人确认。"
                } catch (e: Exception) {
                    status.text = (e.message ?: "无法解析当前页面") + "；旧课表保持不变。"
                }
            }
        } catch (e: Exception) {
            extracting = false
            status.text = "课表提取组件加载失败，旧课表保持不变。"
        }
    }

    override fun onDestroy() {
        browser.stopLoading()
        browser.destroy()
        super.onDestroy()
    }
}
