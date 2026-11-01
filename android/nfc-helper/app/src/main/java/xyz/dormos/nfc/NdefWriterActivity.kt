package xyz.dormos.nfc

import android.net.Uri
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
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

/**
 * Foreground-only generic writer. This activity has no Root/Shell references.
 * Unlike the script binding writer it refuses to overwrite a DORM automation tag.
 */
class NdefWriterActivity : ComponentActivity(), NfcAdapter.ReaderCallback {
    private data class WriteRequest(
        val draft: PreparedNdefWrite,
        val overwriteConfirmed: Boolean
    )
    private val gate = NfcOneShotGate<WriteRequest>()
    @Volatile private var inForeground = false
    @Volatile private var writeIssued = false
    private var adapter: NfcAdapter? = null
    private var ready by mutableStateOf(false)
    private var status by mutableStateOf("选择内容并确认，才会开始写卡")
    private var result by mutableStateOf<String?>(null)
    private var resultVerified by mutableStateOf<Boolean?>(null)
    private var writing by mutableStateOf(false)
    private var nfcAvailable by mutableStateOf(false)
    private var nfcEnabled by mutableStateOf(false)

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
                    resultVerified = resultVerified,
                    writing = writing,
                    nfcAvailable = nfcAvailable,
                    nfcEnabled = nfcEnabled,
                    onBack = {
                        gate.cancelPending()
                        inForeground = false
                        finish()
                    },
                    onInspect = {
                        if (gate.isProcessing()) {
                            status = "NFC 操作仍在进行，不能同时启动另一项扫描"
                        } else {
                            gate.cancelPending()
                            startActivity(Intent(this, TagInspectorActivity::class.java))
                        }
                    },
                    onNfcSettings = {
                        try { startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }
                        catch (_: Exception) {
                            try { startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) }
                            catch (_: Exception) { status = "系统没有可用的 NFC 设置入口，请手动开启 NFC" }
                        }
                    },
                    onDisarm = { disarm() },
                    onArm = { plan, overwrite ->
                        val nfc = adapter
                        if (nfc == null || !nfc.isEnabled) {
                            status = "请先在系统设置中开启 NFC"
                        } else {
                            if (gate.arm(WriteRequest(plan, overwrite))) {
                                result = null
                                resultVerified = null
                                writeIssued = false
                                ready = true
                                status = "请将可写 NFC 标签贴近手机背部"
                            } else {
                                status = "前一次 NFC 写入尚未结束，请先确认标签状态"
                            }
                        }
                    }
                )
            }
        }
    }

    private fun disarm() {
        if (!gate.cancelPending()) {
            status = "写入或回读中，无法取消已开始的操作"
            return
        }
        ready = false
        status = "已取消，未开始新的写卡操作"
    }

    override fun onResume() {
        super.onResume()
        inForeground = true
        val nfc = adapter
        nfcAvailable = nfc != null
        nfcEnabled = nfc?.isEnabled == true
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
        inForeground = false
        gate.cancelPending()
        ready = false
        if (gate.isProcessing()) {
            status = "写卡过程离开前台，结果暂未确认。请重新读取标签核对。"
            resultVerified = null
        }
        try { adapter?.disableReaderMode(this) } catch (_: Exception) {}
        super.onPause()
    }

    override fun onTagDiscovered(tag: Tag) {
        if (!inForeground) return
        val request = gate.claim() ?: return
        runOnUiThread {
            if (inForeground && !isFinishing && !isDestroyed) {
                writing = true
                status = "正在检查标签、写入并回读，请保持标签贴近"
            }
        }
        val outcome = try { writeAndVerify(tag, request.draft, request.overwriteConfirmed) }
        catch (e: Exception) {
            WriteOutcome(false, NfcFailureFeedback.writeFailure(e, writeIssued))
        }
        gate.finish()
        runOnUiThread {
            writing = false
            if (inForeground && !isFinishing && !isDestroyed) {
                ready = false
                result = outcome.message
                resultVerified = outcome.verified
                status = if (outcome.verified) "写入成功 · 已回读验证" else "未验证成功 · 查看下方说明"
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
            ?: return WriteOutcome(false, NfcFailureFeedback.noNdef(NdefFormatable.get(tag) != null))
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
                return WriteOutcome(false, NfcFailureFeedback.insufficientCapacity(bytes.size, ndef.maxSize))
            }

            // Do not begin a new write after leaving the foreground session.
            if (!inForeground) return WriteOutcome(false, "写卡页面已离开，没有开始写入。")
            // An interruption after the write begins makes success unknown until a new read.
            writeIssued = true
            ndef.writeNdefMessage(message)
            val actual = ndef.ndefMessage
            if (actual == null) return WriteOutcome(false, NfcFailureFeedback.readbackUnavailable())
            if (!bytes.contentEquals(actual.toByteArray())) {
                return WriteOutcome(false, NfcFailureFeedback.readbackMismatch())
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
    resultVerified: Boolean?,
    writing: Boolean,
    nfcAvailable: Boolean,
    nfcEnabled: Boolean,
    onBack: () -> Unit,
    onInspect: () -> Unit,
    onNfcSettings: () -> Unit,
    onDisarm: () -> Unit,
    onArm: (PreparedNdefWrite, Boolean) -> Unit
) {
    val p = LocalV2Palette.current
    var kind by remember { mutableStateOf(NdefWriteKind.TEXT) }
    var input by remember { mutableStateOf("") }
    var allowOverwrite by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<PreparedNdefWrite?>(null) }

    V2BottomSheet(
        title = "写入 NFC 标签",
        subtitle = "普通 NDEF 文本与网页 · 不调用 KernelSU",
        onDismiss = onBack
    ) {
        Surface(
            color = p.accentLow,
            shape = RoundedCornerShape(17.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, p.line)
        ) {
            Column(Modifier.fillMaxWidth().padding(15.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(48.dp).background(p.iconBackground, RoundedCornerShape(15.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        V2NfcMark(Modifier.size(27.dp), p.blue)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(status, color = p.ink, fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold, lineHeight = 19.sp)
                        Spacer(Modifier.height(5.dp))
                        Text(
                            when {
                                writing -> "正在写入并回读，请保持标签贴合"
                                ready -> "单次写卡已准备，等待实体标签"
                                resultVerified == true -> "NDEF 内容字节回读一致"
                                else -> "编辑内容 → 确认 → 贴卡回读"
                            }, color = p.muted, fontSize = 11.sp
                        )
                    }
                }
                if (writing) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = p.blue)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("内容类型", color = p.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = kind == NdefWriteKind.TEXT,
                onClick = {
                    if (!ready && !writing) {
                        kind = NdefWriteKind.TEXT
                        error = null
                    }
                },
                enabled = !ready && !writing,
                label = { Text("文本") }
            )
            FilterChip(
                selected = kind == NdefWriteKind.WEB_URL,
                onClick = {
                    if (!ready && !writing) {
                        kind = NdefWriteKind.WEB_URL
                        error = null
                    }
                },
                enabled = !ready && !writing,
                label = { Text("网页链接") }
            )
        }
        OutlinedTextField(
            value = input,
            onValueChange = {
                if (it.length <= 4096) {
                    input = it
                    error = null
                } else error = "输入过长，请控制在 4096 字符以内"
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !ready && !writing,
            placeholder = {
                Text(if (kind == NdefWriteKind.TEXT) "输入需要写入的文字" else "https://example.com")
            },
            minLines = if (kind == NdefWriteKind.TEXT) 3 else 1,
            maxLines = 6,
            label = { Text(if (kind == NdefWriteKind.TEXT) "标签文本" else "网页地址") },
            shape = RoundedCornerShape(14.dp)
        )
        Spacer(Modifier.height(7.dp))
        Text(
            "输入：${input.toByteArray(Charsets.UTF_8).size} B · NFC 容量会在贴卡时校验",
            color = p.muted, fontSize = 11.sp
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = allowOverwrite,
                onCheckedChange = { allowOverwrite = it },
                enabled = !ready && !writing
            )
            Text("允许覆盖已有的普通 NDEF 数据", color = p.ink, fontSize = 12.sp)
        }
        Text(
            "自动化场景标签受保护，此工具不会覆盖已绑定的 DORM 标签。",
            color = p.muted, fontSize = 11.sp, lineHeight = 17.sp
        )
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
        Spacer(Modifier.height(15.dp))
        if (!nfcAvailable || !nfcEnabled) {
            Button(
                onClick = onNfcSettings, enabled = nfcAvailable && !writing,
                modifier = Modifier.fillMaxWidth().height(49.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Text(if (nfcAvailable) "打开系统 NFC 设置" else "当前设备不支持 NFC") }
        } else if (ready && !writing) {
            OutlinedButton(
                onClick = onDisarm,
                modifier = Modifier.fillMaxWidth().height(49.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Text("取消等待贴卡") }
        } else {
            Button(
                enabled = !writing && input.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(49.dp),
                shape = RoundedCornerShape(14.dp),
                onClick = {
                    val prepared = NdefWriteRules.prepare(kind, input)
                    if (prepared.draft == null) error = prepared.error
                    else preview = prepared.draft
                }
            ) { Text("预览并确认写卡") }
        }
        if (result != null) {
            Spacer(Modifier.height(13.dp))
            Surface(
                color = if (resultVerified == true) Color(0xFFE7F6F0) else Color(0xFFFFF3E8),
                shape = RoundedCornerShape(15.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text(result, color = Color(0xFF243448), fontSize = 12.sp, lineHeight = 19.sp)
                    Spacer(Modifier.height(9.dp))
                    OutlinedButton(
                        onClick = onInspect, enabled = !writing,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("打开只读页面再次核验") }
                }
            }
        }
    }

    preview?.let { plan ->
        V2BottomSheet(
            title = "确认写入 NFC 标签",
            subtitle = "一次性写入并回读验证 · 可以取消",
            onDismiss = { preview = null }
        ) {
            Surface(color = p.accentLow, shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.fillMaxWidth().padding(15.dp)) {
                    Text(if (plan.kind == NdefWriteKind.TEXT) "文本记录" else "网页链接",
                        color = p.ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(plan.content.take(260), color = p.ink, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "内容约 ${plan.utf8Bytes} 字节，将替换标签现有 NDEF 消息，不会运行 Root 脚本。",
                        color = p.muted, fontSize = 11.sp, lineHeight = 17.sp
                    )
                }
            }
            Spacer(Modifier.height(15.dp))
            Button(
                onClick = {
                    preview = null
                    onArm(plan, allowOverwrite)
                },
                modifier = Modifier.fillMaxWidth().height(49.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Text("确认并等待贴卡") }
            OutlinedButton(
                onClick = { preview = null },
                modifier = Modifier.fillMaxWidth().height(47.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Text("取消") }
        }
    }
}
