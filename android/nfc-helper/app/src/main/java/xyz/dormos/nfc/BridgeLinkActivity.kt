package xyz.dormos.nfc

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * One-shot website handoff with user confirmation. This exported entry does not
 * accept script IDs, shell text, or commands and never invokes Root directly.
 */
class BridgeLinkActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showLink(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        showLink(intent)
    }

    private fun showLink(source: Intent?) {
        val scene = if (source?.action == Intent.ACTION_VIEW) {
            BridgeLinkRules.sceneFromLink(source.dataString)
        } else null
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(40, 40, 40, 40)
            setBackgroundColor(0xfff6f8fc.toInt())
        }
        val title = TextView(this).apply {
            text = if (scene == null) "无法确认网站链接" else "打开 DORM NFC Manager"
            textSize = 23f
            setTextColor(0xff183252.toInt())
        }
        val message = TextView(this).apply {
            text = if (scene == null) {
                "链接来源、路径或场景参数无效。未执行任何操作。"
            } else {
                "来自网站的【${BridgeLinkRules.label(scene)}】场景请求。\n\n" +
                    "你可以打开本机脚本管理器，自行检查 KernelSU 状态和已保存脚本。" +
                    "\n\n网站链接不会直接执行 Root 脚本，也不会修改 NFC 绑定。"
            }
            textSize = 16f
            setTextColor(0xff43546b.toInt())
            setPadding(0, 20, 0, 24)
        }
        val continueButton = Button(this).apply {
            text = "确认打开管理器"
            isEnabled = scene != null
            setOnClickListener {
                startActivity(Intent(this@BridgeLinkActivity, ManagerActivity::class.java))
                finish()
            }
        }
        val closeButton = Button(this).apply {
            text = "取消"
            setOnClickListener { finish() }
        }
        layout.addView(title)
        layout.addView(message)
        layout.addView(continueButton)
        layout.addView(closeButton)
        setContentView(layout)
    }
}
