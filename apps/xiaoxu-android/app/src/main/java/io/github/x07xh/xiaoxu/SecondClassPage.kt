package io.github.x07xh.xiaoxu

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.x07xh.xiaoxu.data.LocalTaskDatabase
import io.github.x07xh.xiaoxu.data.NativeSchoolRecord
import io.github.x07xh.xiaoxu.ui.*
import java.text.DateFormat
import java.util.Date

@Composable
fun SecondClassPage() {
    val context = LocalContext.current
    val dao = remember(context) { LocalTaskDatabase.open(context).nativeSchoolRecords() }
    val all by remember(dao) { dao.observe() }.collectAsState(initial = emptyList())
    val snapshot = remember(all) { secondClassSnapshot(all) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    var refreshing by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf("显示本机上次保存结果") }
    val scoresScroll = rememberLazyListState()
    val activitiesScroll = rememberLazyListState()
    val reader = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        refreshing = false
        message = if (result.resultCode == Activity.RESULT_OK) "本次刷新已保存"
            else "本次未更新，保留上次数据；可重试或在官网重新登录"
    }
    fun refresh() {
        if (refreshing) return
        refreshing = true
        message = "正在打开官方会话核验并刷新…"
        try {
            reader.launch(Intent(context, NativeSchoolConnectActivity::class.java)
                .putExtra(NativeSchoolConnectActivity.EXTRA_PROVIDER, "second_class")
                .putExtra(NativeSchoolConnectActivity.EXTRA_AUTO_SCORE, true))
        } catch (_: android.content.ActivityNotFoundException) {
            refreshing = false
            message = "无法打开学校读取页面，已保留上次数据"
        }
    }
    LaunchedEffect(snapshot.scores.isNotEmpty()) {
        if (snapshot.scores.isNotEmpty() && !attempted) { attempted = true; refresh() }
    }
    Column(Modifier.fillMaxSize()) {
        SecondaryTabRow(selectedTabIndex = tab) {
            listOf("积分", "报名活动").forEachIndexed { index, label ->
                Tab(selected = tab == index, onClick = { tab = index }, text = { Text(label) })
            }
        }
        LazyColumn(Modifier.fillMaxSize(),
            state = if (tab == 0) scoresScroll else activitiesScroll,
            contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item(key = "status") {
                XiaoxuCard {
                    Text("第二课堂", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(message, style = MaterialTheme.typography.bodyMedium, color = XiaoxuPalette.Ink2)
                    Text("学校官网 · 本机快照", style = MaterialTheme.typography.labelSmall, color = XiaoxuPalette.Ink2)
                    Button(onClick = { attempted = true; refresh() }, enabled = !refreshing,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(if (refreshing) "正在读取…" else if (snapshot.scores.isEmpty()) "连接学校账号" else "刷新积分与报名活动")
                    }
                }
            }
            if (tab == 0) {
                if (snapshot.scores.isEmpty()) item { Text("尚未读取本人积分。请在学校官方页面正常登录后读取。", color = XiaoxuPalette.Ink2) }
                items(snapshot.scores, key = { "score:" + it.fingerprint }) { row ->
                    SecondClassRecordCard(row, prominent = row.title == "学校积分合计")
                }
            } else {
                item {
                    Text(if (snapshot.activitiesComplete) "本人报名 · ${snapshot.activities.size} 条" else "本人报名活动",
                        style = MaterialTheme.typography.titleLarge, color = XiaoxuPalette.Ink)
                    Text(if (snapshot.activitiesComplete) "已完成上次活动分页读取；活动分数不与官方积分合计相加。"
                        else "尚未完成活动分页读取，请刷新；不展示旧手动采集记录作为完整结果。",
                        style = MaterialTheme.typography.bodySmall, color = XiaoxuPalette.Ink2)
                }
                if (snapshot.activitiesComplete && snapshot.activities.isEmpty()) item { Text("上次完整读取：暂无报名活动。", color = XiaoxuPalette.Ink2) }
                items(snapshot.activities, key = { "activity:" + it.fingerprint }) { row -> SecondClassRecordCard(row) }
                snapshot.activitySummary?.let { summary -> item { Text("上次更新：" + DateFormat.getDateTimeInstance().format(Date(summary.capturedAt)), style = MaterialTheme.typography.labelSmall, color = XiaoxuPalette.Ink2) } }
            }
            item { Text("离线可查看上次保存结果。学校会话有效时复用；过期后在官网重新授权。", style = MaterialTheme.typography.bodySmall, color = XiaoxuPalette.Ink2) }
        }
    }
}

@Composable
private fun SecondClassRecordCard(row: NativeSchoolRecord, prominent: Boolean = false) {
    XiaoxuCard(container = if (prominent) XiaoxuPalette.BrandSoft else XiaoxuPalette.Surface) {
        Text(row.title, style = if (prominent) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold, color = XiaoxuPalette.Ink)
        Text(row.details, style = MaterialTheme.typography.bodyMedium, color = XiaoxuPalette.Ink)
        Text("上次更新：" + DateFormat.getDateTimeInstance().format(Date(row.capturedAt)),
            style = MaterialTheme.typography.labelSmall, color = XiaoxuPalette.Ink2)
    }
}
