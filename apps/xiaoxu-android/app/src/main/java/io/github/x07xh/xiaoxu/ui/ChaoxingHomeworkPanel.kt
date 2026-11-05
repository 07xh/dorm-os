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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.x07xh.xiaoxu.data.CloudClient
import kotlinx.coroutines.launch

/**
 * On-demand read plus explicit opt-in to the EXISTING hourly server sync.
 * Never imitates successful upstream reads and never auto-submits coursework.
 */
@Composable
fun ChaoxingHomeworkPanel(client: CloudClient, openSchoolReader: () -> Unit) {
    val state = client.state
    val scope = rememberCoroutineScope()
    var selecting by remember { mutableStateOf(false) }
    val chosen = state.chaoxingCourses.firstOrNull { it.key == state.chaoxingSelectedKey }
    val connection = state.platforms.firstOrNull { it.id == "chaoxing" }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("学习通 · 本人课程与作业", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold)
            Text(
                "优先在 App 内打开官方学习通读取本人可见课程／作业；云端逐门核验与自动同步属于独立可选功能，不会作答或提交。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = openSchoolReader) {
                Text("在 App 内读取学习通作业")
            }
            if (!state.authenticated) {
                Text("本机读取可直接使用上方入口；若需云端逐门核验，须另外连接本人校序会话和学校授权。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Button(
                    enabled = !state.chaoxingBusy,
                    onClick = { scope.launch { client.loadChaoxingCourses() } }
                ) {
                    Text(if (state.chaoxingBusy) "正在读取…" else
                        if (state.chaoxingCourses.isEmpty()) "读取本人课程" else "重新读取课程")
                }
                if (connection != null) {
                    if (connection.autoSyncAvailable || connection.autoSyncEnabled) {
                        OutlinedButton(
                            enabled = !state.busy && !state.chaoxingAutoSyncBusy &&
                                (connection.autoSyncEnabled || connection.sessionState == "active"),
                            onClick = {
                                scope.launch {
                                    client.setChaoxingAutoSync(!connection.autoSyncEnabled)
                                }
                            }
                        ) {
                            Text(when {
                                state.chaoxingAutoSyncBusy -> "正在更新定时设置…"
                                connection.autoSyncEnabled -> "关闭每小时自动同步"
                                else -> "开启每小时自动同步"
                            })
                        }
                        Text(
                            when {
                                connection.autoSyncEnabled && !connection.autoSyncAvailable ->
                                    "定时 Worker 未确认可用，原计划可能暂停；你仍可以关闭它。"
                                connection.autoSyncEnabled ->
                                    "定时任务已登记；仍需验证服务端实际运行与学校数据更新。"
                                else -> "默认关闭。需要有效的本人学习通会话才能开启。"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        connection.autoSyncLastRunAt?.let { lastRun ->
                            val status = when (connection.autoSyncLastResult) {
                                "success" -> "上次检查成功"
                                "partial" -> "上次部分课程成功"
                                "failed" -> "上次检查失败"
                                "expired" -> "会话已过期，需重新授权"
                                else -> "状态待核实"
                            }
                            Text(
                                "$status · $lastRun",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (connection.autoSyncEnabled && connection.autoSyncNextAt != null) {
                            Text("服务端下次计划检查：" + connection.autoSyncNextAt,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        Text("云端定时任务尚未确认可用；仍可手动检查作业。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                state.chaoxingAutoSyncError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
                if (state.chaoxingCourses.isNotEmpty()) {
                    Text("已从官方会话读取到 ${state.chaoxingCourses.size} 门课程（本次会话）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(
                        enabled = !state.chaoxingBusy && !state.chaoxingDetailBusy &&
                            !state.busy && connection?.sessionState == "active",
                        onClick = { scope.launch { client.syncChaoxingAllCourses() } }
                    ) {
                        Text(if (state.chaoxingBatchBusy) "正在逐门核验…" else
                            "一键核验全部课程作业")
                    }
                    Text("逐门调用现有可信只读接口；登录失效或连续失败时停止，未完成的不算成功。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column {
                        OutlinedButton(
                            enabled = !state.chaoxingBusy,
                            onClick = { selecting = true }
                        ) { Text("选择课程：${chosen?.title ?: "请选择"}") }
                        DropdownMenu(expanded = selecting, onDismissRequest = { selecting = false }) {
                            state.chaoxingCourses.forEach { course ->
                                DropdownMenuItem(
                                    text = { Text(course.title) },
                                    onClick = {
                                        selecting = false
                                        client.chooseChaoxingCourse(course.key)
                                    }
                                )
                            }
                        }
                    }
                    if (chosen != null) {
                        OutlinedButton(
                            enabled = !state.chaoxingBusy,
                            onClick = { scope.launch { client.syncChaoxingCourse() } }
                        ) { Text("同步本课程真实作业及状态") }
                    }
                }
            }
            state.chaoxingBatchStatus?.let { progress ->
                Text(progress, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.chaoxingError?.let {
                Text(it, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium)
            }
            if (state.chaoxingCheckedAt != null) {
                Text("本次官方读取：${state.chaoxingCheckedAt} · 仅当前所选课程",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (state.chaoxingVerifiedEmpty && state.chaoxingHomework.isEmpty()) {
                    Text("官方确认：本课程当前暂无作业")
                }
                state.chaoxingHomework.take(50).forEach { task ->
                    val label = when(task.statusCode) {
                        "todo" -> "官方：未提交"
                        "submitted" -> "官方：已提交 / 待批阅"
                        "graded" -> "官方：已批阅"
                        "returned" -> "官方：已退回"
                        else -> "官方状态未知"
                    }
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(task.title, fontWeight = FontWeight.Medium)
                        Text(label, style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(
                            enabled = !state.chaoxingBusy && !state.chaoxingDetailBusy,
                            onClick = { scope.launch { client.loadChaoxingWorkDetail(task.workId) } }
                        ) { Text("只读查看作业内容") }
                    }
                }
                if (state.chaoxingDetailBusy) {
                    Text("正在从本人官方会话读取作业题目…",
                        style = MaterialTheme.typography.bodySmall)
                }
                state.chaoxingDetailError?.let { error ->
                    Text(error, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
                state.chaoxingPreview?.let { preview ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("只读题目 · " + preview.title,
                                fontWeight = FontWeight.SemiBold)
                            Text("来源：" + preview.course + " · 仅只读预览，不可填写或提交",
                                style = MaterialTheme.typography.labelSmall)
                            if (preview.startTimeRaw != null && preview.deadlineRaw != null) {
                                Text("官网作答时间：" + preview.startTimeRaw + " 至 " + preview.deadlineRaw +
                                    "（仅月日，年份未核实；不作为逾期判定或提醒依据）",
                                    style = MaterialTheme.typography.bodySmall)
                            } else {
                                Text("当前官网详情页没有可核实的截止时间",
                                    style = MaterialTheme.typography.labelSmall)
                            }
                            if (!preview.questionsAvailable) {
                                Text(if (preview.note.isNotBlank()) preview.note
                                    else "本页未提供可读取的题目，请在学习通官方页面查看")
                            } else {
                                Text("当前页面读取 " + preview.questions.size + " 题" +
                                    (preview.declaredQuestionCount?.let { " · 页面声明 $it 题" } ?: ""),
                                    style = MaterialTheme.typography.labelSmall)
                                preview.questions.take(20).forEach { question ->
                                    Text("第" + question.number + "题：" + question.stem,
                                        style = MaterialTheme.typography.bodyMedium)
                                    question.options.forEach { option ->
                                        Text(option, style = MaterialTheme.typography.bodySmall)
                                    }
                                    if (question.hasImage) {
                                        Text("包含图片，请在学习通官方页面查看原图",
                                            style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                if (preview.questions.size > 20) {
                                    Text("当前仅展示前20题，全部题目请在官方学习通查看",
                                        style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            TextButton(onClick = { client.clearChaoxingDetail() }) {
                                Text("收起详情")
                            }
                        }
                    }
                }
                if (state.chaoxingHomework.size > 50) {
                    Text("此处仅展示前 50 份；其他记录请在校序任务列表查看。",
                        style = MaterialTheme.typography.labelSmall)
                }
            } else if (state.chaoxingCourses.isNotEmpty() && state.chaoxingError == null) {
                Text("尚未同步此课程作业；不根据缓存判断提交状态。",
                    style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = openSchoolReader) { Text("打开学习通官网核对本机读取结果") }
        }
    }
}
