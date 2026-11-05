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
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 海报版课表页：「课表更清晰」——周视图 / 日视图 / 课程详情弹层。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosterSchedule(
    demo: Boolean,
    modifier: Modifier = Modifier,
    onProfile: () -> Unit = {}
) {
    var dayView by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(PosterDemo.hero) }
    var reminderOn by remember { mutableStateOf(true) }

    PosterPage(modifier.verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(6.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(PosterDemo.term, fontSize = 11.sp, color = PosterPalette.Ink2, modifier = Modifier.weight(1f))
            if (demo) PosterDemoBadge()
        }
        Text("我的课表", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = PosterPalette.Ink)

        // 周次切换 + 周视图 / 日视图。
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.clip(RoundedCornerShape(PosterRadius.pill))
                    .background(PosterPalette.Surface).padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.ChevronLeft, contentDescription = "上一周",
                    tint = PosterPalette.Ink2, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(PosterDemo.week, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = PosterPalette.Ink)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Outlined.ChevronRight, contentDescription = "下一周",
                    tint = PosterPalette.Ink2, modifier = Modifier.size(15.dp))
            }
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.clip(RoundedCornerShape(PosterRadius.pill))
                    .background(PosterPalette.Surface).padding(3.dp)
            ) {
                PosterViewToggle("周视图", !dayView) { dayView = false }
                PosterViewToggle("日视图", dayView) { dayView = true }
            }
        }

        PosterCard(padding = 10.dp) {
            if (dayView) {
                PosterDayList(lessons = PosterDemo.lessons.filter { it.weekday == 3 }, onLessonClick = { selected = it })
            } else {
                PosterWeekGrid(
                    lessons = PosterDemo.lessons,
                    compact = false,
                    selectedWeekday = 3,
                    onLessonClick = { selected = it }
                )
            }
        }

        // 常驻课程详情：点网格里的课程即切换详情，对应海报下半屏的详情卡。
        PosterLessonDetail(
            lesson = selected,
            onClose = {},
            showClose = false,
            reminderOn = reminderOn,
            onReminderChange = { reminderOn = it }
        )

        // 提醒说明（海报「上课提醒、作业提醒不遗漏」）。
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(PosterPalette.BrandSoft).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                Icon(PosterIcons.Ai, contentDescription = null, tint = PosterPalette.Brand, modifier = Modifier.size(15.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("上课提醒", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = PosterPalette.Ink)
                Text("提前 10 分钟提醒，不错过每节课", fontSize = 10.sp, color = PosterPalette.Ink2)
            }
        }

        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun PosterViewToggle(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(PosterRadius.pill))
            .background(if (selected) PosterPalette.Brand else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp)
    ) {
        Text(label, fontSize = 11.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) Color.White else PosterPalette.Ink2)
    }
}

@Composable
private fun PosterDayList(lessons: List<PosterLesson>, onLessonClick: (PosterLesson) -> Unit) {
    if (lessons.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
            Text("这一天没有课程记录", fontSize = 12.sp, color = PosterPalette.Ink3)
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        lessons.sortedBy { it.section }.forEach { lesson ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(lesson.tint.background).clickable { onLessonClick(lesson) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.width(48.dp)) {
                    Text("第 ${lesson.section}–${lesson.section + 1} 节", fontSize = 10.sp, color = lesson.tint.foreground)
                }
                Column(Modifier.weight(1f)) {
                    Text(lesson.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = lesson.tint.foreground)
                    Text(lesson.room, fontSize = 10.sp, color = lesson.tint.foreground.copy(alpha = 0.8f))
                }
                Icon(Icons.Outlined.ChevronRight, contentDescription = null,
                    tint = lesson.tint.foreground, modifier = Modifier.size(16.dp))
            }
        }
    }
}
