package xyz.dormos.nfc

import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.NdefRecord
import android.nfc.tech.Ndef
import android.os.Bundle
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

/**
 * Explicit, foreground-only read session. Never writes tags or invokes KernelSU.
 */
class TagInspectorActivity : ComponentActivity(), NfcAdapter.ReaderCallback {
    private val busy = AtomicBoolean(false)
    @Volatile private var finishedScan = false
    private var status by mutableStateOf("将 NFC 标签靠近手机背部")
    private var report by mutableStateOf<ReadReport?>(null)
    private var adapter: NfcAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adapter = NfcAdapter.getDefaultAdapter(this)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(
                primary = Color(0xFF4769B5), background = Color(0xFFF3F6FC),
                surface = Color.White, onSurface = Color(0xFF26334C)
            )) {
                ReadScreen(status, report, onBack = { finish() }, onReset = {
                    finishedScan = false
                    report = null
                    status = "请移开标签后再次贴近"
                })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val nfc = adapter
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
        try { adapter?.disableReaderMode(this) } catch (_: Exception) {}
        super.onPause()
    }

    override fun onTagDiscovered(tag: Tag) {
        if (finishedScan || !busy.compareAndSet(false, true)) return
        val scanned = try { inspect(tag) } catch (e: Exception) {
            ReadReport(
                tag.techList.map { it.substringAfterLast('.') }.joinToString(" / "),
                "读取失败", null, null, emptyList(),
                e.message?.take(120) ?: "无法读取标签"
            )
        }
        runOnUiThread {
            if (!isFinishing && !isDestroyed) {
                report = scanned
                status = if (scanned.error == null) "读取成功 · 仅展示信息" else "已识别标签，部分信息不可读"
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
            "已检测到标签，但不提供标准 NDEF 消息；不会读取受保护扇区。"
        )
        ndef.connect()
        return try {
            val items = ndef.ndefMessage?.records?.take(24)?.mapIndexed { index, record ->
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
            } ?: emptyList()
            ReadReport(
                technologies, ndef.type ?: "NDEF", ndef.isWritable,
                ndef.maxSize, items, null
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
    val capacity: Int?, val records: List<ReadRecord>, val error: String?
)

@Composable
private fun ReadScreen(
    status: String, report: ReadReport?, onBack: () -> Unit, onReset: () -> Unit
) {
    val blue = Color(0xFF4769B5)
    val dim = Color(0xFF6E7B91)
    Column(
        Modifier.fillMaxSize().background(Color(0xFFF3F6FC))
            .verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)
    ) {
        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ 返回") }
            Spacer(Modifier.width(6.dp))
            Text("NFC 读取", fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(16.dp))
        Surface(shape = RoundedCornerShape(26.dp), color = Color(0xFFDFE9FC)) {
            Column(
                Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("◎", fontSize = 58.sp, color = blue)
                Spacer(Modifier.height(8.dp))
                Text(status, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                Text("只读 · 不写入 · 不执行脚本", color = dim, fontSize = 12.sp)
                Spacer(Modifier.height(18.dp))
                Button(onClick = onReset, shape = RoundedCornerShape(14.dp)) {
                    Text("重新扫描")
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
                DetailLine("记录数量", report.records.size.toString())
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