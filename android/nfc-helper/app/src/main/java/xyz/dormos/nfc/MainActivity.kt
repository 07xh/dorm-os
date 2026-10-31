package xyz.dormos.nfc

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One NFC tap -> one Wi-Fi diagnostic run.
 * No root, shell, boot receiver, foreground service or background polling.
 * Login is intentionally disabled until a real Ncwu success request is verified.
 */
class MainActivity : Activity() {
    private val running = AtomicBoolean(false)
    private var writing = false
    private val writeInFlight = AtomicBoolean(false)
    private lateinit var status: TextView
    private var nfc: NfcAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nfc = NfcAdapter.getDefaultAdapter(this)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 48, 40, 36)
        }
        val heading = TextView(this).apply {
            text = "DORM OS · Ncwu NFC 助手"
            textSize = 22f
        }
        status = TextView(this).apply {
            text = "NFC / Wi-Fi 诊断原型。\n登录接口尚未验证，不会提交密码。"
            textSize = 16f
            setPadding(0, 24, 0, 24)
        }
        layout.addView(heading)
        layout.addView(status)
        fun addButton(title: String, action: () -> Unit) {
            layout.addView(Button(this).apply {
                text = title
                setOnClickListener { action() }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        addButton("检测 Ncwu（仅一次）") { checkOnce() }
        addButton("授权识别 Wi-Fi 名称") {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 7)
            } else {
                show("已授权。请确保系统定位开关开启，再检测。")
            }
        }
        addButton("写入 NFC 专用标签") { confirmWrite() }
        addButton("检查 NFC 自动启动权限") {
            if (Build.VERSION.SDK_INT >= 36 && nfc?.isTagIntentAllowed == false) {
                show("系统禁止通过 NFC 标签启动本应用。请在设置 → 应用 → 特殊应用权限 → 通过 NFC 启动里允许 DORM OS 助手。")
            } else {
                show("未检测到 NFC 启动禁用状态；实际触发仍需真机碰卡验收。")
            }
        }
        addButton("取消 NFC 写入") { if (writing) { stopWriting(); show("已取消等待写入；不代表标签已经更改。") } else show("当前没有正在等待的 NFC 写入。") }
        addButton("打开 Wi-Fi 设置") { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }
        setContentView(layout)
        handleNfc(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNfc(intent)
    }

    override fun onPause() {
        if (writing) stopWriting()
        super.onPause()
    }

    private fun handleNfc(intent: Intent?) {
        if (intent?.action != NfcAdapter.ACTION_NDEF_DISCOVERED) return
        @Suppress("DEPRECATION")
        val messages = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
        val valid = messages?.filterIsInstance<NdefMessage>()?.any { message ->
            message.records.any { record ->
                record.tnf == NdefRecord.TNF_MIME_MEDIA &&
                    NcwuRules.isExpectedTag(String(record.type, StandardCharsets.US_ASCII), String(record.payload, StandardCharsets.UTF_8))
            }
        } == true
        if (valid) checkOnce() else show("未知 NFC 标签：已忽略，不执行操作。")
    }

    private fun checkOnce() {
        if (!running.compareAndSet(false, true)) {
            show("正在检测，重复扫描已忽略。")
            return
        }
        show("正在检查 Ncwu Wi-Fi……")
        Thread {
            val result = NetworkDiagnostics(this).run()
            runOnUiThread {
                if (!isDestroyed) show(result)
                running.set(false)
            }
        }.start()
    }

    private fun confirmWrite() {
        if (nfc == null || nfc?.isEnabled != true) {
            show("请先打开系统 NFC。")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("写入专用 NFC 标签？")
            .setMessage("将覆盖标签原有 NDEF 内容。只写入动作标识，不包含账号密码。请使用你自己的可写标签。")
            .setNegativeButton("取消", null)
            .setPositiveButton("确认覆盖") { _, _ -> startWriting() }
            .show()
    }

    private fun startWriting() {
        if (writing) { show("正在等待写入，请先取消或触碰标签。"); return }
        writing = true
        show("请将可写 NFC 标签贴近手机背面……")
        nfc?.enableReaderMode(
            this,
            { tag -> writeTag(tag) },
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V,
            null
        )
    }

    private fun writeTag(tag: Tag) {
        if (!writeInFlight.compareAndSet(false, true)) return
        val message = NdefMessage(arrayOf(
            NdefRecord.createMime(MIME, PAYLOAD.toByteArray(StandardCharsets.UTF_8))
        ))
        val outcome = try {
            val ndef = Ndef.get(tag)
            if (ndef == null) {
                // Some blank NFC stickers are not yet NDEF formatted.
                val formatable = NdefFormatable.get(tag) ?: error("此标签不支持 NDEF 写入或格式化")
                formatable.connect()
                try { formatable.format(message) } finally { formatable.close() }
                "已格式化并写入 NDEF；请离开应用再次碰卡，验证标签可触发。"
            } else {
                ndef.connect()
                try {
                    check(ndef.isWritable) { "此标签不可写" }
                    check(ndef.maxSize >= message.toByteArray().size) { "标签空间不足" }
                    ndef.writeNdefMessage(message)
                // Read the physical tag again; the NFC write call alone is not an end-to-end proof.
                val readBack = try { ndef.ndefMessage } catch (_: Exception) { null }
                if (readBack != null && readBack.toByteArray().contentEquals(message.toByteArray())) {
                    "标签写入并回读一致。离开 App 后可再次碰卡验证一次性检测。"
                } else {
                    "已收到写入完成结果，但未确认回读内容；请再次碰卡检查，勿当作验证成功。"
                }
                } finally { ndef.close() }
            }
        } catch (e: Exception) {
            "写入失败：" + (e.message ?: e.javaClass.simpleName)
        } finally {
            writeInFlight.set(false)
        }
        runOnUiThread {
            if (!isDestroyed) {
                stopWriting()
                show(outcome)
            }
        }
    }

    private fun stopWriting() {
        writing = false
        nfc?.disableReaderMode(this)
    }

    private fun show(message: String) { status.text = message }

    companion object {
        const val MIME = "application/vnd.dormos.ncwu"
        const val PAYLOAD = "v1:ncwu"
    }
}
