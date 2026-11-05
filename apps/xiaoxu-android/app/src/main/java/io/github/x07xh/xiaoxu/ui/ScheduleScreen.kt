package io.github.x07xh.xiaoxu.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Room
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.x07xh.xiaoxu.data.CloudClient
import io.github.x07xh.xiaoxu.data.CloudUiState
import io.github.x07xh.xiaoxu.data.VerifiedLesson
import java.time.LocalDate
import kotlinx.coroutines.launch

/*
 * 校序课表页四态 UI（Issue #209）。
 *
 * 数据边界：
 * - 不发起网络请求、不解析接口、不写数据库。唯一来源是 CloudClient.state，
 *   唯一触发的动作是 CloudClient.refresh()（与顶部全局刷新同一个公开方法）。
 * - 没有可信课程时不渲染任何课程占位，只渲染对应的真实状态。
 * - 不推断当前教学周、不做上课倒计时：VerifiedLesson 只给节次与周次，缺少开学日期与
 *   节次作息时间表，任何"第 N 周 / 还有 X 分钟"的结论都等于伪造数据。
 */

// ---------------------------------------------------------------------------
// 状态模型（纯函数，可在 JVM 单元测试里直接验证，不依赖 Compose）
// ---------------------------------------------------------------------------

sealed interface ScheduleUiState {
    /** 首次读取尚未落定，或正在刷新且没有可展示的旧数据。 */
    data object Loading : ScheduleUiState

    /** 云端未连接，须由本人在"我的"页完成校序口令登录。 */
    data object NotConnected : ScheduleUiState

    /** 已连接但没有青果可信来源（status = not_connected）。 */
    data object NoTrustedSource : ScheduleUiState

    /** 有来源但未通过可信只读校验，不导入也不猜测。 */
    data object UnverifiedSource : ScheduleUiState

    /** 读取失败且没有旧数据可展示。message 为服务端返回的真实错误分类。 */
    data class Failed(val message: String) : ScheduleUiState

    /** 可信来源明确返回 0 门课程。 */
    data object VerifiedEmpty : ScheduleUiState

    /**
     * 已验证且有真实课程。
     * stale = 当前展示的不是本次读取结果（上次保留，或服务端标记未确认调整）。
     * refreshing = 本次刷新仍在进行，课程来自上次保留。
     */
    data class Verified(
        val lessons: List<VerifiedLesson>,
        val stale: Boolean,
        val fetchedAt: String?,
        val refreshWarning: String?,
        val term: String?,
        val sourceLabel: String = "华水青果 · 可信只读",
        val refreshing: Boolean = false
    ) : ScheduleUiState
}

/**
 * CloudUiState 没有"首次加载完成"标志位，默认 authMessage 是唯一可判定的哨兵值。
 * 这里读取默认实例而不是重复写字面量；数据层将来补字段后可删除此判断。
 */
private val PENDING_AUTH_MESSAGE = CloudUiState().authMessage

/** 本机导入课表与云端无关：不受 scheduleFresh / 刷新失败影响，但始终标注待本人核对。 */
fun localImportedScheduleState(lessons: List<VerifiedLesson>): ScheduleUiState.Verified =
    ScheduleUiState.Verified(
        lessons = lessons,
        stale = false,
        fetchedAt = null,
        refreshWarning = null,
        term = null,
        sourceLabel = "华水青果 · 本机导入（待本人核对）"
    )

/**
 * 推导顺序即优先级，逐条对应 Issue #209 的四态：
 * 1. 有真实课程 -> 已验证（保留旧数据，但按 scheduleFresh 区分"本次读取 / 上次保留 / 刷新中"）；
 * 2. 忙碌 / 首次读取 -> 加载；
 * 3. 未登录 -> 未连接；
 * 4. 有错误且无旧数据 -> 错误；
 * 5. 其余按来源状态落到"无可信来源 / 未验证 / 可信空结果"。
 */
fun deriveScheduleUiState(state: CloudUiState): ScheduleUiState {
    if (state.lessons.isNotEmpty()) {
        // scheduleFresh 只在本次 /api/v1/schedule 成功返回时由 CloudClient 置 true。
        // 刷新开始时它被置 false，失败后保持 false——这两种情况下 lessons 都是上次保留的，
        // 不能标记为"本次已核验"。
        val keptFromLastRead = !state.scheduleFresh
        val refreshing = keptFromLastRead && state.busy
        return ScheduleUiState.Verified(
            lessons = state.lessons,
            stale = keptFromLastRead || state.scheduleStatus == "stale",
            fetchedAt = state.scheduleFetchedAt,
            // privateError 聚合了任务 / 连接 / 微信候选的失败，不能当作课表专属提示。
            refreshWarning = if (keptFromLastRead && !state.busy) "本次课表请求未成功" else null,
            term = state.lessons.firstOrNull { it.term.isNotBlank() }?.term,
            refreshing = refreshing
        )
    }
    if (state.busy || state.signingIn) return ScheduleUiState.Loading
    if (!state.authenticated) {
        return if (state.authMessage == PENDING_AUTH_MESSAGE) ScheduleUiState.Loading
        else ScheduleUiState.NotConnected
    }
    if (state.privateError != null) {
        // 聚合错误可能来自任务 / 连接 / 微信候选，只如实说明"这次云端读取没成功"。
        return ScheduleUiState.Failed("本次云端读取失败，课表暂无可用数据；请稍后重试")
    }
    return when (state.scheduleStatus) {
        "ready", "stale" -> ScheduleUiState.VerifiedEmpty
        "not_connected" -> ScheduleUiState.NoTrustedSource
        else -> ScheduleUiState.UnverifiedSource
    }
}

/** 离散周次压成区间用于课程卡信息密度：1,2,3,5 -> "第1–3、5周"。 */
fun formatWeeks(weeks: List<Int>): String {
    val sorted = weeks.filter { it > 0 }.distinct().sorted()
    if (sorted.isEmpty()) return "周次未提供"
    val parts = mutableListOf<String>()
    var start = sorted.first()
    var previous = start
    for (index in 1 until sorted.size) {
        val week = sorted[index]
        if (week == previous + 1) {
            previous = week
            continue
        }
        parts += if (start == previous) "$start" else "$start–$previous"
        start = week
        previous = week
    }
    parts += if (start == previous) "$start" else "$start–$previous"
    return "第" + parts.joinToString("、") + "周"
}

/** 每天课程数，用于星期条上的密度点。 */
fun lessonCountByWeekday(lessons: List<VerifiedLesson>): Map<Int, Int> =
    lessons.groupingBy { it.weekday }.eachCount()

// ---------------------------------------------------------------------------
// UI 模型
// ---------------------------------------------------------------------------

private val WEEKDAY_SHORT = listOf("一", "二", "三", "四", "五", "六", "日")
private const val ALL_WEEKS = 0
private val ContentMaxWidth = 860.dp
private val StateMaxWidth = 560.dp

private fun weekdayLong(weekday: Int): String =
    "星期" + WEEKDAY_SHORT.getOrElse(weekday - 1) { "?" }

/** VerifiedLesson 含 List<Int>，Compose 无法推断稳定性；UI 侧显式声明不可变。 */
@Immutable
private data class LessonUi(
    val key: String,
    val colorKey: String,
    val title: String,
    val sectionLabel: String,
    val teacher: String,
    val location: String,
    val weeksLabel: String,
    val term: String,
    val weekday: Int,
    val startSection: Int
)

private fun VerifiedLesson.toUi(): LessonUi = LessonUi(
    key = (if (id.isBlank()) "$weekday-$startSection-$endSection-$title" else id) + "-$weekday",
    // 与首页 CourseTile 共用相同的稳定课程标识，跨页面保持六色一致。
    colorKey = id.ifBlank { title },
    title = title.ifBlank { "未命名课程" },
    sectionLabel = "第$startSection–$endSection 节",
    teacher = teacher.ifBlank { "教师未排" },
    location = location.ifBlank { "地点未排" },
    weeksLabel = formatWeeks(weeks),
    term = term.ifBlank { "学期未标注" },
    weekday = weekday,
    startSection = startSection
)

/** 窄屏占满、宽屏居中并限宽，避免平板上正文被拉到全屏宽。 */
private fun Modifier.pageWidth(max: androidx.compose.ui.unit.Dp): Modifier =
    this.widthIn(max = max).fillMaxWidth()

// ---------------------------------------------------------------------------
// 入口：保持既有函数名与包路径，MainActivity 仍调用 CloudTimetablePanel
// ---------------------------------------------------------------------------

@Suppress("UNUSED_PARAMETER")
@Composable
fun CloudTimetablePanel(
    client: CloudClient,
    openWebsite: () -> Unit,
    openProfile: (() -> Unit)? = null,
    localLessons: List<VerifiedLesson> = emptyList(),
    openLocalImport: () -> Unit = {},
    openFileImport: () -> Unit = {}
) {
    // Timetable has exactly one source of truth: the locally imported schedule.
    // CloudClient remains available to other tabs (tasks, grades and connections),
    // but neither its auth state nor its schedule snapshot controls this page.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val expanded = maxWidth >= 840.dp
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (localLessons.isEmpty()) {
                LocalTimetableEmptyState(openFileImport)
            } else {
                ScheduleVerifiedContent(
                    data = localImportedScheduleState(localLessons),
                    expanded = expanded,
                    onRefresh = openFileImport,
                    openLocalImport = openFileImport,
                    openFileImport = openFileImport
                )
            }
        }
    }
}

@Composable
private fun LocalTimetableEmptyState(onImport: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().pageWidth(StateMaxWidth)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("我的课表", style = MaterialTheme.typography.headlineSmall,
            color = XiaoxuPalette.Ink, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() })
        Text("本机课表 · 离线查看 · 不需要连接教务账号",
            style = MaterialTheme.typography.bodySmall, color = XiaoxuPalette.Ink2)
        XiaoxuCard(modifier = Modifier.fillMaxWidth(), contentPadding = 22.dp) {
            Icon(Icons.Outlined.DateRange, contentDescription = null,
                modifier = Modifier.size(36.dp), tint = XiaoxuPalette.Brand)
            Spacer(Modifier.height(12.dp))
            Text("还没有导入课表", style = MaterialTheme.typography.titleLarge,
                color = XiaoxuPalette.Ink, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text("选择手机中的课表文件，预览课程、教室、节次及周次后保存。",
                style = MaterialTheme.typography.bodyMedium, color = XiaoxuPalette.Ink2)
        }
        Button(onClick = onImport, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text("从文件导入课表")
        }
        Text("支持 .ics 和标准 .json；文件只在当前设备解析和存储。成绩仍在教务页独立连接。",
            style = MaterialTheme.typography.bodySmall, color = XiaoxuPalette.Ink2)
    }
}

// ---------------------------------------------------------------------------
// 已验证 + 真实数据
// ---------------------------------------------------------------------------

@Composable
private fun ScheduleVerifiedContent(
    data: ScheduleUiState.Verified,
    expanded: Boolean,
    onRefresh: () -> Unit,
    openLocalImport: () -> Unit,
    openFileImport: () -> Unit
) {
    val localImport = data.sourceLabel.contains("本机导入")
    val lessons = remember(data.lessons) { data.lessons.map { it.toUi() } }
    val weekNumbers = remember(data.lessons) {
        data.lessons.flatMap { it.weeks }.filter { it > 0 }.distinct().sorted()
    }
    // The first available teaching week is a safe, deterministic preview.
    // Without an official semester start it must NOT be labeled current week.
    var selectedWeek by rememberSaveable {
        mutableStateOf(weekNumbers.firstOrNull() ?: ALL_WEEKS)
    }
    var viewMode by rememberSaveable { mutableStateOf("week") }
    var showWeekPicker by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(weekNumbers) {
        if (selectedWeek != ALL_WEEKS && selectedWeek !in weekNumbers) {
            selectedWeek = weekNumbers.firstOrNull() ?: ALL_WEEKS
        }
    }
    var selectedWeekday by rememberSaveable { mutableStateOf(LocalDate.now().dayOfWeek.value) }
    var selectedCourseKey by rememberSaveable { mutableStateOf<String?>(null) }

    // 周次筛选回查原始 weeks，避免 UI 模型丢失周次后无法判定。
    val allowedKeys = remember(data.lessons, selectedWeek) {
        if (selectedWeek == ALL_WEEKS) null
        else data.lessons.filter { selectedWeek in it.weeks }.map { it.toUi().key }.toSet()
    }
    val filtered = remember(lessons, allowedKeys) {
        if (allowedKeys == null) lessons else lessons.filter { it.key in allowedKeys }
    }
    val counts = remember(filtered) { filtered.groupingBy { it.weekday }.eachCount() }
    val todayWeekday = remember { LocalDate.now().dayOfWeek.value }

    // Selection must stay on the user's chosen weekday, even if it is empty.
    // Never silently jump from Saturday to an unrelated class day.
    val effectiveWeekday = selectedWeekday
    val dayLessons = remember(filtered, effectiveWeekday) {
        filtered.filter { it.weekday == effectiveWeekday }.sortedBy { it.startSection }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .pageWidth(ContentMaxWidth)
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("我的课表", style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold, color = XiaoxuPalette.Ink,
                    modifier = Modifier.semantics { heading() })
                Text("本机导入 · " + data.lessons.size + " 个课块 · 请核对课程信息",
                    style = MaterialTheme.typography.labelSmall, color = XiaoxuPalette.Ink2)
            }
            OutlinedButton(onClick = openFileImport, modifier = Modifier.heightIn(min = 44.dp)) {
                Text("重新导入")
            }
        }
        if (weekNumbers.size > 1 && showWeekPicker) {
            WeekSelector(weeks = weekNumbers, selected = selectedWeek,
                onSelect = { selectedWeek = it; showWeekPicker = false })
        }
        if (weekNumbers.isNotEmpty()) {
            // Move only through weeks present in the owner's actual timetable.
            // No synthetic "current week" or hardcoded 1..20 semester range.
            val currentIndex = weekNumbers.indexOf(selectedWeek)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { selectedWeek = weekNumbers[currentIndex - 1] },
                    enabled = currentIndex > 0,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) { Text("上一周") }
                TextButton(
                    onClick = { showWeekPicker = !showWeekPicker },
                    enabled = weekNumbers.size > 1,
                    modifier = Modifier.heightIn(min = 44.dp)
                ) {
                    Text(if (currentIndex >= 0) "第 $selectedWeek 周 ▾" else "全部周次 ▾",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = { selectedWeek = weekNumbers[currentIndex + 1] },
                    enabled = currentIndex < weekNumbers.lastIndex,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) { Text("下一周") }
            }
        }
        if (selectedWeek == ALL_WEEKS && weekNumbers.size > 1) {
            Text(
                text = "当前混合展示整学期课块；选择具体周次才能判断那周是否有课。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        ScheduleViewModeTabs(mode = viewMode, onChange = { viewMode = it })
        val openCourse: (String) -> Unit = { key -> selectedCourseKey = key }
        if (viewMode == "week") {
            if (expanded) {
                // 平板：左七日网格 + 右当日列表。表头选中哪天就显示那天（空日走空态），
                // 不做"跳回最近有课日"，否则点了周六却显示周三，选中态也对不上。
                val tabletWeekday = selectedWeekday
                val tabletDayLessons = remember(filtered, tabletWeekday) {
                    filtered.filter { it.weekday == tabletWeekday }.sortedBy { it.startSection }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Column(
                        modifier = Modifier.weight(1.35f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (selectedWeek != ALL_WEEKS) {
                            val weekLessons = remember(data.lessons, selectedWeek) {
                                data.lessons.filter { selectedWeek in it.weeks }
                            }
                            WeeklyTimetableGrid(
                                lessons = weekLessons,
                                selectedWeekday = tabletWeekday,
                                onSelectWeekday = { selectedWeekday = it }
                            ) { item ->
                                openCourse(item.toUi().key)
                            }
                        }
                    }
                    DayLessonList(
                        dayLessons = tabletDayLessons,
                        selectedWeek = selectedWeek,
                        weekday = tabletWeekday,
                        todayWeekday = todayWeekday,
                        onOpen = openCourse,
                        openLocalImport = openLocalImport,
                        modifier = Modifier.weight(1f).padding(start = 12.dp)
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (selectedWeek != ALL_WEEKS) {
                        val weekLessons = remember(data.lessons, selectedWeek) {
                            data.lessons.filter { selectedWeek in it.weeks }
                        }
                        WeeklyTimetableGrid(
                            lessons = weekLessons,
                            selectedWeekday = selectedWeekday,
                            onSelectWeekday = { selectedWeekday = it }
                        ) { item ->
                            openCourse(item.toUi().key)
                        }
                    }
                }
            }
        } else if (expanded) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                WeekdaySideBar(
                    counts = counts, selected = effectiveWeekday, today = todayWeekday,
                    onSelect = { selectedWeekday = it }
                )
                DayLessonList(
                    dayLessons = dayLessons,
                    selectedWeek = selectedWeek,
                    weekday = effectiveWeekday,
                    todayWeekday = todayWeekday,
                    onOpen = openCourse,
                    openLocalImport = openLocalImport,
                    modifier = Modifier.weight(1f).padding(start = 12.dp)
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                WeekdayStrip(
                    counts = counts, selected = effectiveWeekday, today = todayWeekday,
                    onSelect = { selectedWeekday = it }
                )
                DayLessonList(
                    dayLessons = dayLessons,
                    selectedWeek = selectedWeek,
                    weekday = effectiveWeekday,
                    todayWeekday = todayWeekday,
                    onOpen = openCourse,
                    openLocalImport = openLocalImport
                )
            }
        }
    }
    // 详情始终从本次已验证的课程集合查找，不保留旧课程快照。
    val opened = filtered.firstOrNull { it.key == selectedCourseKey }
    if (opened != null) {
        CourseDetailBottomSheet(
            lesson = opened, localImport = localImport,
            onDismiss = { selectedCourseKey = null }
        )
    }
}

/**
 * 周 / 日 切换。两个标签是同一组单选（`selectableGroup`），各自带准确描述，
 * 读屏能区分"周视图=七日历课程网格"与"日视图=单日课程列表"，并播报选中项。
 */
@Composable
private fun ScheduleViewModeTabs(mode: String, onChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ViewModeTab(
            isSelected = mode == "week",
            description = "周视图，七日历课程网格，点击查看整周课表",
            onClick = { onChange("week") }
        ) {
            Text("周视图")
        }
        ViewModeTab(
            isSelected = mode == "day",
            description = "日视图，单日课程列表，点击查看某一天的课程",
            onClick = { onChange("day") }
        ) {
            Text("日视图")
        }
    }
}

@Composable
private fun ViewModeTab(
    isSelected: Boolean,
    description: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val background by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        animationSpec = tween(180),
        label = "mode-tab-bg"
    )
    val foreground by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(180),
        label = "mode-tab-fg"
    )
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 96.dp)
            .clip(RoundedCornerShape(50))
            .background(background)
            // selectable 直接提供 role=Tab 与 selected，无需再手写覆盖。
            .selectable(selected = isSelected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .semantics {
                contentDescription = description +
                    if (isSelected) "，当前视图" else "，未选中"
            },
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides foreground) { content() }
    }
}

/**
 * 某一天的课程列表。手机、平板日视图、平板周视图右侧共用一份实现，
 * 只渲染传入的真实课块；没有课就走 NoLessonCard 空态。
 */
@Composable
private fun DayLessonList(
    dayLessons: List<LessonUi>,
    selectedWeek: Int,
    weekday: Int,
    todayWeekday: Int,
    onOpen: (String) -> Unit,
    openLocalImport: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DaySummary(weekday, dayLessons.size, weekday == todayWeekday)
        if (dayLessons.isEmpty()) NoLessonCard(selectedWeek)
        dayLessons.forEach { lesson ->
            LessonCard(lesson = lesson, onOpen = { onOpen(lesson.key) })
        }
        ScheduleFooterNote()
    }
}

/**
 * 课表 P1 视觉统一：复用首页白卡与来源徽标，不复制另一套颜色。
 * badge 严格使用现有 Verified.stale/refreshing 与本机来源，不更改学校数据判断。
 */
@Composable
private fun ScheduleHeader(
    subtitle: String,
    onRefresh: () -> Unit,
    localImport: Boolean,
    stale: Boolean,
    refreshing: Boolean
) {
    val badgeLabel = when {
        localImport -> "本机导入 · 待核对"
        refreshing -> "刷新中 · 上次课表"
        stale -> "上次读取 · 待刷新"
        else -> "官网可信读取"
    }
    val badgeTone = if (localImport || stale || refreshing) XiaoxuTone.Warning
        else XiaoxuTone.Success
    XiaoxuCard(
        modifier = Modifier.padding(top = 8.dp, bottom = 10.dp),
        contentPadding = 16.dp
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "我的课表",
                    style = MaterialTheme.typography.headlineSmall,
                    color = XiaoxuPalette.Ink,
                    modifier = Modifier.semantics { heading() }
                )
                SourceBadge(text = badgeLabel, tone = badgeTone)
            }
            TextButton(onClick = onRefresh, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(
                    Icons.Outlined.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(if (localImport) "核对" else "刷新")
            }
        }
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = XiaoxuPalette.Ink2
            )
        }
    }
}

@Composable
private fun ScheduleBanner(
    icon: ImageVector,
    title: String,
    message: String,
    containerColor: Color,
    contentColor: Color,
    compact: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(containerColor)
            .padding(horizontal = 14.dp, vertical = if (compact) 10.dp else 12.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.Top
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = contentColor)
        Spacer(Modifier.width(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
            Text(text = message, style = MaterialTheme.typography.bodySmall, color = contentColor)
        }
    }
}

@Composable
private fun WeekSelector(weeks: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "周次",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                SelectablePill(
                    label = "全部",
                    isSelected = selected == ALL_WEEKS,
                    a11yLabel = "显示全部周次",
                    onClick = { onSelect(ALL_WEEKS) }
                )
            }
            items(count = weeks.size, key = { index -> weeks[index] }) { index ->
                val week = weeks[index]
                SelectablePill(
                    label = "$week",
                    isSelected = selected == week,
                    a11yLabel = "第 $week 周",
                    onClick = { onSelect(week) }
                )
            }
        }
    }
}

@Composable
private fun SelectablePill(
    label: String,
    isSelected: Boolean,
    a11yLabel: String,
    onClick: () -> Unit
) {
    val background by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surface,
        animationSpec = tween(180),
        label = "pill-bg"
    )
    val foreground by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(180),
        label = "pill-fg"
    )
    Box(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(50))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .semantics {
                role = Role.Tab
                selected = isSelected
                contentDescription = a11yLabel
            },
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = foreground)
    }
}

@Composable
private fun WeekdayStrip(counts: Map<Int, Int>, selected: Int, today: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (1..7).forEach { day ->
            DayCell(
                weekday = day,
                count = counts[day] ?: 0,
                isSelected = day == selected,
                isToday = day == today,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(day) }
            )
        }
    }
}

@Composable
private fun DayCell(
    weekday: Int,
    count: Int,
    isSelected: Boolean,
    isToday: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val background by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.primary
            isToday -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(180),
        label = "day-bg"
    )
    val labelColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(180),
        label = "day-label"
    )
    Column(
        modifier = modifier
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
            .semantics {
                role = Role.Tab
                selected = isSelected
                contentDescription = weekdayLong(weekday) +
                    (if (isToday) "，今天" else "") + "，$count 个课块"
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = WEEKDAY_SHORT[weekday - 1],
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (isSelected || isToday) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else labelColor
        )
        Spacer(Modifier.height(4.dp))
        if (count > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(minOf(count, 4)) {
                    Box(
                        Modifier
                            .size(4.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.primary
                            )
                    )
                }
            }
        } else {
            Box(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun WeekdaySideBar(counts: Map<Int, Int>, selected: Int, today: Int, onSelect: (Int) -> Unit) {
    Column(
        modifier = Modifier.width(200.dp).padding(end = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        (1..7).forEach { day ->
            val isSelected = day == selected
            val background by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface,
                animationSpec = tween(180),
                label = "side-bg-$day"
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(background)
                    .clickable { onSelect(day) }
                    .padding(horizontal = 14.dp)
                    .semantics {
                        role = Role.Tab
                        // 必须显式 this.：外层参数 selected: Int 会遮蔽 semantics 的 selected: Boolean。
                        this.selected = isSelected
                        this.contentDescription = weekdayLong(day) +
                            (if (day == today) "，今天" else "") + "，${counts[day] ?: 0} 个课块"
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = weekdayLong(day),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                    Text(
                        text = "${counts[day] ?: 0} 个课块",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (day == today) {
                    Text(
                        text = "今天",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun DaySummary(weekday: Int, count: Int, isToday: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = weekdayLong(weekday),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() }
        )
        if (isToday) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = "今天",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = if (count == 0) "无课" else "$count 个课块",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NoLessonCard(selectedWeek: Int) {
    EmptyHint(
        icon = Icons.Outlined.DateRange,
        title = "这一天没有已读取的课程",
        message = if (selectedWeek == ALL_WEEKS) "整学期该星期没有已保存的课程课块。"
        else "第 $selectedWeek 周该星期没有已保存的课程课块。"
    )
}

@Composable
private fun LessonCard(lesson: LessonUi, onOpen: () -> Unit) {
    val courseColor = xiaoxuCourseColor(lesson.colorKey)
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = tween(120),
        label = "card-press"
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(
                interactionSource = interaction,
                indication = ripple(),
                onClickLabel = "查看课程详情",
                role = Role.Button,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onOpen()
                }
            ),
        shape = RoundedCornerShape(XiaoxuRadius.card),
        colors = CardDefaults.cardColors(containerColor = courseColor.background)
    ) {
        Row(Modifier.fillMaxWidth()) {
            Box(
                Modifier.width(4.dp).heightIn(min = 76.dp)
                    .background(courseColor.accent)
            )
            Column(Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = lesson.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = courseColor.foreground,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    SectionBadge(lesson.sectionLabel)
                    Icon(
                        imageVector = Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Room, contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = lesson.location,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(12.dp))
                    Icon(Icons.Outlined.PersonOutline, contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = lesson.teacher,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/** 首页与完整课表共用同一课程详情抽屉和真实课程映射。 */
@Composable
internal fun HomeCourseDetailSheet(lesson: VerifiedLesson, onDismiss: () -> Unit) {
    CourseDetailBottomSheet(lesson.toUi(), localImport = true, onDismiss = onDismiss)
}

/** 真实课程详情：原生模态抽屉，不推断作息时间，不提供未实现的提醒开关。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CourseDetailBottomSheet(
    lesson: LessonUi, localImport: Boolean, onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = lesson.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = if (localImport) "本机导入 · 待核对"
                    else "华水青果 · 可信只读",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider()
            CourseSheetRow("星期", weekdayLong(lesson.weekday))
            CourseSheetRow("上课教室", lesson.location)
            CourseSheetRow("任课教师", lesson.teacher)
            CourseSheetRow("上课周次", lesson.weeksLabel)
            CourseSheetRow("节次", lesson.sectionLabel)
            CourseSheetRow("学期", lesson.term)
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("关闭详情")
            }
        }
    }
}

@Composable
private fun CourseSheetRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            modifier = Modifier.width(90.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun SectionBadge(label: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(48.dp)
        )
        Text(text = value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ScheduleFooterNote() {
    Text(
        text = "按完整学期展示；未接入开学日期与节次作息时间，因此不推断当前周、不计算上课倒计时。",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp)
    )
}

// ---------------------------------------------------------------------------
// 未连接 / 加载 / 错误 / 无来源 / 未验证 / 可信空结果
// ---------------------------------------------------------------------------

@Composable
private fun ScheduleLoadingState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pageWidth(ContentMaxWidth)
            .padding(horizontal = 18.dp, vertical = 16.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "正在读取可信课表",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "只展示已通过校验的本人课程，读取完成前不显示任何示例课程。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        SkeletonBlock(Modifier.fillMaxWidth().height(64.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(7) { SkeletonBlock(Modifier.weight(1f).height(56.dp)) }
        }
        repeat(3) { SkeletonBlock(Modifier.fillMaxWidth().height(84.dp)) }
    }
}

@Composable
private fun SkeletonBlock(modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "skeleton-shift"
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    val brush = Brush.linearGradient(
        colors = listOf(
            base.copy(alpha = 0.45f),
            base.copy(alpha = 0.85f),
            base.copy(alpha = 0.45f)
        ),
        start = Offset(-320f + shift * 1400f, 0f),
        end = Offset(shift * 1400f, 0f)
    )
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(brush))
}

@Composable
private fun ScheduleNotConnectedState(
    openLocalImport: () -> Unit,
    openProfile: (() -> Unit)?
) {
    StateScaffold(
        icon = Icons.Outlined.CloudOff,
        title = "尚未读取课表",
        message = "在 App 内登录学校账号后导入课表。"
    ) {
        Button(onClick = openLocalImport) { Text("在 App 内导入青果课表") }
        if (openProfile != null) {
            OutlinedButton(onClick = openProfile) {
                Text("云端同步设置（可选）")
            }
        }
    }
}

@Composable
private fun ScheduleSourceMissingState(openLocalImport: () -> Unit, onRefresh: () -> Unit) {
    StateScaffold(
        icon = Icons.Outlined.DateRange,
        title = "还没有可展示的课程",
        message = "先从 App 内读取本人课表。"
    ) {
        Button(onClick = openLocalImport) { Text("在 App 内导入青果课表") }
        OutlinedButton(onClick = onRefresh) { Text("重新检查云端课表") }
    }
}

@Composable
private fun ScheduleUnverifiedState(openLocalImport: () -> Unit, onRefresh: () -> Unit) {
    StateScaffold(
        icon = Icons.Outlined.Info,
        title = "云端课表来源未核验",
        message = "可改用 App 内官方页面读取本人课表。"
    ) {
        Button(onClick = openLocalImport) { Text("在 App 内导入青果课表") }
        OutlinedButton(onClick = onRefresh) { Text("重试云端核验") }
    }
}

@Composable
private fun ScheduleFailedState(
    message: String,
    openLocalImport: () -> Unit,
    onRefresh: () -> Unit
) {
    StateScaffold(
        icon = Icons.Outlined.Warning,
        title = "云端课表读取失败",
        message = message,
        accent = true
    ) {
        Button(onClick = openLocalImport) { Text("改用 App 内青果课表读取") }
        OutlinedButton(onClick = onRefresh) {
            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("重试云端")
        }
    }
}

@Composable
private fun ScheduleEmptyState(openLocalImport: () -> Unit, onRefresh: () -> Unit) {
    StateScaffold(
        icon = Icons.Outlined.DateRange,
        title = "云端来源返回 0 门课程",
        message = "云端未返回课程；这不等于你的学校账号没有课表。可以在 App 内打开个人课表并主动导入。"
    ) {
        Button(onClick = openLocalImport) { Text("在 App 内导入青果课表") }
        OutlinedButton(onClick = onRefresh) { Text("重新读取云端") }
    }
}

@Composable
private fun StateScaffold(
    icon: ImageVector,
    title: String,
    message: String,
    accent: Boolean = false,
    content: @Composable () -> Unit
) {
    XiaoxuCard(
        modifier = Modifier
            .pageWidth(StateMaxWidth)
            .padding(horizontal = 18.dp, vertical = 16.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        contentPadding = 18.dp
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (accent) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.primaryContainer
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(26.dp),
                tint = if (accent) MaterialTheme.colorScheme.onErrorContainer
                else MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = if (accent) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        content()
    }
}

@Composable
private fun EmptyHint(icon: ImageVector, title: String, message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
