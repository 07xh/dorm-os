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
 * Manual, per-course read of the EXISTING owner-scoped server adapter.
 * Never imitates successful upstream reads and never auto-submits coursework.
 */
@Composable
fun ChaoxingHomeworkPanel(client: CloudClient, openWebsite: () -> Unit) {
    val state = client.state
    val scope = rememberCoroutineScope()
    var selecting by remember { mutableStateOf(false) }
    val chosen = state.chaoxingCourses.firstOrNull { it.key == state.chaoxingSelectedKey }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("学习通 · 本人课程与作业", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold)
            Text(
                "使用校序现有的学习通可信会话。先读取本人课程，再按课程主动同步作业，不会代交或自动完成。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!state.authenticated) {
                Text("请先在「我的」连接校序，再通过官方学习通完成学校账号授权。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Button(
                    enabled = !state.chaoxingBusy,
                    onClick = { scope.launch { client.loadChaoxingCourses() } }
                ) {
                    Text(if (state.chaoxingBusy) "正在读取…" else
                        if (state.chaoxingCourses.isEmpty()) "读取本人课程" else "重新读取课程")
                }
                if (state.chaoxingCourses.isNotEmpty()) {
                    Text("已从官方会话读取到 ${state.chaoxingCourses.size} 门课程（本次会话）",
                        style = MaterialTheme.typography.bodySmall,
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
            TextButton(onClick = openWebsite) { Text("前往现有数据连接中心") }
        }
    }
}
