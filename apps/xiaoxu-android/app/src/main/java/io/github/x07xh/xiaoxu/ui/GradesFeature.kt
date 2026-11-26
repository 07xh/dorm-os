package io.github.x07xh.xiaoxu.ui

// SPDX-License-Identifier: GPL-3.0-only
// Upstream GradesScreen.kt and MeasuredGradesHeader.kt from znjhahaha/zhengfang-apk
// @ bb3b80a6d64e4c2057c0544bb417696ceaa2a9ed. Original authors and contributors.
// Original page geometry and lists retained; owner-scoped snapshot adapter replaces
// upstream provider operations. Unsupported manual exam writes/export are omitted.
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.tyust.course.ui.system.LocalAppBackdrop
import com.tyust.course.ui.system.LocalAppOverlayBottomInset
import com.tyust.course.ui.system.LocalControlBackdrop
import com.tyust.course.ui.system.isBackdropSupported
import com.tyust.course.ui.system.ProvideWallpaperAppearance
import com.tyust.course.ui.system.rememberWallpaperRegionState
import com.tyust.course.ui.system.rememberWallpaperRegionAppearance
import com.tyust.course.ui.system.wallpaperRegion
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private fun recordedCredits(grades: List<GradeItemUi>): String {
    if (grades.isEmpty() || grades.any { it.credits.toDoubleOrNull()?.isFinite() != true }) return "—"
    return java.lang.String.format(java.util.Locale.ROOT, "%.1f", grades.sumOf { it.credits.toDouble() })
}

private fun semesterAverageGpa(grades: List<GradeItemUi>): String {
    if (grades.isEmpty() || grades.any { it.credits.toDoubleOrNull()?.let { c -> c.isFinite() && c > 0 } != true ||
            it.gpa.toDoubleOrNull()?.let { g -> g.isFinite() && g >= 0 } != true }) return "—"
    val credits = grades.sumOf { it.credits.toDouble() }
    return java.lang.String.format(java.util.Locale.ROOT, "%.2f", grades.sumOf { it.credits.toDouble() * it.gpa.toDouble() } / credits)
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
private fun SemesterSelector(semesters: List<String>, semesterLabels: Map<String,String>, currentSemester: String,
    onSemesterChange: (String) -> Unit, isLoading: Boolean, error: String, onRefresh: (() -> Unit)?) {
    AcademicTermPicker(semesters, currentSemester.takeIf { it.isNotBlank() }) { onSemesterChange(it.orEmpty()) }
    if (isLoading) AppNotice("正在读取学期")
    if (error.isNotBlank()) AppNotice(error, error = true)
}

private class GradesHeaderMetrics {
    var expanded by mutableStateOf(140.dp)
    var collapsed by mutableStateOf(68.dp)
    val travel: Dp get() = (expanded - collapsed).coerceAtLeast(1.dp)
}

@Composable
private fun rememberGradesHeaderMetrics(): GradesHeaderMetrics = remember(LocalDensity.current) { GradesHeaderMetrics() }

@Composable
private fun GradesScreen(
    currentTab: Int,
    onTabChange: (Int) -> Unit,
    semesterGrades: List<GradeItemUi>,
    semesters: List<String>,
    currentSemester: String,
    onSemesterChange: (String) -> Unit,
    semesterIsLoading: Boolean,
    overallGrades: List<GradeItemUi>,
    overallIsLoading: Boolean,
    examList: List<ExamItemUi>,
    examIsLoading: Boolean,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    gradeSnapshotLabel: String,
    examSnapshotLabel: String,
    semesterError: String = "",
    overallError: String = "",
    examError: String = "",
    supportedTabs: Set<Int> = setOf(0, 1, 2),
    semesterLabels: Map<String, String> = emptyMap(),
    semestersLoading: Boolean = false,
    semestersError: String = "",
    onRefreshSemesters: (() -> Unit)? = null,
    syncStatus: String = "",
    onConnect: () -> Unit
) {
    val availableTabs = listOf(0, 1, 2).filter { it in supportedTabs }.ifEmpty { listOf(0, 1, 2) }
    val tabTitles = availableTabs.map { listOf("学期", "总体", "考试")[it] + if (it in supportedTabs) "" else " · 未适配" }
    val isRefreshing = semesterIsLoading || overallIsLoading || examIsLoading
    val subtitle = (when (currentTab) {
        0 -> gradeSnapshotLabel
        1 -> gradeSnapshotLabel
        else -> examSnapshotLabel
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

    val statusBarHeight = 0.dp // RebuildShell already owns safeDrawing insets.
    // 【常量】top inset：Scaffold 的 topPadding 随顶栏一起缩，喂给滚动容器会让
    // 内容走两倍行程（课表页踩过）。这里固定按展开态高度留白。
    val contentTopInset = statusBarHeight + metrics.expanded
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
                    onBack = onBack,
                    isRefreshing = isRefreshing,
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
                        snapshotLabel = gradeSnapshotLabel,
                        onConnect = onConnect
                    )

                    1 -> OverallGradesContent(
                        grades = overallGrades,
                        isLoading = overallIsLoading,
                        error = overallError,
                        listState = overallListState,
                        topInset = contentTopInset,
                        bottomInset = contentBottomInset,
                        snapshotLabel = gradeSnapshotLabel,
                        onConnect = onConnect
                    )

                    else -> ExamScheduleContent(
                        exams = examList,
                        isLoading = examIsLoading,
                        error = examError,
                        listState = examListState,
                        topInset = contentTopInset,
                        bottomInset = contentBottomInset,
                        syncStatus = examSnapshotLabel,
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


@Composable
private fun GradesHeader(
    subtitle: String, tabTitles: List<String>, currentTab: Int, onTabChange: (Int) -> Unit,
    collapseFraction: Float, metrics: GradesHeaderMetrics, sampleBackdrop: Backdrop?,
    onBack: () -> Unit, isRefreshing: Boolean, onRefresh: () -> Unit
) {
    MeasuredGradesHeader(subtitle, tabTitles, currentTab, onTabChange, collapseFraction,
        sampleBackdrop, isRefreshing, onBack, onRefresh) { expanded, collapsed ->
        metrics.expanded = expanded
        metrics.collapsed = collapsed
    }
}

/** Reserve horizontal space before the segment row reaches the action row. */
private fun gradesActionReservation(collapse: Float, titleRowHeight: Float, gap: Float, actionHeight: Float): Float {
    // Actions begin vertically centered in the title row and move upward too.
    // Reserve the full rail before either moving row's bounds can meet.
    val separation = titleRowHeight + 2f * gap + actionHeight
    val overlapAt = ((titleRowHeight + 2f * gap - actionHeight) / separation.coerceAtLeast(1f))
        .coerceAtLeast(0.01f) * 0.95f
    return (collapse / overlapAt).coerceIn(0f, 1f)
}

@Composable
private fun MeasuredGradesHeader(
    subtitle: String, tabs: List<String>, selected: Int, onSelect: (Int) -> Unit,
    collapse: Float, backdrop: Backdrop?,
    refreshing: Boolean, onBack: () -> Unit, onRefresh: () -> Unit,
    onHeights: (Dp, Dp) -> Unit
) {
    val density = LocalDensity.current
    val wallpaperRegion = rememberWallpaperRegionState()
    val appearance = rememberWallpaperRegionAppearance(wallpaperRegion)
    val p = collapse.coerceIn(0f, 1f)
    val statusBar = 0.dp // RebuildShell owns the window insets.
    val effectiveBackdrop = backdrop?.takeIf { isBackdropSupported() }
    val layer = if (effectiveBackdrop != null) rememberLayerBackdrop() else null
    val controlBackdrop = if (effectiveBackdrop != null && layer != null)
        rememberCombinedBackdrop(effectiveBackdrop, layer) else null
    val anchor = if (controlBackdrop != null) rememberGlassLensRegion("grades-chips", selected, refreshing,
        (p * 8).toInt(), freshness = LocalPageGlassFreshness.current,
        drawSource = { drawBackdropSource(controlBackdrop, density, it) }) else null
    val measurer = rememberTextMeasurer()
    val tabStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium)
    val minTabWidth = tabs.sumOf { label ->
        maxOf(with(density) { 48.dp.toPx() }.roundToInt(), measurer.measure(label, tabStyle).size.width + with(density) { 24.dp.roundToPx() })
    }

    ProvideWallpaperAppearance(appearance) {
    Box(Modifier.fillMaxWidth().wallpaperRegion(wallpaperRegion).glassLensAnchor(anchor).then(wallpaperHeaderScrim())) {
        Box(Modifier.matchParentSize().then(if (layer != null) Modifier.layerBackdrop(layer) else Modifier)) {
            StatusBarFrost(statusBar + 1.dp, p, effectiveBackdrop)
            HeaderGlassSlab(((p - 0.35f) / 0.65f).coerceIn(0f, 1f), effectiveBackdrop, 26.dp,
                Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, top = statusBar + 6.dp, bottom = 4.dp))
        }
        CompositionLocalProvider(LocalControlBackdrop provides controlBackdrop, LocalGlassLensAnchor provides anchor) {
            Layout(modifier = Modifier.fillMaxWidth().testTag("grades-header"), content = {
                Column(Modifier.testTag("grades-title").semantics { if (p > 0.95f) hideFromAccessibility() }) {
                    Text("成绩与考试", fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold,
                        letterSpacing = 0.sp, color = appearance.onSurface, maxLines = 2)
                    Spacer(Modifier.height(3.dp))
                    Text(subtitle, style = MaterialTheme.typography.labelMedium, lineHeight = 18.sp,
                        color = appearance.onSurfaceVariant, maxLines = 2)
                }
                LiquidSegmentedControl(tabs, selected, onSelect,
                    modifier = Modifier.testTag("grades-segments"), height = 52.dp - 4.dp * p,
                    refractLabels = false, stableLabelRefraction = true)
                TopBarActionRail(modifier = Modifier.testTag("grades-actions")) {
                    action(0, Icons.AutoMirrored.Outlined.ArrowBack, "返回", onBack)
                    action(1, contentDescription = if (refreshing) "正在刷新" else "刷新", onClick = onRefresh, enabled = !refreshing) {
                        AnimatedLineIcon(AnimatedIconSpec.Refresh, Modifier.size(TopBarLayoutMetrics.IconSize),
                            state = if (refreshing) IconVisualState.Running else IconVisualState.Idle)
                    }
                }
                Text("成绩与考试", fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                    color = appearance.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { if (p < 0.95f) hideFromAccessibility() })
            }) { children, constraints ->
                val inset = AppSpacing.pageHorizontal.roundToPx()
                val width = constraints.maxWidth
                val innerWidth = (width - inset * 2).coerceAtLeast(1)
                val gap = 10.dp.roundToPx()
                val top = statusBar.roundToPx() + 10.dp.roundToPx()
                val bottom = 10.dp.roundToPx()
                val actions = children[2].measure(Constraints(maxWidth = innerWidth, minHeight = 48.dp.roundToPx(), maxHeight = 48.dp.roundToPx()))
                val titleWidth = (innerWidth - actions.width - gap).coerceAtLeast(1)
                val title = children[0].measure(Constraints(maxWidth = titleWidth))
                val smallTitle = children[3].measure(Constraints(maxWidth = titleWidth))
                val titleRow = maxOf(title.height, actions.height)
                val stacked = innerWidth < minTabWidth + actions.width + gap
                val expanded = 10.dp.roundToPx() + titleRow + gap + 52.dp.roundToPx() + bottom
                val collapsed = 10.dp.roundToPx() + (if (stacked) actions.height + gap else 0) + 48.dp.roundToPx() + bottom
                onHeights(expanded.toDp(), collapsed.toDp())
                val reserve = if (stacked) 0f else gradesActionReservation(p, titleRow.toFloat(), gap.toFloat(), actions.height.toFloat())
                val segmentWidth = (innerWidth - (actions.width + gap) * reserve).roundToInt().coerceAtLeast(1)
                val segmentHeight = (52.dp.toPx() - 4.dp.toPx() * p).roundToInt()
                val segments = children[1].measure(Constraints.fixed(segmentWidth, segmentHeight))
                val segmentStart = top + titleRow + gap
                val segmentEnd = top + if (stacked) actions.height + gap else 0
                val segmentY = (segmentStart + (segmentEnd - segmentStart) * p).roundToInt()
                val actionStart = top + (titleRow - actions.height) / 2
                val actionY = (actionStart + (top - actionStart) * p).roundToInt()
                val height = statusBar.roundToPx() + (expanded + (collapsed - expanded) * p).roundToInt()
                layout(width, height) {
                    title.placeRelativeWithLayer(inset, top) {
                        scaleX = 1f - p; scaleY = scaleX; alpha = 1f - p
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                    segments.placeRelative(inset, segmentY)
                    actions.placeRelative(width - inset - actions.width, actionY)
                    smallTitle.placeRelativeWithLayer(inset, top + (actions.height - smallTitle.height) / 2) {
                        alpha = if (stacked) ((p - 0.65f) / 0.35f).coerceIn(0f, 1f) else 0f
                    }
                }
            }
        }
    }
}

}

@Composable
private fun OverallGradesContent(
    grades: List<GradeItemUi>,
    isLoading: Boolean,
    error: String,
    listState: LazyListState,
    topInset: Dp,
    bottomInset: Dp,
    snapshotLabel: String,
    onConnect: () -> Unit
) {
    val rowKeys = remember(grades) { grades.mapIndexed { index, row -> row.jxbId + ":" + index } }
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
                        AppNotice(snapshotLabel)
                        SystemStatStrip(items = listOf("快照课程数" to grades.size.toString(),
                            "已记录学分" to recordedCredits(grades)))
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
    snapshotLabel: String,
    onConnect: () -> Unit
) {
    val totalCredits = remember(grades) { recordedCredits(grades) }
    val averageGpa = remember(grades) { semesterAverageGpa(grades) }
    val rowKeys = remember(grades) { grades.mapIndexed { index, row -> row.jxbId + ":" + index } }

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

                when {
                    isLoading && grades.isEmpty() -> GlassLoadingState(text = "正在加载学期成绩…")
                    error.isNotBlank() && grades.isEmpty() -> SystemEmptyState(title = "学期成绩加载失败", message = error)
                    grades.isEmpty() -> SystemEmptyState(
                        title = "暂无学期成绩",
                        message = if (currentSemester.isBlank()) {
                            "尚无已读取成绩，请刷新或检查数据连接。"
                        } else {
                            "$currentSemester 快照暂无成绩，不代表学校实时记录"
                        }, action = { TextButton(onClick = onConnect) { Text("数据连接") } }
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
                if (semesters.isNotEmpty()) AcademicTermPicker(semesters, currentSemester.takeIf { it.isNotBlank() }) { onSemesterChange(it.orEmpty()) }
                if (exams.isNotEmpty()) {
                    SystemStatStrip(items = listOf("考试数" to exams.size.toString()))
                    SystemSectionHeader(title = "考试列表", subtitle = null)
                }
                if (error.isNotBlank()) Text(error, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (exams.isEmpty()) item {
            SystemEmptyState(title = if (isLoading) "正在同步考试安排" else if (syncStatus == "尚无已保存快照" || syncStatus == "未连接云端账户") "尚未读取考试" else "暂无考试安排",
                message = error.ifBlank { "仅展示已读取快照，不代表学校没有考试。" },
                action = { TextButton(onClick = onConnect) { Text("数据连接") } })
        }
        items(exams, key = { it.recordId.ifBlank { it.courseName + it.examTime + it.examName } }) { exam ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ExamItemRow(exam = exam)
            }
        }
    }
}



@Composable
internal fun AcademicRecordsFeature(data: AppWorkspaceState, actions: AppPageActions, initialTab: Int, onBack: () -> Unit) {
    val cloud = data.cloud
    val owner = cloud.ownerId.takeIf { cloud.authenticated }
    val scope = rememberCoroutineScope()
    key(owner, initialTab) {
        var tab by rememberSaveable { mutableStateOf(initialTab) }
        var gradeTerm by rememberSaveable { mutableStateOf("") }
        var examTerm by rememberSaveable { mutableStateOf("") }
        val gradeSnapshot = cloud.grades.takeIf { owner != null && cloud.gradesOwner == owner }
        val examSnapshot = cloud.exams.takeIf { owner != null && cloud.examsOwner == owner }
        val gradeRows = gradeSnapshot?.takeUnless { it.status == "not_connected" }?.rows.orEmpty()
        val examRows = examSnapshot?.takeUnless { it.status == "not_connected" }?.rows.orEmpty()
        val gradeTerms = gradeRows.map { it.term }.filter { it.isNotBlank() }.distinct().sortedDescending()
        val examTerms = examRows.map { it.term }.filter { it.isNotBlank() }.distinct().sortedDescending()
        val currentTerm = if (tab == 2) examTerm.takeIf { it in examTerms }.orEmpty()
            else gradeTerm.takeIf { it in gradeTerms }.orEmpty()
        val grades = remember(gradeRows) { gradeRows.map { row ->
            GradeItemUi(courseName = row.title, grade = row.score.ifBlank { "未记录" },
                credits = row.credits?.toString().orEmpty(), gpa = row.gradePoint?.toString().orEmpty(),
                courseType = "", term = row.term, jxbId = row.id, detail = row.note)
        } }
        val exams = remember(examRows, examTerm) { examRows.filter { examTerm !in examTerms || it.term == examTerm }
            .sortedWith(compareBy({ it.date }, { it.startTime }, { it.id })).map { row ->
                ExamItemUi(courseName = row.title, examTime = listOf(row.date, row.startTime, row.endTime)
                    .filter { it.isNotBlank() }.joinToString(" "), location = row.location, seatNumber = "",
                    examName = "考试", teacher = row.teacher, sourceId = row.id,
                    recordId = row.term + ":" + row.id)
            } }
        fun snapshotLabel(status: String?, provenance: String?, capturedAt: String?): String = when {
            owner == null -> "未连接云端账户"
            status == null || status == "not_connected" -> "尚无已保存快照"
            else -> listOfNotNull(if (provenance == "trusted_readonly") "教务可信只读快照" else "个人导入快照",
                "非实时", if (status == "stale") "已过期" else null, capturedAt).joinToString(" · ")
        }
        val gradeLabel = snapshotLabel(gradeSnapshot?.status, gradeSnapshot?.provenance, gradeSnapshot?.capturedAt)
        val examLabel = snapshotLabel(examSnapshot?.status, examSnapshot?.provenance, examSnapshot?.capturedAt)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = AppSpacing.workspaceMaxWidth).fillMaxSize()) {
                GradesScreen(currentTab = tab, onTabChange = { tab = it },
                    semesterGrades = grades.filter { currentTerm.isBlank() || it.term == currentTerm },
                    semesters = if (tab == 2) examTerms else gradeTerms, currentSemester = currentTerm,
                    onSemesterChange = { if (tab == 2) examTerm = it else gradeTerm = it },
                    semesterIsLoading = cloud.gradesBusy, overallGrades = grades,
                    overallIsLoading = cloud.gradesBusy, examList = exams, examIsLoading = cloud.examsBusy,
                    onRefresh = { if (owner == null) actions.navigate(AppRoute.ACCOUNT) else scope.launch {
                        if (tab == 2) actions.readExams() else actions.readGrades()
                    } },
                    onBack = onBack, gradeSnapshotLabel = gradeLabel, examSnapshotLabel = examLabel,
                    semesterError = cloud.gradesError.orEmpty(), overallError = cloud.gradesError.orEmpty(),
                    examError = cloud.examsError.orEmpty(), onConnect = { actions.navigate(AppRoute.CONNECTIONS) })
            }
        }
    }
}
