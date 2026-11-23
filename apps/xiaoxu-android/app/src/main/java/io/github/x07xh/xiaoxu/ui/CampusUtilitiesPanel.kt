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
import io.github.x07xh.xiaoxu.NativeSchoolConnectActivity
import io.github.x07xh.xiaoxu.data.CampusUtilitiesPortal
import io.github.x07xh.xiaoxu.data.CampusUtilityKind

/**
 * Native utility entry. All school authentication and read requests stay in
 * the existing official WebView and never traverse an external token prompt.
 */
@Composable
fun CampusUtilitiesPanel() {
    val context = LocalContext.current

    fun openOfficial(selected: CampusUtilityKind) {
        val intent = Intent(Intent.ACTION_VIEW,
            Uri.parse(CampusUtilitiesPortal.officialUrl(selected)))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "未找到可打开官方校园服务的浏览器", Toast.LENGTH_LONG).show()
        } catch (_: SecurityException) {
            Toast.makeText(context, "无法打开官方页面，可复制链接在微信或浏览器中访问", Toast.LENGTH_LONG).show()
        }
    }

    fun copyOfficial(selected: CampusUtilityKind) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newPlainText("华水校园服务 · " + selected.title,
                CampusUtilitiesPortal.officialUrl(selected))
        )
        Toast.makeText(context, "已复制学校官方查询地址", Toast.LENGTH_SHORT).show()
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        XiaoxuCard {
            Text("校园生活 · 宿舍水电",
                style = MaterialTheme.typography.titleMedium, color = XiaoxuPalette.Ink)
            Text("每位学生在学校官方页面使用自己的会话。校序在相同学校域名内执行只读查询，不用粘贴 Token，也不共用开发者账号。",
                style = MaterialTheme.typography.bodySmall, color = XiaoxuPalette.Ink2)
        }

        XiaoxuGroupedSection(title = "本机授权与查询") {
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        context.startActivity(
                            Intent(context, NativeSchoolConnectActivity::class.java)
                                .putExtra(NativeSchoolConnectActivity.EXTRA_PROVIDER, "campus_utilities")
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("进入学校登录并直接查询水电费")
                }
                Text("在该页面正常登录学校服务，填写本人有权查询的房间六段坐标和月份，选水费或电费后查询。学校不支持当前 WebView 登录时可使用下方官方入口。",
                    style = MaterialTheme.typography.bodySmall, color = XiaoxuPalette.Ink2)
            }
        }

        XiaoxuGroupedSection(title = "官方页面备用入口") {
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (selected in CampusUtilityKind.entries) {
                    OutlinedButton(onClick = { openOfficial(selected) },
                        modifier = Modifier.fillMaxWidth()) {
                        Text("打开学校官方" + selected.title + "页面")
                    }
                    OutlinedButton(onClick = { copyOfficial(selected) },
                        modifier = Modifier.fillMaxWidth()) {
                        Text("复制官方" + selected.title + "链接")
                    }
                }
            }
        }

        XiaoxuCard {
            Text("接入状态：已开发原生只读适配器，真实回包待验收",
                style = MaterialTheme.typography.titleSmall, color = XiaoxuPalette.Ink)
            Text("没有学校真实授权和用量回包的端到端验证前，不展示虚构余额。此页面不储存 Token、账户和水电数据；共用设备请在学校官网退出前一位使用者。",
                style = MaterialTheme.typography.bodySmall, color = XiaoxuPalette.Ink2)
        }
    }
}
