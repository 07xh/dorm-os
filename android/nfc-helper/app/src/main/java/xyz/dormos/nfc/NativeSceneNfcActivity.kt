package xyz.dormos.nfc

import android.app.Activity
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.widget.TextView
import java.util.concurrent.atomic.AtomicInteger

/** NFC system dispatch, not a permanent background service or an accessibility service. */
class NativeSceneNfcActivity : Activity() {
    private lateinit var status: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val scanGeneration = AtomicInteger(0)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = TextView(this).apply {
            textSize = 17f
            setTextColor(0xFF17345A.toInt())
            setBackgroundColor(0xFFF6F9FF.toInt())
            gravity = Gravity.CENTER
            setPadding(36, 36, 36, 36)
            text = "正在核实 NFC 标签…"
        }
        setContentView(status)
        handle(intent)
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }
    override fun onDestroy() {
        scanGeneration.incrementAndGet()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
    private fun handle(source: Intent?) {
        val generation = scanGeneration.incrementAndGet()
        handler.removeCallbacksAndMessages(null)
        if (source?.action != NfcAdapter.ACTION_NDEF_DISCOVERED) {
            closeWith("只有实体 NFC 扫描可直接执行本场景")
            return
        }
        val suppliedUri = source.dataString
        val suppliedId = NativeSceneRules.sceneId(suppliedUri)
        val tag = try {
            if (Build.VERSION.SDK_INT >= 33)
                source.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
            else {
                @Suppress("DEPRECATION")
                source.getParcelableExtra(NfcAdapter.EXTRA_TAG) as? Tag
            }
        } catch (_: Exception) { null }
        if (suppliedId == null || tag == null) {
            closeWith("无效的 NFC 场景标签")
            return
        }

        // NFC cold starts can outlast a brief tag tap. Read off the UI thread and
        // retry transient tag I/O failures, but never trust unverified intent extras.
        status.text = "正在读取实体 NFC 标签，请保持贴近手机…"
        Thread {
            val physicalRead = readPhysicalUri(tag)
            runOnUiThread {
                if (generation != scanGeneration.get() || isFinishing || isDestroyed) return@runOnUiThread
                when (NativeNfcDispatchRules.verify(suppliedUri, physicalRead)) {
                    NativeNfcDispatchDecision.VERIFIED -> executeVerifiedScene(suppliedId)
                    NativeNfcDispatchDecision.INVALID_INTENT_URI ->
                        closeWith("无效的 NFC 场景地址，未执行")
                    NativeNfcDispatchDecision.NOT_NDEF ->
                        closeWith("标签不支持 NDEF，请检查实际扫描的标签")
                    NativeNfcDispatchDecision.MISSING_URI_RECORD ->
                        closeWith("没有读到 TapFlow 场景 URI，请从场景页重新写 NFC")
                    NativeNfcDispatchDecision.IO_FAILED ->
                        closeWith("标签接触中断，无法重新读取；请保持贴近后重试")
                    NativeNfcDispatchDecision.URI_MISMATCH ->
                        closeWith("实体标签与请求地址不一致，已拒绝")
                }
            }
        }.start()
    }

    private fun executeVerifiedScene(suppliedId: String) {
        val guard = getSharedPreferences("native_nfc_guard", MODE_PRIVATE)
        val now = SystemClock.elapsedRealtime()
        val previous = guard.getLong("lastAt", -10000L)
        if (guard.getString("lastId", "") == suppliedId && now - previous in 0..1400) {
            closeWith("重复触发已忽略")
            return
        }
        guard.edit().putString("lastId", suppliedId).putLong("lastAt", now).apply()
        val scene = NativeSceneStore(this).find(suppliedId)
        if (scene == null) {
            closeWith("本机未配置场景：" + suppliedId)
            return
        }
        status.text = "正在执行：" + scene.name
        NativeSceneRunner.run(this, scene, rootApproved = false, done = ::closeWith)
    }

    /** The physical tag, not the incoming URI/extras, remains the trust source. */
    private fun readPhysicalUri(tag: Tag): PhysicalUriRead {
        val tech = try { Ndef.get(tag) } catch (_: Exception) { null }
            ?: return PhysicalUriRead.notNdef()
        for (attempt in 0 until 3) {
            try {
                tech.connect()
                val uri = tech.ndefMessage?.records?.asSequence()
                    ?.filter { it.tnf == NdefRecord.TNF_WELL_KNOWN && it.type.contentEquals(NdefRecord.RTD_URI) }
                    ?.mapNotNull { it.toUri()?.toString() }
                    ?.firstOrNull()
                return if (uri == null) PhysicalUriRead.missingUri()
                    else PhysicalUriRead.found(uri)
            } catch (_: Exception) {
                if (attempt == 2) return PhysicalUriRead.ioFailed()
            } finally {
                try { tech.close() } catch (_: Exception) {}
            }
            try {
                Thread.sleep(100)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return PhysicalUriRead.ioFailed()
            }
        }
        return PhysicalUriRead.ioFailed()
    }
    private fun closeWith(message: String) {
        if (!isFinishing) {
            status.text = message
            handler.postDelayed({ if (!isFinishing) finish() }, 1600L)
        }
    }
}
