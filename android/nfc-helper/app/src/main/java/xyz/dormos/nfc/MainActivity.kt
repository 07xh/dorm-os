package xyz.dormos.nfc

import android.app.Activity
import android.content.Intent
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.nfc.NdefMessage
import android.nfc.NdefRecord
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
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Minimal one-shot NFC bridge.
 * Scene management, action mapping, configuration and logs live in the KernelSU WebUI.
 * This Activity only receives a tag, writes an opaque scene ID when explicitly requested,
 * and calls one fixed KernelSU dispatcher entry point. No services, receivers or polling.
 */
class MainActivity : Activity(), NfcAdapter.ReaderCallback {
    private var adapter: NfcAdapter? = null
    private var writeSceneId: String? = null
    private lateinit var status: TextView
    private val writeInFlight = AtomicBoolean(false)
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
            text = "DORM NFC 接收器\n\nNFC：$nfcState\n$launchState\n\n请打开 DORM NFC Manager 管理脚本与 NFC 标签。\n\n本应用只负责接收 NFC 标签和按需写卡，不运行后台服务。"
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
        if (source?.action == ACTION_WRITE_TAG) {
            val id = source.getStringExtra(EXTRA_SCENE_ID)
            if (!NfcProfiles.validId(id)) {
                showAndFinish("写卡请求无效。请返回 KernelSU WebUI 重新发起。")
                return
            }
            writeSceneId = id
            status.text = "DORM OS NFC 写卡\n\n请将你自己的可写 NFC 标签靠近手机背部。\n\n只写入场景编号，不写入账号、密码或 Shell 命令。"
            if (hasWindowFocus()) enableWriterReaderMode()
            return
        }
        if (source?.action == NfcAdapter.ACTION_NDEF_DISCOVERED) {
            handleNfc(source)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (writeSceneId != null) {
            val nfc = adapter
            if (nfc == null || !nfc.isEnabled) {
                showAndFinish("设备没有可用 NFC，或系统 NFC 尚未开启。")
                return
            }
            enableWriterReaderMode()
        }
    }

    private fun enableWriterReaderMode() {
        if (writeSceneId == null) return
        val nfc = adapter
        if (nfc == null || !nfc.isEnabled) {
            showAndFinish("设备没有可用 NFC，或系统 NFC 尚未开启。")
            return
        }
        try {
            nfc.enableReaderMode(
                this,
                this,
                NfcAdapter.FLAG_READER_NFC_A or
                    NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_NFC_F or
                    NfcAdapter.FLAG_READER_NFC_V or
                    NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
                null
            )
        } catch (_: Exception) {
            showAndFinish("无法启动本次 NFC 写卡会话。请检查系统 NFC 设置。")
        }
    }

    override fun onPause() {
        try { adapter?.disableReaderMode(this) } catch (_: Exception) { }
        super.onPause()
    }

    override fun onTagDiscovered(tag: Tag) {
        val id = writeSceneId ?: return
        if (!writeInFlight.compareAndSet(false, true)) return
        val result = try {
            writeSceneTag(tag, id)
        } catch (error: Exception) {
            "写卡失败：" + (error.message ?: error.javaClass.simpleName)
        }
        runOnUiThread {
            writeSceneId = null
            try { adapter?.disableReaderMode(this) } catch (_: Exception) { }
            status.text = result
            finishSoon()
        }
    }

    private fun writeSceneTag(tag: Tag, id: String): String {
        val payload = NfcProfiles.payload(id)
        val record = NdefRecord.createMime(
            NfcProfiles.MIME,
            payload.toByteArray(StandardCharsets.UTF_8)
        )
        val message = NdefMessage(arrayOf(record))
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            ndef.connect()
            try {
                if (!ndef.isWritable) return "标签只读，未写入。"
                if (ndef.maxSize < message.toByteArray().size) return "标签容量不足，未写入。"
                ndef.writeNdefMessage(message)
                val readBack = ndef.ndefMessage
                    ?.records
                    ?.firstOrNull { it.tnf == NdefRecord.TNF_MIME_MEDIA }
                    ?.payload
                    ?.let { String(it, StandardCharsets.UTF_8) }
                if (readBack != payload) return "写入后回读不一致，请不要使用此标签。"
                return "NFC 标签写入并回读成功。\n场景 ID：" + id
            } finally {
                try { ndef.close() } catch (_: Exception) { }
            }
        }

        val formatable = NdefFormatable.get(tag)
            ?: return "此标签不支持 NDEF 写入，未修改标签。"
        formatable.connect()
        try {
            formatable.format(message)
            return "未格式化标签已格式化并写入场景编号。\n请再次碰卡验证。"
        } finally {
            try { formatable.close() } catch (_: Exception) { }
        }
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
        val messages = source.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
        val payload = messages
            ?.filterIsInstance<NdefMessage>()
            ?.asSequence()
            ?.flatMap { it.records.asSequence() }
            ?.firstOrNull { record ->
                record.tnf == NdefRecord.TNF_MIME_MEDIA &&
                    String(record.type, StandardCharsets.US_ASCII) == NfcProfiles.MIME &&
                    record.payload.size in 1..80
            }
            ?.let { String(it.payload, StandardCharsets.UTF_8) }

        if (payload == NfcProfiles.LEGACY_NCWU) {
            runLegacyNcwu()
            return
        }

        val id = NfcProfiles.decodeId(payload)
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

    private fun runLegacyNcwu() {
        status.text = "识别到旧版 Ncwu 标签。\n正在执行只读网络识别……"
        Thread {
            val result = KsuActionRunner().runLegacyNcwu()
            runOnUiThread {
                if (!isFinishing) {
                    status.text = result
                    finishSoon()
                }
            }
        }.start()
    }

    private fun showAndFinish(message: String) {
        writeSceneId = null
        status.text = message
        finishSoon()
    }

    private fun finishSoon() {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ if (!isFinishing) finish() }, 2200L)
    }

    companion object {
        const val ACTION_WRITE_TAG = "xyz.dormos.nfc.action.WRITE_TAG"
        const val EXTRA_SCENE_ID = "scene_id"
    }
}
