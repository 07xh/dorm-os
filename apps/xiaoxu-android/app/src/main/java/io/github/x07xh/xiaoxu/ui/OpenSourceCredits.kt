package io.github.x07xh.xiaoxu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.github.x07xh.xiaoxu.ui.v2.UiV2SettingsGroup
import io.github.x07xh.xiaoxu.ui.v2.UiV2SettingsEntry
import io.github.x07xh.xiaoxu.ui.v2.UiV2SettingsDivider

/**
 * 保留全部随包许可，不在 UI 中重写许可证正文。
 * 采用与「关于校序」一致的连续分组与整行点击；冗长原文按需展开。
 */
@Composable
fun OpenSourceCreditsPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    val projects = aboutUpstreams + AboutUpstream(
        "Phosphor Icons", "图标资源", "MIT", "https://github.com/phosphor-icons/core"
    )
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        UiV2SettingsGroup(title = "项目致谢") {
            projects.forEachIndexed { index, project ->
                if (index > 0) UiV2SettingsDivider()
                UiV2SettingsEntry(
                    title = project.name, icon = Icons.Outlined.Code,
                    subtitle = project.detail + " · " + project.license,
                    onClick = {
                        runCatching { uriHandler.openUri(project.url) }.onFailure {
                            android.widget.Toast.makeText(context, "无法打开链接",
                                android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
        UiV2SettingsGroup(title = "许可与源码") {
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
            }
            UiV2SettingsDivider()
            UiV2SettingsEntry("查看完整声明", onClick = { expanded = true })
        }
    }
    if (expanded) {
        val notices by produceState<String?>(initialValue = null) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    context.assets.open("THIRD_PARTY_NOTICES.txt")
                        .bufferedReader().use { it.readText() }
                }.getOrElse { "" }
            }
        }
        XiaoxuBottomSheet(title = "完整许可与来源声明", onDismiss = { expanded = false },
            showCancel = false) {
            Text(
                when {
                    notices == null -> "正在读取…"
                    notices.isNullOrBlank() -> "暂无法读取许可全文，请检查 APK 内 assets/THIRD_PARTY_NOTICES.txt"
                    else -> notices.orEmpty()
                },
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = XiaoxuPalette.Ink2
            )
        }
    }
}
