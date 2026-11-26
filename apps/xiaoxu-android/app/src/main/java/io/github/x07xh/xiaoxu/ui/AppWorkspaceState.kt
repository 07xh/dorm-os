package io.github.x07xh.xiaoxu.ui

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.x07xh.xiaoxu.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** App-scoped read model. No second database or school client is created by a page. */
@Stable
internal class AppWorkspaceState(
    private val tasksSource: () -> PageSnapshot<List<LocalTask>>,
    private val dependenciesSource: () -> PageSnapshot<List<LocalTaskDependency>>,
    private val lessonsSource: () -> PageSnapshot<List<VerifiedLesson>>,
    private val recordsSource: () -> PageSnapshot<List<NativeSchoolRecord>>,
    private val cloudSource: () -> CloudUiState,
    private val campusSource: () -> String?,
    private val calendarSource: () -> SemesterCalendar?,
    private val sectionsSource: () -> Int,
    private val settingsLoadedSource: () -> Boolean
) {
    constructor(localTasks: PageSnapshot<List<LocalTask>>, dependencies: PageSnapshot<List<LocalTaskDependency>>,
        lessons: PageSnapshot<List<VerifiedLesson>>, nativeRecords: PageSnapshot<List<NativeSchoolRecord>>,
        cloud: CloudUiState, campusRunScope: String?, semesterCalendar: SemesterCalendar?,
        timetableSections: Int, timetableSettingsLoaded: Boolean
    ) : this({ localTasks }, { dependencies }, { lessons }, { nativeRecords }, { cloud },
        { campusRunScope }, { semesterCalendar }, { timetableSections }, { timetableSettingsLoaded })

    val localTasks get() = tasksSource()
    val dependencies get() = dependenciesSource()
    val lessons get() = lessonsSource()
    val nativeRecords get() = recordsSource()
    val cloud get() = cloudSource()
    val campusRunScope get() = campusSource()
    val semesterCalendar get() = calendarSource()
    val timetableSections get() = sectionsSource()
    val timetableSettingsLoaded get() = settingsLoadedSource()
}

@Composable
internal fun <T> rememberSnapshotRows(owner: Any, query: () -> Flow<List<T>>): State<List<T>?> {
    val rows: Flow<List<T>?> = remember(owner) { query().distinctUntilChanged() }
    return key(owner) { rows.collectAsStateWithLifecycle(initialValue = null) }
}

@Composable
internal fun rememberAppWorkspace(
    dao: LocalTaskDao, cloud: CloudClient,
    lessons: List<VerifiedLesson>, scheduleRevision: Int, settingsRevision: Int,
    lifecycle: Lifecycle, returnedSchool: String?, consumeWebsiteReturn: () -> Boolean
): AppWorkspaceState {
    val context = LocalContext.current.applicationContext
    val nativeDao = remember(context) { LocalTaskDatabase.open(context).nativeSchoolRecords() }
    val tasks = rememberSnapshotRows(dao) { dao.observe() }
    val dependencies = rememberSnapshotRows(dao) { dao.observeDependencies() }
    val records = rememberSnapshotRows(nativeDao) { nativeDao.observe() }
    val settings = produceState<Pair<SemesterCalendar?, Int>?>(initialValue = null,
        key1 = context, key2 = scheduleRevision, key3 = settingsRevision) {
        value = withContext(Dispatchers.IO) {
            LocalSemesterConfigStore.load(context) to LocalSemesterConfigStore.loadSections(context)
        }
    }
    val scope = rememberCoroutineScope()
    val currentReturn by rememberUpdatedState(returnedSchool)
    val currentConsume by rememberUpdatedState(consumeWebsiteReturn)
    DisposableEffect(lifecycle, cloud) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && currentReturn == null) {
                val force = currentConsume()
                scope.launch { if (force) cloud.refresh() else cloud.refreshOnForeground() }
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    // Observe real Room emissions in the effect, not in the root composition.
    // Reconcile against the last processed snapshot, including after a burst of updates.
    LaunchedEffect(dao, tasks, context) {
        var previousTasks: List<LocalTask>? = null
        snapshotFlow { tasks.value }.filterNotNull().collect { snapshot ->
            val previous = previousTasks
            withContext(Dispatchers.IO) {
                LocalDeadlineReminders.reconcile(context, snapshot, previousTasks = previous)
            }
            previousTasks = snapshot
        }
    }
    val account by remember(cloud) { derivedStateOf { cloud.state.authenticated to cloud.state.ownerId } }
    val (authenticated, owner) = account
    val generation = cloud.ownerSessionGeneration
    val campusScope = key(cloud, authenticated, owner, generation) {
        produceState<String?>(null) {
            if (authenticated && !owner.isNullOrBlank()) value = withContext(Dispatchers.IO) {
                campusRunVisibleScope(authenticated = true, ownerSessionBinding = cloud.ownerSessionBinding())
            }
        }
    }
    val latestLessons = rememberUpdatedState(lessons)
    val latestRevision = rememberUpdatedState(scheduleRevision)
    // Stable source handles: the Compose scope that reads a field observes that
    // source directly. Unrelated page data changes no longer invalidate the shell.
    return remember(tasks, dependencies, records, settings, cloud, campusScope, latestLessons, latestRevision) {
        AppWorkspaceState(
            tasksSource = { pageSnapshot(tasks.value) },
            dependenciesSource = { pageSnapshot(dependencies.value) },
            lessonsSource = { pageSnapshot(latestLessons.value.takeIf { latestRevision.value > 0 || it.isNotEmpty() }) },
            recordsSource = { pageSnapshot(records.value) },
            cloudSource = { cloud.state },
            campusSource = { campusScope.value },
            calendarSource = { settings.value?.first },
            sectionsSource = { settings.value?.second ?: 12 },
            settingsLoadedSource = { settings.value != null }
        )
    }
}
