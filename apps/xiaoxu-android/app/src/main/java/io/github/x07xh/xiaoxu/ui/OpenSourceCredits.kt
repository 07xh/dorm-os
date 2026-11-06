package io.github.x07xh.xiaoxu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 「我的」页底部的开源署名区块。
 *
 * SleepDown 课程表的「署名非商业、源码可见许可 1.1」要求在使用方的 App 内「关于」
 * 位置署名并给出上游链接，本区块就是这项义务的履行点；`THIRD_PARTY_NOTICES.md`
 * 里「App 内『关于』页署名与链接」一行指向这里。
 *
 * 正文直接读取随 APK 分发的 `assets/THIRD_PARTY_NOTICES.txt`，不在 Kotlin 里另抄
 * 一份许可文本 —— 抄写会随时间漂移，读资产文件才能保证展示内容与发布包内的许可
 * 声明逐字一致。文件约 2 KB，展开时才读，读取放在 IO 线程，不阻塞首帧。
 */
@Composable
fun OpenSourceCreditsPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val notices by produceState(initialValue = "") {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open("THIRD_PARTY_NOTICES.txt")
                    .bufferedReader().use { it.readText() }
            }.getOrElse { "" }
        }
    }
    XiaoxuCard(modifier = modifier) {
        Text("开源组件与致谢", style = MaterialTheme.typography.titleSmall)
        Text(
            "本 App 包含两处第三方代码：拾光课程表的青果教务适配（MIT）与 SleepDown 课程表" +
                "的导航动画驱动（署名非商业、源码可见许可 1.1，非 OSI 开源许可）。后者为修改版，" +
                "仅替换 package 与文件头注释，逻辑未改动；校序按两者中更严格的一方履行义务。",
            style = MaterialTheme.typography.bodySmall,
            color = XiaoxuPalette.Ink2
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = {
                runCatching { uriHandler.openUri("https://github.com/xiaomanjun233/SleepDown-Schedule") }
            }) { Text("SleepDown-Schedule", style = MaterialTheme.typography.labelMedium) }
            TextButton(onClick = {
                runCatching { uriHandler.openUri("https://github.com/ShiGuangSchedule/shiguang_warehouse") }
            }) { Text("拾光课程表", style = MaterialTheme.typography.labelMedium) }
        }
        Text(
            if (notices.isBlank()) "许可全文随 APK 分发：assets/THIRD_PARTY_NOTICES.txt"
            else notices,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = XiaoxuPalette.Ink3
        )
    }
}
