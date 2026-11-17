package io.github.x07xh.xiaoxu.ui

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.x07xh.xiaoxu.R

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
    )
)

/**
 * 关于校序：SleepDown 的大圆角品牌卡、同组连续列表与轻量底部弹窗。
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

    Column(verticalArrangement = Arrangement.spacedBy(XiaoxuSpacing.section)) {
        AboutBrandHero(version = installed.first, onVersionClick = { showVersion = true })
        XiaoxuGroupedSection {
            AboutInfoRow(title = "关于开发者", subtitle = "07xh · GitHub") {
                openLink("https://github.com/07xh")
            }
            AboutLine()
            AboutInfoRow(title = "项目仓库", subtitle = "07xh/dorm-os") {
                openLink("https://github.com/07xh/dorm-os")
            }
        }
        XiaoxuGroupedSection {
            AboutInfoRow("官方网站") {
                openLink("https://dorm-os-dk9.pages.dev")
            }
            AboutLine()
            AboutInfoRow("开源许可", onClick = onOpenLicenses)
            AboutLine()
            AboutInfoRow("版本信息") { showVersion = true }
        }
        XiaoxuGroupedSection(title = "项目致谢") {
            aboutUpstreams.forEachIndexed { index, upstream ->
                if (index != 0) AboutLine()
                AboutInfoRow(upstream.name, upstream.detail, icon = true) {
                    selectedUpstream = upstream
                }
            }
        }
        Text(
            "校序 · 让学习安排更有序",
            style = MaterialTheme.typography.labelSmall,
            color = XiaoxuPalette.Ink3,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp)
        )
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
    Box(
        modifier = Modifier.fillMaxWidth().height(254.dp)
            .clip(RoundedCornerShape(XiaoxuRadius.hero))
            .background(Brush.linearGradient(listOf(
                Color(0xFF171D59), Color(0xFF424298), Color(0xFF244E91)
            )))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val band = Path().apply {
                moveTo(0f, h * 0.40f)
                cubicTo(w * 0.24f, h * 0.57f, w * 0.56f, h * 0.74f, w, h * 0.83f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(band, Color(0xFF15134D).copy(alpha = 0.90f))
            drawOval(
                brush = Brush.linearGradient(listOf(
                    Color(0xFF8784C9).copy(alpha = 0.77f),
                    Color(0xFF3E72AE).copy(alpha = 0.68f),
                    Color(0xFF222462).copy(alpha = 0.83f)
                )),
                topLeft = Offset(w * 0.43f, h * 0.15f),
                size = Size(w * 0.92f, h * 0.85f)
            )
            drawOval(
                brush = Brush.linearGradient(listOf(
                    Color(0xFF5EA7BC).copy(alpha = 0.85f),
                    Color(0xFF7E62B8).copy(alpha = 0.75f),
                    Color(0xFF332A87).copy(alpha = 0.72f)
                )),
                topLeft = Offset(-w * 0.17f, h * 0.66f),
                size = Size(w * 0.73f, h * 0.66f)
            )
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(vertical = 23.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher),
                contentDescription = null,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "校序 Academic OS",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                fontSize = 25.sp,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Text(
                "版本 " + version,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.88f)
            )
            Spacer(Modifier.height(24.dp))
            val interaction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier.clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = 0.18f))
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Button,
                        onClick = onVersionClick
                    ).padding(horizontal = 24.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("版本详情", color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun AboutInfoRow(
    title: String,
    subtitle: String? = null,
    icon: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = if (subtitle == null) 58.dp else 68.dp)
            .clickable(role = Role.Button, onClickLabel = "打开" + title, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon) {
            Icon(Icons.Outlined.Code, contentDescription = null,
                tint = XiaoxuPalette.Brand, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                title, style = MaterialTheme.typography.titleMedium, color = XiaoxuPalette.Ink,
                maxLines = 1
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle, style = MaterialTheme.typography.bodySmall,
                    color = XiaoxuPalette.Ink2, maxLines = 2
                )
            }
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null,
            tint = XiaoxuPalette.Ink3, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun AboutLine() {
    HorizontalDivider(
        color = XiaoxuPalette.Line, thickness = 0.5.dp,
        modifier = Modifier.padding(horizontal = 18.dp)
    )
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
