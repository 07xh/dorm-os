package io.github.x07xh.xiaoxu.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.x07xh.xiaoxu.data.CloudClient
import io.github.x07xh.xiaoxu.data.WechatCandidate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

/**
 * Native review of existing D1 candidate records.
 * A candidate is an extracted hint from the paired computer, not a full
 * WeChat conversation or officially verified school assignment.
 * The owner session / backend review transaction remain authoritative.
 */
private fun wechatKindLabel(kind: String): String = when (kind) {
    "homework" -> "作业候选"
    "exam" -> "考试候选"
    "activity" -> "活动候选"
    "signup" -> "报名候选"
    else -> "其他候选"
}

private val wechatTimestamp = DateTimeFormatter.ofPattern("MM-dd HH:mm")
    .withZone(ZoneId.of("Asia/Shanghai"))

private fun sentTime(value: String): String =
    runCatching { wechatTimestamp.format(Instant.parse(value)) }.getOrDefault("时间未知")

private fun dueLabel(item: WechatCandidate): String = when (item.duePrecision) {
    "date" -> "候选日期：${item.dueLocalDate ?: "未识别"}"
    "exact" -> "候选截止日期：${item.dueLocalDate ?: "未识别"}"
    else -> "未识别截止日期"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WechatReviewPanel(client: CloudClient, pendingCount: Int?, openWebsite: () -> Unit) {
    val state = client.state
    val scope = rememberCoroutineScope()
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDecision by remember { mutableStateOf<String?>(null) }
    val selected = state.wechatCandidates.firstOrNull { it.id == selectedId }
    val reviewing = state.wechatReviewingId != null

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("微信待审核", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
                Text(
                    pendingWechatLabel(pendingCount, state.authenticated),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                "来自已绑定电脑的消息候选，仅在本人确认后加入云端任务；不等于学校官方作业。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!state.authenticated) {
                Text("请先在「我的」连接校序云端，再使用 App 审核。",
                    style = MaterialTheme.typography.bodyMedium)
            } else {
                if (state.wechatError != null) {
                    Text(state.wechatError, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                }
                if (pendingCount == null) {
                    Text("还未取得可信的候选列表，请点击刷新。",
                        style = MaterialTheme.typography.bodyMedium)
                } else if (state.wechatCandidates.isEmpty() && state.wechatError == null) {
                    Text("暂无待审核候选。",
                        style = MaterialTheme.typography.bodyMedium)
                }
                // Show the actual server response only: no sample chats or guessed messages.
                state.wechatCandidates.forEach { item ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable(enabled = !reviewing) { selectedId = item.id }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.Medium,
                                style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${item.chatLabel ?: "群聊未标注"} · ${wechatKindLabel(item.kind)} · ${sentTime(item.sentAt)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(dueLabel(item), style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                        Icon(Icons.Outlined.ChevronRight, contentDescription = "查看候选详情")
                    }
                }
                if (pendingCount != null && pendingCount >= 30) {
                    Text("接口每次最多返回 30 条，审核后刷新即可加载后续候选。",
                        style = MaterialTheme.typography.labelSmall)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { scope.launch { client.refreshPrivate() } },
                    enabled = state.authenticated && !state.busy && !reviewing,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null)
                    Spacer(Modifier.width(5.dp))
                    Text("刷新候选")
                }
                TextButton(onClick = openWebsite, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("网页审核备用")
                }
            }
        }
    }

    if (selected != null && state.authenticated) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { if (!reviewing) selectedId = null },
            sheetState = sheet
        ) {
            Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("核对微信候选", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold)
                Text(wechatKindLabel(selected.kind),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium)
                Text(selected.title, style = MaterialTheme.typography.titleMedium)
                ReviewMeta("来源群聊", selected.chatLabel ?: "未标注")
                ReviewMeta("消息时间", sentTime(selected.sentAt) + "（北京时间）")
                ReviewMeta("候选截止", dueLabel(selected))
                Text("电脑端提取的候选摘要", fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.bodyMedium)
                Text(selected.excerpt ?: "本条候选没有摘要；请前往微信核实原消息。",
                    style = MaterialTheme.typography.bodyMedium)
                Text(
                    "这里不是完整微信聊天原文。请核对原消息，必要时返回微信确认；审核通过仅创建普通云端任务，不会自动提交作业。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                state.wechatError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { pendingDecision = "dismiss" },
                        enabled = !reviewing, modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) { Text("忽略") }
                    Button(
                        onClick = { pendingDecision = "accept" },
                        enabled = !reviewing, modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                        Spacer(Modifier.width(5.dp))
                        Text(if (reviewing) "正在提交" else "加入任务")
                    }
                }
                TextButton(
                    onClick = { selectedId = null },
                    enabled = !reviewing,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) { Text("稍后再审核") }
            }
        }
    }

    if (selected != null && pendingDecision != null) {
        val decision = pendingDecision!!
        AlertDialog(
            onDismissRequest = { if (!reviewing) pendingDecision = null },
            title = { Text(if (decision == "accept") "确认加入任务？" else "确认忽略候选？") },
            text = {
                Text(if (decision == "accept")
                    "将候选创建为一条普通云端任务，并标记为人工审核通过；不代表学校官方确认。"
                    else "将这条候选标记为已忽略，之后不再出现在待审核列表。")
            },
            confirmButton = {
                TextButton(
                    enabled = !reviewing,
                    onClick = {
                        pendingDecision = null
                        scope.launch {
                            val ok = client.reviewWechatCandidate(selected.id, decision)
                            if (ok) selectedId = null
                        }
                    }
                ) { Text("确认") }
            },
            dismissButton = {
                TextButton(
                    enabled = !reviewing,
                    onClick = { pendingDecision = null }
                ) { Text("取消") }
            }
        )
    }
}

@Composable
private fun ReviewMeta(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(72.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}
