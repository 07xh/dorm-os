package io.github.x07xh.xiaoxu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.x07xh.xiaoxu.data.CloudClient
import kotlinx.coroutines.launch

/**
 * Native read-only view over the EXISTING /api/v1/second-class snapshot.
 * Imported/user-entered totals are never labelled as official school scores.
 */
@Composable
fun SecondClassPanel(client: CloudClient, openWebsite: () -> Unit) {
    val state = client.state
    val scope = rememberCoroutineScope()
    val result = state.secondClass
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("第二课堂 · 活动与积分",
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text("复用校序现有的本人只读数据，不重新登录或采集学校账号。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!state.authenticated) {
            Text("连接校序云端后才能查看本人第二课堂记录。",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Button(
                enabled = !state.secondClassBusy && !state.busy,
                onClick = { scope.launch { client.loadSecondClass() } }
            ) {
                Text(if (state.secondClassBusy) "正在读取…" else "刷新活动与积分")
            }
        }
        state.secondClassError?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        if (result == null) {
            Text(if (state.authenticated) "未读取到第二课堂个人数据。" else "云端尚未连接。",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (result.status == "not_connected") {
            Text("尚无第二课堂个人数据。请先完成官方登录和可信读取。",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val trusted = result.provenance == "trusted_readonly"
            val source = if (trusted) "服务端已验证只读来源"
                else "用户自行导入的数据（非官方实时同步）"
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(source, fontWeight = FontWeight.SemiBold)
                    if (result.status == "stale") {
                        Text("数据已超过36小时，仅供参考，不代表学校当前记录。",
                            color = MaterialTheme.colorScheme.error)
                    }
                    Text("上次数据时间：" + (result.lastSuccessAt ?: "未知"),
                        style = MaterialTheme.typography.bodySmall)
                    Text("活动记录：" + result.activities.size + " 条",
                        style = MaterialTheme.typography.bodyMedium)

                    val official = result.officialScore
                    if (trusted && official != null) {
                        Text("学校官方汇总", fontWeight = FontWeight.SemiBold)
                        Text("素质分：" + (official.qualityScore?.toString() ?: "未公布"))
                        Text("志愿服务时长：" +
                            (official.volunteerHours?.toString() ?: "未公布"))
                        Text("官方汇总不与逐条活动分数相加，避免重复计算。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else if (!trusted && result.personalScore != null) {
                        Text("用户导入的个人汇总（未获学校独立证明）",
                            fontWeight = FontWeight.SemiBold)
                        Text("素质分：" +
                            (result.personalScore.qualityScore?.toString() ?: "未知"))
                        Text("志愿服务时长：" +
                            (result.personalScore.volunteerHours?.toString() ?: "未知"))
                    } else {
                        Text("暂无可核验的学校官方汇总分数。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (result.activities.isEmpty()) {
                Text("此数据批次无可显示的活动记录；请核实官方页面。",
                    style = MaterialTheme.typography.bodySmall)
            }
            result.activities.take(60).forEach { activity ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(activity.title, fontWeight = FontWeight.SemiBold)
                        Text(listOfNotNull(
                            activity.date?.takeIf { it.isNotBlank() },
                            activity.activityStatus?.let { "活动：$it" },
                            activity.reviewStatus?.let { "审核：$it" }
                        ).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                        Text(
                            "活动记录：素质分 " +
                                (activity.points?.toString() ?: "未知") +
                                " · 志愿时长 " +
                                (activity.hours?.toString() ?: "未知"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (result.activities.size > 60) {
                Text("仅展示前60条；完整记录请在校序网站查看。",
                    style = MaterialTheme.typography.labelSmall)
            }
        }
        TextButton(onClick = openWebsite) { Text("打开现有第二课堂连接与诊断") }
    }
}
