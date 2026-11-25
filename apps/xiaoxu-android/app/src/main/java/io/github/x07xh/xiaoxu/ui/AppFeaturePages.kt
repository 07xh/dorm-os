package io.github.x07xh.xiaoxu.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.ZoneId
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
    val reviewWechatCandidate: suspend (String, String) -> Boolean,
    val signInCloud: suspend (String) -> Boolean,
    val signOutCloud: suspend () -> Unit,
    val updateLocalTask: suspend (Long, Boolean) -> Boolean,
    val updateWechatTask: suspend (String, Int, Boolean, String) -> Boolean,
    val addCloudTask: suspend (String, String) -> Boolean,
    val syncAuthorizedSchool: suspend (String) -> Boolean,
    val openArticle: (String) -> Unit,
    val saveScheduleSettings: suspend (SemesterCalendar, Int) -> Boolean,
    val readGrades: suspend () -> Unit,
    val readExams: suspend () -> Unit,
    val readChaoxingProgress: suspend (ChaoxingGradeCourse?) -> Boolean,
    val addLocalTask: suspend (String, String?) -> Long,
    val executionDao: TaskExecutionDao,
    val localTaskDao: LocalTaskDao,
    val saveCourseChanges: suspend (List<VerifiedLesson>, List<VerifiedLesson>, String?) -> Boolean,
    val openLocalTask: (Long) -> Unit,
    val openLocalCourse: (String, Int) -> Unit
)

@Composable
internal fun AppFeaturePage(
    route: AppRoute, data: AppWorkspaceState, actions: AppPageActions,
    reminderTaskId: Long?, consumeTask: () -> Unit,
    reminderLessonId: String?, consumeLesson: () -> Unit,
    back: (() -> Unit)? = null
) {
    if (route == AppRoute.PROFILE) {
        SettingsScreen(data, actions)
        return
    }
    if (route == AppRoute.SCHEDULE) {
        GlassPageScaffold(title = route.title, topBar = {}) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.item)) {
                ScheduleFeature(data, actions, reminderLessonId, consumeLesson)
            }
        }
        return
    }
    if (route == AppRoute.GRADES || route == AppRoute.EXAMS) {
        AcademicRecordsFeature(data, actions, if (route == AppRoute.EXAMS) 2 else 0,
            onBack = { if (back != null) back() else actions.navigate(AppRoute.ACADEMIC) })
        return
    }
    AppPageFrame(title = route.title, onBack = back,
        wideContent = route == AppRoute.HOME || route == AppRoute.TASKS || route == AppRoute.MESSAGES) {
        when (route) {
            AppRoute.HOME -> HomeFeature(data, actions)
            AppRoute.MESSAGES -> MessageCenterFeature(data, actions)
            AppRoute.SCHEDULE -> Unit
            AppRoute.SCHEDULE_SETTINGS -> ScheduleSettingsFeature(data, actions)
            AppRoute.ACADEMIC -> {
                AcademicHubFeature(actions)

            }
            AppRoute.GRADES -> Unit
            AppRoute.EXAMS -> Unit
            AppRoute.CAMPUS_RUN -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.section)) {
                CampusRunFeature(data.campusRunScope)
            }
            AppRoute.STUDY_PROGRESS -> StudyProgressFeature(data, actions)
            AppRoute.SECOND_CLASS -> SecondClassContent(data.nativeRecords, modifier = Modifier.weight(1f))
            AppRoute.TASKS -> TasksFeature(data, actions, reminderTaskId, consumeTask)
            AppRoute.PROFILE -> Unit
            AppRoute.CONNECTIONS -> ConnectionsFeature(data, actions)
            AppRoute.ACCOUNT -> AccountFeature(data, actions)
            AppRoute.ABOUT -> AboutFeature({ actions.navigate(AppRoute.LICENSES) }, actions.openWebsite)
            AppRoute.SECOND_CLASS_SETTINGS -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                InsetGroupedSection(header = "本机第二课堂读取") { SecondClassAutoReadControls() }
                AppGroupedEntry("手动读取与积分", showDivider = false, onClick = { actions.navigate(AppRoute.SECOND_CLASS) })
            }
            AppRoute.REMINDER_SETTINGS -> ReminderSettingsFeature(data)
            AppRoute.TASK_BACKUP -> LazyColumn { item { LocalTaskBackupPanel() } }
            AppRoute.LICENSES -> LazyColumn { item { OpenSourceCreditsPanel() } }
        }
    }
}

@Composable
private fun AcademicHubFeature(actions: AppPageActions) {
    val tint = MaterialTheme.colorScheme.primary
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.section)) {
        InsetGroupedSection(header = "教务查询") {
            SettingsRow(Icons.Outlined.School, tint, "成绩", onClick = { actions.navigate(AppRoute.GRADES) })
            SettingsRow(Icons.Outlined.DateRange, tint, "考试", showDivider = false,
                onClick = { actions.navigate(AppRoute.EXAMS) })
        }
        InsetGroupedSection(header = "学习与活动") {
            SettingsRow(Icons.Outlined.Event, tint, "第二课堂", onClick = { actions.navigate(AppRoute.SECOND_CLASS) })
            SettingsRow(Icons.Outlined.MenuBook, tint, "学习进度",
                onClick = { actions.navigate(AppRoute.STUDY_PROGRESS) })
            SettingsRow(Icons.Outlined.Event, tint, "校园跑记录", showDivider = false,
                onClick = { actions.navigate(AppRoute.CAMPUS_RUN) })
        }
    }
}

@Composable
private fun HomeFeature(data: AppWorkspaceState, actions: AppPageActions) {
    // Shared upstream grouped rows and stat strip, not a new dashboard component system.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 680.dp) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.section)) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.section)) {
                    HomeSchedule(data, actions)
                    HomeTasks(data, actions)
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    HomeNotices(data, actions)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(AppSpacing.section)) {
                item { HomeSchedule(data, actions) }
                item { HomeTasks(data, actions) }
                item { HomeNotices(data, actions) }
            }
        }
    }
}

@Composable
private fun HomeSchedule(data: AppWorkspaceState, actions: AppPageActions) {
    val today = LocalDate.now(ZoneId.of("Asia/Shanghai"))
    val week = data.semesterCalendar?.currentWeek(today)
    val day = today.dayOfWeek.value
    AppSnapshotContent(data.lessons) { lessons ->
        val preview = remember(lessons, week, day) {
            if (week == null) homeCoursePreview(lessons)
            else homeCoursePreview(lessons.filter { it.weekday == day && week in it.weeks }.sortedBy { it.startSection })
        }
        InsetGroupedSection(header = if (week == null) "本机课表" else "今日课程") {
            preview.forEach { lesson ->
                InsetGroupedRow(title = lesson.title,
                    subtitle = listOfNotNull(
                        if (week == null) "星期${lesson.weekday}" else null,
                        "第${lesson.startSection}–${lesson.endSection}节",
                        lesson.location.takeIf { it.isNotBlank() }).joinToString(" · "),
                    onClick = { actions.openLocalCourse(lesson.id, lesson.weekday) })
            }
            AppGroupedEntry("查看课表",
                summary = if (lessons.isEmpty()) "尚未导入" else if (week != null && preview.isEmpty()) "今日无已导入课程"
                    else if (week == null) "未确定当前教学周" else null,
                showDivider = false, onClick = { actions.navigate(AppRoute.SCHEDULE) })
        }
    }
}

@Composable
private fun HomeTasks(data: AppWorkspaceState, actions: AppPageActions) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val archiveStore = remember(context) { LocalTaskArchiveStore.get(context) }
    val archivedKeys = archiveStore.archivedKeys
    AppSnapshotContent(data.localTasks) { tasks ->
        val native = (data.nativeRecords as? PageSnapshot.Ready)?.value
        val cloud = data.cloud.tasks.takeIf { data.cloud.authenticated && data.cloud.tasksFresh }.orEmpty()
        val all = remember(tasks, cloud, native, archivedKeys) {
            visibleWorkspaceTasks(combineWorkspaceTasks(tasks, cloud, native.orEmpty()), archivedKeys)
        }
        val today = LocalDate.now(ZoneId.of("Asia/Shanghai"))
        val priorities = remember(all, today) {
            all.filter { isTodayPriorityWorkspaceTask(it, today) }.sortedBy { it.date }.take(3)
        }
        InsetGroupedSection(header = "任务总览") {
            SystemStatStrip(items = listOf("待办" to all.count(::isActionableWorkspaceTask).toString(),
                "待核验" to all.count(::requiresChaoxingVerification).toString(),
                "已完成" to all.count(::considerCompleteInTaskList).toString()))
            if (native == null || (data.cloud.authenticated && !data.cloud.tasksFresh))
                AppNotice("部分任务来源尚未读取或核验，统计仅包含已读取记录。")
            priorities.forEach { task ->
                InsetGroupedRow(title = task.title,
                    subtitle = listOfNotNull(task.origin, task.date?.toString()).joinToString(" · "),
                    onClick = {
                        val id = task.key.takeIf { task.storage == "local" }?.removePrefix("local:")?.toLongOrNull()
                        if (id != null) actions.openLocalTask(id) else actions.navigate(AppRoute.TASKS)
                    })
            }
            AppGroupedEntry("查看任务", showDivider = false, onClick = { actions.navigate(AppRoute.TASKS) })
        }
    }
}

@Composable
private fun HomeNotices(data: AppWorkspaceState, actions: AppPageActions) {
    val phase = deriveMessagePhase(data.cloud)
    val notices = data.cloud.notices.distinctBy { it.url }.take(5)
    InsetGroupedSection(header = "校园通知") {
        if (notices.isEmpty()) {
            val summary = when (phase) {
                MessagePhase.Loading -> "正在读取"
                MessagePhase.NotConnected -> "尚未读取"
                MessagePhase.Empty -> "已读取，暂无通知"
                is MessagePhase.Failed -> phase.message
                is MessagePhase.Content -> "暂无可展示公告"
            }
            InsetGroupedRow(title = summary)
        }
        notices.forEach { notice ->
            InsetGroupedRow(title = notice.title, subtitle = notice.dateLabel,
                onClick = { actions.openArticle(notice.url) })
        }
        AppGroupedEntry("通知中心", summary = if (data.cloud.noticesPartial) "部分来源读取失败" else "公告与提醒",
            showDivider = false, onClick = { actions.navigate(AppRoute.MESSAGES) })
    }
}
