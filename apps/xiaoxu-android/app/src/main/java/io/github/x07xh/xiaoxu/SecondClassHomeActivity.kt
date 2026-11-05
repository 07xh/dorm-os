package io.github.x07xh.xiaoxu

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.x07xh.xiaoxu.data.LocalTaskDatabase
import io.github.x07xh.xiaoxu.ui.XiaoxuTheme
import java.text.DateFormat
import java.util.Date

/** Dedicated local snapshot screen; refresh executes only in the visible official session. */
class SecondClassHomeActivity : ComponentActivity() {
    private var refreshMessage by mutableStateOf("显示本机上次保存结果")
    private val reader = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            setResult(Activity.RESULT_OK, result.data)
            refreshMessage = "本次刷新已保存"
        } else refreshMessage = "本次未更新，保留上次数据；可重试或在官网重新登录"
    }
    private fun refresh() {
        refreshMessage = "正在打开官方会话核验并刷新…"
        reader.launch(Intent(this, NativeSchoolConnectActivity::class.java)
            .putExtra(NativeSchoolConnectActivity.EXTRA_PROVIDER, "second_class")
            .putExtra(NativeSchoolConnectActivity.EXTRA_AUTO_SCORE, true))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dao = LocalTaskDatabase.open(this).nativeSchoolRecords()
        setContent { XiaoxuTheme {
            val all by dao.observe().collectAsState(initial = emptyList())
            val rows = all.filter { it.provider == "second_class" && it.kind == "credits" }
            var attempted by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(rows.isNotEmpty()) {
                if (rows.isNotEmpty() && !attempted) { attempted = true; refresh() }
            }
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    TextButton(onClick = { finish() }) { Text("返回校序") }
                    Text("第二课堂", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(refreshMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("进入此页会尝试刷新已连接账号。会话有效时无需重新登录；过期时在官网授权。首次保存或切换账号须确认。",
                        style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { attempted = true; refresh() }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (rows.isEmpty()) "连接并读取本人积分" else "立即刷新积分")
                    }
                    if (rows.isEmpty()) Text("尚无本机积分，请先连接学校官网。")
                    rows.sortedBy { if (it.title == "学校积分合计") 0 else 1 }.forEach { row ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(row.title, fontWeight = FontWeight.SemiBold)
                                Text(row.details)
                                Text("上次更新：" + DateFormat.getDateTimeInstance().format(Date(row.capturedAt)),
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Text("来源：学校官网 · 手机本机快照。离线可查看旧记录，时间不代表当前登录仍有效。",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        } }
    }
}
