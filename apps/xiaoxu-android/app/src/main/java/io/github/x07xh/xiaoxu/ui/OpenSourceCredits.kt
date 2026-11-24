package io.github.x07xh.xiaoxu.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 保留全部随包许可，不在 UI 中重写许可证正文。
 * 采用与「关于校序」一致的连续分组与整行点击；冗长原文按需展开。
 */
@Composable
fun OpenSourceCreditsPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    val notices by produceState(initialValue = "", key1 = expanded) {
        if (expanded) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    context.assets.open("THIRD_PARTY_NOTICES.txt")
                        .bufferedReader().use { it.readText() }
                }.getOrElse { "" }
            }
        }
    }
    val projects = listOf(
        Triple("SleepDown-Schedule", "导航手势与页面动效", "https://github.com/xiaomanjun233/SleepDown-Schedule"),
        Triple("拾光课程表", "教务适配 · MIT", "https://github.com/ShiGuangSchedule/shiguang_warehouse"),
        Triple("北化课表", "玻璃材质与图标 · GPL-3.0", "https://github.com/znjhahaha/zhengfang-apk/tree/v1.0.83"),
        Triple("Phosphor Icons", "图标资源 · MIT", "https://github.com/phosphor-icons/core")
    )
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        XiaoxuGroupedSection(title = "项目致谢") {
            projects.forEachIndexed { index, project ->
                if (index > 0) {
                    HorizontalDivider(
                        color = XiaoxuPalette.Line, thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 18.dp)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp)
                        .clickable(role = Role.Button, onClickLabel = "打开" + project.first) {
                            runCatching { uriHandler.openUri(project.third) }
                                .onFailure {
                                    android.widget.Toast.makeText(
                                        context, "无法打开链接", android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                        }.padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Code, contentDescription = null,
                        tint = XiaoxuPalette.Brand, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.size(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(project.first, style = MaterialTheme.typography.titleMedium,
                            color = XiaoxuPalette.Ink)
                        Text(project.second, style = MaterialTheme.typography.bodySmall,
                            color = XiaoxuPalette.Ink2)
                    }
                    Icon(Icons.Outlined.ChevronRight, contentDescription = null,
                        tint = XiaoxuPalette.Ink3)
                }
            }
        }
        XiaoxuGroupedSection(title = "许可与源码") {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 15.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "完整许可与来源记录随 APK 一起分发。开源署名与源码链接均予保留。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = XiaoxuPalette.Ink
                )
                Text(
                    "目前的 GPLv3 适配与 SleepDown 非商业衍生代码的组合分发兼容性尚未完成审查，" +
                        "此版本仅供开发与设备验收；不可据此认定可公开分发。",
                    style = MaterialTheme.typography.bodySmall,
                    color = XiaoxuPalette.Warning
                )
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "收起完整声明" else "查看完整声明")
                }
                if (expanded) {
                    Text(
                        if (notices.isBlank()) "暂无法读取许可全文，请检查 APK 内 assets/THIRD_PARTY_NOTICES.txt"
                        else notices,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = XiaoxuPalette.Ink2
                    )
                }
            }
        }
    }
}
