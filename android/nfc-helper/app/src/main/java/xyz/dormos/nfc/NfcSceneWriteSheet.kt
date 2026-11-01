package xyz.dormos.nfc

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The physical NFC write is a stateful bottom sheet over the current workbench.
 * Neither a modal press nor a Tag object alone indicates a successful binding:
 * the NDEF readback must match, then KSU must persist the scene association.
 */
@Composable
internal fun NfcSceneWriteSheet(
    sceneName: String,
    scriptName: String,
    session: NfcSceneWriteSession,
    onDismiss: () -> Unit,
    onReadTag: () -> Unit,
    onNfcSettings: () -> Unit
) {
    val p = LocalV2Palette.current
    val phase = session.phase
    val pending = phase == SceneWritePhase.WRITING || phase == SceneWritePhase.REGISTERING
    V2BottomSheet(
        title = "绑定 NFC 标签",
        subtitle = "写入、回读、登记 · 全程留在当前页面",
        onDismiss = { if (!pending) onDismiss() }
    ) {
        Surface(
            color = p.accentLow,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, p.line)
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(Modifier.size(100.dp), contentAlignment = Alignment.Center) {
                    Surface(
                        modifier = Modifier.size(100.dp),
                        color = Color.Transparent,
                        border = BorderStroke(2.dp, p.blue.copy(alpha = 0.22f)),
                        shape = RoundedCornerShape(50.dp)
                    ) {}
                    Surface(
                        modifier = Modifier.size(77.dp),
                        color = p.iconBackground,
                        shape = RoundedCornerShape(26.dp)
                    ) {}
                    if (phase == SceneWritePhase.DONE) {
                        Text("✓", color = p.blue, fontSize = 40.sp, fontWeight = FontWeight.SemiBold)
                    } else {
                        V2NfcMark(Modifier.size(36.dp), p.blue)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    when (phase) {
                        SceneWritePhase.WAITING -> "靠近手机背部的 NFC 区域"
                        SceneWritePhase.WRITING -> "正在写入并回读"
                        SceneWritePhase.CONFIRM_OVERWRITE -> "标签已有数据"
                        SceneWritePhase.CONFIRM_FORMAT -> "需要格式化"
                        SceneWritePhase.FORMATTED -> "等待再次读取验证"
                        SceneWritePhase.REGISTERING -> "正在保存本机绑定"
                        SceneWritePhase.DONE -> "绑定完成"
                        SceneWritePhase.INTERRUPTED -> "验证已中断"
                        SceneWritePhase.FAILED -> "本次未完成绑定"
                    },
                    color = p.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    session.status,
                    color = p.muted, fontSize = 12.sp, lineHeight = 19.sp
                )
                if (pending) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = p.blue)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Surface(
            color = p.surface,
            shape = RoundedCornerShape(17.dp),
            border = BorderStroke(1.dp, p.line)
        ) {
            Column(Modifier.fillMaxWidth().padding(15.dp)) {
                Text("本次场景", color = p.muted, fontSize = 11.sp)
                Spacer(Modifier.height(5.dp))
                Text(sceneName, color = p.ink, fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(7.dp))
                Text("执行动作：$scriptName", color = p.muted, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = p.line)
                Spacer(Modifier.height(10.dp))
                Text(
                    "标签只保存随机场景编号，不保存 Shell 命令。仅在回读验证和 KernelSU 保存成功后才显示绑定完成。",
                    color = p.muted, fontSize = 11.sp, lineHeight = 17.sp
                )
            }
        }
        if (phase == SceneWritePhase.CONFIRM_OVERWRITE ||
            phase == SceneWritePhase.CONFIRM_FORMAT) {
            Spacer(Modifier.height(12.dp))
            Surface(color = Color(0xFFFFF3E5), shape = RoundedCornerShape(14.dp)) {
                Text(
                    if (phase == SceneWritePhase.CONFIRM_OVERWRITE)
                        "已有内容将被替换。确认后必须重新贴近刚才的同一张标签；旧 DORM 场景只会在新绑定验证成功后清理。"
                    else
                        "格式化可能改变标签的原始数据。确认后重新贴卡，完成格式化后仍需再次核验才能绑定。",
                    modifier = Modifier.padding(13.dp), color = Color(0xFF80552E),
                    fontSize = 12.sp, lineHeight = 18.sp
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        when (phase) {
            SceneWritePhase.CONFIRM_OVERWRITE, SceneWritePhase.CONFIRM_FORMAT -> {
                Button(
                    onClick = { session.confirmExistingData() },
                    modifier = Modifier.fillMaxWidth().height(49.dp),
                    shape = RoundedCornerShape(15.dp)
                ) {
                    Text(if (phase == SceneWritePhase.CONFIRM_OVERWRITE)
                        "确认覆盖并重新贴卡" else "确认格式化并重新贴卡")
                }
            }
            SceneWritePhase.FORMATTED -> {
                Button(
                    onClick = { session.retry() },
                    modifier = Modifier.fillMaxWidth().height(49.dp),
                    enabled = !session.isWorking,
                    shape = RoundedCornerShape(15.dp)
                ) { Text("继续写卡并回读") }
            }
            SceneWritePhase.FAILED, SceneWritePhase.INTERRUPTED -> {
                Button(
                    onClick = onReadTag,
                    modifier = Modifier.fillMaxWidth().height(49.dp),
                    enabled = !session.isWorking,
                    shape = RoundedCornerShape(15.dp)
                ) { Text("只读核对标签") }
            }
            SceneWritePhase.DONE -> {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(49.dp),
                    shape = RoundedCornerShape(15.dp)
                ) { Text("完成") }
            }
            SceneWritePhase.WAITING -> {
                if (!session.available) {
                    Text("当前设备不支持 NFC", color = MaterialTheme.colorScheme.error)
                } else if (!session.enabled) {
                    Button(onClick = onNfcSettings, modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(15.dp)) { Text("打开 NFC 设置") }
                } else {
                    Text("准备就绪，贴卡后自动处理", color = p.muted,
                        fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                }
            }
            else -> {}
        }
        if (phase != SceneWritePhase.DONE) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onDismiss,
                enabled = !pending && !session.isWorking,
                modifier = Modifier.fillMaxWidth().height(47.dp),
                shape = RoundedCornerShape(15.dp)
            ) { Text("取消") }
        }
    }
}
