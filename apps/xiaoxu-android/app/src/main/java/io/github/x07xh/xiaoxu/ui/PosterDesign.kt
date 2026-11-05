package io.github.x07xh.xiaoxu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 海报版设计系统（Issue #284）。
 *
 * 与 [XiaoxuPalette] 并存：后者服务于既有真实数据页面，本文件只负责
 * "形"——把设计海报的配色、圆角、间距、组件抽成可复用 Composable。
 * 不接触任何数据来源判定，演示数据由调用方显式传入并必须配 [PosterDemoBadge]。
 */
object PosterPalette {
    val Bg = Color(0xFFF5F8FF)
    val Surface = Color(0xFFFFFFFF)
    val Brand = Color(0xFF2E6BF6)
    val BrandLight = Color(0xFF5B9BFF)
    val BrandDeep = Color(0xFF1B47C8)
    val BrandSoft = Color(0xFFE8F0FF)
    val Ink = Color(0xFF1B2540)
    val Ink2 = Color(0xFF66759A)
    val Ink3 = Color(0xFF9AA7BE)
    val Line = Color(0xFFE9EFFA)
    val DemoBg = Color(0xFFFFF3E0)
    val DemoInk = Color(0xFFE8890C)
    val VioletSoft = Color(0xFFF0EDFF)
    val Violet = Color(0xFF7A4FD1)
    val Success = Color(0xFF16A34A)
    val Danger = Color(0xFFE04455)
}

object PosterRadius {
    val card: Dp = 18.dp
    val hero: Dp = 20.dp
    val tile: Dp = 8.dp
    val pill: Dp = 999.dp
}

/** 页面统一容器：极浅蓝白底 + 16dp 水平留白。 */
@Composable
fun PosterPage(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PosterPalette.Bg)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content
    )
}

/** 白卡：无描边、极淡阴影感（用 1dp 浅线模拟），18dp 圆角。 */
@Composable
fun PosterCard(
    modifier: Modifier = Modifier,
    padding: Dp = 14.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(PosterRadius.card))
            .background(PosterPalette.Surface)
            .border(1.dp, PosterPalette.Line, RoundedCornerShape(PosterRadius.card))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

/** 「ⓘ 演示数据」：只要页面用演示数据填充版式，就必须显示，绝不省略。 */
@Composable
fun PosterDemoBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(PosterRadius.pill))
            .background(PosterPalette.DemoBg)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Info, contentDescription = null, tint = PosterPalette.DemoInk, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(4.dp))
        Text("演示数据", fontSize = 11.sp, color = PosterPalette.DemoInk, fontWeight = FontWeight.Medium)
    }
}

/** 日期行：左侧「2026 年 11 月 4 日 · 星期三」，右侧铃铛。 */
@Composable
fun PosterDateRow(date: String, onBell: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(date, fontSize = 12.sp, color = PosterPalette.Ink2, modifier = Modifier.weight(1f))
        if (onBell != null) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(PosterPalette.Surface)
                    .clickable(onClick = onBell),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Notifications, contentDescription = "通知", tint = PosterPalette.Ink, modifier = Modifier.size(17.dp))
            }
        }
    }
}

/** 分区标题：加粗深色 + 右侧「查看全部 >」蓝色操作。 */
@Composable
fun PosterSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PosterPalette.Ink, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Row(
                Modifier
                    .heightIn(min = 40.dp)
                    .clickable(onClick = onAction)
                    .padding(start = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(action, fontSize = 12.sp, color = PosterPalette.Brand)
                Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = PosterPalette.Brand, modifier = Modifier.size(14.dp))
            }
        }
    }
}

/**
 * 亮蓝渐变 Hero：海报首页主视觉。
 * 顶部「✦ 下一节课」+ 右侧时间，中间课程大标题，下面教室 / 节次，右下描边胶囊按钮。
 * 右上角叠一个半透明菱形装饰（海报里的钻石图形）。
 */
@Composable
fun PosterHero(
    lesson: PosterLesson,
    timeLabel: String,
    modifier: Modifier = Modifier,
    kicker: String = "✦ 下一节课",
    actionLabel: String = "打开课表",
    onAction: () -> Unit
) {
    val shape = RoundedCornerShape(PosterRadius.hero)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 152.dp)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(PosterPalette.Brand, PosterPalette.BrandLight),
                    start = Offset.Zero, end = Offset(900f, 520f)
                )
            )
            .drawBehind {
                // 右上角三层菱形：模拟海报里的钻石徽记，比圆形更贴近原稿。
                val cx = size.width * 0.85f
                val cy = size.height * 0.34f
                val r = size.minDimension * 0.17f
                fun diamond(radius: Float, alpha: Float) {
                    val path = Path().apply {
                        moveTo(cx, cy - radius)
                        lineTo(cx + radius * 0.92f, cy)
                        lineTo(cx, cy + radius)
                        lineTo(cx - radius * 0.92f, cy)
                        close()
                    }
                    drawPath(path, Color.White.copy(alpha = alpha))
                }
                diamond(r * 1.55f, 0.06f)
                diamond(r * 1.12f, 0.10f)
                diamond(r * 0.74f, 0.17f)
                drawCircle(
                    Color.White.copy(alpha = 0.05f),
                    radius = size.minDimension * 0.55f,
                    center = Offset(size.width * 1.02f, -size.height * 0.30f)
                )
            }
            .padding(16.dp)
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(kicker, fontSize = 11.sp, color = Color.White.copy(alpha = 0.88f), modifier = Modifier.weight(1f))
                Text(timeLabel, fontSize = 11.sp, color = Color.White.copy(alpha = 0.88f))
            }
            Spacer(Modifier.height(2.dp))
            Text(lesson.title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Place, contentDescription = null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(4.dp))
                Text(lesson.room, fontSize = 11.sp, color = Color.White.copy(alpha = 0.9f))
                Spacer(Modifier.width(14.dp))
                Icon(Icons.Outlined.DateRange, contentDescription = null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(4.dp))
                Text("2 节课", fontSize = 11.sp, color = Color.White.copy(alpha = 0.9f))
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Row(
                    Modifier
                        .heightIn(min = 36.dp)
                        .clip(RoundedCornerShape(PosterRadius.pill))
                        .border(1.dp, Color.White.copy(alpha = 0.75f), RoundedCornerShape(PosterRadius.pill))
                        .clickable(onClick = onAction)
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(actionLabel, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Outlined.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                }
            }
        }
    }
}

/** 统计卡：左侧彩色圆角图标，右侧大数字 + 说明。海报「03 待完成作业 / 02 本周截止」。 */
@Composable
fun PosterMetricCard(
    value: String,
    label: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(PosterRadius.card))
            .background(PosterPalette.Surface)
            .border(1.dp, PosterPalette.Line, RoundedCornerShape(PosterRadius.card))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(value, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = PosterPalette.Ink)
            Text(label, fontSize = 11.sp, color = PosterPalette.Ink2)
        }
    }
}

/** 宫格项：淡紫底图标 + 文字，海报「校园助手 / 校园快捷」。 */
@Composable
fun PosterGridItem(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .heightIn(min = 68.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(PosterPalette.VioletSoft.copy(alpha = 0.55f))
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = PosterPalette.Violet, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(5.dp))
        Text(label, fontSize = 11.sp, color = PosterPalette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 筛选 chip：选中蓝底白字，未选中白底灰字。 */
@Composable
fun PosterChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    dot: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .heightIn(min = 34.dp)
            .clip(RoundedCornerShape(PosterRadius.pill))
            .background(if (selected) PosterPalette.Brand else PosterPalette.Surface)
            .border(1.dp, if (selected) PosterPalette.Brand else PosterPalette.Line, RoundedCornerShape(PosterRadius.pill))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) Color.White else PosterPalette.Ink2)
        if (dot) {
            Spacer(Modifier.width(4.dp))
            Box(Modifier.size(5.dp).clip(CircleShape).background(PosterPalette.Danger))
        }
    }
}

/** 搜索框：白底圆角 + 放大镜 + 占位文字。 */
@Composable
fun PosterSearchBox(hint: String, query: String, modifier: Modifier = Modifier, onQuery: (String) -> Unit) {
    BasicTextField(
        value = query,
        onValueChange = onQuery,
        singleLine = true,
        textStyle = TextStyle(fontSize = 12.sp, color = PosterPalette.Ink),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(PosterPalette.Surface)
                    .border(1.dp, PosterPalette.Line, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Search, contentDescription = null, tint = PosterPalette.Ink3, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Text(hint, fontSize = 12.sp, color = PosterPalette.Ink3)
                    inner()
                }
            }
        }
    )
}

/** 周课表网格：7 列（周一~周日）+ 时间轴列，课程块用马卡龙色。 */
@Composable
fun PosterWeekGrid(
    lessons: List<PosterLesson>,
    modifier: Modifier = Modifier,
    selectedWeekday: Int = 4,
    compact: Boolean = false,
    onLessonClick: (PosterLesson) -> Unit = {}
) {
    val rows: List<Pair<String, Int>> = if (compact) {
        listOf("08:00" to 1, "10:05" to 3, "14:00" to 5, "16:05" to 7)
    } else {
        listOf("1-2" to 1, "3-4" to 3, "5-6" to 5, "7-8" to 7, "9-10" to 9)
    }
    val rowHeight: Dp = if (compact) 44.dp else 60.dp
    val dayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    val dayDates = listOf("02", "03", "04", "05", "06", "07", "08")

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(34.dp))
            dayNames.forEachIndexed { index, name ->
                Column(
                    Modifier.weight(1f).padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(name, fontSize = 10.sp, color = PosterPalette.Ink3)
                    Spacer(Modifier.height(3.dp))
                    Box(
                        Modifier
                            .size(if (compact) 24.dp else 28.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (index + 1 == selectedWeekday) PosterPalette.Brand else Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(dayDates[index], fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                            color = if (index + 1 == selectedWeekday) Color.White else PosterPalette.Ink2)
                    }
                }
            }
        }
        rows.forEach { (label, section) ->
            Row(Modifier.fillMaxWidth().height(rowHeight), verticalAlignment = Alignment.Top) {
                Box(Modifier.width(34.dp).height(rowHeight), contentAlignment = Alignment.TopStart) {
                    Text(label, fontSize = 9.sp, color = PosterPalette.Ink3, modifier = Modifier.padding(top = 4.dp))
                }
                (1..7).forEach { day ->
                    val lesson = lessons.firstOrNull { it.weekday == day && it.section == section }
                    Box(
                        Modifier
                            .weight(1f)
                            .height(rowHeight)
                            .border(0.5.dp, PosterPalette.Line)
                            .padding(2.dp)
                    ) {
                        if (lesson != null) {
                            Column(
                                Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(PosterRadius.tile))
                                    .background(lesson.tint.background)
                                    .clickable { onLessonClick(lesson) }
                                    .padding(horizontal = 3.dp, vertical = 3.dp),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(lesson.title, fontSize = if (compact) 9.sp else 10.sp,
                                    fontWeight = FontWeight.SemiBold, color = lesson.tint.foreground,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 11.sp)
                                if (!compact) {
                                    Text(lesson.room, fontSize = 9.sp, color = lesson.tint.foreground.copy(alpha = 0.8f), maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 底部课程详情弹层内容（不含 ModalBottomSheet 外壳，由调用方决定呈现方式）。 */
@Composable
fun PosterLessonDetail(
    lesson: PosterLesson,
    onClose: () -> Unit,
    reminderOn: Boolean,
    onReminderChange: (Boolean) -> Unit,
    showClose: Boolean = true
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .background(PosterPalette.Surface)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.width(36.dp).height(4.dp).clip(CircleShape).background(PosterPalette.Line).align(Alignment.CenterHorizontally))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(lesson.tint.background),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Book, contentDescription = null, tint = lesson.tint.foreground, modifier = Modifier.size(21.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(lesson.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PosterPalette.Ink)
                Text("专业必修 · 3 学分", fontSize = 11.sp, color = PosterPalette.Ink2)
            }
            if (showClose) {
                Box(Modifier.size(30.dp).clip(CircleShape).background(PosterPalette.Bg).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Close, contentDescription = "关闭", tint = PosterPalette.Ink2, modifier = Modifier.size(16.dp))
                }
            }
        }
        PosterDetailRow(Icons.Outlined.Place, "上课教室", lesson.room)
        PosterDetailRow(Icons.Outlined.PersonOutline, "任课教师", lesson.teacher.ifBlank { "未提供" })
        PosterDetailRow(Icons.Outlined.DateRange, "上课周次", lesson.weeks)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Notifications, contentDescription = null, tint = PosterPalette.Brand, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(10.dp))
            Text("上课提醒", fontSize = 12.sp, color = PosterPalette.Ink2, modifier = Modifier.weight(1f))
            Text("提前 10 分钟提醒", fontSize = 11.sp, color = PosterPalette.Ink2)
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .width(38.dp).height(22.dp)
                    .clip(CircleShape)
                    .background(if (reminderOn) PosterPalette.Brand else PosterPalette.Line)
                    .clickable { onReminderChange(!reminderOn) },
                contentAlignment = if (reminderOn) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                Box(Modifier.padding(2.dp).size(18.dp).clip(CircleShape).background(Color.White))
            }
        }
    }
}

@Composable
private fun PosterDetailRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = PosterPalette.Brand, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, fontSize = 12.sp, color = PosterPalette.Ink2, modifier = Modifier.weight(1f))
        Text(value, fontSize = 12.sp, color = PosterPalette.Ink)
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = PosterPalette.Ink3, modifier = Modifier.size(15.dp))
    }
}

/** 通用小标签（消息卡右上「待提取 / 官方通知」、任务截止状态等）。 */
@Composable
fun PosterTag(text: String, tint: Color, background: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(text, fontSize = 10.sp, color = tint, fontWeight = FontWeight.Medium)
    }
}

/** 海报常用图标集合，供页面引用，避免各页各挑一套。 */
object PosterIcons {
    val Schedule: ImageVector = Icons.Outlined.DateRange
    val Task: ImageVector = Icons.Outlined.CheckCircle
    val Message: ImageVector = Icons.Outlined.MailOutline
    val Library: ImageVector = Icons.Outlined.Book
    val Room: ImageVector = Icons.Outlined.Place
    val School: ImageVector = Icons.Outlined.School
    val More: ImageVector = Icons.Outlined.Apps
    val Ai: ImageVector = Icons.Outlined.AutoAwesome
}
