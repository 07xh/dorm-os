package com.tyust.course.academic.plugin

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.tyust.course.MainActivity
import io.github.x07xh.xiaoxu.NativeSchoolConnectActivity
import io.github.x07xh.xiaoxu.data.CloudClient
import io.github.x07xh.xiaoxu.data.LocalScheduleStore
import io.github.x07xh.xiaoxu.data.LocalTaskDatabase
import io.github.x07xh.xiaoxu.data.LocalTask
import io.github.x07xh.xiaoxu.data.VerifiedLesson
import io.github.x07xh.xiaoxu.ui.MessageCenterPanel
import io.github.x07xh.xiaoxu.ui.RealAcademicTaskHub
import io.github.x07xh.xiaoxu.ui.XiaoxuTheme
import io.github.x07xh.xiaoxu.ui.rememberLocalRows
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Hosts the original task-center Compose screen inside the bundled Service Extension.
 * UI, filtering, task details, dependency graph, execution steps and backup surfaces
 * are copied from the original Xiaoxu UI source. Only Activity navigation is adapted.
 */
@Suppress("UNUSED_PARAMETER")
@androidx.compose.runtime.Composable
internal fun TaskCenterServiceScreen(
    title: String,
    schoolName: String,
    accountScope: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val client = remember(appContext, accountScope) { CloudClient(appContext) }
    val database = remember(appContext) { LocalTaskDatabase.open(appContext) }
    val dao = remember(database) { database.tasks() }
    val localTasks by rememberLocalRows(dao) { dao.observe() }
    val localLessons by produceState(
        initialValue = emptyList<VerifiedLesson>(),
        key1 = appContext
    ) {
        value = withContext(Dispatchers.IO) {
            runCatching { LocalScheduleStore.load(appContext) }.getOrDefault(emptyList())
        }
    }

    var messagesOpen by rememberSaveable { mutableStateOf(false) }
    var focusedTaskId by rememberSaveable { mutableStateOf<Long?>(null) }
    var focusToken by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(client) {
        client.refreshOnForeground()
    }
    BackHandler(enabled = messagesOpen) {
        messagesOpen = false
    }

    val openWebsite: () -> Unit = {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://dorm-os-dk9.pages.dev/#settings"))
                .addCategory(Intent.CATEGORY_BROWSABLE)
        )
    }
    val openArticle: (String) -> Unit = { raw ->
        val uri = runCatching { Uri.parse(raw) }.getOrNull()
        if (uri?.scheme == "https" && uri.host == "www2.ncwu.edu.cn" &&
            (uri.path ?: "").startsWith("/jiaowu/info/")) {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
            )
        }
    }
    val openLocalSchool: (String) -> Unit = { provider ->
        if (provider == "chaoxing") {
            context.startActivity(
                Intent(context, NativeSchoolConnectActivity::class.java)
                    .putExtra(NativeSchoolConnectActivity.EXTRA_PROVIDER, provider)
            )
        }
    }

    XiaoxuTheme {
        if (messagesOpen) {
            MessageCenterPanel(
                client = client,
                openWebsite = openWebsite,
                openArticle = openArticle,
                onBack = { messagesOpen = false },
                localTasks = localTasks,
                localLessons = localLessons,
                onOpenLocalTasks = { taskId ->
                    focusedTaskId = taskId
                    focusToken++
                    messagesOpen = false
                },
                onOpenLocalSchedule = { lessonId, weekday ->
                    context.startActivity(
                        Intent(context, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            .putExtra("pageId", "app.schedule")
                    )
                    onBack()
                },
                onOpenReminderSettings = {
                    messagesOpen = false
                    onBack()
                }
            )
        } else {
            RealAcademicTaskHub(
                localTasks = localTasks,
                dao = dao,
                client = client,
                openWebsite = openWebsite,
                reminderTaskId = focusedTaskId,
                reminderTaskToken = focusToken,
                onReminderConsumed = { focusedTaskId = null },
                openLocalSchool = openLocalSchool,
                onMessages = { messagesOpen = true },
                onBack = onBack
            )
        }
    }
}
