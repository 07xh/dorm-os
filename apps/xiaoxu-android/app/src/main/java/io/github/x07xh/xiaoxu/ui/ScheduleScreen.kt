package io.github.x07xh.xiaoxu.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Room
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import io.github.x07xh.xiaoxu.data.VerifiedLesson
import io.github.x07xh.xiaoxu.data.LocalSemesterConfigStore
import io.github.x07xh.xiaoxu.data.SemesterCalendar
import io.github.x07xh.xiaoxu.data.selectableTeachingWeeks
import io.github.x07xh.xiaoxu.data.LocalScheduleEditor
import io.github.x07xh.xiaoxu.data.LocalCourseReminders
import android.Manifest
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
    val startSection: Int,
    val endSection: Int
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
    startSection = startSection,
    endSection = endSection
)

/** 窄屏占满、宽屏居中并限宽，避免平板上正文被拉到全屏宽。 */
private fun Modifier.pageWidth(max: androidx.compose.ui.unit.Dp): Modifier =
    this.widthIn(max = max).fillMaxWidth()

// ---------------------------------------------------------------------------
// 入口保持现有函数名，供 MainActivity 直接调用；课表只接收本机课程和文件导入操作。
// ---------------------------------------------------------------------------

@Composable
fun CloudTimetablePanel(
    localLessons: List<VerifiedLesson>,
    openFileImport: () -> Unit,
    openFileExport: () -> Unit,
    localScheduleRevision: Int,
    onLocalLessonsChanged: (List<VerifiedLesson>) -> Unit
) {
    // Local import is the only timetable source of truth; no cloud/session dependency.
    val context = LocalContext.current
    val editorScope = rememberCoroutineScope()
    var addingFirstCourse by remember { mutableStateOf(false) }
    var addBusy by remember { mutableStateOf(false) }
    var addError by remember { mutableStateOf<String?>(null) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val expanded = maxWidth >= 840.dp
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (localLessons.isEmpty()) {
                LocalTimetableEmptyState(openFileImport) {
                    addError = null
                    addingFirstCourse = true
                }
            } else {
                ScheduleVerifiedContent(
                    data = localImportedScheduleView(localLessons),
                    expanded = expanded,
                    openFileImport = openFileImport,
                    openFileExport = openFileExport,
                    localScheduleRevision = localScheduleRevision,
                    onLocalLessonsChanged = onLocalLessonsChanged
                )
            }
        }
    }
    if (addingFirstCourse) {
        CourseEditorDialog(
            original = null, selectedWeek = null, busy = addBusy,
            externalError = addError,
            onDismiss = { if (!addBusy) addingFirstCourse = false },
            onSave = { replacement, _ ->
                if (!addBusy) {
                    addBusy = true
                    editorScope.launch {
                        try {
                            val updated = withContext(Dispatchers.IO) {
                                val edited = LocalScheduleEditor.upsert(localLessons, null, replacement)
                                LocalScheduleStore.saveEdited(context, edited)
                                val fresh = LocalScheduleStore.load(context)
                                LocalCourseReminders.reconcile(
                                    context, fresh, LocalSemesterConfigStore.load(context))
                                fresh
                            }
                            onLocalLessonsChanged(updated)
                            addingFirstCourse = false
                        } catch (failure: Exception) {
                            addError = failure.message ?: "添加课程失败"
                        } finally {
                            addBusy = false
                        }
                    }
                }
            },
            onDelete = { }
        )
    }
}

@Composable
private fun LocalTimetableEmptyState(onImport: () -> Unit, onAdd: () -> Unit) {
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
    onLocalLessonsChanged: (List<VerifiedLesson>) -> Unit
) {
    val context = LocalContext.current
    var calendar by remember { mutableStateOf(LocalSemesterConfigStore.load(context)) }
    var showSemesterSettings by rememberSaveable { mutableStateOf(false) }

    val editorScope = rememberCoroutineScope()
    val reminderPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && LocalCourseReminders.canNotify(context)) {
            editorScope.launch {
                withContext(Dispatchers.IO) {
                    LocalCourseReminders.setEnabled(context, true)
                    LocalCourseReminders.reconcile(context, data.lessons, calendar)
                }
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    editOriginal = null
                    editingError = null
                    showingCourseEditor = true
                }) { Text("新增") }
                TextButton(onClick = { showSemesterSettings = true }) { Text("设置") }
                OutlinedButton(onClick = openFileImport, modifier = Modifier.heightIn(min = 44.dp)) {
                    Text("重新导入")
                }
            }
        }
        if (weekNumbers.size > 1 && showWeekPicker) {
            WeekSelector(weeks = weekNumbers, selected = selectedWeek,
                onSelect = { selectedWeek = it; showWeekPicker = false })
        }
        if (weekNumbers.isNotEmpty()) {
            // Without term settings, only imported weeks are selectable.
            // Explicitly configured semesters also expose empty teaching weeks.
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
        if (calendar != null && selectedWeek != ALL_WEEKS) {
            val monday = calendar?.dateFor(selectedWeek, 1)
            val sunday = calendar?.dateFor(selectedWeek, 7)
            if (monday != null && sunday != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("$monday 至 $sunday",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (actualCurrentWeek != null && actualCurrentWeek in weekNumbers &&
                        selectedWeek != actualCurrentWeek) {
                        TextButton(onClick = { selectedWeek = actualCurrentWeek!! }) {
                            Text("返回本周")
                        }
                    }
                }
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
            editorScope.launch {
                try {
                    val saved = withContext(Dispatchers.IO) {
                        LocalScheduleStore.saveEdited(context, changed)
                        val fresh = LocalScheduleStore.load(context)
                        LocalCourseReminders.reconcile(context, fresh, calendar)
                        fresh
                    }
                    onLocalLessonsChanged(saved)
                    showingCourseEditor = false
                    editOriginal = null
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
    if (showSemesterSettings) {
        SemesterSettingsDialog(
            current = calendar,
            reminderEnabled = LocalCourseReminders.enabled(context),
            onExport = openFileExport,
            onDismiss = { showSemesterSettings = false },
            onSave = { config, wantReminders ->
                LocalSemesterConfigStore.save(context, config)
                calendar = config
                showSemesterSettings = false
                config.currentWeek(LocalDate.now())?.takeIf { it in weekNumbers }?.let {
                    selectedWeek = it
                }
                if (!wantReminders) {
                    editorScope.launch {
                        withContext(Dispatchers.IO) {
                            LocalCourseReminders.setEnabled(context, false)
                        }
                    }
                } else if (LocalCourseReminders.canNotify(context)) {
                    editorScope.launch {
                        withContext(Dispatchers.IO) {
                            LocalCourseReminders.setEnabled(context, true)
                            LocalCourseReminders.reconcile(context, data.lessons, config)
                        }
                    }
                } else if (Build.VERSION.SDK_INT >= 33) {
                    reminderPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    Toast.makeText(context, "请先在系统设置中允许校序通知", Toast.LENGTH_LONG).show()
                }
            }
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
 * 当前课表仅标注本机导入，不使用云端新鲜度计算徽标。
 */
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
    CourseDetailBottomSheet(lesson.toUi(), onDismiss = onDismiss)
}

/** 真实课程详情：原生模态抽屉，不推断作息时间，不提供未实现的提醒开关。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CourseDetailBottomSheet(
    lesson: LessonUi, onDismiss: () -> Unit, timeLabel: String? = null,
    onEdit: (() -> Unit)? = null
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
                text = "本机导入 · 待核对",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider()
            CourseSheetRow("星期", weekdayLong(lesson.weekday))
            CourseSheetRow("上课教室", lesson.location)
            CourseSheetRow("任课教师", lesson.teacher)
            CourseSheetRow("上课周次", lesson.weeksLabel)
            CourseSheetRow("节次", lesson.sectionLabel)
            if (timeLabel != null) CourseSheetRow("上课时间", timeLabel)
            CourseSheetRow("学期", lesson.term)
            if (onEdit != null) {
                TextButton(onClick = onEdit, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("编辑课程")
                }
            }
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
private fun ScheduleFooterNote() {
    Text(
        text = "课程来自本机导入；日期和节次时间仅依据本人设置，未进行教务实时核验。",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp)
    )
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
