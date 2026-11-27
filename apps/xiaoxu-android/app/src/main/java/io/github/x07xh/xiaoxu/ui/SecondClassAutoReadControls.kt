package io.github.x07xh.xiaoxu.ui

import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.x07xh.xiaoxu.data.SecondClassAutoRefresh

@Composable
fun SecondClassAutoReadControls() {
    val c = LocalContext.current.applicationContext
    val prefs = remember(c) { c.getSharedPreferences(SecondClassAutoRefresh.SETTINGS, 0) }
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changed ->
            if (changed == null || changed in setOf("enabled", "background", "status", "success_at",
                    "background_check_at", "success_background")) revision++
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val enabled = remember(revision) { SecondClassAutoRefresh.enabled(c) }
    val background = remember(revision) { SecondClassAutoRefresh.background(c) }
    val status = remember(revision) { SecondClassAutoRefresh.status(c) }
    val message = when (status) {
        "reading" -> "正在自动读取…"
        "needs_reconnect" -> "学校登录已失效，请重新连接"
        "needs_confirmation" -> "学校账号已变化，请重新确认本人账号"
        "failed" -> "自动读取未完成，已保留上次数据"
        else -> null
    }
    val success = remember(revision) { SecondClassAutoRefresh.lastSuccess(c) }
    val checked = remember(revision) { SecondClassAutoRefresh.lastBackgroundCheck(c) }
    val backgroundSuccess = remember(revision) { SecondClassAutoRefresh.lastSuccessWasBackground(c) }
    val recent = when {
        success > 0 -> "上次自动更新：" + android.text.format.DateFormat.format("MM-dd HH:mm", success) +
            if (backgroundSuccess) "（后台）" else ""
        checked > 0 -> "最近后台检查：" + android.text.format.DateFormat.format("MM-dd HH:mm", checked)
        else -> null
    }
    InsetGroupedSection(header = "自动同步", animateSize = false,
        footer = "仅同步本人已授权记录；后台执行时间可能因系统省电策略而延迟。") {
        InsetGroupedRow(title = "自动读取", subtitle = "返回应用或进入第二课堂时检查；10 分钟内使用已保存数据",
            trailing = { LiquidSwitch(checked = enabled, onCheckedChange = {
                SecondClassAutoRefresh.configure(c, it, background)
            }) })
        InsetGroupedRow(title = "后台定期检查", subtitle = "约每 30 分钟检查一次",
            enabled = enabled, showDivider = false,
            trailing = { LiquidSwitch(checked = background, enabled = enabled, onCheckedChange = {
                SecondClassAutoRefresh.configure(c, enabled, it)
            }) })
    }
    InsetGroupedSection(header = "同步状态", animateSize = false) {
        InsetGroupedRow(title = message ?: if (!enabled) "自动读取已关闭" else "等待下次检查",
            subtitle = recent ?: "尚无自动读取记录", showDivider = false)
    }
}
