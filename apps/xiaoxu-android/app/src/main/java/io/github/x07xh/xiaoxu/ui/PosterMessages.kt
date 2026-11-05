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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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

/** 海报版消息中心：「消息不再杂乱」——搜索 / 分类 / 查看全文 / 一键提取任务。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosterMessages(demo: Boolean, modifier: Modifier = Modifier, onExtract: () -> Unit = {}) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("全部") }
    var opened by remember { mutableStateOf<PosterMessage?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val visible = PosterDemo.messages.filter { msg ->
        val hit = query.isBlank() || msg.title.contains(query, true) || msg.summary.contains(query, true)
        val match = when (filter) {
            "课程" -> msg.tag == "待提取"
            "校园" -> msg.tag == "官方通知" || msg.tag == "本地提示"
            "待审核" -> msg.tag == "待提取"
            else -> true
        }
        hit && match
    }

    PosterPage(modifier.verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(6.dp))
        Text("MESSAGE CENTER", fontSize = 10.sp, color = PosterPalette.Ink3, letterSpacing = 1.2.sp)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("消息中心", fontSize = 24.sp, fontWeight = FontWeight.Bold,
                color = PosterPalette.Ink, modifier = Modifier.weight(1f))
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(PosterPalette.Surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Notifications, contentDescription = "通知",
                    tint = PosterPalette.Ink, modifier = Modifier.size(17.dp))
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("来源清楚 · 智能管理 · 人工确认", fontSize = 11.sp,
                color = PosterPalette.Ink2, modifier = Modifier.weight(1f))
            if (demo) PosterDemoBadge()
        }

        PosterSearchBox(
            hint = "搜索群聊、通知内容、关键词（如：考试）",
            query = query, onQuery = { query = it }
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("全部", "课程", "校园", "待审核").forEach { label ->
                PosterChip(label = label, selected = filter == label, dot = label == "待审核") { filter = label }
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("共 ${visible.size} 条消息", fontSize = 11.sp, color = PosterPalette.Ink2, modifier = Modifier.weight(1f))
            Text("按时间 ↓", fontSize = 11.sp, color = PosterPalette.Ink3)
        }

        visible.forEach { msg ->
            PosterMessageCard(msg = msg, onClick = { opened = msg })
        }

        Spacer(Modifier.height(14.dp))
    }

    if (opened != null) {
        ModalBottomSheet(
            onDismissRequest = { opened = null },
            sheetState = sheetState,
            containerColor = PosterPalette.Surface
        ) {
            PosterMessageDetail(msg = opened!!, onClose = { opened = null }, onExtract = {
                opened = null
                onExtract()
            })
        }
    }
}

@Composable
private fun PosterMessageCard(msg: PosterMessage, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(PosterRadius.card))
            .background(PosterPalette.Surface)
            .border(1.dp, if (msg.tag == "待提取") PosterPalette.Brand.copy(alpha = 0.45f) else PosterPalette.Line,
                RoundedCornerShape(PosterRadius.card))
            .clickable(onClick = onClick)
            .padding(13.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier.size(30.dp).clip(RoundedCornerShape(10.dp))
                .background(msg.tint.background),
            contentAlignment = Alignment.Center
        ) {
            Icon(PosterIcons.Message, contentDescription = null,
                tint = msg.tint.foreground, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(msg.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = PosterPalette.Ink, modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                PosterTag(
                    text = msg.tag,
                    tint = if (msg.tag == "官方通知") PosterPalette.DemoInk else PosterPalette.Ink2,
                    background = if (msg.tag == "官方通知") PosterPalette.DemoBg else PosterPalette.Bg
                )
            }
            Text(msg.time, fontSize = 10.sp, color = PosterPalette.Ink3)
            Text(msg.summary, fontSize = 11.sp, color = PosterPalette.Ink2, maxLines = 2,
                overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null,
            tint = PosterPalette.Ink3, modifier = Modifier.size(16.dp).align(Alignment.CenterVertically))
    }
}

@Composable
private fun PosterMessageDetail(msg: PosterMessage, onClose: () -> Unit, onExtract: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(msg.title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = PosterPalette.Ink)
                Text(msg.time, fontSize = 11.sp, color = PosterPalette.Ink3)
            }
            PosterTag(msg.tag, PosterPalette.Brand, PosterPalette.BrandSoft)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(28.dp).clip(CircleShape).background(PosterPalette.Bg).clickable(onClick = onClose),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Close, contentDescription = "关闭",
                    tint = PosterPalette.Ink2, modifier = Modifier.size(15.dp))
            }
        }
        Text(msg.detail.ifBlank { msg.summary }, fontSize = 12.sp, color = PosterPalette.Ink2, lineHeight = 19.sp)
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(PosterPalette.Bg).padding(11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(28.dp).clip(RoundedCornerShape(7.dp)).background(PosterPalette.Danger.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center) {
                Text("PDF", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = PosterPalette.Danger)
            }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text("高数练习要求与参考.pdf", fontSize = 11.sp, color = PosterPalette.Ink)
                Text("1.2 MB", fontSize = 10.sp, color = PosterPalette.Ink3)
            }
            Icon(Icons.Outlined.Download, contentDescription = "下载",
                tint = PosterPalette.Brand, modifier = Modifier.size(17.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.weight(1f).heightIn(min = 42.dp).clip(RoundedCornerShape(PosterRadius.pill))
                    .border(1.dp, PosterPalette.Line, RoundedCornerShape(PosterRadius.pill))
                    .clickable(onClick = onClose).padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("查看原消息", fontSize = 12.sp, color = PosterPalette.Ink2)
            }
            Box(
                Modifier.weight(1f).heightIn(min = 42.dp).clip(RoundedCornerShape(PosterRadius.pill))
                    .background(PosterPalette.Brand).clickable(onClick = onExtract).padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("提取为任务 ✨", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
