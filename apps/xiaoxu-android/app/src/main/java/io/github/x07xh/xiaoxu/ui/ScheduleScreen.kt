package io.github.x07xh.xiaoxu.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Room
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
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
import io.github.x07xh.xiaoxu.data.VerifiedLesson
import io.github.x07xh.xiaoxu.data.LocalSemesterConfigStore
import io.github.x07xh.xiaoxu.data.SemesterCalendar
import io.github.x07xh.xiaoxu.data.selectableTeachingWeeks
import io.github.x07xh.xiaoxu.data.LocalScheduleEditor
import io.github.x07xh.xiaoxu.data.LocalCourseReminders
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings as AndroidSettings
import androidx.core.content.ContextCompat
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import io.github.x07xh.xiaoxu.data.LocalScheduleStore
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDate

/*
 * 课表仅渲染本机导入的 localLessons；旧云端课表状态不再控制此页面。
 * CloudClient 的任务、连接状态与学校会话逻辑仍由对应模块负责。
 */

/** 本机导入课程和来源标签，永不冒充学校实时核验结果。 */
data class LocalImportedScheduleView(
    val lessons: List<VerifiedLesson>,
    val sourceLabel: String
)

fun localImportedScheduleView(lessons: List<VerifiedLesson>) =
    LocalImportedScheduleView(
        lessons = lessons,
        sourceLabel = "华水青果 · 本机导入（待本人核对）"
    )

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


// ---------------------------------------------------------------------------
// UI 模型
// ---------------------------------------------------------------------------

private val WEEKDAY_SHORT = listOf("一", "二", "三", "四", "五", "六", "日")
private const val ALL_WEEKS = 0

private fun weekdayLong(weekday: Int): String =
    "星期" + WEEKDAY_SHORT.getOrElse(weekday - 1) { "?" }

/** VerifiedLesson 含 List<Int>，Compose 无法推断稳定性；UI 侧显式声明不可变。 */
@Immutable
private data class LessonUi(
    val key: String,
    val sourceId: String,
    val colorKey: String,
    val title: String,
    val sectionLabel: String,
    val teacher: String,
    val location: String,
    val weeksLabel: String,
    val term: String,
    val weekday: Int,
    val startSection: Int,
    val endSection: Int
)

private fun VerifiedLesson.toUi(): LessonUi = LessonUi(
    key = (if (id.isBlank()) "$weekday-$startSection-$endSection-$title" else id) + "-$weekday",
    sourceId = id,
    // 与首页 CourseTile 共用相同的稳定课程标识，跨页面保持六色一致。
    colorKey = id.ifBlank { title },
    title = title.ifBlank { "未命名课程" },
    sectionLabel = "第$startSection–$endSection 节",
    teacher = teacher.ifBlank { "教师未排" },
    location = location.ifBlank { "地点未排" },
    weeksLabel = formatWeeks(weeks),
    term = term.ifBlank { "学期未标注" },
    weekday = weekday,
    startSection = startSection,
    endSection = endSection
)

// ---------------------------------------------------------------------------
// 入口保持现有函数名，供 MainActivity 直接调用；课表只接收本机课程和文件导入操作。
// ---------------------------------------------------------------------------

@Composable
fun CloudTimetablePanel(
    localLessons: List<VerifiedLesson>,
    openFileImport: () -> Unit,
    openFileExport: () -> Unit,
    localScheduleRevision: Int,
    onLocalLessonsChanged: (List<VerifiedLesson>) -> Unit,
    onMessages: () -> Unit,
    settingsOpen: Boolean = false,
    onSettingsOpenChanged: (Boolean) -> Unit = {}
) {
    // Both timetable and settings occupy the same native Activity content window.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val expanded = maxWidth >= 840.dp
        ScheduleVerifiedContent(
            data = localImportedScheduleView(localLessons),
            expanded = expanded,
            openFileImport = openFileImport,
            openFileExport = openFileExport,
            localScheduleRevision = localScheduleRevision,
            onLocalLessonsChanged = onLocalLessonsChanged,
            onMessages = onMessages,
            settingsOpen = settingsOpen,
            onSettingsOpenChanged = onSettingsOpenChanged
        )
    }
}

/** 唯一课表标题：保留操作与来源提示，但不再叠放二级导航和文字按钮行。 */
@Composable
private fun SchedulePageHeading(
    onMessages: () -> Unit,
    sourceLabel: String? = null,
    onAdd: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "我的课表", modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold, color = XiaoxuPalette.Ink
            )
            if (sourceLabel != null) {
                Text(
                    sourceLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = XiaoxuPalette.Ink3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (onAdd != null) XiaoxuToolbarAction(
            icon = Icons.Outlined.Add,
            label = "新增课程",
            onClick = onAdd
        )
        if (onSettings != null) XiaoxuToolbarAction(
            icon = Icons.Outlined.Settings,
            label = "课表设置",
            onClick = onSettings
        )
        XiaoxuToolbarAction(
            icon = Icons.Outlined.NotificationsNone,
            label = "消息中心",
            onClick = onMessages
        )
    }
}

@Composable
private fun LocalTimetableEmptyState(
    onSettings: () -> Unit, onAdd: () -> Unit, onMessages: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().xiaoxuPageWidth(XiaoxuPageLayout.StateMaxWidth)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SchedulePageHeading(onMessages, sourceLabel = "本机课表 · 离线查看",
            onSettings = onSettings, onAdd = onAdd)
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
        Button(onClick = onSettings, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text("打开课表设置")
        }
        OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("手动添加第一门课程")
        }
        Text("支持 .ics 和标准 .json；文件只在当前设备解析和存储。成绩仍在教务页独立连接。",
            style = MaterialTheme.typography.bodySmall, color = XiaoxuPalette.Ink2)
    }
}

// ---------------------------------------------------------------------------
// 本机导入 + 真实课程数据
// ---------------------------------------------------------------------------

@Composable
private fun ScheduleVerifiedContent(
    data: LocalImportedScheduleView,
    expanded: Boolean,
    openFileImport: () -> Unit,
    openFileExport: () -> Unit,
    localScheduleRevision: Int,
    onLocalLessonsChanged: (List<VerifiedLesson>) -> Unit,
    onMessages: () -> Unit,
    settingsOpen: Boolean,
    onSettingsOpenChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var calendar by remember { mutableStateOf(LocalSemesterConfigStore.load(context)) }

    val editorScope = rememberCoroutineScope()
    val reminderPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && LocalCourseReminders.canNotify(context)) {
            editorScope.launch {
                val reminderOk = withContext(Dispatchers.IO) {
                    runCatching {
                        LocalCourseReminders.setEnabled(context, true)
                        LocalCourseReminders.reconcileAfterSave(
                            context, LocalScheduleStore.load(context),
                            LocalSemesterConfigStore.load(context))
                    }.getOrDefault(false)
                }
                if (!reminderOk) Toast.makeText(
                    context, "课程未改变，但上课提醒启用失败；请重新开启", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(context, "未获取通知权限，上课提醒保持关闭", Toast.LENGTH_LONG).show()
        }
    }
    LaunchedEffect(localScheduleRevision) {
        // Restore may change only the semester settings, leaving course rows identical.
        calendar = withContext(Dispatchers.IO) { LocalSemesterConfigStore.load(context) }
    }
    var showingCourseEditor by remember { mutableStateOf(false) }
    var editOriginal by remember { mutableStateOf<VerifiedLesson?>(null) }
    var editingBusy by remember { mutableStateOf(false) }
    var editingError by remember { mutableStateOf<String?>(null) }
    val actualCurrentWeek = calendar?.currentWeek(LocalDate.now())
    val lessons = remember(data.lessons) { data.lessons.map { it.toUi() } }
    val weekNumbers = remember(data.lessons, calendar) {
        selectableTeachingWeeks(data.lessons, calendar)
    }
    // Current-week selection requires the user's explicit semester start date.
    // Without it, fall back to the first imported week, never a guessed "today".
    var selectedWeek by rememberSaveable {
        mutableStateOf(actualCurrentWeek?.takeIf { it in weekNumbers }
            ?: weekNumbers.firstOrNull() ?: ALL_WEEKS)
    }
    var viewMode by rememberSaveable { mutableStateOf(LocalSemesterConfigStore.loadViewMode(context)) }
    var visibleSections by rememberSaveable { mutableStateOf(LocalSemesterConfigStore.loadSections(context)) }
    var showWeekPicker by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(weekNumbers) {
        // 只允许具体教学周被用户选择；ALL_WEEKS 仅用于无有效周次的内部空态。
        if (weekNumbers.isNotEmpty() && selectedWeek !in weekNumbers) {
            selectedWeek = actualCurrentWeek?.takeIf { it in weekNumbers }
                ?: weekNumbers.first()
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

    // Keep the timetable's scroll offset while switching to its real settings route.
    val timetableScroll = rememberScrollState()
    if (!settingsOpen) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(timetableScroll),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
    if (data.lessons.isEmpty()) {
        LocalTimetableEmptyState(
            onSettings = { onSettingsOpenChanged(true) },
            onAdd = {
                editOriginal = null
                editingError = null
                showingCourseEditor = true
            },
            onMessages = onMessages
        )
    } else {
    Column(
        Modifier
            .fillMaxWidth()
            .xiaoxuPageWidth(XiaoxuPageLayout.ContentMaxWidth)
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        SchedulePageHeading(
            onMessages = onMessages,
            sourceLabel = "本机导入 · 待本人核对",
            onAdd = {
                editOriginal = null
                editingError = null
                showingCourseEditor = true
            },
            onSettings = { onSettingsOpenChanged(true) }
        )
        if (weekNumbers.size > 1 && showWeekPicker) {
            WeekSelector(weeks = weekNumbers, selected = selectedWeek,
                onSelect = { selectedWeek = it; showWeekPicker = false })
        }
        if (weekNumbers.isNotEmpty()) {
            // Without term settings, only imported weeks are selectable.
            // Explicitly configured semesters also expose empty teaching weeks.
            val currentIndex = weekNumbers.indexOf(selectedWeek)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(XiaoxuRadius.tile))
                    .background(XiaoxuPalette.GlassCard)
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { selectedWeek = weekNumbers[currentIndex - 1] },
                    enabled = currentIndex > 0
                ) {
                    Icon(Icons.Outlined.ChevronLeft, contentDescription = "上一周")
                }
                TextButton(
                    onClick = { showWeekPicker = !showWeekPicker },
                    enabled = weekNumbers.size > 1,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) {
                    Text(if (currentIndex >= 0) "第 $selectedWeek 周 ▾" else "选择周次 ▾",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = XiaoxuPalette.Ink)
                }
                IconButton(
                    onClick = { selectedWeek = weekNumbers[currentIndex + 1] },
                    enabled = currentIndex < weekNumbers.lastIndex
                ) {
                    Icon(Icons.Outlined.ChevronRight, contentDescription = "下一周")
                }
            }
        }
        if (calendar != null && selectedWeek != ALL_WEEKS) {
            val monday = calendar?.dateFor(selectedWeek, 1)
            val sunday = calendar?.dateFor(selectedWeek, 7)
            if (monday != null && sunday != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${monday.monthValue}月${monday.dayOfMonth}日 — " +
                            "${sunday.monthValue}月${sunday.dayOfMonth}日",
                        style = MaterialTheme.typography.labelMedium,
                        color = XiaoxuPalette.Ink2
                    )
                    if (actualCurrentWeek != null && actualCurrentWeek in weekNumbers &&
                        selectedWeek != actualCurrentWeek) {
                        TextButton(onClick = { selectedWeek = actualCurrentWeek!! }) {
                            Text("返回本周")
                        }
                    }
                }
            }
        }
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
                                dailySections = visibleSections,
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
                            dailySections = visibleSections,
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
                    onOpen = openCourse
                )
            }
        }
    }
    }
    // 详情从当前筛选的本机课程集合查找，避免回显已替换的旧课表。
    val opened = filtered.firstOrNull { it.key == selectedCourseKey }
    if (opened != null) {
        CourseDetailBottomSheet(
            lesson = opened,
            timeLabel = calendar?.periodLabel(opened.startSection, opened.endSection),
            onEdit = {
                editOriginal = data.lessons.firstOrNull { it.toUi().key == opened.key }
                editingError = null
                selectedCourseKey = null
                showingCourseEditor = editOriginal != null
            },
            onDismiss = { selectedCourseKey = null }
        )
    }
    if (showingCourseEditor) {
        fun commitEdited(changed: List<VerifiedLesson>) {
            if (editingBusy) return
            editingBusy = true
            editingError = null
            val previouslyEditedId = editOriginal?.id
            editorScope.launch {
                try {
                    val (saved, reminderOk) = withContext(Dispatchers.IO) {
                        LocalScheduleStore.saveEdited(context, changed)
                        val fresh = LocalScheduleStore.load(context)
                        val preferenceOk = if (previouslyEditedId.isNullOrBlank()) true else {
                            runCatching {
                                LocalCourseReminders.migrateCourseEnabled(
                                    context, previouslyEditedId,
                                    io.github.x07xh.xiaoxu.data.remappedCourseIds(
                                        previouslyEditedId, changed, fresh
                                    )
                                )
                            }.getOrDefault(false)
                        }
                        val scheduled = LocalCourseReminders.reconcileAfterSave(
                            context, fresh, calendar
                        )
                        fresh to (preferenceOk && scheduled)
                    }
                    onLocalLessonsChanged(saved)
                    showingCourseEditor = false
                    editOriginal = null
                    if (!reminderOk) Toast.makeText(
                        context, "课程已保存，但提醒维护失败", Toast.LENGTH_LONG).show()
                } catch (failure: Exception) {
                    editingError = failure.message ?: "本机课表修改失败"
                } finally {
                    editingBusy = false
                }
            }
        }
        CourseEditorDialog(
            original = editOriginal,
            selectedWeek = selectedWeek.takeIf { it != ALL_WEEKS },
            busy = editingBusy,
            externalError = editingError,
            onDismiss = { if (!editingBusy) showingCourseEditor = false },
            onSave = { replacement, onlyThisWeek ->
                try {
                    val modified = LocalScheduleEditor.upsert(
                        data.lessons, editOriginal?.id, replacement,
                        selectedWeek.takeIf { onlyThisWeek && it != ALL_WEEKS }
                    )
                    commitEdited(modified)
                } catch (failure: Exception) {
                    editingError = failure.message ?: "课程冲突，未保存"
                }
            },
            onDelete = { onlyThisWeek ->
                try {
                    val target = editOriginal ?: error("没有选中课程")
                    val modified = LocalScheduleEditor.delete(
                        data.lessons, target.id,
                        selectedWeek.takeIf { onlyThisWeek && it != ALL_WEEKS }
                    )
                    commitEdited(modified)
                } catch (failure: Exception) {
                    editingError = failure.message ?: "删除失败"
                }
            }
        )
    }
        }
    }
    if (settingsOpen) {
        SemesterSettingsPage(
            current = calendar,
            reminderEnabled = LocalCourseReminders.enabled(context),
            initialViewMode = viewMode,
            initialSections = visibleSections,
            onAddCourse = {
                onSettingsOpenChanged(false)
                editOriginal = null
                editingError = null
                showingCourseEditor = true
            },
            onImport = {
                onSettingsOpenChanged(false)
                openFileImport()
            },
            onExport = {
                onSettingsOpenChanged(false)
                openFileExport()
            },
            onDismiss = { onSettingsOpenChanged(false) },
            onSave = { config, wantReminders, newViewMode, newSections ->
                LocalSemesterConfigStore.saveViewMode(context, newViewMode)
                LocalSemesterConfigStore.saveSections(context, newSections)
                viewMode = newViewMode
                visibleSections = newSections
                if (config != null) {
                    LocalSemesterConfigStore.save(context, config)
                    calendar = config
                    config.currentWeek(LocalDate.now())?.let {
                        selectedWeek = it
                    }
                }
                onSettingsOpenChanged(false)
                if (!wantReminders) {
                    editorScope.launch {
                        val disabled = withContext(Dispatchers.IO) {
                            runCatching {
                                LocalCourseReminders.setEnabled(context, false)
                            }.isSuccess
                        }
                        if (!disabled) Toast.makeText(
                            context, "通知设置保存失败，请重试", Toast.LENGTH_LONG).show()
                    }
                } else if (LocalCourseReminders.canNotify(context)) {
                    editorScope.launch {
                        val reminderOk = withContext(Dispatchers.IO) {
                            runCatching {
                                LocalCourseReminders.setEnabled(context, true)
                                LocalCourseReminders.reconcileAfterSave(
                                    context, LocalScheduleStore.load(context), calendar)
                            }.getOrDefault(false)
                        }
                        if (!reminderOk) Toast.makeText(
                            context, "学期设置已保存，但提醒启用失败；请重新开启",
                            Toast.LENGTH_LONG).show()
                    }
                } else {
                    // Settings and course detail must use the same distinction:
                    // runtime permission denial can be prompted, while an app/
                    // channel notification block must be handled in Settings.
                    val permissionMissing = Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(
                            context, Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    if (permissionMissing) {
                        reminderPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        val settings = Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
                        runCatching { context.startActivity(settings) }
                            .onFailure {
                                Toast.makeText(context, "请在系统设置中允许校序通知",
                                    Toast.LENGTH_LONG).show()
                            }
                    }
                }
            }
        )
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
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DaySummary(weekday, dayLessons.size, weekday == todayWeekday)
        if (dayLessons.isEmpty()) NoLessonCard(selectedWeek)
        dayLessons.forEach { lesson ->
            LessonCard(lesson = lesson, onOpen = { onOpen(lesson.key) })
        }
        // 来源说明已在页头显示，日视图不重复铺长段说明文字。
    }
}

/**
 * 课表 P1 视觉统一：复用首页白卡与来源徽标，不复制另一套颜色。
 * 当前课表仅标注本机导入，不使用云端新鲜度计算徽标。
 */
@Composable
private fun WeekSelector(weeks: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    // The chosen teaching week may be outside the first visible chips
    // (for example week 10 while the row is still displaying weeks 1–6).
    val selectedIndex = weeks.indexOf(selected)
    val weekListState = rememberLazyListState(
        initialFirstVisibleItemIndex = (selectedIndex - 2).coerceAtLeast(0)
    )
    LaunchedEffect(weeks, selected) {
        if (selectedIndex >= 0) {
            weekListState.scrollToItem((selectedIndex - 2).coerceAtLeast(0))
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "周次",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(8.dp))
        LazyRow(state = weekListState, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
        animationSpec = tween(XiaoxuMotion.SelectionMs),
        label = "pill-bg"
    )
    val foreground by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(XiaoxuMotion.SelectionMs),
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
        animationSpec = tween(XiaoxuMotion.SelectionMs),
        label = "day-bg"
    )
    val labelColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(XiaoxuMotion.SelectionMs),
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
                animationSpec = tween(XiaoxuMotion.SelectionMs),
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
            color = XiaoxuPalette.Ink,
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
            color = XiaoxuPalette.Ink2
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
        animationSpec = tween(XiaoxuMotion.FeedbackMs),
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
                Modifier.width(4.dp).heightIn(min = XiaoxuSpacing.listRowMin)
                    .background(courseColor.accent)
            )
            Column(Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = lesson.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = courseColor.foreground,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    SectionBadge(lesson.sectionLabel)
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = XiaoxuPalette.Ink2
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
    CourseDetailBottomSheet(lesson.toUi(), onDismiss = onDismiss)
}

/**
 * 课程详情直接复刻参考图的底部抽屉结构：保留原弹出位置，
 * 标题、两张信息卡、教师/周次和提醒的位置不随页面来源变化。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CourseDetailBottomSheet(
    lesson: LessonUi, onDismiss: () -> Unit, timeLabel: String? = null,
    onEdit: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reminderSelected by remember(lesson.sourceId) {
        mutableStateOf(
            lesson.sourceId.isNotBlank() &&
                LocalCourseReminders.enabledForCourse(context, lesson.sourceId)
        )
    }
    fun applyReminder(enabled: Boolean) {
        LocalCourseReminders.setCourseEnabled(context, lesson.sourceId, enabled)
        reminderSelected = enabled
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                LocalCourseReminders.reconcileAfterSave(
                    context, LocalScheduleStore.load(context),
                    LocalSemesterConfigStore.load(context)
                )
            }
            if (!ok) Toast.makeText(context, "提醒设置已保存，但调度失败", Toast.LENGTH_LONG).show()
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && LocalCourseReminders.canNotify(context)) applyReminder(true)
        else Toast.makeText(context, "未获得通知权限，课程提醒保持关闭", Toast.LENGTH_SHORT).show()
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp),
        containerColor = Color(0xFFF6F7FC),
        dragHandle = {
            Box(
                Modifier.padding(top = 12.dp, bottom = 12.dp)
                    .size(width = 48.dp, height = 5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFFDADFE9))
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(width = 34.dp, height = 4.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFF4FAAF0)))
                        Spacer(Modifier.width(10.dp))
                        Text("课程详情", style = MaterialTheme.typography.bodyLarge,
                            color = XiaoxuPalette.Ink)
                    }
                    // Long press preserves the existing edit action without adding
                    // a new visible label or rearranging the reference layout.
                    Text(
                        lesson.title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = XiaoxuPalette.Ink,
                        modifier = Modifier
                            .then(if (onEdit == null) Modifier else Modifier.combinedClickable(
                                onClick = onEdit,
                                onLongClick = onEdit
                            ))
                            .semantics { heading() }
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = "关闭课程详情",
                        modifier = Modifier.size(29.dp), tint = XiaoxuPalette.Ink2)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CourseDetailInfoCard(
                    icon = Icons.Outlined.Schedule,
                    label = "上课时间",
                    value = "周" + WEEKDAY_SHORT.getOrElse(lesson.weekday - 1) { "?" } +
                        " · " + lesson.sectionLabel.replaceFirst("第", "第 ").replace('–', '-'),
                    modifier = Modifier.weight(1f)
                )
                CourseDetailInfoCard(
                    icon = Icons.Outlined.Room,
                    label = "上课地点",
                    value = lesson.location,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(18.dp))
            CourseDetailInfoRow(Icons.Outlined.PersonOutline, "教师", lesson.teacher)
            Spacer(Modifier.height(14.dp))
            CourseDetailInfoRow(Icons.Outlined.DateRange, "周次", lesson.weeksLabel.removePrefix("第").replace("–", "-"))
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(21.dp))
                    .background(Color(0xFFF7F1FA))
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.NotificationsNone, contentDescription = null,
                    modifier = Modifier.size(24.dp), tint = XiaoxuPalette.Ink2)
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text("课程提醒", color = XiaoxuPalette.Ink,
                        style = MaterialTheme.typography.titleMedium)
                    Text(
                        (if (reminderSelected) "已开启" else "未开启") + " · 上课前 15 分钟",
                        color = XiaoxuPalette.Ink2,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                XiaoxuSettingsSwitch(
                    checked = reminderSelected,
                    enabled = lesson.sourceId.isNotBlank(),
                    onCheckedChange = { checked ->
                        if (!checked) {
                            applyReminder(false)
                        } else {
                            val configured = LocalSemesterConfigStore.load(context)
                            if (configured == null ||
                                configured.sectionTimes[lesson.startSection] == null) {
                                Toast.makeText(context, "请先在课表设置中填写学期与上课时间",
                                    Toast.LENGTH_LONG).show()
                            } else if (!LocalCourseReminders.canNotify(context)) {
                                val runtimePermissionGranted = Build.VERSION.SDK_INT < 33 ||
                                    ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.POST_NOTIFICATIONS
                                    ) == PackageManager.PERMISSION_GRANTED
                                if (!runtimePermissionGranted && Build.VERSION.SDK_INT >= 33) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    // Permission may be granted while app notifications remain
                                    // disabled. A second permission prompt cannot repair that.
                                    val settings = Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
                                    runCatching { context.startActivity(settings) }
                                        .onFailure {
                                            Toast.makeText(context, "请在系统设置中开启校序通知",
                                                Toast.LENGTH_LONG).show()
                                        }
                                }
                            } else applyReminder(true)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun CourseDetailInfoCard(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(20.dp))
            .background(Color(0xFFF8F3FC))
            .padding(horizontal = 15.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null,
                tint = Color(0xFF1675D5), modifier = Modifier.size(21.dp))
            Spacer(Modifier.width(9.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium,
                color = XiaoxuPalette.Ink)
        }
        Text(value, style = MaterialTheme.typography.titleMedium,
            color = XiaoxuPalette.Ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CourseDetailInfoRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 30.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null,
            tint = XiaoxuPalette.Ink2, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = XiaoxuPalette.Ink2)
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = XiaoxuPalette.Ink)
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

// ---------------------------------------------------------------------------
// 未连接 / 加载 / 错误 / 无来源 / 未验证 / 可信空结果
// ---------------------------------------------------------------------------

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
