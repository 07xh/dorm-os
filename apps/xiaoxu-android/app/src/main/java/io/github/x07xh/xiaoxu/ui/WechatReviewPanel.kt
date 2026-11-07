package io.github.x07xh.xiaoxu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
 *
 * Issue #275 只做候选卡的视觉收敛（图标块 + 类型徽标 + 一张一行），
 * 逐条审核事务、二次确认与后端调用保持原样，一个字节都没改。
 */
private fun wechatKindLabel(kind: String): String = when (kind) {
    "homework" -> "作业候选"
    "exam" -> "考试候选"
    "activity" -> "活动候选"
    "signup" -> "报名候选"
    else -> "其他候选"
}

private fun wechatKindIcon(kind: String): ImageVector = when (kind) {
    "homework" -> Icons.AutoMirrored.Outlined.MenuBook
    "exam" -> Icons.Outlined.DateRange
    "activity" -> Icons.Outlined.EmojiEvents
    "signup" -> Icons.Outlined.CheckCircle
    else -> Icons.Outlined.Info
}

private fun wechatKindTone(kind: String): XiaoxuTone = when (kind) {
    "homework" -> XiaoxuTone.Brand
    "exam" -> XiaoxuTone.Warning
    "activity" -> XiaoxuTone.Success
    "signup" -> XiaoxuTone.Success
    else -> XiaoxuTone.Neutral
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

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "逐条审核",
                    style = MaterialTheme.typography.titleMedium,
                    color = XiaoxuPalette.Ink,
                    modifier = Modifier.weight(1f)
                )
                // 只在真有候选可刷新且已连接时给出刷新，避免无意义按钮。
                if (state.authenticated) {
                    Box(
                        modifier = Modifier
                            .heightIn(min = 44.dp)
                            .clip(RoundedCornerShape(XiaoxuRadius.pill))
                            .background(XiaoxuPalette.SurfaceAlt)
                            .clickable(
                                enabled = !state.busy && !reviewing,
                                onClick = { scope.launch { client.refreshPrivate() } },
                                role = Role.Button
                            )
                            .padding(horizontal = 12.dp)
                            .semantics { contentDescription = "刷新微信待审核候选" },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = XiaoxuPalette.Ink2
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = "刷新",
                                style = MaterialTheme.typography.labelMedium,
                                color = XiaoxuPalette.Ink2
                            )
                        }
                    }
                }
            }
            Text(
                text = pendingWechatLabel(pendingCount, state.authenticated, state.wechatError),
                style = MaterialTheme.typography.labelMedium,
                color = if (pendingCount == null && state.authenticated)
                    XiaoxuPalette.Warning else XiaoxuPalette.Ink2
            )
            if (!state.authenticated) {
                Text(
                    text = "请先在「我的」连接校序云端。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = XiaoxuPalette.Ink2
                )
            } else {
                if (state.wechatError != null) {
                    Text(
                        text = state.wechatError,
                        style = MaterialTheme.typography.bodySmall,
                        color = XiaoxuPalette.Danger
                    )
                }
                if (pendingCount == null) {
                    Text(
                        text = "还未取得可信的候选列表，请点击刷新。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = XiaoxuPalette.Ink2
                    )
                } else if (state.wechatCandidates.isEmpty() && state.wechatError == null) {
                    Text(
                        text = "暂无待审核候选。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = XiaoxuPalette.Ink3
                    )
                }
                // Show the actual server response only: no sample chats or guessed messages.
                state.wechatCandidates.forEach { item ->
                    WechatCandidateRow(
                        item = item,
                        enabled = !reviewing,
                        onClick = { selectedId = item.id }
                    )
                }
                if (pendingCount != null && pendingCount >= 30) {
                    Text(
                        text = "接口每次最多返回 30 条，审核后刷新即可加载后续候选。",
                        style = MaterialTheme.typography.labelSmall,
                        color = XiaoxuPalette.Ink3
                    )
                }
            }
            HorizontalDivider(color = XiaoxuPalette.Line)
            Text(
                text = "来自已绑定电脑 · 确认后加入云端任务",
                style = MaterialTheme.typography.labelSmall,
                color = XiaoxuPalette.Ink3
            )
            Box(
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(XiaoxuRadius.pill))
                    .clickable(onClick = openWebsite, role = Role.Button)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "网页审核备用",
                    style = MaterialTheme.typography.labelLarge,
                    color = XiaoxuPalette.Brand
                )
            }
    }

    if (selected != null && state.authenticated) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { if (!reviewing) selectedId = null },
            sheetState = sheet,
            containerColor = XiaoxuPalette.Surface,
            scrimColor = XiaoxuPalette.ModalScrim
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "核对微信候选",
                    style = MaterialTheme.typography.titleLarge,
                    color = XiaoxuPalette.Ink
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SourceBadge(
                        text = wechatKindLabel(selected.kind),
                        tone = wechatKindTone(selected.kind)
                    )
                    Spacer(Modifier.width(8.dp))
                    SourceBadge(text = "本人云端", tone = XiaoxuTone.Neutral)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(XiaoxuPalette.SurfaceAlt)
                        .padding(16.dp)
                ) {
                    Text(
                        text = selected.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = XiaoxuPalette.Ink,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                ReviewMeta("来源群聊", selected.chatLabel ?: "未标注")
                ReviewMeta("消息时间", sentTime(selected.sentAt) + "（北京时间）")
                ReviewMeta("候选截止", dueLabel(selected))
                Text(
                    text = "电脑端提取的候选摘要",
                    style = MaterialTheme.typography.labelMedium,
                    color = XiaoxuPalette.Ink3
                )
                Text(
                    text = selected.excerpt ?: "本条候选没有摘要；请前往微信核实原消息。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = XiaoxuPalette.Ink
                )
                // Links belong to the same owner-scoped candidate, not to a new page.
                WechatTaskLinkActions(selected.links)
                HorizontalDivider(color = XiaoxuPalette.Line)
                Text(
                    text = "候选摘要 · 请核对原消息后确认",
                    color = XiaoxuPalette.Ink2,
                    style = MaterialTheme.typography.bodySmall
                )
                state.wechatError?.let {
                    Text(
                        text = it,
                        color = XiaoxuPalette.Danger,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionBox(
                        label = "忽略",
                        filled = false,
                        enabled = !reviewing,
                        modifier = Modifier.weight(1f)
                    ) { pendingDecision = "dismiss" }
                    ActionBox(
                        label = if (reviewing) "正在提交" else "加入任务",
                        filled = true,
                        enabled = !reviewing,
                        modifier = Modifier.weight(1f),
                        icon = Icons.Outlined.CheckCircle
                    ) { pendingDecision = "accept" }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(enabled = !reviewing, onClick = { selectedId = null }, role = Role.Button),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "稍后再审核",
                        style = MaterialTheme.typography.labelLarge,
                        color = XiaoxuPalette.Ink2
                    )
                }
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

/** 候选行：类型图标块 + 标题 + 群聊/时间 + 截止，右侧箭头。整行可点开详情。 */
@Composable
private fun WechatCandidateRow(
    item: WechatCandidate,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val tone = wechatKindTone(item.kind)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(XiaoxuRadius.card))
            .background(XiaoxuPalette.Surface)
            .border(1.dp, XiaoxuPalette.Line, RoundedCornerShape(XiaoxuRadius.card))
            .clickable(enabled = enabled, onClick = onClick, role = Role.Button)
            .padding(horizontal = 13.dp, vertical = 13.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = wechatKindLabel(item.kind) + "：" + item.title +
                    "。打开可逐条核对，不会自动加入任务"
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(tone.containerColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = wechatKindIcon(item.kind),
                contentDescription = null,
                modifier = Modifier.size(17.dp),
                tint = tone.contentColor
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                color = XiaoxuPalette.Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = (item.chatLabel ?: "群聊未标注") + " · " + wechatKindLabel(item.kind) +
                    " · " + sentTime(item.sentAt),
                style = MaterialTheme.typography.labelSmall,
                color = XiaoxuPalette.Ink3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = dueLabel(item),
                style = MaterialTheme.typography.labelSmall,
                color = tone.contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(6.dp))
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = XiaoxuPalette.Ink3
        )
    }
}

/** 抽屉内的两枚动作：主按钮实心、次按钮描边，高度均为 48dp。 */
@Composable
private fun ActionBox(
    label: String,
    filled: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    val container = if (filled) XiaoxuPalette.Brand else Color.Transparent
    val ink = if (filled) Color.White else XiaoxuPalette.Ink2
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) container else container.copy(alpha = 0.5f))
            .border(
                width = if (filled) 0.dp else 1.dp,
                color = XiaoxuPalette.Line,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(enabled = enabled, onClick = onClick, role = Role.Button),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = ink
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = ink,
                fontWeight = if (filled) FontWeight.SemiBold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ReviewMeta(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = XiaoxuPalette.Ink3,
            modifier = Modifier.width(72.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = XiaoxuPalette.Ink,
            modifier = Modifier.weight(1f)
        )
    }
}
