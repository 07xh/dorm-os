package io.github.x07xh.xiaoxu

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.x07xh.xiaoxu.data.LocalTaskDatabase
import java.text.DateFormat
import java.util.Date

@Composable
fun SecondClassPage() {
    val context = LocalContext.current
    val dao = remember(context) { LocalTaskDatabase.open(context).nativeSchoolRecords() }
    val all by dao.observe().collectAsState(initial = emptyList())
    val scores = all.filter { it.provider == "second_class" && it.kind == "credits" }
    val candidates = all.filter { it.provider == "second_class" && it.kind in setOf("activity", "activity_summary") }
    val batch = candidates.singleOrNull { it.kind == "activity_summary" }
    val activities = if (batch != null && candidates.all { it.capturedAt == batch.capturedAt }) candidates else emptyList()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf("显示本机上次保存结果") }
    val reader = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        message = if (result.resultCode == Activity.RESULT_OK) "本次刷新已保存"
            else "本次未更新，保留上次数据；可重试或在官网重新登录"
    }
    fun refresh() {
        message = "正在打开官方会话核验并刷新…"
        reader.launch(Intent(context, NativeSchoolConnectActivity::class.java)
            .putExtra(NativeSchoolConnectActivity.EXTRA_PROVIDER, "second_class")
            .putExtra(NativeSchoolConnectActivity.EXTRA_AUTO_SCORE, true))
    }
    LaunchedEffect(scores.isNotEmpty()) {
        if (scores.isNotEmpty() && !attempted) { attempted = true; refresh() }
    }
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            listOf("积分", "报名活动").forEachIndexed { index, label ->
                Tab(selected = tab == index, onClick = { tab = index }, text = { Text(label) })
            }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { attempted = true; refresh() }, modifier = Modifier.fillMaxWidth()) {
                Text(if (scores.isEmpty()) "连接并读取本人积分与活动" else "刷新积分与报名活动")
            }
            val rows = if (tab == 0) scores.sortedBy { if (it.title == "学校积分合计") 0 else 1 }
                else activities.sortedBy { if (it.kind == "activity_summary") 0 else 1 }
            if (rows.isEmpty()) Text(if (tab == 0) "尚无本机积分，请先连接学校官网。"
                else "尚未完成活动分页读取，请刷新。")
            rows.forEach { row ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(row.title, fontWeight = FontWeight.SemiBold)
                        Text(row.details)
                        Text("上次更新：" + DateFormat.getDateTimeInstance().format(Date(row.capturedAt)),
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Text("来源：学校官网 · 手机本机快照。离线可查看旧记录。", style = MaterialTheme.typography.bodySmall)
        }
    }
}
