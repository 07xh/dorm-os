package io.github.x07xh.xiaoxu.ui

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 海报版首页（Issue #284）：与「校序 · Academic OS」「首页一眼掌握」两张海报对齐。
 *
 * demo = true 表示本机没有可信课表/任务，用 [PosterDemo] 填满版式骨架，
 * 并在页面顶部显示 [PosterDemoBadge]。demo = false 时由调用方传入真实记录，
 * 这里保留同一套版式；当前版本真实数据仍由既有 RealAcademicHome 承担。
 */
@Composable
fun PosterHome(
    demo: Boolean,
    modifier: Modifier = Modifier,
    onSchedule: () -> Unit,
    onTasks: () -> Unit,
    onMessages: () -> Unit,
    onProfile: () -> Unit,
    onAcademic: () -> Unit,
    onLibrary: () -> Unit,
    onRoom: () -> Unit,
    onMore: () -> Unit
) {
    PosterPage(modifier.verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(6.dp))

        // 日期行：海报式，右侧铃铛取代品牌方块顶栏。
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(PosterDemo.date, fontSize = 12.sp, color = PosterPalette.Ink2, modifier = Modifier.weight(1f))
            if (demo) PosterDemoBadge()
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(PosterPalette.Surface)
                    .clickable(onClick = onMessages),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Notifications, contentDescription = "通知",
                    tint = PosterPalette.Ink, modifier = Modifier.size(17.dp))
            }
        }

        Text("晚上好，今天也要加油 ✨", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = PosterPalette.Ink)

        PosterHero(
            lesson = PosterDemo.hero,
            timeLabel = "明天 14:00",
            onAction = onSchedule
        )

        // 本周课表：7 列周网格，与海报一致。
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("本周课表", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    color = PosterPalette.Ink, modifier = Modifier.weight(1f))
                Row(
                    Modifier.height(32.dp).clip(RoundedCornerShape(PosterRadius.pill))
                        .background(PosterPalette.BrandSoft)
                        .clickable(onClick = onSchedule)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${PosterDemo.week} · 周视图", fontSize = 11.sp, color = PosterPalette.Brand)
                    Icon(Icons.Outlined.ChevronRight, contentDescription = null,
                        tint = PosterPalette.Brand, modifier = Modifier.size(13.dp))
                }
            }
            PosterCard(padding = 10.dp) {
                PosterWeekGrid(
                    lessons = PosterDemo.lessons,
                    compact = true,
                    selectedWeekday = 3,
                    onLessonClick = { onSchedule() }
                )
            }
        }

        // 学习任务：两枚带图标的统计卡。
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PosterSectionHeader("学习任务", action = "查看全部", onAction = onTasks)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PosterMetricCard(
                    value = "03", label = "待完成作业",
                    icon = PosterIcons.Task, tint = PosterPalette.Brand,
                    modifier = Modifier.weight(1f), onClick = onTasks
                )
                PosterMetricCard(
                    value = "02", label = "本周截止",
                    icon = Icons.Outlined.DateRange, tint = PosterPalette.Success,
                    modifier = Modifier.weight(1f), onClick = onTasks
                )
            }
        }

        // 校园助手：4 宫格（教务入口保留在这里，不丢 PR #282 功能）。
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("校园助手", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PosterPalette.Ink)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PosterGridItem("成绩查询", PosterIcons.School, Modifier.weight(1f), onAcademic)
                PosterGridItem("图书馆", PosterIcons.Library, Modifier.weight(1f), onLibrary)
                PosterGridItem("空教室", PosterIcons.Room, Modifier.weight(1f), onRoom)
                PosterGridItem("更多服务", PosterIcons.More, Modifier.weight(1f), onMore)
            }
        }

        // AI 学习助手 · 今日建议（海报「首页一眼掌握」）。
        PosterCard(padding = 12.dp, onClick = onTasks) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(PosterIcons.Ai, contentDescription = null, tint = PosterPalette.Violet, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Text("AI 学习助手 · 今日建议", fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold, color = PosterPalette.Ink,
                    modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.ChevronRight, contentDescription = null,
                    tint = PosterPalette.Ink3, modifier = Modifier.size(15.dp))
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(PosterPalette.VioletSoft.copy(alpha = 0.6f)).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("高等数学学习题加强", fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold, color = PosterPalette.Ink)
                    Text("根据近期错题，推荐 3 道相似题巩固", fontSize = 10.sp, color = PosterPalette.Ink2,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(PosterPalette.Brand),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null,
                        tint = Color.White, modifier = Modifier.size(15.dp))
                }
            }
        }

        // 待办与提醒。
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PosterSectionHeader("待办与提醒 (4)", action = "查看全部", onAction = onTasks)
            PosterCard(padding = 4.dp) {
                PosterTodoRow("《电路基础》作业", "明天 23:59 截止", true, onTasks)
                PosterTodoRow("大学英语四六级报名", "还有 3 天", false, onTasks)
            }
        }

        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun PosterTodoRow(title: String, trailing: String, urgent: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(if (urgent) PosterPalette.Danger else PosterPalette.Brand))
        Spacer(Modifier.width(10.dp))
        Text(title, fontSize = 12.sp, color = PosterPalette.Ink, modifier = Modifier.weight(1f),
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(trailing, fontSize = 10.sp, color = if (urgent) PosterPalette.Danger else PosterPalette.Ink2)
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Outlined.ChevronRight, contentDescription = null,
            tint = PosterPalette.Ink3, modifier = Modifier.size(14.dp))
    }
}
