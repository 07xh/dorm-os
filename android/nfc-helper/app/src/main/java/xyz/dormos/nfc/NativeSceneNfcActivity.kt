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

/** NFC system dispatch, not a permanent background service or an accessibility service. */
class NativeSceneNfcActivity : Activity() {
    private lateinit var status: TextView
    private val handler = Handler(Looper.getMainLooper())
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
    private fun handle(source: Intent?) {
        if (source?.action != NfcAdapter.ACTION_NDEF_DISCOVERED) {
            closeWith("只有实体 NFC 扫描可直接执行本场景")
            return
        }
        val suppliedId = NativeSceneRules.sceneId(source.dataString)
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
        // Never trust data/extras alone; re-read the NDEF URI from the physical tag.
        val recordUri = readPhysicalUri(tag)
        if (NativeSceneRules.sceneId(recordUri) != suppliedId || recordUri != source.dataString) {
            closeWith("实体标签与请求地址不一致，已拒绝")
            return
        }
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
    private fun readPhysicalUri(tag: Tag): String? {
        val tech = try { Ndef.get(tag) } catch (_: Exception) { null } ?: return null
        return try {
            tech.connect()
            tech.ndefMessage?.records?.asSequence()
                ?.filter { it.tnf == NdefRecord.TNF_WELL_KNOWN && it.type.contentEquals(NdefRecord.RTD_URI) }
                ?.mapNotNull { it.toUri()?.toString() }
                ?.firstOrNull()
        } catch (_: Exception) { null }
        finally { try { tech.close() } catch (_: Exception) {} }
    }
    private fun closeWith(message: String) {
        if (!isFinishing) {
            status.text = message
            handler.postDelayed({ if (!isFinishing) finish() }, 1600L)
        }
    }
}
