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
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
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
            val theme = getSharedPreferences("dorm_nfc_view", 0).getString("theme", "default") ?: "default"
            val p = v2Palette(theme)
            CompositionLocalProvider(LocalV2Palette provides p) {
            MaterialTheme(colorScheme = if (theme == "night") darkColorScheme(
                primary = p.blue, onPrimary = Color(0xFF102135),
                surface = p.surface, onSurface = p.ink,
                background = p.background, onBackground = p.ink
            ) else lightColorScheme(
                primary = p.blue, onPrimary = Color.White,
                background = p.background, onBackground = p.ink,
                surface = p.surface, onSurface = p.ink
            )) {
                ReadScreen(status, report, reading, nfcAvailable, nfcEnabled,
                    onBack = { finish() },
                    onNfcSettings = {
                        try { startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }
                        catch (_: Exception) {
                            try { startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) }
                            catch (_: Exception) { status = "系统没有可用的 NFC 设置入口，请手动开启 NFC" }
                        }
                    },
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
            val message = ndef.ndefMessage
            val usedBytes = message?.toByteArray()?.size
            val allRecords = message?.records ?: emptyArray()
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
                    record.tnf == NdefRecord.TNF_MIME_MEDIA -> {
                        val mime = String(record.type, StandardCharsets.US_ASCII)
                        if (NfcProfiles.isSupportedMime(mime)) "DORM 自动化标签"
                        else mime.take(90)
                    }
                    else -> "记录类型 " + record.tnf.toString()
                }
                ReadRecord(index + 1, type, preview, record.payload.size)
            }
            ReadReport(
                technologies, ndef.type ?: "NDEF", ndef.isWritable,
                ndef.maxSize, items, null, allRecords.size, usedBytes
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
        return try {
            charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(payload, offset, payload.size - offset))
                .toString()
        } catch (_: Exception) {
            null
        }
    }
}

internal data class ReadRecord(
    val number: Int, val type: String, val preview: String?, val payloadBytes: Int
)

internal data class ReadReport(
    val technologies: String, val ndefType: String, val writable: Boolean?,
    val capacity: Int?, val records: List<ReadRecord>, val error: String?,
    val totalRecords: Int = records.size,
    val usedBytes: Int? = null
)

@Composable
private fun ReadScreen(
    status: String, report: ReadReport?, reading: Boolean,
    nfcAvailable: Boolean, nfcEnabled: Boolean,
    onBack: () -> Unit, onNfcSettings: () -> Unit, onReset: () -> Unit
) {
    V2BottomSheet(
        title = "NFC 标签",
        subtitle = "实体标签只读扫描 · 不写入、不执行脚本",
        onDismiss = onBack
    ) {
        Surface(color = Color(0xFFF3F6FC), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(17.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(88.dp).background(Color(0xFFE8EFFF), RoundedCornerShape(26.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    V2NfcMark(Modifier.size(48.dp), Color(0xFF2F65E8))
                }
                Spacer(Modifier.height(14.dp))
                Text(status, color = Color(0xFF172234), fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    when {
                        reading -> "正在读取实体标签，请保持手机与标签贴合"
                        report == null -> "将标签靠近手机背部 NFC 感应区域"
                        report.error == null -> "已从实体标签读取信息"
                        else -> "已识别标签 · 查看下方失败原因"
                    },
                    color = Color(0xFF7A8798), fontSize = 12.sp
                )
                if (reading) {
                    Spacer(Modifier.height(14.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
        if (report == null) {
            Spacer(Modifier.height(13.dp))
            Text(
                "支持查看普通 NDEF 文本、网址、记录数量、容量和技术类型。不会尝试解密门禁或校园卡数据。",
                color = Color(0xFF7A8798), fontSize = 12.sp, lineHeight = 18.sp
            )
        } else {
            Spacer(Modifier.height(13.dp))
            Surface(color = Color.White, shape = RoundedCornerShape(17.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE8EDF4))) {
                Column(Modifier.fillMaxWidth().padding(15.dp)) {
                    Text("标签详情", color = Color(0xFF172234), fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    ReadDetailLine("技术类型", report.technologies.ifBlank { "未知" })
                    ReadDetailLine("NDEF 格式", report.ndefType)
                    ReadDetailLine("可写入", when (report.writable) {
                        true -> "是"; false -> "否"; null -> "未知"
                    })
                    ReadDetailLine("最大容量", report.capacity?.let { "${it} B" } ?: "未知")
                    report.usedBytes?.let { ReadDetailLine("已用空间", "${it} B") }
                    ReadDetailLine("记录数量", report.totalRecords.toString())
                    report.error?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = Color(0xFF9B5F38), fontSize = 12.sp)
                    }
                }
            }
            if (report.records.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Surface(color = Color.White, shape = RoundedCornerShape(17.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE8EDF4))) {
                    Column(Modifier.fillMaxWidth().padding(15.dp)) {
                        Text("NDEF 内容", color = Color(0xFF172234), fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(10.dp))
                        report.records.forEach { record ->
                            Text("记录 ${record.number} · ${record.type}",
                                color = Color(0xFF2F65E8), fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                record.preview ?: "未显示原始二进制 · ${record.payloadBytes} 字节",
                                color = Color(0xFF65748A), fontSize = 12.sp, lineHeight = 18.sp
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        if (nfcAvailable && !nfcEnabled) {
            Button(onClick = onNfcSettings, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)) { Text("打开 NFC 设置") }
        } else if (nfcEnabled) {
            Button(onClick = onReset, enabled = !reading, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)) {
                Text(if (report == null) "重新开始扫描" else "扫描另一张标签")
            }
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)) { Text("完成") }
    }
}

@Composable
private fun ReadDetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, color = Color(0xFF7A8798), fontSize = 12.sp,
            modifier = Modifier.width(88.dp))
        Text(value, color = Color(0xFF172234), fontSize = 12.sp,
            modifier = Modifier.weight(1f))
    }
}
