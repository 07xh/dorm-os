package io.github.x07xh.xiaoxu.ui

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.x07xh.xiaoxu.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** App-scoped read model. No second database or school client is created by a page. */
internal class AppWorkspaceState(
    val localTasks: PageSnapshot<List<LocalTask>>,
    val lessons: PageSnapshot<List<VerifiedLesson>>,
    val nativeRecords: PageSnapshot<List<NativeSchoolRecord>>,
    val cloud: CloudUiState
)

@Composable
internal fun <T> rememberSnapshotRows(owner: Any, query: () -> Flow<List<T>>): State<List<T>?> {
    val rows: Flow<List<T>?> = remember(owner) { query().distinctUntilChanged() }
    return key(owner) { rows.collectAsStateWithLifecycle(initialValue = null) }
}

@Composable
internal fun rememberAppWorkspace(
    dao: LocalTaskDao, cloud: CloudClient,
    lessons: List<VerifiedLesson>, scheduleRevision: Int,
    lifecycle: Lifecycle, returnedSchool: String?, consumeWebsiteReturn: () -> Boolean
): AppWorkspaceState {
    val context = LocalContext.current.applicationContext
    val nativeDao = remember(context) { LocalTaskDatabase.open(context).nativeSchoolRecords() }
    val tasks by rememberSnapshotRows(dao) { dao.observe() }
    val records by rememberSnapshotRows(nativeDao) { nativeDao.observe() }
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
    // Do not reconcile a fictitious empty list before Room has emitted a snapshot.
    var previousTasks by remember(dao) { mutableStateOf<List<LocalTask>?>(null) }
    LaunchedEffect(tasks) {
        val snapshot = tasks ?: return@LaunchedEffect
        val previous = previousTasks
        withContext(Dispatchers.IO) {
            LocalDeadlineReminders.reconcile(context, snapshot, previousTasks = previous)
        }
        previousTasks = snapshot
    }
    return AppWorkspaceState(
        localTasks = pageSnapshot(tasks),
        lessons = pageSnapshot(lessons.takeIf { scheduleRevision > 0 || it.isNotEmpty() }),
        nativeRecords = pageSnapshot(records),
        cloud = cloud.state
    )
}
