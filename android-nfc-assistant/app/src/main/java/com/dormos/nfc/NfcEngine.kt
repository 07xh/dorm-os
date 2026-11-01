package com.dormos.nfc

import android.app.Activity
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.net.Uri
import android.os.Build

sealed class ScanEvent {
    data class Found(val id: String): ScanEvent()
    data class Message(val text: String, val success: Boolean): ScanEvent()
}

/** Explicit foreground reader for read/write; Android NFC-dispatch for background/opened scans. */
class NfcEngine(private val activity: Activity, private val emit: (ScanEvent) -> Unit) : NfcAdapter.ReaderCallback {
    private val adapter = NfcAdapter.getDefaultAdapter(activity)
    @Volatile private var pendingWrite: String? = null
    private var reading = false
    private var lastId: String? = null
    private var lastAt = 0L
    fun available(): Boolean = adapter != null && adapter.isEnabled
    fun startRead() { pendingWrite = null; reading = true; enable() }
    fun startWrite(sceneId: String) {
        pendingWrite = sceneId; reading = true; enable()
    }
    fun stop() {
        pendingWrite = null; reading = false
        try { adapter?.disableReaderMode(activity) } catch (_: Exception) {}
    }
    fun onResume() { if (reading) enable() }
    fun onPause() { if (reading) try { adapter?.disableReaderMode(activity) } catch (_: Exception) {} }
    private fun enable() {
        if (!available()) { emit(ScanEvent.Message("NFC 不可用，请检查系统开关", false)); return }
        try {
            adapter?.enableReaderMode(activity, this,
                NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V,
                null)
        } catch (ex: Exception) { emit(ScanEvent.Message("读卡器启动失败：${ex.message}", false)) }
    }
    override fun onTagDiscovered(tag: Tag) {
        val write = pendingWrite
        if (write != null) {
            try {
                val message = NdefMessage(arrayOf(NdefRecord.createUri(Uri.parse("dormos://scene/$write"))))
                val tech = Ndef.get(tag)
                if (tech != null) {
                    tech.connect()
                    try {
                        check(tech.isWritable) { "标签为只读" }
                        check(tech.maxSize >= message.toByteArray().size) { "标签空间不足" }
                        tech.writeNdefMessage(message)
                    } finally { tech.close() }
                } else {
                    val form = NdefFormatable.get(tag) ?: error("标签不支持 NDEF")
                    form.connect()
                    try { form.format(message) } finally { form.close() }
                }
                pendingWrite = null
                emit(ScanEvent.Message("标签写入成功。建议退出写入模式后重新扫描验证。", true))
                activity.runOnUiThread { stop() }
            } catch (ex: Exception) { emit(ScanEvent.Message("写入失败：${ex.message}", false)) }
        } else {
            try {
                val tech = Ndef.get(tag) ?: error("这不是 NDEF 场景标签")
                tech.connect()
                val message = try { tech.ndefMessage } finally { tech.close() }
                val id = message?.records?.firstNotNullOfOrNull { it.toUri()?.let(::parseSceneUri) }
                if (id == null) emit(ScanEvent.Message("未发现 DORM OS 场景记录", false)) else report(id)
            } catch (ex: Exception) { emit(ScanEvent.Message("识别失败：${ex.message}", false)) }
        }
    }
    private fun report(sceneId: String) {
        val now = System.currentTimeMillis()
        if (sceneId == lastId && now - lastAt < 3000) return
        lastId = sceneId; lastAt = now
        emit(ScanEvent.Found(sceneId))
    }
    fun dispatch(intent: Intent?) {
        if (intent?.action != NfcAdapter.ACTION_NDEF_DISCOVERED) return
        @Suppress("DEPRECATION")
        val tag = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
                  else intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        if (tag == null) return  // Ignore arbitrary ACTION_VIEW/deep links without actual NFC tag.
        intent.data?.let(::parseSceneUri)?.let(::report)
    }
    private fun parseSceneUri(uri: Uri): String? {
        if (uri.scheme != "dormos" || uri.host != "scene") return null
        val parts = uri.pathSegments
        if (parts.size != 1) return null
        return parts[0].takeIf { it.matches(Regex("[a-z0-9-]{1,48}")) }
    }
}
