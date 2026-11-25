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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import com.tyust.course.ui.theme.rememberAppWallpaperStyle
import com.tyust.course.ui.system.drawWallpaperPattern
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
    var routeTrail by rememberSaveable { mutableStateOf("") }
    val reducedMotion = rememberGlassAccessibilityMode().reduceMotion ||
        !android.animation.ValueAnimator.areAnimatorsEnabled()
    val navigationMotion = rememberNavigationMotionState(route.ordinal, "xiaoxu-routes", reducedMotion)
    fun goTo(next: AppRoute) {
        if (route == next) return
        routeTrail = if (next.parent == null) "" else listOf(routeTrail, route.name).filter { it.isNotEmpty() }.joinToString("/")
        navigationMotion.select(next.ordinal, reducedMotion)
        route = next
    }
    fun goBack() {
        val parent = routeTrail.substringAfterLast('/').takeIf { it.isNotEmpty() }
            ?.let { value -> AppRoute.entries.firstOrNull { it.name == value } } ?: route.parent
        routeTrail = routeTrail.substringBeforeLast('/', "")
        if (parent != null) {
            navigationMotion.select(parent.ordinal, reducedMotion)
            route = parent
        }
    }
    val pageState = rememberSaveableStateHolder()
    var inboxTaskId by remember { mutableStateOf<Long?>(null) }
    var inboxLessonId by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current.applicationContext
    var settingsRevision by remember { mutableIntStateOf(0) }
    val data = rememberAppWorkspace(dao, cloud, localLessons, localScheduleRevision, settingsRevision,
        appLifecycle, returnedSchool, consumeWebsiteReturn)
    val actions = AppPageActions(
        navigate = ::goTo, importTimetable = openFileImport, exportTimetable = openFileExport,
        authorize = startSchoolAuthorization, connectLocal = openLocalSchool, openWebsite = openWebsite,
        refreshCloud = { cloud.refresh() }, signInCloud = { cloud.signIn(it) }, signOutCloud = { cloud.signOut() }, updateLocalTask = { id, value -> dao.trySetCompleted(id, value) },
        openArticle = openArticle,
        readGrades = { cloud.loadGrades() },
        readExams = { cloud.loadExams() },
        localTaskDao = dao,
        openLocalTask = { id -> inboxTaskId = id; goTo(AppRoute.TASKS) },
        openLocalCourse = { id, _ -> inboxLessonId = id; goTo(AppRoute.SCHEDULE) },
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
            goTo(AppRoute.TASKS)
            consumedTaskEvent = taskReminderOpenCount
        }
    }
    LaunchedEffect(scheduleReminderOpenCount) {
        if (scheduleReminderOpenCount > consumedScheduleEvent) {
            goTo(AppRoute.SCHEDULE)
            consumedScheduleEvent = scheduleReminderOpenCount
        }
    }
    LaunchedEffect(localCaptureReturn) {
        val receipt = localCaptureReturn ?: return@LaunchedEffect
        if (receipt.serial != consumedCapture) {
            goTo(if (receipt.provider == "chaoxing") AppRoute.TASKS else AppRoute.SECOND_CLASS)
            consumedCapture = receipt.serial
        }
    }
    LaunchedEffect(returnedSchool) {
        val provider = returnedSchool ?: return@LaunchedEffect
        goTo(AppRoute.CONNECTIONS)
        try { cloud.completeSchoolReturn(provider) }
        finally { onSchoolReturnConsumed() }
    }
    BackHandler(enabled = route.parent != null || routeTrail.isNotEmpty()) { goBack() }

    GlassWindowHost {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 700.dp
            val showPrimary = route.parent == null
            Row(Modifier.fillMaxSize()) {
                if (wide) NavigationRail {
                    MainDestination.entries.forEach { dest ->
                        NavigationRailItem(selected = route.primary == dest,
                            onClick = { goTo(AppRoute.root(dest)) },
                            icon = { Icon(dest.icon, contentDescription = null) }, label = { Text(dest.label) })
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    val tier = rememberNavigationMaterialTier()
                    val wallpaperBackdrop = com.tyust.course.ui.system.LocalAppBackdrop.current
                    val fallbackBackdrop = rememberLayerBackdrop()
                    Scaffold(containerColor = Color.Transparent) { padding ->
                        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
                            .padding(bottom = if (!wide && showPrimary) RebuildDockMetrics.contentReserve() else 0.dp)) {
                            // Reuse the upstream navigation timeline; only the active business
                            // page is composed to avoid duplicate readers, reminders and dialog hosts.
                            NavigationPages(navigationMotion, activePage = route.ordinal, retainOutgoing = false) { index ->
                                val pageRoute = AppRoute.entries[index]
                                pageState.SaveableStateProvider(pageRoute.name) {
                                    CompositionLocalProvider(LocalLoadingRoute provides pageRoute.name.lowercase()) {
                                        AppFeaturePage(pageRoute, data, actions, inboxTaskId ?: reminderTaskId,
                                            { inboxTaskId = null; onReminderTaskConsumed() }, inboxLessonId ?: reminderLessonId,
                                            { inboxLessonId = null; onReminderCourseConsumed() },
                                            back = if (pageRoute.parent != null || routeTrail.isNotEmpty()) ::goBack else null)
                                    }
                                }
                            }
                        }
                    }
                    if (!wide && showPrimary) RebuildCapsuleDock(
                        selected = route.primary, onSelect = { goTo(AppRoute.root(it)) },
                        tier = if (wallpaperBackdrop != null) tier else NavigationMaterialTier.Opaque,
                        backdrop = wallpaperBackdrop ?: fallbackBackdrop, modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }
    }
}
