package xyz.dormos.nfc

import android.app.Activity
import android.content.Intent
import android.nfc.tech.Ndef
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import java.nio.charset.StandardCharsets

/**
 * Minimal one-shot NFC bridge.
 * Script management and tag-to-script bindings live in the DORM NFC Manager app.
 * This exported receiver validates a physical NFC tag and calls the fixed KernelSU
 * dispatcher entry point. Tag writing is exclusively an inline ManagerActivity session.
 * No services or polling.
 */
class MainActivity : Activity() {
    private var adapter: NfcAdapter? = null
    private lateinit var status: TextView
    private val nfcGate = NfcIntentGate()
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adapter = NfcAdapter.getDefaultAdapter(this)
        buildMinimalStatus()
        handleLaunchIntent(intent)
    }

    private fun buildMinimalStatus() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(36, 36, 36, 36)
            setBackgroundColor(0xFFF1F6FD.toInt())
        }
        status = TextView(this).apply {
            val nfcState = if (adapter?.isEnabled == true) "已开启" else "未开启/不可用"
            val launchState = if (Build.VERSION.SDK_INT >= 36 && adapter?.isTagIntentAllowed == false) "系统尚未允许通过 NFC 启动本应用" else "系统 NFC 启动权限未发现明确阻断"
            text = "DORM NFC 接收器\n\nNFC：$nfcState\n$launchState\n\n请打开 DORM NFC Manager 管理脚本与 NFC 标签。\n\n本接收器只负责 NFC 标签触发，不显示脚本编辑页，不运行后台服务。"
            textSize = 16f
            setTextColor(0xFF183252.toInt())
            gravity = Gravity.CENTER
            setLineSpacing(8f, 1f)
        }
        root.addView(status, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        setContentView(root)
    }

    private fun handleLaunchIntent(source: Intent?) {
        if (source?.action == NfcAdapter.ACTION_NDEF_DISCOVERED) {
            handleNfc(source)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchIntent(intent)
    }

    private fun handleNfc(source: Intent) {
        val actualTag = try {
            if (Build.VERSION.SDK_INT >= 33) {
                source.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
            } else {
                @Suppress("DEPRECATION")
                source.getParcelableExtra(NfcAdapter.EXTRA_TAG) as? Tag
            }
        } catch (_: Exception) { null }

        if (actualTag == null) {
            showAndFinish("未收到实体 NFC 标签信息，已忽略。")
            return
        }

        @Suppress("DEPRECATION")
        val id = readPhysicalTagId(actualTag)
        if (id == null) {
            showAndFinish("未知或无效 NFC 标签，未执行任何操作。")
            return
        }
        if (!nfcGate.accept(id, SystemClock.elapsedRealtime())) {
            showAndFinish("重复 NFC 触发已忽略，没有重复执行。")
            return
        }
        runScene(id)
    }

    /**
     * Do not trust EXTRA_NDEF_MESSAGES: another app can explicitly launch this exported
     * NFC activity with forged extras. Read the NDEF payload back from the physical Tag.
     */
    private fun readPhysicalTagId(tag: Tag): String? {
        val ndef = try { Ndef.get(tag) } catch (_: Exception) { null } ?: return null
        return try {
            ndef.connect()
            ndef.ndefMessage?.records
                ?.asSequence()
                ?.filter { it.tnf == NdefRecord.TNF_MIME_MEDIA }
                ?.mapNotNull { record ->
                    NfcProfiles.decodeRecord(
                        String(record.type, StandardCharsets.US_ASCII),
                        record.payload
                    )
                }
                ?.firstOrNull()
        } catch (_: Exception) {
            null
        } finally {
            try { ndef.close() } catch (_: Exception) { }
        }
    }

    private fun runScene(id: String) {
        status.text = "已识别 NFC 场景。\n正在请求 KernelSU 执行固定动作……"
        Thread {
            val result = KsuActionRunner().runScene(id)
            runOnUiThread {
                if (!isFinishing) {
                    status.text = result
                    finishSoon()
                }
            }
        }.start()
    }

    private fun showAndFinish(message: String) {
        status.text = message
        finishSoon()
    }

    private fun finishSoon() {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ if (!isFinishing) finish() }, 2200L)
    }

}
