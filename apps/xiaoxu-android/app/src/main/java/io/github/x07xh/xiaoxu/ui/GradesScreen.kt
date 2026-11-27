package io.github.x07xh.xiaoxu.ui

// SPDX-License-Identifier: GPL-3.0-only
// Source: znjhahaha/zhengfang-apk @ bb3b80a6d64e4c2057c0544bb417696ceaa2a9ed, ui/screen/GradesScreen.kt.
// Original authors and contributors.


import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.tyust.course.ui.system.LocalAppBackdrop
import com.tyust.course.ui.system.LocalAppOverlayBottomInset
import com.tyust.course.ui.system.isBackdropSupported
import com.tyust.course.ui.theme.SemanticDanger
import com.tyust.course.ui.theme.SemanticInfo
import com.tyust.course.ui.theme.SemanticSuccess
import com.tyust.course.ui.theme.SemanticWarning

data class GradeItemUi(
    val courseName: String,
    val grade: String,
    val credits: String,
    val gpa: String,
    val courseType: String,
    val year: String = "",
    val term: String = "",
    val college: String = "",
    val courseCode: String = "",
    val teachingClass: String = "",
    val jxbId: String = "",
    val detail: String = ""
)

data class ExamItemUi(
    val courseName: String,
    val examTime: String,
    val location: String,
    val seatNumber: String,
    val examName: String,
    val teacher: String,
    val sourceId: String = "",
    val recordId: String = ""
)


data class OverallStatsUi(
    val gpa: String,
    val credits: String,
    val courseCount: Int,
    val excellent: Int,
    val good: Int,
    val medium: Int,
    val pass: Int
)

// ── 顶栏折叠几何 ────────────────────────────────────────────────
// 与课表页同一条铁律：展开态与折叠态的高度【差】必须等于折叠行程，
// 于是顶栏收缩与内容上滚 1:1 对消、全程跟手。脱钩就会互相追赶。
//
// 总高不再写死：标题块要跟着系统字体缩放走，三个高度全部由下面这组 token 推导，
// 那条恒等式因此是结构性的（见 GradesHeaderMetrics.travel），不靠人肉对账。

private class GradesHeaderMetrics {
    var expanded by mutableStateOf(140.dp)
    var collapsed by mutableStateOf(68.dp)
    val travel: Dp get() = (expanded - collapsed).coerceAtLeast(1.dp)
}

@Composable
private fun rememberGradesHeaderMetrics(): GradesHeaderMetrics = remember(LocalDensity.current) { GradesHeaderMetrics() }

@Composable
fun GradesScreen(
    currentTab: Int,
    onTabChange: (Int) -> Unit,
    semesterGrades: List<GradeItemUi>,
    semesters: List<String>,
    currentSemester: String,
    onSemesterChange: (String) -> Unit,
    semesterIsLoading: Boolean,
    overallGrades: List<GradeItemUi>,
    overallStats: OverallStatsUi,
    overallIsLoading: Boolean,
    examList: List<ExamItemUi>,
    examIsLoading: Boolean,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onConnect: () -> Unit,
    semesterError: String = "",
    overallError: String = "",
    examError: String = "",
    supportedTabs: Set<Int> = setOf(0, 1, 2),
    semesterLabels: Map<String, String> = emptyMap(),
    semestersLoading: Boolean = false,
    semestersError: String = "",
    onRefreshSemesters: (() -> Unit)? = null,
    syncStatus: String = ""
) {
    val availableTabs = listOf(0, 1, 2).filter { it in supportedTabs }.ifEmpty { listOf(0, 1, 2) }
    val tabTitles = availableTabs.map { listOf("学期", "总体", "考试")[it] + if (it in supportedTabs) "" else " · 未适配" }
    val isRefreshing = semesterIsLoading || overallIsLoading || examIsLoading
    val subtitle = (when (currentTab) {
        0 -> "${semesterGrades.size} 门课程"
        1 -> "累计成绩与分布概览"
        else -> "近期考试安排"
    }) + if (syncStatus.isBlank()) "" else " · $syncStatus"

    // 每个 tab 一份滚动位置：共用一份会让切 tab 时另一页的位置被顶掉，
    // 折叠进度也跟着跳。顶栏读当前 tab 那一份。
    val semesterListState = rememberLazyListState()
    val overallListState = rememberLazyListState()
    val examListState = rememberLazyListState()
    val activeListState = when (currentTab) {
        0 -> semesterListState
        1 -> overallListState
        else -> examListState
    }
    val metrics = rememberGradesHeaderMetrics()
    val travelPx = with(LocalDensity.current) { metrics.travel.toPx() }
    val headerCollapse by remember(travelPx, activeListState) {
        derivedStateOf {
            // 第 0 项就是首屏那一块内容，它比行程高得多，所以只看它的偏移；
            // 一旦滚过第 0 项，直接判定为完全折叠。
            if (activeListState.firstVisibleItemIndex > 0) {
                1f
            } else {
                (activeListState.firstVisibleItemScrollOffset / travelPx).coerceIn(0f, 1f)
            }
        }
    }

    // 内容的捕获层。顶栏玻璃采样「壁纸 + 这一层」，于是列表从玻璃条底下穿过时
    // 会被折射；顶栏本身不在这一层内，不构成自采样。
    val wallpaperBackdrop = LocalAppBackdrop.current
    val contentBackdrop = if (wallpaperBackdrop != null && isBackdropSupported()) {
        rememberLayerBackdrop()
    } else {
        null
    }
    val headerSampleBackdrop = if (wallpaperBackdrop != null && contentBackdrop != null) {
        rememberCombinedBackdrop(wallpaperBackdrop, contentBackdrop)
    } else {
        null
    }

    // 【常量】top inset：Scaffold 的 topPadding 随顶栏一起缩，喂给滚动容器会让
    // 内容走两倍行程（课表页踩过）。这里固定按展开态高度留白。
    val contentTopInset = metrics.expanded
    val contentBottomInset = LocalAppOverlayBottomInset.current + 24.dp

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            // topBar slot 只测量单个子项，内联通知必须与顶栏在同一个 Column 里
            Column(modifier = Modifier.moduleEntrance(0)) {
                GradesHeader(
                    subtitle = subtitle,
                    tabTitles = tabTitles,
                    currentTab = availableTabs.indexOf(currentTab).coerceAtLeast(0),
                    onTabChange = { index -> availableTabs.getOrNull(index)?.takeIf { it in supportedTabs }?.let(onTabChange) },
                    collapseFraction = headerCollapse,
                    metrics = metrics,
                    sampleBackdrop = headerSampleBackdrop,
                    isRefreshing = isRefreshing,
                    onBack = onBack,
                    onRefresh = onRefresh
                )
            }
        }
    ) { padding ->
        // 内容铺满整屏、从顶栏底下穿过；留白由各自的 contentPadding 负责（见上）。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(padding)
                .then(
                    if (contentBackdrop != null) {
                        Modifier.layerBackdrop(contentBackdrop)
                    } else {
                        Modifier
                    }
                )
        ) {
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "grades_tab_content"
            ) { tab ->
                when (tab) {
                    0 -> SemesterGradesContent(
                        grades = semesterGrades,
                        semesters = semesters,
                        semesterLabels = semesterLabels,
                        currentSemester = currentSemester,
                        onSemesterChange = onSemesterChange,
                        isLoading = semesterIsLoading,
                        error = semesterError,
                        listState = semesterListState,
                        topInset = contentTopInset,
                        bottomInset = contentBottomInset,
                        semestersLoading = semestersLoading,
                        semestersError = semestersError,
                        onRefreshSemesters = onRefreshSemesters,
                        onConnect = onConnect
                    )

                    1 -> OverallGradesContent(
                        grades = overallGrades,
                        stats = overallStats,
                        isLoading = overallIsLoading,
                        error = overallError,
                        listState = overallListState,
                        topInset = contentTopInset,
                        bottomInset = contentBottomInset
                    )

                    else -> ExamScheduleContent(
                        exams = examList,
                        isLoading = examIsLoading,
                        error = examError,
                        listState = examListState,
                        topInset = contentTopInset,
                        bottomInset = contentBottomInset,
                        syncStatus = syncStatus,
                        onConnect = onConnect,
                        semesters = semesters,
                        currentSemester = currentSemester,
                        onSemesterChange = onSemesterChange
                    )
                }
            }
        }
    }
}

/**
 * 成绩页顶栏：上划收拢成一条悬浮玻璃胶囊。
 *
 * 展开态是 iOS 的大标题（直接浮在内容上）+ 一条**全宽原尺寸**的选择栏；折叠态只留选择栏。
 * 三个标签本身就说明了在哪一页，小标题是多余信息。
 *
 * 两枚动作钮**不属于任何一行**——它们是这个 Box 的第二个子节点，钉在右上角。理由有两条：
 * 1. 放进选择栏那一行就要和它抢宽度，三段中文标签会被挤成一条窄带；
 * 2. 标题块整块收掉之后，"标题行右上角"与"选择栏右侧"这两个位置的 y 几乎重合，
 *    所以芯片全程只走几 dp——真正在动的是选择栏升上来、以及它右侧连续让出的那一段。
 */
@Composable
private fun GradesHeader(
    subtitle: String, tabTitles: List<String>, currentTab: Int, onTabChange: (Int) -> Unit,
    collapseFraction: Float, metrics: GradesHeaderMetrics, sampleBackdrop: Backdrop?,
    isRefreshing: Boolean,
    onBack: () -> Unit, onRefresh: () -> Unit
) {
    MeasuredGradesHeader(subtitle, tabTitles, currentTab, onTabChange, collapseFraction, sampleBackdrop,
        isRefreshing, onBack, onRefresh) { expanded, collapsed ->
        metrics.expanded = expanded
        metrics.collapsed = collapsed
    }
}
@Composable
private fun OverallGradesContent(
    grades: List<GradeItemUi>,
    stats: OverallStatsUi,
    isLoading: Boolean,
    error: String,
    listState: LazyListState,
    topInset: Dp,
    bottomInset: Dp
) {
    val rowKeys = remember(grades) { gradeRowKeys(grades) }
    when {
        isLoading && grades.isEmpty() -> {
            Box(
                modifier = Modifier.fillMaxSize().padding(top = topInset),
                contentAlignment = Alignment.Center
            ) {
                GlassLoadingState(text = "正在加载总体成绩…")
            }
        }

        grades.isEmpty() -> {
            Box(
                modifier = Modifier.fillMaxSize().padding(top = topInset),
                contentAlignment = Alignment.Center
            ) {
                SystemEmptyState(
                    title = if (error.isNotBlank()) "总体成绩加载失败" else "暂无总体成绩",
                    message = error.ifBlank { "当前已保存快照未提供成绩，不代表学校没有成绩。" }
                )
            }
        }

        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(
                    start = AppSpacing.pageHorizontal,
                    end = AppSpacing.pageHorizontal,
                    top = topInset,
                    bottom = bottomInset
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Column(Modifier.moduleEntrance(1), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        GradeRefreshStatus(isLoading, error)
                        SystemStatStrip(
                            items = listOf(
                                "快照绩点" to stats.gpa,
                                "已记录学分" to stats.credits,
                                "总课程" to stats.courseCount.toString()
                            )
                        )
                        GradeDistributionCard(stats = stats)
                        SystemSectionHeader(
                            title = "课程明细",
                            subtitle = "共 ${grades.size} 门课程"
                        )
                    }
                }

                items(grades.size, key = { rowKeys[it] }, contentType = { "grade" }) { index ->
                    GradeItemRow(item = grades[index])
                }
            }
        }
    }
}

@Composable
private fun SemesterGradesContent(
    grades: List<GradeItemUi>,
    semesters: List<String>,
    semesterLabels: Map<String, String>,
    currentSemester: String,
    onSemesterChange: (String) -> Unit,
    isLoading: Boolean,
    error: String,
    listState: LazyListState,
    topInset: Dp,
    bottomInset: Dp,
    semestersLoading: Boolean,
    semestersError: String,
    onRefreshSemesters: (() -> Unit)?,
    onConnect: () -> Unit
) {
    val totalCredits = remember(grades) { recordedCredits(grades) }
    val averageGpa = remember(grades) { semesterAverageGpa(grades) }
    val rowKeys = remember(grades) { gradeRowKeys(grades) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(
            start = AppSpacing.pageHorizontal,
            end = AppSpacing.pageHorizontal,
            top = topInset,
            bottom = bottomInset
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(Modifier.moduleEntrance(1), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SemesterSelector(
                    semesters = semesters, semesterLabels = semesterLabels,
                    currentSemester = currentSemester,
                    onSemesterChange = onSemesterChange,
                    isLoading = semestersLoading,
                    error = semestersError,
                    onRefresh = onRefreshSemesters
                )
                androidx.compose.material3.TextButton(onClick = onConnect) { Text("数据连接") }

                when {
                    isLoading && grades.isEmpty() -> GlassLoadingState(text = "正在加载学期成绩…")
                    error.isNotBlank() && grades.isEmpty() -> SystemEmptyState(title = "学期成绩加载失败", message = error)
                    grades.isEmpty() -> SystemEmptyState(
                        title = "暂无学期成绩",
                        message = if (currentSemester.isBlank()) {
                            "快照暂无成绩，不代表学校实时记录"
                        } else {
                            "$currentSemester 快照暂无成绩，不代表学校实时记录"
                        }
                    )
                    else -> {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            GradeRefreshStatus(isLoading, error)
                            SystemStatStrip(
                                items = listOf(
                                    "快照绩点" to averageGpa,
                                    "已记录学分" to totalCredits,
                                    "课程数" to grades.size.toString()
                                )
                            )
                            SystemSectionHeader(
                                title = "课程明细",
                                subtitle = null
                            )
                        }
                    }
                }
            }
        }

        if (grades.isNotEmpty()) {
            items(grades.size, key = { rowKeys[it] }, contentType = { "grade" }) { index ->
                GradeItemRow(item = grades[index])
            }
        }
    }
}

@Composable
private fun GradeRefreshStatus(isLoading: Boolean, error: String) {
    if (isLoading) {
        androidx.compose.material3.LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().height(2.dp)
        )
    } else if (error.isNotBlank()) {
        Text(error, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error)
    }
}

@Composable
internal fun SemesterSelector(
    semesters: List<String>,
    semesterLabels: Map<String, String>,
    currentSemester: String,
    onSemesterChange: (String) -> Unit,
    isLoading: Boolean = false,
    error: String = "",
    onRefresh: (() -> Unit)? = null
) {
    val selectedIndex = semesters.indexOf(currentSemester).takeIf { it >= 0 }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SystemPicker(
            options = semesters.map { semesterLabels[it] ?: it.ifBlank { "全部学期" } },
            selectedIndex = selectedIndex,
            onSelect = { index -> semesters.getOrNull(index)?.let(onSemesterChange) },
            modifier = Modifier.fillMaxWidth(),
            label = "学期",
            maxLabelLines = 2,
            maxSelectedLabelLines = 1,
            placeholder = if (isLoading) "正在加载学期…" else "选择学期",
            actionLabel = if (onRefresh != null && !isLoading) "刷新学期列表" else null,
            onAction = onRefresh.takeUnless { isLoading }
        )
        when {
            isLoading -> Text("正在加载学期列表…", style = MaterialTheme.typography.bodySmall)
            error.isNotBlank() -> Text("学期列表加载失败：$error", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error)
            semesters.size <= 1 -> Text("快照暂无可选学期", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun GradeDistributionCard(
    stats: OverallStatsUi
) {
    SystemCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = MaterialTheme.colorScheme.surface,
        borderColor = MaterialTheme.colorScheme.outlineVariant
    ) {
        SystemSectionHeader(
            title = "成绩分布",
            subtitle = "按已统计课程划分"
        )

        GlassSegmentedBar(
            segments = listOf(
                stats.excellent.toFloat() to SemanticSuccess,
                stats.good.toFloat() to SemanticInfo,
                stats.medium.toFloat() to SemanticWarning,
                stats.pass.toFloat() to SemanticDanger
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DistributionItem("优", stats.excellent, SystemTone.Success, Modifier.weight(1f))
            DistributionItem("良", stats.good, SystemTone.Info, Modifier.weight(1f))
            DistributionItem("中", stats.medium, SystemTone.Warning, Modifier.weight(1f))
            DistributionItem("及", stats.pass, SystemTone.Danger, Modifier.weight(1f))
        }
    }
}

@Composable
private fun DistributionItem(
    label: String,
    count: Int,
    tone: SystemTone,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SystemStatusBadge(
            text = label,
            tone = tone
        )
        Text(
            text = "$count 门",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GradeItemRow(
    item: GradeItemUi
) {
    var expanded by rememberSaveable(item.year, item.term, item.courseCode, item.courseName, item.jxbId) { mutableStateOf(false) }
    val notes = item.detail
    val gradeColor = getGradeColor(item.grade)
    val reduced = rememberGlassAccessibilityMode().reduceMotion
    val hasDetail = notes.isNotBlank() || item.gpa.isNotBlank() || item.credits.isNotBlank() ||
        item.courseType.isNotBlank() || item.teachingClass.isNotBlank()
    SystemCard(
        modifier = Modifier.fillMaxWidth().moduleEntrance(2),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        onClick = if (hasDetail) ({ expanded = !expanded }) else null
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // 左侧：课程名 + 代码/学院
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.courseName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val subInfo = buildString {
                        if (item.courseCode.isNotEmpty()) append(item.courseCode)
                        if (item.college.isNotEmpty()) {
                            if (isNotEmpty()) append("   ")
                            append(item.college)
                        }
                    }
                    if (subInfo.isNotEmpty()) {
                        Text(
                            text = subInfo,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // 右侧：学分 + 成绩 + chevron
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (item.credits.isBlank()) "学分未记录" else "${item.credits}学分",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.grade,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = gradeColor
                    )
                }

                if (hasDetail) {
                    AnimatedLineIcon(
                        AnimatedIconSpec.Chevron, Modifier.size(20.dp),
                        state = if (expanded) IconVisualState.Expanded else IconVisualState.Idle,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // 展开区域：成绩构成进度条
            AnimatedVisibility(
                visible = expanded,
                enter = ModuleMotion.expand(reduced),
                exit = ModuleMotion.collapse(reduced)
            ) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    SystemDivider(alpha = 0.5f)
                    Spacer(modifier = Modifier.height(10.dp))

                    if (notes.isNotBlank()) Text(notes, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)

                    // 元信息落到小芯片上（复用筛选面板那一枚）：原先绩点根本没露过面，
                    // 教学班只在"无分项"那条分支里以裸文字出现。
                    val metaChips = buildList {
                        if (item.gpa.isNotBlank()) add("绩点 ${item.gpa}")
                        if (item.credits.isNotBlank()) add("${item.credits} 学分")
                        if (item.courseType.isNotBlank()) add(item.courseType)
                        if (item.teachingClass.isNotBlank()) add(item.teachingClass)
                    }
                    if (metaChips.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            metaChips.forEach { label ->
                                GlassFilterChip(
                                    label = label,
                                    selected = false,
                                    compact = true
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExamScheduleContent(
    exams: List<ExamItemUi>, isLoading: Boolean, error: String,
    listState: LazyListState, topInset: Dp, bottomInset: Dp,
    syncStatus: String, onConnect: () -> Unit,
    semesters: List<String>, currentSemester: String, onSemesterChange: (String) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), state = listState,
        contentPadding = PaddingValues(start = AppSpacing.pageHorizontal, end = AppSpacing.pageHorizontal, top = topInset, bottom = bottomInset),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SemesterSelector(semesters, emptyMap(), currentSemester, onSemesterChange)
                androidx.compose.material3.TextButton(onClick = onConnect) { Text("数据连接") }
                SystemStatStrip(items = listOf("快照考试数" to exams.size.toString()))
                SystemSectionHeader(title = "考试列表", subtitle = syncStatus)
                if (error.isNotBlank()) Text(error, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (exams.isEmpty()) item {
            SystemEmptyState(title = if (isLoading) "正在同步考试安排" else "暂无考试安排",
                message = "仅展示已读取快照，不代表学校没有考试。")
        }
        items(exams, key = { it.recordId.ifBlank { it.courseName + it.examTime + it.examName } }) { exam ->
            ExamItemRow(exam = exam)
        }
    }
}

@Composable
private fun ExamItemRow(
    exam: ExamItemUi
) {
    val examTone = if (exam.examName.contains("期中")) SystemTone.Warning else SystemTone.Info

    SystemCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = exam.courseName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(12.dp))
            SystemStatusBadge(
                text = if (exam.examName.isBlank()) "考试" else exam.examName,
                tone = examTone
            )
        }

        ExamDetailRow(
            icon = Icons.Default.Schedule,
            text = exam.examTime.ifBlank { "未提供考试时间" }
        )
        ExamDetailRow(
            icon = Icons.Default.LocationOn,
            text = buildString {
                append(exam.location.ifBlank { "未提供地点" })
                if (exam.seatNumber.isNotBlank()) {
                    append(" · 座位 ${exam.seatNumber}")
                }
            }
        )
        ExamDetailRow(
            icon = Icons.Default.Person,
            text = exam.teacher.ifBlank { "未提供教师信息" }
        )
    }
}

@Composable
private fun ExamDetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

fun getGradeColor(grade: String): Color {
    val score = grade.trim().toDoubleOrNull()?.takeIf { it.isFinite() }

    if (score != null) {
        return when {
            score >= 90 -> SemanticSuccess
            score >= 80 -> SemanticInfo
            score >= 70 -> SemanticWarning
            score >= 60 -> Color(0xFFB26A00)
            else -> SemanticDanger
        }
    }

    return when (grade) {
        "优秀" -> SemanticSuccess
        "良好" -> SemanticInfo
        "中等" -> SemanticWarning
        "及格" -> Color(0xFFB26A00)
        else -> SemanticDanger
    }
}
