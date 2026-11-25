package io.github.x07xh.xiaoxu.ui

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.github.x07xh.xiaoxu.R

/** Original AboutPage installed-version query; common grouped rows replace legacy brand cards. */
@Composable
internal fun AboutFeature(onOpenLicenses: () -> Unit, openWebsite: () -> Unit) {
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

    fun openLink(url: String) {
        runCatching { uriHandler.openUri(url) }
            .onFailure { android.widget.Toast.makeText(context, "无法打开链接", android.widget.Toast.LENGTH_SHORT).show() }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.section)) {
        InsetGroupedSection(header = "校序 Academic OS") {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Image(painterResource(R.drawable.ic_launcher), contentDescription = null, modifier = Modifier.size(48.dp))
                Column { Text("校序", style = MaterialTheme.typography.titleLarge); Text("版本 " + installed.first) }
            }
            InsetGroupedRow(title = "构建编号", subtitle = installed.second.toString(), showDivider = false)
        }
        InsetGroupedSection(header = "项目") {
            AppGroupedEntry("开发者", "07xh · GitHub", onClick = { openLink("https://github.com/07xh") })
            AppGroupedEntry("项目仓库", "07xh/dorm-os", onClick = { openLink("https://github.com/07xh/dorm-os") })
            AppGroupedEntry("官方网站", onClick = openWebsite)
            AppGroupedEntry("开源许可与致谢", showDivider = false, onClick = onOpenLicenses)
        }
        AppNotice("显示当前安装包版本，不代表已联网检查更新。")
    }
}
