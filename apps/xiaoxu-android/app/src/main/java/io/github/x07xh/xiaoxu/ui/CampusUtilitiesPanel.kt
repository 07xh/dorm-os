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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.x07xh.xiaoxu.data.CampusUtilitiesPortal
import io.github.x07xh.xiaoxu.data.CampusUtilityKind

/**
 * Per-student self-service entry to the school's own H5. Every request and
 * sign-in stays on the official site; no shared Xiaoxu school identity,
 * embedded credential reader, or local/cloud meter snapshot is created here.
 */
@Composable
fun CampusUtilitiesPanel() {
    val context = LocalContext.current

    fun openOfficial(kind: CampusUtilityKind) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(CampusUtilitiesPortal.officialUrl(kind)))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "未找到可打开官方校园服务的浏览器", Toast.LENGTH_LONG).show()
        } catch (_: SecurityException) {
            Toast.makeText(context, "无法打开官方页面，请复制链接在微信或浏览器中访问", Toast.LENGTH_LONG).show()
        }
    }

    fun copyOfficial(kind: CampusUtilityKind) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newPlainText("华水校园服务 · ${kind.title}", CampusUtilitiesPortal.officialUrl(kind))
        )
        Toast.makeText(context, "已复制官方${kind.title}查询地址", Toast.LENGTH_SHORT).show()
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        XiaoxuCard {
            Text("校园生活 · 宿舍水电",
                style = MaterialTheme.typography.titleMedium, color = XiaoxuPalette.Ink)
            Text("每位学生在学校官方页面自行登录，查看自己有权限的宿舍与账单。校序不共用开发者账号，也不保存学校密码、Token 或水电明细。",
                style = MaterialTheme.typography.bodySmall, color = XiaoxuPalette.Ink2)
        }

        for (kind in CampusUtilityKind.entries) {
            XiaoxuGroupedSection(title = "${kind.title}查询") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("在学校校园生活 H5 中查询；需要官方登录或微信环境时，以学校页面提示为准。",
                        style = MaterialTheme.typography.bodySmall, color = XiaoxuPalette.Ink2)
                    OutlinedButton(
                        onClick = { openOfficial(kind) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("打开官方${kind.title}页面") }
                    OutlinedButton(
                        onClick = { copyOfficial(kind) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("复制官方链接") }
                }
            }
        }

        XiaoxuCard {
            Text("接入状态：官方查询入口已接入",
                style = MaterialTheme.typography.titleSmall, color = XiaoxuPalette.Ink)
            Text("原生余额、用量及多房间数据读取尚未通过真实授权与权限验收，暂不展示虚构数值。多人共用设备时，请先在官方页面退出前一位学生的登录。",
                style = MaterialTheme.typography.bodySmall, color = XiaoxuPalette.Ink2)
        }
    }
}
