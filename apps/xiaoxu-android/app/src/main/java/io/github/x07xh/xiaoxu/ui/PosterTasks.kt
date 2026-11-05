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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 海报版任务中心：「任务集中管理」——自动提取 / 人工审核 / 截止提醒。 */
@Composable
fun PosterTasks(demo: Boolean, modifier: Modifier = Modifier, onAdded: () -> Unit = {}) {
    var tab by remember { mutableStateOf("待完成") }
    var ignored by remember { mutableStateOf(setOf<String>()) }
    val candidates = PosterDemo.candidates.filter { it.group !in ignored }

    PosterPage(modifier.verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(PosterDemo.date, fontSize = 11.sp, color = PosterPalette.Ink2, modifier = Modifier.weight(1f))
            Box(Modifier.size(32.dp).clip(CircleShape).background(PosterPalette.Surface),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Notifications, contentDescription = "通知",
                    tint = PosterPalette.Ink, modifier = Modifier.size(17.dp))
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("任务中心", fontSize = 24.sp, fontWeight = FontWeight.Bold,
                color = PosterPalette.Ink, modifier = Modifier.weight(1f))
            if (demo) PosterDemoBadge()
        }
        Text("汇集课程与群消息任务 · 进度清晰可控", fontSize = 11.sp, color = PosterPalette.Ink2)

        // 三枚状态 tab 卡。
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PosterTaskTab("待完成", "5", tab == "待完成", Modifier.weight(1f)) { tab = "待完成" }
            PosterTaskTab("本周截止", "3", tab == "本周截止", Modifier.weight(1f)) { tab = "本周截止" }
            PosterTaskTab("已完成", "✓", tab == "已完成", Modifier.weight(1f)) { tab = "已完成" }
        }

        if (tab == "已完成") {
            PosterCard(padding = 20.dp) {
                Text("暂无已完成的任务", fontSize = 12.sp, color = PosterPalette.Ink3)
                Text("完成课程作业后，会在这里显示进度。", fontSize = 11.sp, color = PosterPalette.Ink3)
            }
        } else {
            val shown = if (tab == "本周截止") PosterDemo.tasks.filter { it.urgent || it.deadline.contains("3 天") }
                        else PosterDemo.tasks
            shown.forEach { task -> PosterTaskRow(task) }
        }

        // 群消息任务候选。
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(PosterIcons.Message, contentDescription = null, tint = PosterPalette.Brand, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("群消息任务候选 ${candidates.size}", fontSize = 14.sp,
                    fontWeight = FontWeight.Bold, color = PosterPalette.Ink, modifier = Modifier.weight(1f))
                Row(Modifier.height(32.dp).clickable(onClick = {
                    ignored = ignored + candidates.map { it.group }
                    onAdded()
                }), verticalAlignment = Alignment.CenterVertically) {
                    Text("一键加入全部", fontSize = 11.sp, color = PosterPalette.Brand)
                    Icon(Icons.Outlined.ChevronRight, contentDescription = null,
                        tint = PosterPalette.Brand, modifier = Modifier.size(13.dp))
                }
            }
            Text("从学习群消息中提取的可能任务，请审核后加入", fontSize = 10.sp, color = PosterPalette.Ink3)

            candidates.forEach { candidate ->
                PosterCard(padding = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.PersonOutline, contentDescription = null,
                            tint = PosterPalette.Brand, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(candidate.group, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            color = PosterPalette.Ink, modifier = Modifier.weight(1f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("(${candidate.members})", fontSize = 10.sp, color = PosterPalette.Ink3)
                        Spacer(Modifier.width(8.dp))
                        Text(candidate.time, fontSize = 10.sp, color = PosterPalette.Ink3)
                    }
                    Text(candidate.summary, fontSize = 11.sp, color = PosterPalette.Ink2)
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp))
                            .background(PosterPalette.BrandSoft).padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null,
                            tint = PosterPalette.Brand, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("提取：${candidate.extracted}", fontSize = 10.sp, color = PosterPalette.Brand,
                            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Row(horizontalArrangement = Arrangement.End) {
                        Box(
                            Modifier.clip(RoundedCornerShape(PosterRadius.pill))
                                .border(1.dp, PosterPalette.Line, RoundedCornerShape(PosterRadius.pill))
                                .clickable { ignored = ignored + candidate.group }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text("忽略", fontSize = 11.sp, color = PosterPalette.Ink2)
                        }
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier.clip(RoundedCornerShape(PosterRadius.pill))
                                .background(PosterPalette.Brand)
                                .clickable {
                                    ignored = ignored + candidate.group
                                    onAdded()
                                }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text("加入任务", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun PosterTaskTab(label: String, badge: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(PosterRadius.pill))
            .background(if (selected) PosterPalette.Brand else PosterPalette.Surface)
            .border(1.dp, if (selected) PosterPalette.Brand else PosterPalette.Line,
                RoundedCornerShape(PosterRadius.pill))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            color = if (selected) Color.White else PosterPalette.Ink2)
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier.size(17.dp).clip(CircleShape)
                .background(if (selected) Color.White.copy(alpha = 0.24f) else PosterPalette.Bg),
            contentAlignment = Alignment.Center
        ) {
            Text(badge, fontSize = 9.sp, color = if (selected) Color.White else PosterPalette.Ink2)
        }
    }
}

@Composable
private fun PosterTaskRow(task: PosterTask) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(PosterRadius.card))
            .background(PosterPalette.Surface)
            .border(1.dp, PosterPalette.Line, RoundedCornerShape(PosterRadius.card))
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(task.tint.background),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Book, contentDescription = null,
                tint = task.tint.foreground, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(task.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                color = PosterPalette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(task.source, fontSize = 10.sp, color = PosterPalette.Ink3,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(6.dp))
        PosterTag(
            text = task.deadline,
            tint = if (task.urgent) PosterPalette.Danger else PosterPalette.Brand,
            background = if (task.urgent) PosterPalette.Danger.copy(alpha = 0.10f) else PosterPalette.BrandSoft
        )
        Icon(Icons.Outlined.ChevronRight, contentDescription = null,
            tint = PosterPalette.Ink3, modifier = Modifier.size(15.dp))
    }
}
