package io.github.x07xh.xiaoxu.ui

import androidx.compose.foundation.layout.*
import com.tyust.course.ui.system.LocalAppOverlayBottomInset
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
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
    openWebsite: () -> Unit, openArticle: (String) -> Unit,
    requestSecondClassRefresh: () -> Unit = {}
) {
    var route by rememberSaveable { mutableStateOf(AppRoute.HOME) }
    var routeTrail by rememberSaveable { mutableStateOf("") }
    val latestSecondClassRefresh by rememberUpdatedState(requestSecondClassRefresh)
    LaunchedEffect(route) {
        if (route == AppRoute.SECOND_CLASS) latestSecondClassRefresh()
    }
    val reducedMotion = rememberGlassAccessibilityMode().reduceMotion ||
        !android.animation.ValueAnimator.areAnimatorsEnabled()
    val navigationMotion = rememberNavigationMotionState(route.ordinal, "xiaoxu-routes", reducedMotion)
    fun goTo(next: AppRoute) {
        if (route == next) return
        routeTrail = if (next.parent == null) "" else (routeTrail.split("/").filter { it.isNotEmpty() } + route.name).takeLast(30).joinToString("/")
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
        reviewWechatCandidate = { id, decision, owner -> cloud.reviewWechatCandidate(id, decision, owner) },
        updateWechatTask = { id, revision, completed, owner -> cloud.setWechatTaskCompleted(id, revision, completed, owner) },
        addCloudTask = { title, owner -> cloud.addCloudTask(title, owner) },
        syncAuthorizedSchool = { provider -> cloud.syncAuthorizedSchool(provider) },
        readGrades = { cloud.loadGrades() },
        readExams = { cloud.loadExams() },
        readChaoxingProgress = { course -> cloud.readChaoxingCourseProgress(course) },
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

    // MainActivity.kt upstream application host: one overlay/window, shared page state,
    // navigation timeline, scroll intent, and a dock outside the page subtree.
    // The upstream overlay inset is the sole source of dock content spacing.
    val accountKey = if (data.cloud.authenticated) "cloud:" + data.cloud.ownerId.orEmpty() else "local"
    val pageData = remember(accountKey) { PageDataState() }
    DisposableEffect(pageData) { onDispose { pageData.close() } }
    // MainActivity.kt upstream: one scroll state, one capture, chrome outside that capture.
    val lensFreshness = remember { if (isGlassLensApplicable()) GlassLensFreshness() else null }
    val density = LocalDensity.current
    val autoCollapse = com.tyust.course.manager.AppearanceSettingsManager.navBarAutoCollapseEnabled &&
        !reducedMotion
    var minimized by remember { mutableStateOf(false) }
    val intent = remember { NavScrollIntent() }
    LaunchedEffect(route, autoCollapse) { minimized = false; intent.reset() }
    CompositionLocalProvider(LocalPageGlassFreshness provides lensFreshness) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 700.dp
            val showPrimary = route.parent == null
            val phoneDock = !wide && showPrimary
            val navBarContentInset = if (phoneDock) NavBarMetrics.contentInset() else 0.dp
            CompositionLocalProvider(LocalAppOverlayBottomInset provides navBarContentInset,
                LocalPageDataState provides pageData) {
                GlassWindowHost(
                    chrome = {
                        val tier = rememberNavigationMaterialTier()
                        val navBarBackdrop = com.tyust.course.ui.system.LocalModalBackdrop.current
                        if (phoneDock) RebuildCapsuleDock(
                            selected = route.primary, onSelect = { goTo(AppRoute.root(it)) },
                            tier = if (navBarBackdrop != null) tier else NavigationMaterialTier.Opaque,
                            backdrop = navBarBackdrop,
                            modifier = Modifier.align(Alignment.BottomCenter)
                                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                                .imePadding(),
                            minimized = autoCollapse && minimized,
                            onExpandRequest = { minimized = false; intent.reset() },
                            lensFreshness = lensFreshness
                        )
                    }
                ) {
                    val dialogs = LocalDialogHost.current
                    val scroll = remember(density, dialogs, autoCollapse, lensFreshness) {
                        object : NestedScrollConnection {
                            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                                if (autoCollapse && source == NestedScrollSource.UserInput && dialogs?.hasBlockingSurface != true) {
                                    if (consumed.y == 0f && available.y > 0f) {
                                        minimized = false
                                        intent.reset()
                                    } else if (kotlin.math.abs(consumed.y) > kotlin.math.abs(consumed.x)) {
                                        intent.scroll(consumed.y / density.density)?.let { minimized = it }
                                    }
                                }
                                // Upstream: refresh during inertial scrolling too.
                                if (consumed.y != 0f) lensFreshness?.onScroll()
                                return Offset.Zero
                            }
                        }
                    }
                    // Wide-screen rail is the existing business layout adaptation.
                    Row(Modifier.fillMaxSize().then(
                        if (phoneDock) Modifier.windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                        ).imePadding()
                        else Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                    )) {
                        if (wide) NavigationRail {
                            MainDestination.entries.forEach { dest ->
                                NavigationRailItem(selected = route.primary == dest,
                                    onClick = { goTo(AppRoute.root(dest)) },
                                    icon = { Icon(dest.icon, contentDescription = null) }, label = { Text(dest.label) })
                            }
                        }
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            key(accountKey) {
                                val savedPages = rememberSaveableStateHolder()
                                NavigationPages(navigationMotion, modifier = Modifier.fillMaxSize().nestedScroll(scroll)) { index ->
                                    val pageRoute = AppRoute.entries[index]
                                    val routeKey = "app." + pageRoute.name.lowercase()
                                    savedPages.SaveableStateProvider(pageRoute.name) {
                                        val active = pageRoute == route
                                        CompositionLocalProvider(LocalLoadingRoute provides routeKey, LocalLoadingActive provides active) {
                                            AppFeaturePage(pageRoute, data, actions,
                                                if (active) inboxTaskId ?: reminderTaskId else null,
                                                { if (active) { inboxTaskId = null; onReminderTaskConsumed() } },
                                                if (active) inboxLessonId ?: reminderLessonId else null,
                                                { if (active) { inboxLessonId = null; onReminderCourseConsumed() } },
                                                back = if (active && (pageRoute.parent != null || routeTrail.isNotEmpty())) ::goBack else null)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
