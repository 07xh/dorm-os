package xyz.dormos.nfc

import android.net.Uri
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
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
 * Foreground-only generic writer. This activity has no Root/Shell references.
 * Unlike the script binding writer it refuses to overwrite a DORM automation tag.
 */
class NdefWriterActivity : ComponentActivity(), NfcAdapter.ReaderCallback {
    private data class WriteRequest(
        val draft: PreparedNdefWrite,
        val overwriteConfirmed: Boolean
    )
    private val armedFlag = AtomicBoolean(false)
    @Volatile private var pending: WriteRequest? = null
    private var adapter: NfcAdapter? = null
    private var ready by mutableStateOf(false)
    private var status by mutableStateOf("选择内容并确认，才会开始写卡")
    private var result by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adapter = NfcAdapter.getDefaultAdapter(this)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(
                primary = Color(0xFF4769B5),
                background = Color(0xFFF3F6FC),
                surface = Color.White,
                onSurface = Color(0xFF26334C)
            )) {
                WriteScreen(
                    status = status,
                    ready = ready,
                    result = result,
                    onBack = { finish() },
                    onDisarm = { disarm() },
                    onArm = { plan, overwrite ->
                        val nfc = adapter
                        if (nfc == null || !nfc.isEnabled) {
                            status = "请先在系统设置中开启 NFC"
                        } else {
                            pending = WriteRequest(plan, overwrite)
                            result = null
                            ready = true
                            armedFlag.set(true)
                            status = "请将可写 NFC 标签贴近手机背部"
                        }
                    }
                )
            }
        }
    }

    private fun disarm() {
        armedFlag.set(false)
        pending = null
        ready = false
        status = "已取消，未开始新的写卡操作"
    }

    override fun onResume() {
        super.onResume()
        val nfc = adapter
        if (nfc == null || !nfc.isEnabled) {
            status = "NFC 未开启或当前设备不支持 NFC"
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
            status = "无法启动 NFC 写卡会话，请检查系统权限"
        }
    }

    override fun onPause() {
        armedFlag.set(false)
        pending = null
        ready = false
        try { adapter?.disableReaderMode(this) } catch (_: Exception) {}
        super.onPause()
    }

    override fun onTagDiscovered(tag: Tag) {
        if (!armedFlag.compareAndSet(true, false)) return
        val request = pending ?: return
        pending = null
        val outcome = try { writeAndVerify(tag, request.draft, request.overwriteConfirmed) }
        catch (e: Exception) {
            WriteOutcome(false, "写入未通过验证：" + (e.message?.take(90) ?: "标签连接中断"))
        }
        runOnUiThread {
            if (!isFinishing && !isDestroyed) {
                ready = false
                result = outcome.message
                status = if (outcome.verified) "写入成功 · 已回读验证" else "写卡未完成"
            }
        }
    }

    private data class WriteOutcome(val verified: Boolean, val message: String)

    private fun writeAndVerify(
        tag: Tag, plan: PreparedNdefWrite, overwriteConfirmed: Boolean
    ): WriteOutcome {
        val record = when (plan.kind) {
            NdefWriteKind.TEXT -> NdefRecord.createTextRecord("zh", plan.content)
            NdefWriteKind.WEB_URL -> NdefRecord.createUri(Uri.parse(plan.content))
        }
        val message = NdefMessage(arrayOf(record))
        val bytes = message.toByteArray()
        val ndef = Ndef.get(tag)
            ?: return WriteOutcome(false, "此标签不支持标准 NDEF 写入；未修改标签")
        ndef.connect()
        return try {
            val previous = ndef.ndefMessage
            val existingMimeTypes = previous?.records
                ?.filter { it.tnf == NdefRecord.TNF_MIME_MEDIA }
                ?.map { String(it.type, StandardCharsets.US_ASCII) }
                ?: emptyList()
            val deny = NdefWriteRules.replacementError(
                previous?.records?.size ?: 0,
                existingMimeTypes,
                overwriteConfirmed
            )
            if (deny != null) return WriteOutcome(false, deny)
            if (!ndef.isWritable) return WriteOutcome(false, "标签只读；未修改任何数据")
            if (ndef.maxSize < bytes.size) {
                return WriteOutcome(false, "标签空间不足：需要 " + bytes.size + " B，容量 " + ndef.maxSize + " B")
            }

            // Never auto-retry a write. Re-read from the physical tag before reporting success.
            ndef.writeNdefMessage(message)
            val actual = ndef.ndefMessage
            if (actual == null || !bytes.contentEquals(actual.toByteArray())) {
                return WriteOutcome(false, "写入后回读不一致；不要把这个标签视为成功")
            }
            WriteOutcome(true, "写入并回读成功 · " + bytes.size + " 字节。未执行任何自动化脚本。")
        } finally {
            try { ndef.close() } catch (_: Exception) {}
        }
    }
}

@Composable
private fun WriteScreen(
    status: String,
    ready: Boolean,
    result: String?,
    onBack: () -> Unit,
    onDisarm: () -> Unit,
    onArm: (PreparedNdefWrite, Boolean) -> Unit
) {
    var kind by remember { mutableStateOf(NdefWriteKind.TEXT) }
    var input by remember { mutableStateOf("") }
    var allowOverwrite by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<PreparedNdefWrite?>(null) }
    val blue = Color(0xFF4769B5)
    val muted = Color(0xFF708097)
    Column(
        Modifier.fillMaxSize().background(Color(0xFFF3F6FC))
            .verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)
    ) {
        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ 返回") }
            Spacer(Modifier.width(6.dp))
            Text("NFC 写入", fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))
        Surface(color = Color(0xFFDFE9FC), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text("◎", color = blue, fontSize = 43.sp)
                Text(status, color = Color(0xFF26334C), fontSize = 16.sp)
                Spacer(Modifier.height(6.dp))
                Text("前台单次写入 · 写后回读 · 不调用 Root", color = muted, fontSize = 12.sp)
                if (ready) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = onDisarm) { Text("取消等待") }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Surface(color = Color.White, shape = RoundedCornerShape(21.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Text("写入内容", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    FilterChip(
                        selected = kind == NdefWriteKind.TEXT,
                        onClick = {
                            if (!ready) {
                                kind = NdefWriteKind.TEXT
                                error = null
                            }
                        }, enabled = !ready, label = { Text("文本") }
                    )
                    FilterChip(
                        selected = kind == NdefWriteKind.WEB_URL,
                        onClick = {
                            if (!ready) {
                                kind = NdefWriteKind.WEB_URL
                                error = null
                            }
                        }, enabled = !ready, label = { Text("网址") }
                    )
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !ready,
                    placeholder = {
                        Text(if (kind == NdefWriteKind.TEXT) "输入需要写入标签的文字" else "https://example.com")
                    },
                    minLines = if (kind == NdefWriteKind.TEXT) 3 else 1,
                    maxLines = 6,
                    label = { Text(if (kind == NdefWriteKind.TEXT) "标签文本" else "网页地址") }
                )
                Spacer(Modifier.height(8.dp))
                Text("写入会替换原有 NDEF 内容；已有标签需额外确认。",
                    color = muted, fontSize = 12.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = allowOverwrite,
                        onCheckedChange = { allowOverwrite = it },
                        enabled = !ready
                    )
                    Text("允许覆盖已有的普通 NDEF 数据", fontSize = 13.sp)
                }
                Text("DORM 脚本绑定标签始终禁止在这里覆盖。",
                    color = Color(0xFF99602E), fontSize = 12.sp)
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    enabled = !ready && input.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(15.dp),
                    onClick = {
                        val prepared = NdefWriteRules.prepare(kind, input)
                        if (prepared.draft == null) error = prepared.error
                        else preview = prepared.draft
                    }
                ) { Text(if (ready) "请贴近 NFC 标签" else "预览并确认写卡") }
            }
        }
        if (result != null) {
            Spacer(Modifier.height(14.dp))
            Surface(color = Color.White, shape = RoundedCornerShape(18.dp)) {
                Text(result, modifier = Modifier.fillMaxWidth().padding(18.dp),
                    fontSize = 14.sp, color = Color(0xFF26334C))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    preview?.let { plan ->
        AlertDialog(
            onDismissRequest = { preview = null },
            title = { Text("确认写入 NFC 标签？") },
            text = {
                Column {
                    Text(if (plan.kind == NdefWriteKind.TEXT) "文本记录" else "网页链接")
                    Spacer(Modifier.height(8.dp))
                    Text(plan.content.take(260), fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("内容约 " + plan.utf8Bytes + " 字节。此操作会替换现有 NDEF 消息，不会运行脚本。",
                        fontSize = 12.sp, color = muted)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    preview = null
                    onArm(plan, allowOverwrite)
                }) { Text("确认并等待贴卡") }
            },
            dismissButton = {
                TextButton(onClick = { preview = null }) { Text("取消") }
            }
        )
    }
}
