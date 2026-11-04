package xyz.dormos.nfc

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

/** Ordinary text / URL NDEF writing stays inside the existing TapFlow workbench. */
@Composable
internal fun NdefWriteSheet(
    session: NdefWriteSession,
    initialContent: String = "",
    onDismiss: () -> Unit,
    onReadTag: () -> Unit,
    onNfcSettings: () -> Unit
) {
    val p = LocalV2Palette.current
    var kind by remember(initialContent) { mutableStateOf(if (initialContent.startsWith("tapflow://")) NdefWriteKind.WEB_URL else NdefWriteKind.TEXT) }
    var input by remember(initialContent) { mutableStateOf(initialContent) }
    var allowOverwrite by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<PreparedNdefWrite?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val phase = session.phase
    val locked = phase == NdefWritePhase.ARMED || phase == NdefWritePhase.WRITING
    val result = session.lastResult
    val requiresReview = NdefWriteRecoveryPolicy.needsReadOnlyReview(phase, result)

    V2BottomSheet(
        title = "写入 NFC 标签",
        subtitle = "普通 NDEF 文本、网址和 TapFlow 场景 URI · 不调用 KernelSU",
        onDismiss = { if (!session.isWorking) onDismiss() }
    ) {
        Surface(
            color = p.accentLow,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, p.line)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(52.dp).background(p.iconBackground, RoundedCornerShape(17.dp)),
                    contentAlignment = Alignment.Center
                ) { V2NfcMark(Modifier.size(29.dp), p.blue) }
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when (phase) {
                            NdefWritePhase.ARMED -> "等待 NFC 标签"
                            NdefWritePhase.WRITING -> "正在写入并回读"
                            NdefWritePhase.VERIFIED -> "写入验证成功"
                            NdefWritePhase.FAILED -> "本次写入未通过验证"
                            NdefWritePhase.INTERRUPTED -> "写入结果需要核对"
                            else -> "选择内容并确认，才会开始写卡"
                        },
                        color = p.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when (phase) {
                            NdefWritePhase.ARMED -> "贴近手机背部 NFC 区域；只执行一次"
                            NdefWritePhase.WRITING -> "请保持标签贴合，不要移开"
                            NdefWritePhase.VERIFIED -> "完整 NDEF 数据已逐字节回读一致"
                            NdefWritePhase.FAILED, NdefWritePhase.INTERRUPTED -> session.status
                            else -> "编辑内容 → 预览确认 → 贴卡写入 → 回读验证"
                        },
                        color = p.muted, fontSize = 11.sp, lineHeight = 17.sp
                    )
                }
            }
        }
        if (phase == NdefWritePhase.WRITING) {
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = p.blue,
                trackColor = p.accentLow
            )
        }

        if (!session.nfcAvailable || !session.nfcEnabled) {
            Spacer(Modifier.height(10.dp))
            Text(
                if (session.nfcAvailable) "NFC 当前未开启。开启系统 NFC 后即可继续。"
                else "当前设备没有可用的 NFC 硬件。",
                color = p.muted, fontSize = 12.sp, lineHeight = 18.sp
            )
            if (session.nfcAvailable) {
                OutlinedButton(
                    onClick = onNfcSettings,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(13.dp)
                ) { Text("打开系统 NFC 设置") }
            }
        }

        val mayConfirmPreview = phase == NdefWritePhase.EDITING ||
            (phase == NdefWritePhase.FAILED && result == null)
        if (mayConfirmPreview) {
            if (preview == null) {
                Spacer(Modifier.height(15.dp))
                Text("内容类型", color = p.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    FilterChip(
                        selected = kind == NdefWriteKind.TEXT,
                        onClick = { kind = NdefWriteKind.TEXT; error = null },
                        label = { Text("文本") }
                    )
                    FilterChip(
                        selected = kind == NdefWriteKind.WEB_URL,
                        onClick = { kind = NdefWriteKind.WEB_URL; error = null },
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
                    placeholder = {
                        Text(if (kind == NdefWriteKind.TEXT) "输入需要写入的文字" else "https://example.com 或 tapflow://scene/...")
                    },
                    label = { Text(if (kind == NdefWriteKind.TEXT) "标签文本" else "网页地址") },
                    minLines = if (kind == NdefWriteKind.TEXT) 3 else 1,
                    maxLines = 5,
                    shape = RoundedCornerShape(15.dp)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "输入：${input.toByteArray(Charsets.UTF_8).size} B · 最大 2048 B，标签容量将在贴卡时检查",
                    color = p.muted, fontSize = 11.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = allowOverwrite,
                        onCheckedChange = { allowOverwrite = it }
                    )
                    Text("允许覆盖已有的普通 NDEF 数据", color = p.ink, fontSize = 12.sp)
                }
                Text(
                    "已绑定的 TapFlow 自动化标签受到保护；普通写入不会覆盖场景绑定。",
                    color = p.muted, fontSize = 11.sp, lineHeight = 17.sp
                )
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
                Spacer(Modifier.height(13.dp))
                Button(
                    enabled = input.isNotBlank() && session.nfcAvailable,
                    onClick = {
                        val prepared = NdefWriteRules.prepare(kind, input)
                        if (prepared.draft == null) error = prepared.error
                        else {
                            preview = prepared.draft
                            error = null
                            session.returnToEdit()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(49.dp),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("预览写入内容") }
            } else {
                Spacer(Modifier.height(14.dp))
                Surface(
                    color = p.accentLow,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, p.line)
                ) {
                    Column(Modifier.fillMaxWidth().padding(15.dp)) {
                        Text(
                            if (preview?.kind == NdefWriteKind.TEXT) "文本记录预览" else "网页链接预览",
                            color = p.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(preview?.content.orEmpty().take(500), color = p.ink,
                            fontSize = 13.sp, lineHeight = 19.sp)
                        Spacer(Modifier.height(9.dp))
                        Text(
                            "约 ${preview?.utf8Bytes ?: 0} B。确认后将替换标签现有普通 NDEF 消息；不会运行 Root 脚本。",
                            color = p.muted, fontSize = 11.sp, lineHeight = 17.sp
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        val plan = preview ?: return@Button
                        error = null
                        if (!session.arm(plan, allowOverwrite)) error = session.status
                    },
                    enabled = session.nfcAvailable && session.nfcEnabled,
                    modifier = Modifier.fillMaxWidth().height(49.dp),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("确认并等待贴卡") }
                OutlinedButton(
                    onClick = { preview = null; error = null; session.returnToEdit() },
                    modifier = Modifier.fillMaxWidth().height(45.dp),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("返回修改") }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                if (result != null && !result.verified) {
                    Spacer(Modifier.height(8.dp))
                    Text(result.message, color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp, lineHeight = 18.sp)
                }
            }
        }

        if (locked) {
            Spacer(Modifier.height(13.dp))
            Surface(color = p.surface, shape = RoundedCornerShape(15.dp),
                border = BorderStroke(1.dp, p.line)) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text(if (phase == NdefWritePhase.WRITING) "请保持标签贴合" else "本次写入内容",
                        color = p.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(preview?.content.orEmpty().take(240), color = p.muted,
                        fontSize = 12.sp, lineHeight = 18.sp)
                }
            }
            if (phase == NdefWritePhase.ARMED) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        if (session.cancelArmed()) error = null else error = session.status
                    },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("取消等待贴卡") }
            }
        }

        if (phase == NdefWritePhase.VERIFIED && result != null) {
            Spacer(Modifier.height(14.dp))
            Surface(color = Color(0xFFE7F6F0), shape = RoundedCornerShape(15.dp)) {
                Column(Modifier.fillMaxWidth().padding(15.dp)) {
                    Text("✓ 写入并回读验证成功", color = Color(0xFF176B51),
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(result.message, color = Color(0xFF315C4D),
                        fontSize = 12.sp, lineHeight = 18.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Text("完成") }
        }

        if (phase == NdefWritePhase.FAILED || phase == NdefWritePhase.INTERRUPTED) {
            Spacer(Modifier.height(10.dp))
            Surface(color = Color(0xFFFFF3E8), shape = RoundedCornerShape(15.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text(
                        result?.message ?: session.status,
                        color = Color(0xFF80552E), fontSize = 12.sp, lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "如果提示写入可能已发生，请先使用读取 NFC 标签核对实际内容，不要直接重复写入。",
                        color = Color(0xFF80552E), fontSize = 11.sp, lineHeight = 17.sp
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            if (requiresReview) {
                Button(
                    onClick = onReadTag,
                    enabled = !session.isWorking,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("只读核对标签") }
            } else {
                OutlinedButton(
                    onClick = {
                        if (session.returnToEdit()) {
                            preview = null
                            error = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("返回编辑") }
            }
        }
    }
}
