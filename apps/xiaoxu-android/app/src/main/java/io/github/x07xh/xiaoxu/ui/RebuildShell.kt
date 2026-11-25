package io.github.x07xh.xiaoxu.ui

import androidx.compose.foundation.layout.*
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import io.github.x07xh.xiaoxu.*
import io.github.x07xh.xiaoxu.data.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalContext

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
    var route by rememberSaveable { mutableStateOf(AppRoute.HOME) }
    val pageState = rememberSaveableStateHolder()
    var inboxTaskId by remember { mutableStateOf<Long?>(null) }
    var inboxLessonId by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current.applicationContext
    var settingsRevision by remember { mutableIntStateOf(0) }
    val data = rememberAppWorkspace(dao, cloud, localLessons, localScheduleRevision, settingsRevision,
        appLifecycle, returnedSchool, consumeWebsiteReturn)
    val actions = AppPageActions(
        navigate = { route = it }, importTimetable = openFileImport, exportTimetable = openFileExport,
        authorize = startSchoolAuthorization, connectLocal = openLocalSchool, openWebsite = openWebsite,
        refreshCloud = { cloud.refresh() }, updateLocalTask = { id, value -> dao.trySetCompleted(id, value) },
        openArticle = openArticle,
        readGrades = { cloud.loadGrades() },
        readExams = { cloud.loadExams() },
        localTaskDao = dao,
        openLocalTask = { id -> inboxTaskId = id; route = AppRoute.TASKS },
        openLocalCourse = { id, _ -> inboxLessonId = id; route = AppRoute.SCHEDULE },
        saveCourseChanges = { expected, changed, originalId ->
            val (fresh, reminderOk) = withContext(Dispatchers.IO) {
                require(LocalScheduleStore.load(context) == expected) { "课表已经变化，请重新打开编辑器" }
                LocalScheduleStore.saveEdited(context, changed)
                val saved = LocalScheduleStore.load(context)
                val preferenceOk = if (originalId.isNullOrBlank()) true else runCatching {
                    LocalCourseReminders.migrateCourseEnabled(context, originalId,
                        remappedCourseIds(originalId, changed, saved))
                }.getOrDefault(false)
                val scheduled = LocalCourseReminders.reconcileAfterSave(context, saved,
                    LocalSemesterConfigStore.load(context))
                saved to (preferenceOk && scheduled)
            }
            onLocalLessonsChanged(fresh)
            reminderOk
        },
        executionDao = remember(context) { LocalTaskDatabase.open(context).executions() },
        addLocalTask = { title, due -> dao.insert(LocalTask(title = title, source = TASK_SOURCE_LOCAL, due = due)) },
        saveScheduleSettings = { calendar, sections ->
            require(sections in 1..24)
            LocalSemesterConfigStore.save(context, calendar)
            LocalSemesterConfigStore.saveSections(context, sections)
            settingsRevision++
            withContext(Dispatchers.IO) {
                LocalCourseReminders.reconcileAfterSave(context, LocalScheduleStore.load(context), calendar)
            }
        }
    )
    var consumedTaskEvent by remember { mutableIntStateOf(0) }
    var consumedScheduleEvent by remember { mutableIntStateOf(0) }
    var consumedCapture by remember { mutableLongStateOf(0L) }
    LaunchedEffect(taskReminderOpenCount) {
        if (taskReminderOpenCount > consumedTaskEvent) {
            route = AppRoute.TASKS
            consumedTaskEvent = taskReminderOpenCount
        }
    }
    LaunchedEffect(scheduleReminderOpenCount) {
        if (scheduleReminderOpenCount > consumedScheduleEvent) {
            route = AppRoute.SCHEDULE
            consumedScheduleEvent = scheduleReminderOpenCount
        }
    }
    LaunchedEffect(localCaptureReturn) {
        val receipt = localCaptureReturn ?: return@LaunchedEffect
        if (receipt.serial != consumedCapture) {
            route = if (receipt.provider == "chaoxing") AppRoute.TASKS else AppRoute.SECOND_CLASS
            consumedCapture = receipt.serial
        }
    }
    LaunchedEffect(returnedSchool) {
        val provider = returnedSchool ?: return@LaunchedEffect
        route = AppRoute.CONNECTIONS
        try { cloud.completeSchoolReturn(provider) }
        finally { onSchoolReturnConsumed() }
    }
    BackHandler(enabled = route.parent != null) { route.parent?.let { route = it } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 700.dp
        val showPrimary = route.parent == null
        Row(Modifier.fillMaxSize()) {
            if (wide) NavigationRail {
                MainDestination.entries.forEach { dest ->
                    NavigationRailItem(selected = route.primary == dest,
                        onClick = { route = AppRoute.root(dest) },
                        icon = { Icon(dest.icon, contentDescription = null) }, label = { Text(dest.label) })
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                val tier = rememberNavigationMaterialTier()
                val backdrop = rememberLayerBackdrop()
                val dialogHost = rememberDialogHostState()
                val accessibility = rememberGlassAccessibilityMode()
                val allowPortal = !wide && tier == NavigationMaterialTier.Refractive && !accessibility.highContrast
                val capture = if (!wide && tier == NavigationMaterialTier.Refractive &&
                    (showPrimary || dialogHost.hasBlockingSurface))
                    Modifier.layerBackdrop(backdrop) else Modifier
                val backgroundSemantics = if (dialogHost.hasBlockingSurface)
                    Modifier.semantics { hideFromAccessibility() } else Modifier
                CompositionLocalProvider(
                    LocalDialogHost provides dialogHost.takeIf { allowPortal },
                    LocalAppDialogBackdrop provides backdrop.takeIf { allowPortal }
                ) {
                    Scaffold(modifier = Modifier.fillMaxSize().then(capture).then(backgroundSemantics)) { padding ->
                        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
                            .padding(bottom = if (!wide && showPrimary) RebuildDockMetrics.contentReserve() else 0.dp)) {
                            pageState.SaveableStateProvider(route.name) {
                                AppFeaturePage(route, data, actions, inboxTaskId ?: reminderTaskId,
                                    { inboxTaskId = null; onReminderTaskConsumed() }, inboxLessonId ?: reminderLessonId,
                                    { inboxLessonId = null; onReminderCourseConsumed() })
                            }
                        }
                    }
                    if (!wide && showPrimary) RebuildCapsuleDock(
                        selected = route.primary, onSelect = { route = AppRoute.root(it) }, tier = tier,
                        backdrop = backdrop, modifier = Modifier.align(Alignment.BottomCenter).then(backgroundSemantics)
                    )
                    // Sibling to the source layer: never include the optical surface in its own backdrop.
                    DialogHost(dialogHost, Modifier.fillMaxSize())
                }
            }
        }
    }
}
