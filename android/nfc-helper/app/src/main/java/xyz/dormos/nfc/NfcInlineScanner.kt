package xyz.dormos.nfc

import android.nfc.NfcAdapter
import android.nfc.Tag
import androidx.activity.ComponentActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Foreground-only scanner attached to ManagerActivity. It doesn't navigate
 * to another Activity, write to the tag or invoke KernelSU.
 * ReaderMode is enabled only when the inline scan sheet is active.
 */
internal class NfcInlineScanner(private val activity: ComponentActivity) : NfcAdapter.ReaderCallback {
    private val adapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)
    private val busy = AtomicBoolean(false)
    private val generation = AtomicInteger(0)
    @Volatile private var active = false
    @Volatile private var registered = false
    @Volatile private var completed = false

    var report by mutableStateOf<ReadReport?>(null)
        private set
    var scanning by mutableStateOf(false)
        private set
    var status by mutableStateOf("将 NFC 标签靠近手机背部")
        private set

    val available get() = adapter != null
    val enabled get() = adapter?.isEnabled == true

    fun start() {
        active = true
        generation.incrementAndGet()
        completed = false
        report = null
        scanning = false
        status = "将 NFC 标签靠近手机背部"
        registerIfResumed()
    }

    fun reset() {
        if (!active || busy.get()) return
        generation.incrementAndGet()
        completed = false
        report = null
        scanning = false
        status = "准备重新扫描 · 请移开标签后再贴近"
        registerIfResumed()
    }

    fun stop() {
        active = false
        generation.incrementAndGet()
        completed = false
        scanning = false
        disableReader()
    }

    fun onResume() {
        if (active) registerIfResumed()
    }

    fun onPause() {
        generation.incrementAndGet()
        scanning = false
        disableReader()
    }

    private fun disableReader() {
        if (!registered) return
        registered = false
        try { adapter?.disableReaderMode(activity) } catch (_: Exception) {}
    }

    private fun registerIfResumed() {
        if (!active || !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        if (adapter == null) {
            status = "此设备不支持 NFC"
            return
        }
        if (!adapter.isEnabled) {
            status = "NFC 未开启，请先到系统设置打开"
            return
        }
        if (registered) return
        try {
            adapter.enableReaderMode(
                activity, this,
                NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V,
                null
            )
            registered = true
        } catch (_: Exception) {
            status = "无法开启前台 NFC 扫描，请检查权限和系统开关"
        }
    }

    override fun onTagDiscovered(tag: Tag) {
        if (!active || completed || !busy.compareAndSet(false, true)) return
        val mySession = generation.get()
        activity.runOnUiThread {
            if (active && generation.get() == mySession) {
                scanning = true
                status = "已发现标签 · 正在读取内容"
            }
        }
        val value = try {
            NfcTagReader.inspect(tag)
        } catch (error: Exception) {
            ReadReport(
                technologies = tag.techList.map { it.substringAfterLast('.') }
                    .take(12).joinToString(" / "),
                ndefType = "读取失败",
                writable = null,
                capacity = null,
                records = emptyList(),
                error = NfcFailureFeedback.readFailure(error)
            )
        }
        activity.runOnUiThread {
            if (active && generation.get() == mySession &&
                !activity.isFinishing && !activity.isDestroyed) {
                report = value
                scanning = false
                completed = true
                status = if (value.error == null) "读取完成" else "标签已识别，请查看提示"
            }
            busy.set(false)
        }
    }
}

@Composable
internal fun NfcInlineScanSheet(
    scanner: NfcInlineScanner,
    onDismiss: () -> Unit,
    onNfcSettings: () -> Unit
) {
    val p = LocalV2Palette.current
    val report = scanner.report
    V2BottomSheet(
        title = "NFC 标签",
        subtitle = "当前页面扫描 · 只读，不写卡或运行脚本",
        onDismiss = onDismiss
    ) {
        Surface(
            color = p.accentLow,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, p.line)
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier.size(112.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(56.dp),
                        border = BorderStroke(2.dp, p.blue.copy(alpha = 0.20f)),
                        color = Color.Transparent,
                        modifier = Modifier.size(112.dp)
                    ) {}
                    Surface(
                        shape = RoundedCornerShape(44.dp),
                        border = BorderStroke(3.dp, p.blue.copy(alpha = 0.18f)),
                        color = p.iconBackground,
                        modifier = Modifier.size(86.dp)
                    ) {}
                    V2NfcMark(Modifier.size(38.dp), p.blue)
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    scanner.status,
                    color = p.ink, fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    when {
                        scanner.scanning -> "正在接收真实 NFC 数据"
                        report == null -> "请贴近手机背部 NFC 感应区域"
                        report.error == null -> "读取到以下实际标签信息"
                        else -> "这张标签的部分内容无法读取"
                    },
                    color = p.muted, fontSize = 12.sp
                )
                if (scanner.scanning) {
                    Spacer(Modifier.height(14.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = p.blue
                    )
                }
            }
        }
        if (report != null) {
            Spacer(Modifier.height(12.dp))
            Surface(
                color = p.surface,
                border = BorderStroke(1.dp, p.line),
                shape = RoundedCornerShape(17.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(15.dp)) {
                    Text("标签详情", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = p.ink)
                    Spacer(Modifier.height(8.dp))
                    NfcScanDetailLine("技术类型", report.technologies)
                    NfcScanDetailLine("NDEF 类型", report.ndefType)
                    NfcScanDetailLine("记录数量", report.totalRecords.toString())
                    NfcScanDetailLine("容量", report.capacity?.let { "$it B" } ?: "未知")
                    NfcScanDetailLine(
                        "可写入",
                        when (report.writable) { true -> "是"; false -> "否"; else -> "未知" }
                    )
                    if (report.error != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(report.error, color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp, lineHeight = 18.sp)
                    }
                    report.records.take(10).forEach { record ->
                        Spacer(Modifier.height(9.dp))
                        HorizontalDivider(color = p.line)
                        Spacer(Modifier.height(8.dp))
                        Text("记录 ${record.number} · ${record.type}",
                            color = p.blue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            record.preview ?: "二进制数据 · ${record.payloadBytes} B",
                            color = p.muted, fontSize = 12.sp, lineHeight = 18.sp
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        if (!scanner.enabled && scanner.available) {
            Button(
                onClick = onNfcSettings,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Text("打开系统 NFC 设置") }
        } else if (scanner.enabled && report != null) {
            Button(
                onClick = { scanner.reset() },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                enabled = !scanner.scanning,
                shape = RoundedCornerShape(14.dp)
            ) { Text("扫描另一张标签") }
        } else if (scanner.enabled && !scanner.scanning) {
            Text(
                "扫描已就绪 · 贴卡后将直接在这里显示结果",
                color = p.muted, fontSize = 12.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp)
        ) { Text("完成") }
    }
}

@Composable
private fun NfcScanDetailLine(title: String, value: String) {
    val p = LocalV2Palette.current
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(title, modifier = Modifier.width(84.dp), color = p.muted, fontSize = 12.sp)
        Text(value, modifier = Modifier.weight(1f), color = p.ink,
            fontSize = 12.sp, lineHeight = 17.sp)
    }
}
