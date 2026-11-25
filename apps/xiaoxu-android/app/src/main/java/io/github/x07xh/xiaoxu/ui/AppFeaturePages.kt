package io.github.x07xh.xiaoxu.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import io.github.x07xh.xiaoxu.SecondClassContent
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
    val openArticle: (String) -> Unit,
    val saveScheduleSettings: suspend (SemesterCalendar, Int) -> Boolean,
    val readGrades: suspend () -> Unit,
    val readExams: suspend () -> Unit,
    val addLocalTask: suspend (String, String?) -> Long
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
            AppRoute.SCHEDULE -> ScheduleFeature(data, actions, reminderLessonId, consumeLesson)
            AppRoute.SCHEDULE_SETTINGS -> ScheduleSettingsFeature(data, actions)
            AppRoute.ACADEMIC -> {
                InsetGroupedSection {
                    AppGroupedEntry("成绩", onClick = { actions.navigate(AppRoute.GRADES) })
                    AppGroupedEntry("考试", onClick = { actions.navigate(AppRoute.EXAMS) })
                    AppGroupedEntry("第二课堂", onClick = { actions.navigate(AppRoute.SECOND_CLASS) })
                    AppGroupedEntry("学习进度", showDivider = false, onClick = { actions.navigate(AppRoute.STUDY_PROGRESS) })
                }
            }
            AppRoute.GRADES -> GradesFeature(data, actions)
            AppRoute.EXAMS -> ExamsFeature(data, actions)
            AppRoute.STUDY_PROGRESS -> {
                SystemEmptyState("尚未接入课程进度", "目前没有已核验的课程进度数据。任务完成状态不等于课程学习进度。")
                TextButton(onClick = { actions.navigate(AppRoute.CONNECTIONS) }) { Text("数据连接") }
            }
            AppRoute.SECOND_CLASS -> SecondClassContent(data.nativeRecords, modifier = Modifier.weight(1f))
            AppRoute.TASKS -> TasksFeature(data, actions, reminderTaskId, consumeTask)
            AppRoute.PROFILE -> ProfileFeature(data, actions)
            AppRoute.CONNECTIONS -> ConnectionsFeature(data, actions)
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
        item { SystemSectionHeader("校园通知") }
        if (data.cloud.notices.isEmpty()) item {
            AppNotice(data.cloud.publicError ?: if (data.cloud.busy) "正在读取通知" else "尚无已读取通知")
        }
        if (data.cloud.noticesPartial) item { AppNotice("通知来源部分可用，可能不是完整列表。") }
        items(data.cloud.notices.take(5), key = { it.url }) { notice ->
            AppEntry(notice.title, notice.dateLabel, onClick = { actions.openArticle(notice.url) })
        }
    }
}
