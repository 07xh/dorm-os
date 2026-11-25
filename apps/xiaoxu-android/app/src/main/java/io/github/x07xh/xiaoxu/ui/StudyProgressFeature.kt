package io.github.x07xh.xiaoxu.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.x07xh.xiaoxu.data.NativeSchoolRecord
import java.text.DateFormat
import java.util.Date
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch

/** Existing owner's local course catalog only. No inference from homework/plan
 * status, no invented chapter count and no duplicate school connection. */
internal data class StudyCoursePreview(val id: String, val title: String, val capturedAt: Long)

internal fun latestStudyCoursePreviews(records: List<NativeSchoolRecord>): List<StudyCoursePreview>? {
    val binding = records.singleOrNull {
        it.provider == "chaoxing" && it.kind == "account_binding" &&
            it.fingerprint == "current" && it.sourceHost == "i.chaoxing.com" &&
            it.capturedAt > 0 && it.details.matches(Regex("[a-f0-9]{64}"))
    } ?: return null
    return records.asSequence().filter {
        it.provider == "chaoxing" && it.kind == "course" &&
            it.sourceHost == "mooc2-ans.chaoxing.com" && it.details == "课程列表" &&
            it.capturedAt == binding.capturedAt && it.fingerprint.matches(Regex("[a-f0-9]{64}")) &&
            it.title.isNotBlank()
    }.distinctBy { it.fingerprint }.sortedWith(compareBy({ it.title }, { it.fingerprint }))
        .map { StudyCoursePreview(it.fingerprint, it.title, it.capturedAt) }.toList()
}

@Composable
internal fun ColumnScope.StudyProgressFeature(data: AppWorkspaceState, actions: AppPageActions) {
    val state = data.cloud
    val owner = state.ownerId.takeIf { state.authenticated }
    val connected = owner != null && state.connectionsFresh && state.platforms.any {
        it.id == "chaoxing" && it.sessionState == "active" && it.authorization == "verified_session"
    }
    if (connected) {
        key(owner) { AuthorizedStudyProgress(data, actions) }
        return
    }
    AppNotice("官方课程进度需要本人学习通云端授权。本机课程目录仍可单独使用。")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = actions.openWebsite) { Text("学校连接中心") }
        val scope = rememberCoroutineScope()
        AcademicReadAction("刷新连接", state.busy) { scope.launch { actions.refreshCloud() } }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = { actions.connectLocal("chaoxing") }) { Text("读取学习通") }
    }
    AppSnapshotContent(data.nativeRecords) { records ->
        val courses = remember(records) { latestStudyCoursePreviews(records) }
        if (courses == null || courses.isEmpty()) {
            SystemEmptyState("尚无本次读取的课程名单", "请连接学习通读取本人记录；不能据此推断学校没有课程。")
        } else {
            AppNotice("已读取课程名单 · " + DateFormat.getDateTimeInstance().format(Date(courses.first().capturedAt)))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.item)) {
                item(key = "progress-source") {
                    AppNotice("现有只读来源尚未提供课程学习百分比。作业与学习计划完成状态不等于课程进度。")
                }
                items(courses, key = { it.id }) { course ->
                    InsetGroupedSection {
                        InsetGroupedRow(title = course.title, subtitle = "课程进度未读取",
                            showDivider = false)
                    }
                }
            }
        }
    }
}


@Composable
private fun ColumnScope.AuthorizedStudyProgress(data: AppWorkspaceState, actions: AppPageActions) {
    val state = data.cloud
    val scope = rememberCoroutineScope()
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var attempted by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val courses = state.chaoxingProgressCourses.takeIf { state.chaoxingProgressOwner == state.ownerId }.orEmpty()
    val selected = courses.firstOrNull { it.key == selectedKey }
    val progress = state.chaoxingCourseProgress.takeIf { state.chaoxingProgressOwner == state.ownerId && it?.course == selected }
    val reading = busy || state.busy || state.schoolSyncBusyProvider != null
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("学习通 · 官方任务点", style = MaterialTheme.typography.titleMedium)
        AcademicReadAction("读取课程", reading) {
            busy = true
            selectedKey = null
            scope.launch {
                try { attempted = actions.readChaoxingProgress(null) }
                finally { busy = false }
            }
        }
    }
    state.chaoxingProgressError?.let { AppNotice(it, error = true) }
    LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppSpacing.item)) {
        item { AppNotice("仅查询官方任务点总数与完成数，不提交学习记录。") }
        if (courses.isEmpty() && !reading && state.chaoxingProgressError == null) item {
            SystemEmptyState(if (attempted) "已读取，暂无课程" else "尚未读取课程", "点击读取课程获取本人官方列表。")
        }
        items(courses, key = { it.key }) { course ->
            InsetGroupedSection {
                InsetGroupedRow(title = course.title, subtitle = if (selectedKey == course.key) "当前课程" else null,
                    enabled = !reading, showDivider = false,
                    onClick = {
                        selectedKey = course.key
                        busy = true
                        scope.launch { try { actions.readChaoxingProgress(course) } finally { busy = false } }
                    })
                if (selectedKey == course.key && progress != null) {
                    SystemStatStrip(items = listOf("已完成任务点" to progress.completed.toString(),
                        "任务点总数" to progress.total.toString()))
                    if (progress.total > 0) LinearProgressIndicator(
                        progress = { progress.completed.toFloat() / progress.total.toFloat() },
                        modifier = Modifier.fillMaxWidth().padding(16.dp))
                    else AppNotice("任务点总数为零，完成比例不适用。")
                } else if (selectedKey == course.key && reading) AppNotice("正在查询课程进度")
            }
        }
    }
}
