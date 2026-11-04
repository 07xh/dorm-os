package io.github.x07xh.xiaoxu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.x07xh.xiaoxu.data.CloudClient
import kotlinx.coroutines.launch

/** Existing first-party API; school sessions are not transferred to Android. */
@Composable
fun CloudTasksPanel(client: CloudClient) {
    val state = client.state
    val scope = rememberCoroutineScope()
    var title by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("校序云端任务", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold)
        if (!state.authenticated) {
            Text("连接校序云端后显示已有任务。本机任务不受影响。",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it.take(120) },
                        label = { Text("新增云端任务") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        enabled = title.isNotBlank() && !state.busy,
                        onClick = {
                            val value = title
                            scope.launch { if (client.addCloudTask(value)) title = "" }
                        }
                    ) { Text("保存至云端") }
                }
            }
            if (state.tasks.isEmpty()) {
                Text("云端暂无可展示任务。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.tasks.take(50).forEach { task ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(task.title, fontWeight = FontWeight.Medium)
                        val source = when (task.provider) {
                            "chaoxing" -> "学习通来源（不代表已核验完成状态）"
                            "ncwu" -> "青果来源"
                            "second_class" -> "第二课堂来源"
                            else -> "校序个人任务"
                        }
                        Text(source + " · " + when (task.status) {
                            "done" -> "个人标记已完成"
                            "in_progress" -> "进行中"
                            else -> "待办"
                        }, style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!task.due.isNullOrBlank()) {
                            Text("截止：" + task.due,
                                style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
        if (state.privateError != null) {
            Text(state.privateError, color = MaterialTheme.colorScheme.error)
        }
        Text("本机任务与云端任务分开显示，不自动合并，避免重复或误覆盖。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ConnectionsPanel(client: CloudClient, openWebsite: () -> Unit) {
    val state = client.state
    val scope = rememberCoroutineScope()
    // Never save the passphrase across configuration changes.
    var passphrase by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("校序云端", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold)
        Text(state.authMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!state.authenticated) {
            OutlinedTextField(
                value = passphrase, onValueChange = { passphrase = it.take(256) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("校序个人口令（不是学校密码）") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true
            )
            Button(enabled = passphrase.isNotBlank() && !state.signingIn,
                onClick = {
                    val submitted = passphrase
                    passphrase = ""
                    scope.launch { client.signIn(submitted) }
                }) { Text(if (state.signingIn) "正在连接" else "连接校序云端") }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { scope.launch { client.refresh() } }, enabled = !state.busy) {
                    Text("刷新云端数据")
                }
                TextButton(onClick = { scope.launch { client.signOut() } }) { Text("退出云端") }
            }
            if (state.lastSyncAt != null) {
                Text("最近成功同步：" + state.lastSyncAt,
                    style = MaterialTheme.typography.labelSmall)
            }
        }
        TextButton(onClick = openWebsite) {
            Text("打开现有数据连接中心（官方登录与诊断）")
        }
        HorizontalDivider()
        Text("学校平台连接状态", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold)
        val providers = listOf(
            "chaoxing" to "学习通",
            "ncwu" to "青果教务",
            "second_class" to "第二课堂"
        )
        providers.forEach { (id, title) ->
            val platform = state.platforms.firstOrNull { it.id == id }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, fontWeight = FontWeight.SemiBold)
                    val status = if (!state.authenticated) "未读取校序云端连接状态"
                        else if (platform == null) "连接状态未读取"
                        else if (platform.verified) "已验证会话（学校数据仍需单独比对）"
                        else "未通过本人数据连接验收"
                    Text(status, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (platform?.lastSuccessAt != null) {
                        Text("上次记录：" + platform.lastSuccessAt,
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        if (state.privateError != null) {
            Text(state.privateError, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
fun NoticesPanel(client: CloudClient, openArticle: (String) -> Unit) {
    val state = client.state
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("华水官方通知", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold)
        Text("学校公开教务公告与考试安排。标题点击后打开学校官网。",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.publicError != null) {
            Text(state.publicError, color = MaterialTheme.colorScheme.error)
        }
        if (state.noticesPartial) {
            Text("部分官方栏目读取失败，列表可能不完整。",
                color = MaterialTheme.colorScheme.error)
        }
        if (state.noticeFetchedAt != null) {
            Text("服务端抓取时间：" + state.noticeFetchedAt,
                style = MaterialTheme.typography.labelSmall)
        }
        if (state.notices.isEmpty()) {
            Text("暂无已成功读取的公告，点击顶部刷新重试。")
        }
        state.notices.take(30).forEach { notice ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(12.dp)) {
                    TextButton(onClick = { openArticle(notice.url) }) {
                        Text(notice.title, modifier = Modifier.fillMaxWidth())
                    }
                    Text((if (notice.category == "exam") "考试安排" else "通知公告") +
                        " · " + notice.dateLabel + "（年份未核验）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun WechatCandidatesPanel(client: CloudClient, openWebsite: () -> Unit) {
    val state = client.state
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("微信待审核", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold)
        val count = state.pendingWechatCount
        Text(when {
            !state.authenticated -> "连接校序云端后可读取候选任务。"
            count == null -> "尚未成功读取候选任务。"
            count >= 30 -> "目前读取到至少30条待审核候选（只查询前30条）。"
            else -> "待审核候选：" + count + " 条"
        }, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("继续沿用原有电脑采集器，候选必须在云端审核页面人工确认。",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = openWebsite) { Text("进入原有审核界面") }
    }
}


/** No placeholder classes. A trusted batch must exist before anything renders. */
@Composable
fun CloudTimetablePanel(client: CloudClient, openWebsite: () -> Unit) {
    val state = client.state
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val status = state.scheduleStatus
        if (!state.authenticated) {
            Text("先在「我的」连接校序云端，再读取已经验证的青果课表。",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (status != "ready" && status != "stale") {
            Text(if (status == "not_connected") "尚未读取到可信的青果课程数据。"
                else "课表来源尚未通过可信验证，不展示导入或猜测结果。",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            if (status == "stale") {
                Text("这是上次成功读取的课表，尚未确认近期是否发生调整。",
                    color = MaterialTheme.colorScheme.error)
            }
            if (state.scheduleFetchedAt != null) {
                Text("来源：华水青果 · 上次可信读取：" + state.scheduleFetchedAt,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.lessons.isEmpty()) {
                Text("可信来源返回空课程，请在教务系统核对是否为当前学期。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val dayNames = listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")
            state.lessons.groupBy { it.weekday }.toSortedMap().forEach { (day, lessons) ->
                Text(dayNames.getOrElse(day - 1) { "未知星期" },
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium)
                lessons.sortedBy { it.startSection }.forEach { lesson ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(lesson.title, fontWeight = FontWeight.SemiBold)
                            Text("第" + lesson.startSection + "–" + lesson.endSection + "节 · " +
                                lesson.teacher + " · " + lesson.location,
                                style = MaterialTheme.typography.bodyMedium)
                            Text("学期：" + lesson.term + " · 周次：" + lesson.weeks.joinToString("、"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Text("按完整学期展示，不根据未知开学日期猜测当前周。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = openWebsite) { Text("查看官方连接状态或重新授权") }
    }
}
