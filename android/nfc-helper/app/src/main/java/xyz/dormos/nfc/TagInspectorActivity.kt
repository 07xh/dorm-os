/*
 * NDEF text payload decoder adapted from the AOSP/NFCReader algorithm.
 * Copyright (C) 2010 The Android Open Source Project
 * Licensed under the Apache License, Version 2.0.
 * See assets/licenses/NFCReader-Apache-2.0.txt for license terms.
 * Modifications: defensive length and reserved-status validation, separate UI and reader lifecycle.
 */
package xyz.dormos.nfc

import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.NdefRecord
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Bundle
import android.content.Intent
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Explicit, foreground-only read session. Never writes tags or invokes KernelSU.
 */
class TagInspectorActivity : ComponentActivity(), NfcAdapter.ReaderCallback {
    private val busy = AtomicBoolean(false)
    private val sessionGeneration = AtomicInteger(0)
    @Volatile private var inForeground = false
    @Volatile private var finishedScan = false
    private var status by mutableStateOf("将 NFC 标签靠近手机背部")
    private var report by mutableStateOf<ReadReport?>(null)
    private var reading by mutableStateOf(false)
    private var nfcAvailable by mutableStateOf(false)
    private var nfcEnabled by mutableStateOf(false)
    private var adapter: NfcAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adapter = NfcAdapter.getDefaultAdapter(this)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(
                primary = Color(0xFF4769B5), background = Color(0xFFF3F6FC),
                surface = Color.White, onSurface = Color(0xFF26334C)
            )) {
                ReadScreen(status, report, reading, nfcAvailable, nfcEnabled,
                    onBack = { finish() },
                    onNfcSettings = { startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) },
                    onReset = {
                    sessionGeneration.incrementAndGet()
                    finishedScan = false
                    reading = false
                    report = null
                    status = "准备重新读取 · 请先移开标签，再贴近"
                })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        inForeground = true
        val nfc = adapter
        nfcAvailable = nfc != null
        nfcEnabled = nfc?.isEnabled == true
        if (nfc == null) {
            status = "此设备没有可用的 NFC 硬件"
            return
        }
        if (!nfc.isEnabled) {
            status = "NFC 尚未开启，请先在系统设置中开启"
            return
        }
        try {
            nfc.enableReaderMode(
                this, this,
                NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V,
                null
            )
        } catch (_: Exception) {
            status = "读卡模式开启失败，请检查系统 NFC 设置"
        }
    }

    override fun onPause() {
        inForeground = false
        sessionGeneration.incrementAndGet()
        reading = false
        try { adapter?.disableReaderMode(this) } catch (_: Exception) {}
        super.onPause()
    }

    override fun onTagDiscovered(tag: Tag) {
        if (!inForeground || finishedScan || !busy.compareAndSet(false, true)) return
        val session = sessionGeneration.get()
        runOnUiThread {
            if (inForeground && sessionGeneration.get() == session) {
                reading = true
                status = "已感应标签，正在读取 NDEF 内容"
            }
        }
        val scanned = try { inspect(tag) } catch (e: Exception) {
            ReadReport(
                tag.techList.map { it.substringAfterLast('.') }.joinToString(" / "),
                "读取失败", null, null, emptyList(),
                NfcFailureFeedback.readFailure(e)
            )
        }
        runOnUiThread {
            if (inForeground && sessionGeneration.get() == session && !isFinishing && !isDestroyed) {
                reading = false
                report = scanned
                status = if (scanned.error == null) "读取完成" else "已识别标签 · 查看提示"
                finishedScan = true
            }
            busy.set(false)
        }
    }

    private fun inspect(tag: Tag): ReadReport {
        val technologies = tag.techList.map { it.substringAfterLast('.') }
            .take(12).joinToString(" / ")
        val ndef = Ndef.get(tag) ?: return ReadReport(
            technologies, "无 NDEF", null, null, emptyList(),
            NfcFailureFeedback.noNdef(NdefFormatable.get(tag) != null)
        )
        ndef.connect()
        return try {
            val allRecords = ndef.ndefMessage?.records ?: emptyArray()
            val items = allRecords.take(24).mapIndexed { index, record ->
                val uri = try { record.toUri()?.toString() } catch (_: Exception) { null }
                val isText = record.tnf == NdefRecord.TNF_WELL_KNOWN &&
                    record.type.contentEquals(NdefRecord.RTD_TEXT)
                val preview = when {
                    isText -> NdefTextPayload.decode(record.payload)
                    uri != null -> uri
                    else -> null
                }?.take(400)
                val type = when {
                    isText -> "文本"
                    uri != null -> "链接 / URI"
                    record.tnf == NdefRecord.TNF_MIME_MEDIA ->
                        String(record.type, StandardCharsets.US_ASCII).take(90)
                    else -> "记录类型 " + record.tnf.toString()
                }
                ReadRecord(index + 1, type, preview, record.payload.size)
            }
            ReadReport(
                technologies, ndef.type ?: "NDEF", ndef.isWritable,
                ndef.maxSize, items, null, allRecords.size
            )
        } finally {
            try { ndef.close() } catch (_: Exception) {}
        }
    }
}

internal object NdefTextPayload {
    // NFC Forum Text RTD; adapted from AOSP/NFCReader's Apache-2.0 algorithm.
    fun decode(payload: ByteArray): String? {
        if (payload.isEmpty()) return null
        val status = payload[0].toInt() and 0xff
        if ((status and 0x40) != 0) return null
        val offset = 1 + (status and 0x3f)
        if (offset > payload.size) return null
        val charset = if ((status and 0x80) == 0) StandardCharsets.UTF_8 else StandardCharsets.UTF_16
        return try { String(payload, offset, payload.size - offset, charset) }
        catch (_: Exception) { null }
    }
}

internal data class ReadRecord(
    val number: Int, val type: String, val preview: String?, val payloadBytes: Int
)

internal data class ReadReport(
    val technologies: String, val ndefType: String, val writable: Boolean?,
    val capacity: Int?, val records: List<ReadRecord>, val error: String?,
    val totalRecords: Int = records.size
)

@Composable
private fun ReadScreen(
    status: String, report: ReadReport?, reading: Boolean,
    nfcAvailable: Boolean, nfcEnabled: Boolean,
    onBack: () -> Unit, onNfcSettings: () -> Unit, onReset: () -> Unit
) {
    val blue = Color(0xFF4769B5)
    val dim = Color(0xFF6E7B91)
    Column(
        Modifier.fillMaxSize().background(Color(0xFFF3F6FC))
            .verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)
    ) {
        Spacer(Modifier.height(22.dp))
        NfcPageHeader("读取标签", "扫描信息 · 安全只读 · 不执行脚本", onBack)
        Spacer(Modifier.height(16.dp))
        NfcSessionPanel(
            mood = when {
                reading -> NfcPanelMood.ACTIVE
                report == null -> NfcPanelMood.IDLE
                report.error == null -> NfcPanelMood.VERIFIED
                else -> NfcPanelMood.NEEDS_ATTENTION
            },
            title = status,
            subtitle = if (report == null) "将标签贴近手机背部 NFC 感应区域"
                else if (report.error == null) "已从实体标签读取信息" else "查看下方原因及建议",
            working = reading
        ) {
            if (nfcAvailable && !nfcEnabled) {
                Button(onClick = onNfcSettings, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)) { Text("打开 NFC 设置") }
            } else if (nfcEnabled) {
                Button(onClick = onReset, enabled = !reading, shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()) {
                    Text(if (report == null) "重新开始扫描" else "扫描另一张标签")
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        if (report == null) {
            InfoSurface("使用提示") {
                Text(
                    "适合检查普通 NDEF 标签、链接与文本。受保护的校园卡或门禁数据不会被解密或改写。",
                    color = dim, fontSize = 14.sp
                )
            }
        } else {
            InfoSurface("标签详情") {
                DetailLine("技术类型", report.technologies.ifBlank { "未知" })
                DetailLine("NDEF 格式", report.ndefType)
                DetailLine("可写入", when (report.writable) {
                    true -> "是"; false -> "否"; null -> "未知"
                })
                DetailLine("最大容量", report.capacity?.let { it.toString() + " B" } ?: "未知")
                DetailLine("记录数量", report.totalRecords.toString())
                if (report.totalRecords > report.records.size) {
                    Text("只展示前 " + report.records.size + " 条，避免页面过长。",
                        color = dim, fontSize = 12.sp)
                }
                report.error?.let {
                    HorizontalDivider(Modifier.padding(vertical = 10.dp))
                    Text(it, color = Color(0xFF9B5F38), fontSize = 13.sp)
                }
            }
            if (report.records.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                InfoSurface("NDEF 内容") {
                    report.records.forEach { record ->
                        Text("记录 " + record.number.toString() + " · " + record.type,
                            color = blue, fontWeight = FontWeight.Medium)
                        Text(
                            record.preview ?: "未显示原始二进制 · " + record.payloadBytes.toString() + " 字节",
                            color = dim, fontSize = 13.sp
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun InfoSurface(title: String, body: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = Color.White) {
        Column(Modifier.fillMaxWidth().padding(19.dp)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            body()
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, color = Color(0xFF6E7B91), fontSize = 13.sp,
            modifier = Modifier.width(88.dp))
        Text(value, color = Color(0xFF26334C), fontSize = 13.sp,
            modifier = Modifier.weight(1f))
    }
}