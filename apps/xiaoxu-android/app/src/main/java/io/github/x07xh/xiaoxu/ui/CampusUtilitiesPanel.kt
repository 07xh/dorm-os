package io.github.x07xh.xiaoxu.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.x07xh.xiaoxu.data.CampusUtilitiesException
import io.github.x07xh.xiaoxu.data.CampusUtilitiesPortal
import io.github.x07xh.xiaoxu.data.CampusUtilitiesReadOnlyClient
import io.github.x07xh.xiaoxu.data.CampusUtilityKind
import io.github.x07xh.xiaoxu.data.CampusUtilityMonthResult
import io.github.x07xh.xiaoxu.data.CampusUtilityRoom
import java.time.YearMonth
import kotlinx.coroutines.launch

/**
 * Explicit, per-user direct API reads: never share a developer session.
 * No password collection and no token persistence. The H5 link remains a
 * fallback while school authorization into native app remains unverified.
 */
@Composable
fun CampusUtilitiesPanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember { CampusUtilitiesReadOnlyClient() }
    var token by remember { mutableStateOf("") }
    var roomText by remember { mutableStateOf("") }
    var monthText by remember { mutableStateOf(YearMonth.now().toString()) }
    var kind by remember { mutableStateOf(CampusUtilityKind.ELECTRIC) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<CampusUtilityMonthResult?>(null) }
    var status by remember { mutableStateOf<String?>(null) }

    fun openOfficial(selected: CampusUtilityKind) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(CampusUtilitiesPortal.officialUrl(selected)))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "未找到可打开官方校园服务的浏览器", Toast.LENGTH_LONG).show()
        } catch (_: SecurityException) {
            Toast.makeText(context, "无法打开官方页面，请复制链接在微信或浏览器中访问", Toast.LENGTH_LONG).show()
        }
    }

    fun copyOfficial(selected: CampusUtilityKind) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newPlainText("华水校园服务 · ${selected.title}",
                CampusUtilitiesPortal.officialUrl(selected))
        )
        Toast.makeText(context, "已复制官方${selected.title}查询地址", Toast.LENGTH_SHORT).show()
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        XiaoxuCard {
            Text("校园生活 · 宿舍水电",
                style = MaterialTheme.typography.titleMedium, color = XiaoxuPalette.Ink)
            Text("水、电均可通过校序内置只读接口查询。需要当前学生自己的校园服务有效会话；接口地址和加密密钥不等于免登录权限。",
                style = MaterialTheme.typography.bodySmall, color = XiaoxuPalette.Ink2)
        }

        XiaoxuGroupedSection(title = "直接查询") {
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (item in CampusUtilityKind.entries) {
                        OutlinedButton(
                            enabled = !busy,
                            onClick = { kind = item; result = null; status = null },
                            modifier = Modifier.weight(1f)
                        ) { Text(if (kind == item) "✓ ${item.title}" else item.title) }
                    }
                }
                OutlinedTextField(
                    value = roomText,
                    onValueChange = { roomText = it.take(150); result = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("本人有权查询的房间坐标") },
                    placeholder = { Text("如 05-02-01-1-3-0301") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = monthText,
                    onValueChange = { monthText = it.take(7); result = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("查询月份 YYYY-MM") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it.trim().take(4096); result = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("本人有效 accessToken（不保存）") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    supportingText = { Text("仅用于当前页面的直接查询，不会上传校序云端。请勿提供其他人的 Token。") }
                )
                OutlinedButton(
                    enabled = !busy && token.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val selectedRoom = CampusUtilityRoom.parse(roomText)
                        val selectedMonth = runCatching { YearMonth.parse(monthText) }.getOrNull()
                        if (selectedRoom == null || selectedMonth == null) {
                            status = "请输入正确的房间六段坐标和月份"
                            result = null
                        } else {
                            busy = true
                            status = null
                            result = null
                            val currentToken = token
                            scope.launch {
                                try {
                                    result = client.readMonth(currentToken, kind, selectedRoom, selectedMonth)
                                    status = "已读取学校接口数据（账户与房间归属由学校鉴权判定）"
                                } catch (e: CampusUtilitiesException) {
                                    status = e.message
                                } catch (_: Exception) {
                                    status = "查询失败，当前数据保持未验证"
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    }
                ) { Text(if (busy) "正在只读查询…" else "直接查询${kind.title}") }

                TextButton(onClick = { token = ""; roomText = ""; result = null; status = "本页输入已清除" },
                    enabled = !busy) { Text("清除本页会话与房间输入") }
                status?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = XiaoxuPalette.Ink2)
                }
            }
        }

        result?.let { item ->
            XiaoxuCard {
                Text("${item.utility.title} · ${item.month}",
                    style = MaterialTheme.typography.titleMedium, color = XiaoxuPalette.Ink)
                Text("总用量：${item.totalUsed ?: "接口未提供"}",
                    style = MaterialTheme.typography.bodyMedium, color = XiaoxuPalette.Ink)
                Text("明细记录：${item.recordCount?.toString() ?: "未提供"} · ${item.detailVersion}",
                    style = MaterialTheme.typography.bodySmall, color = XiaoxuPalette.Ink2)
                Text("查询时间：${java.time.Instant.ofEpochMilli(item.fetchedAtMillis)}",
                    style = MaterialTheme.typography.bodySmall, color = XiaoxuPalette.Ink2)
                Text("报告尚无真实回包样本。此处只展示实际返回字段，不推断余额或房间所有权。",
                    style = MaterialTheme.typography.bodySmall, color = XiaoxuPalette.Ink2)
            }
        }

        XiaoxuGroupedSection(title = "官方页面备用入口") {
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("如果没有可供本机使用的授权会话，可继续使用学校自己的 H5。不会使用开发者账号代查。",
                    style = MaterialTheme.typography.bodySmall, color = XiaoxuPalette.Ink2)
                OutlinedButton(onClick = { openOfficial(kind) }, modifier = Modifier.fillMaxWidth()) {
                    Text("打开学校官方${kind.title}页面")
                }
                OutlinedButton(onClick = { copyOfficial(kind) }, modifier = Modifier.fillMaxWidth()) {
                    Text("复制学校官方链接")
                }
            }
        }
    }
}
