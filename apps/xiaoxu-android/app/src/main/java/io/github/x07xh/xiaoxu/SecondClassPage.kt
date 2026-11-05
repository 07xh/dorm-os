package io.github.x07xh.xiaoxu

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.x07xh.xiaoxu.data.LocalTaskDatabase
import java.text.DateFormat
import java.util.Date

/**
 * Main navigation second-class page. Only the existing local official-page
 * reader + Room records are used for "My activities" and "Scores".
 * The school's all-activities discovery feed has not been verified; do NOT
 * mix "my signups" into the catalog or fabricate upcoming activities.
 */
@Composable
fun SecondClassPage(
    onRefreshResult: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dao = remember(context) { LocalTaskDatabase.open(context).nativeSchoolRecords() }
    val all by dao.observe().collectAsState(initial = emptyList())
    val scores = all.filter { it.provider == "second_class" && it.kind == "credits" }
    val candidates = all.filter {
        it.provider == "second_class" && it.kind in setOf("activity", "activity_summary")
    }
    val batch = candidates.singleOrNull { it.kind == "activity_summary" }
    val activities = if (batch != null &&
        candidates.all { it.capturedAt == batch.capturedAt }) candidates else emptyList()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf("显示本机上次保存结果") }
    val reader = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val succeeded = result.resultCode == Activity.RESULT_OK
        message = if (succeeded) "本次刷新已保存"
            else "本次未更新，保留上次数据；可重试或在官网重新登录"
        onRefreshResult?.invoke(succeeded)
    }
    fun refreshPersonalRecords() {
        message = "正在打开官方会话核验并刷新个人记录…"
        reader.launch(Intent(context, NativeSchoolConnectActivity::class.java)
            .putExtra(NativeSchoolConnectActivity.EXTRA_PROVIDER, "second_class")
            .putExtra(NativeSchoolConnectActivity.EXTRA_AUTO_SCORE, true))
    }

    // Only refresh verified personal data when the user opens those sections.
    // Opening Activity Discovery alone must NOT be described as an event sync.
    LaunchedEffect(scores.isNotEmpty(), tab) {
        if (tab != 0 && scores.isNotEmpty() && !attempted) {
            attempted = true
            refreshPersonalRecords()
        }
    }

    Column(modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            listOf("活动广场", "我的活动", "素质积分").forEachIndexed { index, label ->
                Tab(selected = tab == index, onClick = { tab = index },
                    text = { Text(label) })
            }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (tab == 0) {
                Text("发现活动", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("学校活动广场尚未接入",
                            fontWeight = FontWeight.SemiBold)
                        Text("当前校序已具备本人报名活动与积分的读取入口，" +
                            "尚未验证所有正在报名的活动目录、名额和报名截止时间。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("因此这里暂不显示活动卡片，也不会用个人报名列表冒充全校活动。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(context, NativeSchoolConnectActivity::class.java)
                                        .putExtra(NativeSchoolConnectActivity.EXTRA_PROVIDER, "second_class")
                                )
                            }.onFailure { message = "无法打开校序内学校页面" }
                        }) { Text("在校序内检查活动列表来源") }
                        Text("打开后先正常登录学校官网，进入活动页面，再手动开启请求观测；" +
                            "观测结果仅在本机显示，不代表活动数据已接入。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW,
                                        Uri.parse("https://dekt.ncwu.edu.cn/"))
                                )
                            }.onFailure { message = "无法打开学校官网，请检查浏览器" }
                        }) { Text("前往第二课堂官方平台") }
                    }
                }
                if (message.startsWith("无法打开")) {
                    Text(message, color = MaterialTheme.colorScheme.error)
                }
            } else {
                Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { attempted = true; refreshPersonalRecords() },
                    modifier = Modifier.fillMaxWidth()) {
                    Text(if (scores.isEmpty() && activities.isEmpty())
                        "连接并读取本人积分与活动" else "刷新积分与报名活动")
                }
                val rows = if (tab == 2) {
                    scores.sortedBy { if (it.title == "学校积分合计") 0 else 1 }
                } else {
                    activities.sortedBy { if (it.kind == "activity_summary") 0 else 1 }
                }
                if (rows.isEmpty()) {
                    Text(if (tab == 2) "尚无本机积分，请先完成学校官方登录和本人读取。"
                         else "尚未完成本人报名活动的完整分页读取，请刷新。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                rows.forEach { row ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(row.title, fontWeight = FontWeight.SemiBold)
                            Text(row.details)
                            Text("上次更新：" +
                                DateFormat.getDateTimeInstance().format(Date(row.capturedAt)),
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Text("来源：学校官网 · 手机本机快照。离线可查看旧记录；" +
                    "保存时间不证明学校当前会话仍有效。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
