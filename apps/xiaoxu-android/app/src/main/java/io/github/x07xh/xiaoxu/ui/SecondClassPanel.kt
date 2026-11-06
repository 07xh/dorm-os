package io.github.x07xh.xiaoxu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.x07xh.xiaoxu.data.CloudClient
import io.github.x07xh.xiaoxu.data.SecondClassActivity
import io.github.x07xh.xiaoxu.data.SecondClassSnapshot
import kotlinx.coroutines.launch

/*
 * Profile-only cloud snapshot inspector. The real three-tab second-class
 * navigation belongs exclusively to the native Academic > SecondClassPage.
 * This optional inspector displays the owner's preexisting cloud/D1 personal
 * snapshot, never an all-campus activities catalog, school login or fake data.
 */
@Composable
fun SecondClassPanel(client: CloudClient, openWebsite: () -> Unit) {
    val state = client.state
    val scope = rememberCoroutineScope()
    val result = state.secondClass
    var showActivities by rememberSaveable { mutableStateOf(false) }
    var showScores by rememberSaveable { mutableStateOf(false) }

    // Reads the same owner-protected snapshot API; does not initiate school CAS.
    LaunchedEffect(state.authenticated) {
        if (state.authenticated) client.loadSecondClass()
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("云端第二课堂 · 个人快照", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold)
        Text("仅查看本人已保存的云端活动与积分。活动广场请从「教务 → 第二课堂」查看，" +
            "云端快照尚未验证学校正在报名的全部活动目录。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (!state.authenticated) {
            SectionCard {
                Text("尚未连接校序云端", fontWeight = FontWeight.SemiBold)
                Text("未连接时不会填充模拟活动或积分。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = openWebsite) { Text("查看现有数据连接") }
            }
        } else {
            Button(enabled = !state.secondClassBusy && !state.busy,
                onClick = { scope.launch { client.loadSecondClass() } }) {
                Text(if (state.secondClassBusy) "正在读取…" else "刷新云端个人快照")
            }
            state.secondClassError?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            if (result != null && !state.secondClassFresh) {
                Text("本次尚未确认最新记录；以下如有内容，仅为上次读取的快照。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }
            if (result?.status == "stale") {
                Text("数据已超过36小时，仅供参考，不代表学校当前记录。",
                    color = MaterialTheme.colorScheme.error)
            }
            if (result == null || result.status == "not_connected") {
                SectionCard {
                    Text("尚无可核验的第二课堂个人数据")
                    Text("请先在学校官方页面完成本人登录和真实读取。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = openWebsite) { Text("查看现有连接和诊断") }
                }
            } else {
                Text(if (result.provenance == "trusted_readonly") "服务端已验证只读来源"
                    else "用户自行导入的数据（非官方实时同步）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("上次成功读取：" + (result.lastSuccessAt ?: "未知"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                SectionCard {
                    TextButton(onClick = { showActivities = !showActivities },
                        modifier = Modifier.fillMaxWidth()) {
                        Text(if (showActivities) "收起云端个人活动"
                            else "查看云端个人活动（" + result.activities.size + " 条）")
                    }
                    if (showActivities) MySecondClassActivities(result)
                }
                SectionCard {
                    TextButton(onClick = { showScores = !showScores },
                        modifier = Modifier.fillMaxWidth()) {
                        Text(if (showScores) "收起云端素质积分" else "查看云端素质积分")
                    }
                    if (showScores) SecondClassScores(result)
                }
            }
        }
    }
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
    }
}

private fun schoolActivityStatus(value: String?): String = when (value) {
    "registered" -> "已报名"
    "completed" -> "已完成"
    "cancelled" -> "已取消"
    else -> "活动状态未核实"
}

private fun schoolReviewStatus(value: String?): String = when (value) {
    "pending" -> "待审核"
    "approved" -> "已通过"
    "rejected" -> "未通过"
    "attended" -> "已参与"
    else -> "审核状态未核实"
}

@Composable
private fun MySecondClassActivities(result: SecondClassSnapshot) {
    var filter by rememberSaveable { mutableStateOf("all") }
    val filters = listOf("all" to "全部", "registered" to "已报名", "completed" to "已完成")
    val rows = result.activities.filter { activity ->
        filter == "all" || activity.activityStatus == filter
    }
    Text("个人活动记录 " + result.activities.size + " 条",
        style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        filters.forEach { (key, label) ->
            FilterChip(selected = filter == key, onClick = { filter = key },
                label = { Text(label) })
        }
    }
    if (rows.isEmpty()) {
        SectionCard {
            Text(if (result.activities.isEmpty()) "本次快照没有个人活动记录"
                else "当前筛选条件下没有记录")
            Text("列表仅来自本人已授权的记录，不代表学校没有可报名活动。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall)
        }
    }
    rows.take(60).forEach { activity: SecondClassActivity ->
        SectionCard {
            Text(activity.title, fontWeight = FontWeight.SemiBold)
            activity.date?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text(schoolActivityStatus(activity.activityStatus) +
                " · " + schoolReviewStatus(activity.reviewStatus),
                style = MaterialTheme.typography.bodySmall)
            Text("记录分数：" + (activity.points?.toString() ?: "未知") +
                " · 记录时长：" + (activity.hours?.toString() ?: "未知"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (rows.size > 60) {
        Text("当前仅展示前60条；可通过上方筛选查看其他活动。",
            style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SecondClassScores(result: SecondClassSnapshot) {
    val trusted = result.provenance == "trusted_readonly"
    val official = result.officialScore
    val personal = result.personalScore
    SectionCard {
        when {
            trusted && official != null -> {
                Text("学校官方汇总", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
                Text("素质分：" + (official.qualityScore?.toString() ?: "未公布"))
                Text("志愿服务时长：" +
                    (official.volunteerHours?.toString() ?: "未公布"))
                Text("官方汇总不与逐条活动分数相加，避免重复计算。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            !trusted && personal != null -> {
                Text("本人导入的个人汇总（非学校独立认证）",
                    fontWeight = FontWeight.SemiBold)
                Text("素质分：" + (personal.qualityScore?.toString() ?: "未知"))
                Text("志愿服务时长：" +
                    (personal.volunteerHours?.toString() ?: "未知"))
            }
            else -> {
                Text("暂无可核验的学校官方汇总分数。")
                Text("个人活动列表的逐条分数不等于学校官方已结算总分。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
