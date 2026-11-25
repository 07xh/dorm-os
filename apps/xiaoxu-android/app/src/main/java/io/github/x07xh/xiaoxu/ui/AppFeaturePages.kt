package io.github.x07xh.xiaoxu.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import io.github.x07xh.xiaoxu.SecondClassPage
import io.github.x07xh.xiaoxu.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Only callbacks are passed to pages. They cannot mutate navigation directly,
 * perform private HTTP requests, or invent official task completion.
 */
internal class AppPageActions(
    val navigate: (AppRoute) -> Unit,
    val importTimetable: () -> Unit,
    val exportTimetable: () -> Unit,
    val authorize: (String) -> Unit,
    val connectLocal: (String) -> Unit,
    val openWebsite: () -> Unit,
    val refreshCloud: suspend () -> Unit,
    val updateLocalTask: suspend (Long, Boolean) -> Boolean,
    val openArticle: (String) -> Unit
)

@Composable
internal fun AppFeaturePage(
    route: AppRoute, data: AppWorkspaceState, actions: AppPageActions,
    reminderTaskId: Long?, consumeTask: () -> Unit,
    reminderLessonId: String?, consumeLesson: () -> Unit
) {
    AppPageFrame(title = route.title, onBack = route.parent?.let { parent -> { actions.navigate(parent) } }) {
        when (route) {
            AppRoute.HOME -> HomeFeature(data, actions)
            AppRoute.SCHEDULE -> ScheduleFeature(data.lessons, actions, reminderLessonId, consumeLesson)
            AppRoute.ACADEMIC -> {
                InsetGroupedSection {
                    AppGroupedEntry("成绩", onClick = { actions.navigate(AppRoute.GRADES) })
                    AppGroupedEntry("考试", onClick = { actions.navigate(AppRoute.EXAMS) })
                    AppGroupedEntry("第二课堂", onClick = { actions.navigate(AppRoute.SECOND_CLASS) })
                    AppGroupedEntry("学习进度", showDivider = false, onClick = { actions.navigate(AppRoute.STUDY_PROGRESS) })
                }
            }
            AppRoute.GRADES, AppRoute.EXAMS, AppRoute.STUDY_PROGRESS ->
                AppNotice("此功能尚未完成新页面迁移，不显示模拟数据。")
            AppRoute.SECOND_CLASS -> SecondClassPage(modifier = Modifier.weight(1f))
            AppRoute.TASKS -> TasksFeature(data, actions, reminderTaskId, consumeTask)
            AppRoute.PROFILE -> {
                InsetGroupedSection {
                    AppGroupedEntry("数据连接", onClick = { actions.navigate(AppRoute.CONNECTIONS) })
                    AppGroupedEntry("开源许可", showDivider = false, onClick = { actions.navigate(AppRoute.LICENSES) })
                }
            }
            AppRoute.CONNECTIONS -> {
                val scope = rememberCoroutineScope()
                AppNotice(data.cloud.schoolSyncMessage ?: data.cloud.authMessage)
                InsetGroupedSection {
                    AppGroupedEntry("华水青果", onClick = { actions.authorize("ncwu") })
                    AppGroupedEntry("学习通", onClick = { actions.connectLocal("chaoxing") })
                    AppGroupedEntry("第二课堂", onClick = { actions.connectLocal("second_class") })
                    AppGroupedEntry("云端账户管理", showDivider = false, onClick = actions.openWebsite)
                }
                TextButton(onClick = { scope.launch { actions.refreshCloud() } },
                    enabled = !data.cloud.busy) { Text("刷新连接状态") }
                data.cloud.privateError?.let { AppNotice(it, error = true) }
            }
            AppRoute.LICENSES -> LazyColumn { item { OpenSourceCreditsPanel() } }
        }
    }
}

@Composable
private fun HomeFeature(data: AppWorkspaceState, actions: AppPageActions) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(AppSpacing.item)) {
        item {
            AppSnapshotContent(data.lessons) {
                AppEntry("本机课表", "${it.size} 个课块", onClick = { actions.navigate(AppRoute.SCHEDULE) })
            }
        }
        item {
            AppSnapshotContent(data.localTasks) {
                InsetGroupedSection {
                    AppGroupedEntry("本机待办", "${it.count { task -> !task.completed }} 项未完成",
                        showDivider = false, onClick = { actions.navigate(AppRoute.TASKS) })
                    if (it.isNotEmpty()) GlassProgressBar(it.count { task -> task.completed }.toFloat() / it.size,
                        modifier = Modifier.padding(horizontal = androidx.compose.ui.unit.Dp(16f))
                            .padding(bottom = androidx.compose.ui.unit.Dp(12f)))
                }
            }
        }
        item { Text("校园通知", style = MaterialTheme.typography.titleMedium) }
        if (data.cloud.notices.isEmpty()) item {
            AppNotice(data.cloud.publicError ?: if (data.cloud.busy) "正在读取通知" else "尚无已读取通知")
        }
        if (data.cloud.noticesPartial) item { AppNotice("通知来源部分可用，可能不是完整列表。") }
        items(data.cloud.notices.take(5), key = { it.url }) { notice ->
            AppEntry(notice.title, notice.dateLabel, onClick = { actions.openArticle(notice.url) })
        }
    }
}

@Composable
private fun ColumnScope.ScheduleFeature(snapshot: PageSnapshot<List<VerifiedLesson>>,
    actions: AppPageActions, target: String?, consume: () -> Unit) {
    Row {
        TextButton(onClick = actions.importTimetable) { Text("导入") }
        TextButton(onClick = actions.exportTimetable) { Text("导出") }
    }
    AppSnapshotContent(snapshot) { lessons ->
        val ordered = remember(lessons) { lessons.sortedWith(compareBy({ it.weekday }, { it.startSection })) }
        val listState = rememberLazyListState()
        LaunchedEffect(target, ordered) {
            if (target != null) {
                val index = ordered.indexOfFirst { it.id == target }
                if (index >= 0) { listState.scrollToItem(index); consume() }
            }
        }
        if (ordered.isEmpty()) AppNotice("本机尚无课程")
        LazyColumn(Modifier.weight(1f), state = listState) {
            items(ordered) { lesson ->
                ListItem(headlineContent = { Text(lesson.title) },
                    supportingContent = { Text("星期${lesson.weekday} · ${lesson.startSection}–${lesson.endSection} 节 · ${lesson.location}") })
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ColumnScope.TasksFeature(data: AppWorkspaceState, actions: AppPageActions,
    target: Long?, consume: () -> Unit) {
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    AppNotice("本机、学习通与云端任务共享同一列表；仅本机待办可在此勾选。")
    error?.let { AppNotice(it, error = true) }
    AppSnapshotContent(data.localTasks) { local ->
        val records = (data.nativeRecords as? PageSnapshot.Ready)?.value.orEmpty()
        // A stale or unauthenticated owner must not contribute a cloud task.
        val cloud = data.cloud.tasks.takeIf { data.cloud.authenticated && data.cloud.tasksFresh }.orEmpty()
        val combined = remember(local, records, cloud) { combineWorkspaceTasks(local, cloud, records) }
        val listState = rememberLazyListState()
        LaunchedEffect(target, combined) {
            if (target != null) {
                val index = combined.indexOfFirst { it.key == "local:$target" }
                if (index >= 0) { listState.scrollToItem(index); consume() }
            }
        }
        if (data.nativeRecords == PageSnapshot.Loading) AppNotice("正在读取本机平台记录")
        if (data.cloud.authenticated && !data.cloud.tasksFresh) AppNotice("尚未核验最新云端任务")
        if (combined.isEmpty()) AppNotice("暂无已读取任务")
        LazyColumn(Modifier.weight(1f), state = listState) {
            items(combined) { task ->
                val localId = if (task.storage == "local") task.key.removePrefix("local:").toLongOrNull() else null
                ListItem(headlineContent = { Text(task.title) },
                    supportingContent = { Text(listOfNotNull(task.origin, task.date?.toString(),
                        task.officialStatusRaw).joinToString(" · ")) },
                    leadingContent = if (localId != null) {
                        { Checkbox(task.done, onCheckedChange = { value ->
                            scope.launch {
                                try {
                                    error = if (actions.updateLocalTask(localId, value)) null else "请先完成前置任务"
                                } catch (cancel: CancellationException) { throw cancel }
                                catch (_: Exception) { error = "保存失败" }
                            }
                        }) }
                    } else null)
                HorizontalDivider()
            }
        }
    }
}
