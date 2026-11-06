package io.github.x07xh.xiaoxu.ui

import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.x07xh.xiaoxu.data.SecondClassAutoRefresh

@Composable
fun SecondClassAutoReadControls() {
    val c = LocalContext.current.applicationContext
    val prefs = remember(c) { c.getSharedPreferences(SecondClassAutoRefresh.SETTINGS, 0) }
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val enabled = remember(revision) { SecondClassAutoRefresh.enabled(c) }
    val background = remember(revision) { SecondClassAutoRefresh.background(c) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text("自动读取", style = MaterialTheme.typography.bodyMedium)
            Text("回到 App 时检查，10分钟内复用已保存数据",
                style = MaterialTheme.typography.labelSmall)
        }
        Switch(checked = enabled, onCheckedChange = {
            SecondClassAutoRefresh.configure(c, it, background)
        })
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text("后台定期检查", style = MaterialTheme.typography.bodyMedium)
            Text("约30分钟，系统可能延迟", style = MaterialTheme.typography.labelSmall)
        }
        Switch(checked = background, enabled = enabled, onCheckedChange = {
            SecondClassAutoRefresh.configure(c, enabled, it)
        })
    }
    val status = remember(revision) { SecondClassAutoRefresh.status(c) }
    val message = when (status) {
        "reading" -> "正在自动读取…"
        "needs_reconnect" -> "学校登录已失效，请重新连接"
        "needs_confirmation" -> "学校账号已变化，请重新确认本人账号"
        "failed" -> "自动读取未完成，已保留上次数据"
        else -> null
    }
    if (message != null) Text(message, style = MaterialTheme.typography.labelSmall)
}
