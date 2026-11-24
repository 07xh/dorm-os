package io.github.x07xh.xiaoxu.ui

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.x07xh.xiaoxu.R
import io.github.x07xh.xiaoxu.ui.v2.UiV2SettingsGroup
import io.github.x07xh.xiaoxu.ui.v2.UiV2SettingsEntry
import io.github.x07xh.xiaoxu.ui.v2.UiV2SettingsDivider

/** Only projects already credited by the bundled THIRD_PARTY_NOTICES. */
private data class AboutUpstream(
    val name: String,
    val detail: String,
    val license: String,
    val url: String
)

private val aboutUpstreams = listOf(
    AboutUpstream(
        "SleepDown-Schedule", "导航手势与页面动效", "署名非商业、源码可见许可 1.1",
        "https://github.com/xiaomanjun233/SleepDown-Schedule"
    ),
    AboutUpstream(
        "拾光课程表", "教务适配", "MIT",
        "https://github.com/ShiGuangSchedule/shiguang_warehouse"
    ),
    AboutUpstream(
        "北化课表", "玻璃材质与图标 · v1.0.83", "GPL-3.0",
        "https://github.com/znjhahaha/zhengfang-apk/tree/v1.0.83"
    ),
    AboutUpstream(
        "DSH Go", "V2 布局与交互设计参考", "项目 AGPL-3.0；部分文件 MIT",
        "https://github.com/ccch1mneyyy/dsh-Go"
    )
)

/**
 * 关于校序：统一 V2 分组与紧凑版本信息，保留真实项目致谢与许可入口。
 * 使用 APK 真实版本；不声称“最新版本”，不设置尚未发布的隐私链接。
 */
@Composable
internal fun AboutPage(onOpenLicenses: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val installed = remember(context) {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            @Suppress("DEPRECATION")
            val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode
                else info.versionCode.toLong()
            (info.versionName ?: "未知") to code
        }.getOrElse { "未知" to 0L }
    }
    var selectedUpstream by remember { mutableStateOf<AboutUpstream?>(null) }
    var showVersion by remember { mutableStateOf(false) }

    fun openLink(url: String) {
        runCatching { uriHandler.openUri(url) }
            .onFailure { android.widget.Toast.makeText(context, "无法打开链接", android.widget.Toast.LENGTH_SHORT).show() }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AboutBrandHero(version = installed.first, onVersionClick = { showVersion = true })
        UiV2SettingsGroup {
            UiV2SettingsEntry(title = "关于开发者", subtitle = "07xh · GitHub",
                onClick = { openLink("https://github.com/07xh") })
            UiV2SettingsDivider()
            UiV2SettingsEntry(title = "项目仓库", subtitle = "07xh/dorm-os",
                onClick = { openLink("https://github.com/07xh/dorm-os") })
            UiV2SettingsDivider()
            UiV2SettingsEntry("官方网站", onClick = { openLink("https://dorm-os-dk9.pages.dev") })
            UiV2SettingsDivider()
            UiV2SettingsEntry("开源许可", onClick = onOpenLicenses)
        }
        UiV2SettingsGroup(title = "项目致谢") {
            aboutUpstreams.forEachIndexed { index, upstream ->
                if (index != 0) UiV2SettingsDivider()
                UiV2SettingsEntry(title = upstream.name, icon = Icons.Outlined.Code,
                    subtitle = upstream.detail, onClick = { selectedUpstream = upstream })
            }
        }
    }

    if (showVersion) {
        XiaoxuBottomSheet(
            title = "版本信息",
            onDismiss = { showVersion = false },
            subtitle = "校序 Academic OS"
        ) {
            AboutSheetField("应用版本", installed.first)
            AboutSheetField("构建编号", installed.second.toString())
            Text(
                "版本信息来自当前安装的 APK，不代表已联网检查更新。",
                color = XiaoxuPalette.Ink2,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }

    selectedUpstream?.let { upstream ->
        XiaoxuBottomActionSheet(
            title = upstream.name,
            subtitle = upstream.detail + " · " + upstream.license,
            onDismiss = { selectedUpstream = null },
            actions = listOf(
                XiaoxuSheetAction("打开项目仓库", onClick = {
                    selectedUpstream = null
                    openLink(upstream.url)
                }),
                XiaoxuSheetAction("查看完整许可说明", onClick = {
                    selectedUpstream = null
                    onOpenLicenses()
                })
            )
        )
    }
}

@Composable
private fun AboutBrandHero(version: String, onVersionClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(XiaoxuPalette.Surface.copy(alpha = 0.9f))
            .padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Image(painter = painterResource(R.drawable.ic_launcher), contentDescription = null,
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)))
        Text("校序 Academic OS", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold, color = XiaoxuPalette.Ink,
            textAlign = TextAlign.Center)
        UiV2SettingsDivider()
        UiV2SettingsEntry("版本信息", statusLabel = version, onClick = onVersionClick)
    }
}

@Composable
private fun AboutSheetField(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = XiaoxuPalette.Ink2, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(16.dp))
        Text(value, color = XiaoxuPalette.Ink, style = MaterialTheme.typography.titleMedium)
    }
}
