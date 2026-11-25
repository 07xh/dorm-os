package io.github.x07xh.xiaoxu.ui

import androidx.compose.foundation.layout.*
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import io.github.x07xh.xiaoxu.*
import io.github.x07xh.xiaoxu.data.*
import kotlinx.coroutines.launch

data class LocalSchoolImportReceipt(val provider: String, val kind: String, val savedCount: Int, val serial: Long)

internal val MainDestination.label: String get() = when(this) {
    MainDestination.HOME -> "总览"
    MainDestination.SCHEDULE -> "课表"
    MainDestination.ACADEMIC -> "教务"
    MainDestination.TASKS -> "任务"
    MainDestination.PROFILE -> "我的"
}
internal val MainDestination.icon get() = when(this) {
    MainDestination.HOME -> Icons.Outlined.Home
    MainDestination.SCHEDULE -> Icons.Outlined.DateRange
    MainDestination.ACADEMIC -> Icons.Outlined.School
    MainDestination.TASKS -> Icons.Outlined.CheckCircle
    MainDestination.PROFILE -> Icons.Outlined.Person
}

/** Clean native entry. Only real local snapshots are presented at this stage.
 * Existing Activity callbacks stay intact for the next page migrations.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
internal fun NativeShell(
    dao: LocalTaskDao, cloud: CloudClient, appLifecycle: Lifecycle,
    taskReminderOpenCount: Int, reminderTaskId: Long?, onReminderTaskConsumed: () -> Unit,
    scheduleReminderOpenCount: Int, reminderLessonId: String?, reminderLessonWeekday: Int,
    onReminderCourseConsumed: () -> Unit, localLessons: List<VerifiedLesson>,
    localScheduleRevision: Int, onLocalLessonsChanged: (List<VerifiedLesson>) -> Unit,
    returnedSchool: String?, localCaptureReturn: LocalSchoolImportReceipt?,
    onSchoolReturnConsumed: () -> Unit, startSchoolAuthorization: (String) -> Unit,
    openFileImport: () -> Unit, openFileExport: () -> Unit,
    openLocalSchool: (String) -> Unit, consumeWebsiteReturn: () -> Boolean,
    openWebsite: () -> Unit, openArticle: (String) -> Unit
) {
    var route by rememberSaveable { mutableStateOf(MainDestination.HOME) }
    var academic by rememberSaveable { mutableStateOf(AcademicDestination.OVERVIEW) }
    val tasks by rememberLocalRows(dao) { dao.observe() }
    val scope = rememberCoroutineScope()
    var taskError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(taskReminderOpenCount) {
        if(taskReminderOpenCount > 0) route = MainDestination.TASKS
    }
    LaunchedEffect(scheduleReminderOpenCount) {
        if(scheduleReminderOpenCount > 0) route = MainDestination.SCHEDULE
    }
    LaunchedEffect(returnedSchool) {
        if(returnedSchool != null) { route = MainDestination.ACADEMIC }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 700.dp
        Row(Modifier.fillMaxSize()) {
            if(wide) NavigationRail {
                MainDestination.entries.forEach { dest ->
                    NavigationRailItem(selected = route == dest, onClick = { route = dest },
                        icon = { Icon(dest.icon, contentDescription = null) }, label = { Text(dest.label) })
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                val tier = rememberNavigationMaterialTier()
                val backdrop = rememberLayerBackdrop()
                val capture = if (!wide && tier == NavigationMaterialTier.Refractive)
                    Modifier.layerBackdrop(backdrop) else Modifier
                Scaffold(modifier = Modifier.fillMaxSize().then(capture)) { padding ->
                Column(Modifier.fillMaxSize().padding(padding)
                    .padding(bottom = if (wide) 0.dp else RebuildDockMetrics.contentReserve)
                    .padding(horizontal = 20.dp)) {
                    Text(route.label, style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(vertical = 16.dp))
                    when(route) {
                        MainDestination.HOME -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("本机课表 · ${localLessons.size} 个课块")
                            Text("本机待办 · ${tasks.count { !it.completed }} 项")
                            TextButton(onClick = { route = MainDestination.SCHEDULE }) { Text("打开课表") }
                            TextButton(onClick = { route = MainDestination.TASKS }) { Text("打开待办") }
                        }
                        MainDestination.SCHEDULE -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = openFileImport) { Text("导入课表") }
                                TextButton(onClick = openFileExport) { Text("导出备份") }
                            }
                            if(localLessons.isEmpty()) Text("尚未读取本机课表")
                            LazyColumn(Modifier.weight(1f)) {
                                items(localLessons.sortedWith(compareBy({it.weekday},{it.startSection}))) { lesson ->
                                    ListItem(headlineContent = { Text(lesson.title) },
                                        supportingContent = { Text("星期${lesson.weekday} · ${lesson.startSection}–${lesson.endSection} 节 · ${lesson.location}") })
                                    HorizontalDivider()
                                }
                            }
                        }
                        MainDestination.ACADEMIC -> {
                            if(academic == AcademicDestination.SECOND_CLASS) {
                                TextButton(onClick = { academic = AcademicDestination.OVERVIEW }) { Text("返回教务") }
                                SecondClassPage(modifier = Modifier.weight(1f))
                            } else Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { startSchoolAuthorization("ncwu") }) { Text("连接华水青果") }
                                TextButton(onClick = { startSchoolAuthorization("chaoxing") }) { Text("连接学习通") }
                                TextButton(onClick = { academic = AcademicDestination.SECOND_CLASS }) { Text("第二课堂") }
                                Text("成绩、考试页面尚未迁移，不显示模拟数据。", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        MainDestination.TASKS -> {
                            if(tasks.isEmpty()) Text("暂无本机任务")
                            taskError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            LazyColumn(Modifier.weight(1f)) {
                                items(tasks, key = { it.id }) { task ->
                                    ListItem(headlineContent = { Text(task.title) },
                                        supportingContent = { Text(task.due ?: "未设置截止日期") },
                                        leadingContent = { Checkbox(task.completed, onCheckedChange = { value ->
                                            scope.launch {
                                                taskError = runCatching { dao.trySetCompleted(task.id, value) }
                                                    .fold({ if(it) null else "请先完成前置任务" }, { "保存失败" })
                                            }
                                        }) })
                                    HorizontalDivider()
                                }
                            }
                        }
                        MainDestination.PROFILE -> LazyColumn {
                            item { TextButton(onClick = openWebsite) { Text("账户与连接管理") } }
                            item { OpenSourceCreditsPanel() }
                        }
                    }
                }
                }
                if (!wide) RebuildCapsuleDock(
                    selected = route, onSelect = { route = it }, tier = tier,
                    backdrop = backdrop, modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}
