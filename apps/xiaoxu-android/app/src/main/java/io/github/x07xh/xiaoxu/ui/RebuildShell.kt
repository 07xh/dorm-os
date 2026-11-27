package io.github.x07xh.xiaoxu.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.tyust.course.ui.system.LocalAppBackdrop
import com.tyust.course.ui.system.LocalControlBackdrop
import com.tyust.course.ui.system.LocalModalBackdrop
import com.tyust.course.ui.system.drawWallpaperPattern
import com.tyust.course.ui.system.isBackdropSupported
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
    // MainActivity.kt upstream: independent page and bar timelines scoped to the current account.
    val accountKey by remember(cloud) { derivedStateOf {
        if (cloud.state.authenticated) "cloud:" + cloud.state.ownerId.orEmpty() else "local"
    } }
    val destinations = MainDestination.entries
    val items = remember { destinations.map { BottomNavItem(it.label, it.upstreamSymbol) } }
    val selectedNavigationIndex = if (route.parent == null) destinations.indexOf(route.primary) else -1
    val navigationMotion = rememberNavigationMotionState(route.ordinal, accountKey + ":routes", reducedMotion)
    val barMotion = rememberNavigationMotionState(selectedNavigationIndex, accountKey + ":tabs", reducedMotion)
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

    // SPDX-License-Identifier: GPL-3.0-only
    // Root rendering block adapted from znjhahaha/zhengfang-apk MainActivity.kt
    // @ bb3b80a6d64e4c2057c0544bb417696ceaa2a9ed. Native school routes are the data adapter.
    val pageData = remember(accountKey) { PageDataState() }
    DisposableEffect(pageData) { onDispose { pageData.close() } }
    val lensFreshness = remember { if (isGlassLensApplicable()) GlassLensFreshness() else null }
    val density = LocalDensity.current
    val view = LocalView.current
    val dialogs = rememberDialogHostState()
    val appWallpaper = com.tyust.course.ui.theme.rememberAppWallpaperStyle()
    val autoCollapse = com.tyust.course.manager.AppearanceSettingsManager.navBarAutoCollapseEnabled &&
        !reducedMotion
    var minimized by remember { mutableStateOf(false) }
    val intent = remember { NavScrollIntent() }
    LaunchedEffect(route, autoCollapse) { minimized = false; intent.reset() }
    val scroll = remember(density, dialogs, autoCollapse, lensFreshness) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (autoCollapse && source == NestedScrollSource.UserInput && !dialogs.hasBlockingSurface) {
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
    GlassOverlayHost(modifier = Modifier.fillMaxSize()) {
        val useGlass = isBackdropSupported()
        val wallpaperBackdrop = if (useGlass) rememberLayerBackdrop() else null
        // NativeShell contains native Compose destinations only; WebViews are separate activities.
        val capturedPageBackdrop = if (useGlass) rememberLayerBackdrop() else null
        val navBarBackdrop = capturedPageBackdrop ?: wallpaperBackdrop
        val appLensDensity = LocalDensity.current
        val appLensAnchor = if (wallpaperBackdrop != null) {
            rememberGlassLensRegion(tag = "app") { coords ->
                drawBackdropSource(wallpaperBackdrop, appLensDensity, coords)
            }
        } else null
        val modalLensBlurPx = with(appLensDensity) { GlassRecipe.DialogBlurDp.dp.toPx() }
        val modalLensAnchor = if (navBarBackdrop != null) {
            rememberGlassLensRegion("app-modal", route, accountKey, dialogs.currentDialog, navBarBackdrop,
                freshness = lensFreshness) { coords ->
                drawBlurred(modalLensBlurPx) { drawBackdropSource(navBarBackdrop, appLensDensity, coords) }
            }
        } else null
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 700.dp
            val showPrimary = route.parent == null
            val phoneDock = !wide && showPrimary
            val navBarContentInset = if (phoneDock) NavBarMetrics.contentInset() else 0.dp
            CompositionLocalProvider(
                LocalGlassWindowOwner provides view,
                LocalAppBackdrop provides wallpaperBackdrop,
                LocalControlBackdrop provides wallpaperBackdrop,
                LocalModalBackdrop provides navBarBackdrop,
                LocalAppDialogBackdrop provides navBarBackdrop,
                LocalAppOverlayBottomInset provides navBarContentInset,
                LocalDialogHost provides dialogs,
                LocalPageDataState provides pageData,
                LocalPageGlassFreshness provides lensFreshness,
                LocalGlassLensAnchor provides appLensAnchor,
                LocalGlassLensModalAnchor provides modalLensAnchor
            ) {
                Box(Modifier.fillMaxSize().then(
                    if (capturedPageBackdrop != null) Modifier.layerBackdrop(capturedPageBackdrop) else Modifier
                ).glassLensAnchor(appLensAnchor).glassLensAnchor(modalLensAnchor)
                    .then(if (dialogs.hasBlockingSurface) Modifier.semantics { hideFromAccessibility() } else Modifier)) {
                    // Upstream: wallpaper is inside the captured page; controls keep its stationary source.
                    if (useGlass && wallpaperBackdrop != null) {
                        Canvas(Modifier.fillMaxSize().layerBackdrop(wallpaperBackdrop)) {
                            drawWallpaperPattern(appWallpaper)
                        }
                    } else {
                        Canvas(Modifier.fillMaxSize()) {
                            drawWallpaperPattern(appWallpaper, microTexture = false)
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
                                NavigationPages(navigationMotion, modifier = Modifier.fillMaxSize().nestedScroll(scroll).graphicsLayer {
                                    translationX = -6.dp.toPx() * dialogs.pageProgress
                                    alpha = 1f - 0.03f * dialogs.pageProgress
                                }) { index ->
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
                } // Close page capture before chrome.
                CompositionLocalProvider(LocalNavigationMotion provides barMotion) {
                    val tier = rememberNavigationMaterialTier()
                    val accessibility = rememberGlassAccessibilityMode()
                    if (phoneDock) CapsuleNavigationBar(
                        items = items, selectedTab = selectedNavigationIndex,
                        onTabSelect = { index -> destinations.getOrNull(index)?.let { goTo(AppRoute.root(it)) } },
                        minimized = autoCollapse && minimized,
                        onExpandRequest = { minimized = false; intent.reset() },
                        backdrop = navBarBackdrop.takeIf { tier == NavigationMaterialTier.Refractive && !accessibility.highContrast },
                        lensFreshness = lensFreshness,
                        modifier = Modifier.align(Alignment.BottomCenter)
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                            .imePadding()
                            .then(if (dialogs.hasBlockingSurface) Modifier.semantics { hideFromAccessibility() } else Modifier)
                    )
                }
                DialogHost(dialogs)
            }
        }
    }
}
