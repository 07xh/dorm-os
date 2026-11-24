package io.github.x07xh.xiaoxu.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalFocusManager
import io.github.x07xh.xiaoxu.ui.v2.UiV2SettingsSearchField
import io.github.x07xh.xiaoxu.ui.v2.UiV2Setting
import io.github.x07xh.xiaoxu.ui.v2.uiV2DetailWidth
import io.github.x07xh.xiaoxu.ui.v2.UiV2SettingsConnectionCard
import io.github.x07xh.xiaoxu.ui.v2.UiV2SettingsIndexedEntry
import io.github.x07xh.xiaoxu.ui.v2.uiV2SearchSettings
import io.github.x07xh.xiaoxu.ui.v2.uiV2SettingsIndex
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.x07xh.xiaoxu.ui.v2.UiV2SettingsHeader
import io.github.x07xh.xiaoxu.ui.v2.UiV2Theme
import io.github.x07xh.xiaoxu.ui.v2.UiV2MotionSettingsPage
import io.github.x07xh.xiaoxu.ui.v2.UiV2SettingsGroup
import io.github.x07xh.xiaoxu.ui.v2.UiV2SettingsDivider
import io.github.x07xh.xiaoxu.ui.v2.UiV2SettingsEntry
import io.github.x07xh.xiaoxu.ui.v2.UiV2SettingsToggle
import io.github.x07xh.xiaoxu.ui.v2.uiV2SettingsParent
import io.github.x07xh.xiaoxu.data.CloudClient
import io.github.x07xh.xiaoxu.data.LocalCourseReminders
import io.github.x07xh.xiaoxu.data.LocalDeadlineReminders
import io.github.x07xh.xiaoxu.data.DeadlineReminderSchedule
import io.github.x07xh.xiaoxu.data.LocalQuietHours
import io.github.x07xh.xiaoxu.data.LocalReminderQuietHours
import io.github.x07xh.xiaoxu.data.LocalTask
import io.github.x07xh.xiaoxu.data.LocalScheduleStore
import io.github.x07xh.xiaoxu.data.LocalSemesterConfigStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A single settings hierarchy; actual platform connections, timetable and backups stay owned
 * by their existing components. The home page has no fake or duplicated controls. */
@Composable
fun ProfileSettingsPage(
    route: String,
    onRouteChange: (String) -> Unit,
    onScheduleSettings: () -> Unit,
    client: CloudClient,
    openWebsite: () -> Unit,
    openLocalSchool: (String) -> Unit,
    startSchoolAuthorization: (String) -> Unit,
    recentImport: LocalSchoolImportReceipt?,
    localTasks: List<LocalTask>,
    onMessages: () -> Unit,
    onRefresh: () -> Unit,
    onNavigation: (() -> Unit)? = null
) {
    var settingsQuery by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val openSetting: (String) -> Unit = { target ->
        focus.clearFocus()
        settingsQuery = ""
        if (target == "timetable-settings") onScheduleSettings() else onRouteChange(target)
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val compactSettingsHeader = maxHeight < 480.dp
    val settingsTwoColumns = uiV2DetailWidth(
        maxWidth.value.coerceAtMost(XiaoxuPageLayout.ContentMaxWidth.value), maxHeight.value,
        mainMin = 360f, detailMin = 320f, detailMax = 420f, gap = 16f, horizontalPadding = 32f
    ) != null
    androidx.compose.animation.AnimatedContent(
        targetState = route,
        transitionSpec = {
            xiaoxuSettingsRouteTransition(initialState, targetState)
        },
        modifier = Modifier.fillMaxSize().clipToBounds(),
        label = "settingsDetail"
    ) { animatedRoute ->
    val title = when (animatedRoute) {
        "connections" -> "数据连接"
        "notifications" -> "通知与提醒"
        "notifications-course" -> "课程提醒"
        "notifications-task" -> "任务提醒"
        "backup" -> "备份与恢复"
        "about" -> "关于校序"
        "licenses" -> "开源许可"
        "motion" -> "外观与动效"
        else -> "设置"
    }
    val goBack = { onRouteChange(uiV2SettingsParent(animatedRoute)) }
    // Each animated route paints an opaque page, so the outgoing settings
    // labels/cards cannot bleed through the incoming full-screen page.
    Column(
        modifier = Modifier.fillMaxSize().background(XiaoxuPalette.PageGradient),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        UiV2SettingsHeader(
            title = title,
            onBack = if (animatedRoute != "home") goBack else null,
            onRefresh = if (animatedRoute == "home") onRefresh else null,
            refreshing = client.state.busy,
            onMessages = if (animatedRoute == "home") onMessages else null,
            onNavigation = if (animatedRoute == "home") onNavigation else null,
            compact = animatedRoute != "home" || compactSettingsHeader
        )
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.weight(1f).xiaoxuPageWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = UiV2Theme.PageGutter, end = UiV2Theme.PageGutter, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(
                if (animatedRoute == "home") UiV2Theme.HubSectionGap else UiV2Theme.DetailSectionGap)
        ) {
            when (animatedRoute) {
                "home" -> {
                    item(key = "settings-search") {
                        UiV2SettingsSearchField(settingsQuery) { settingsQuery = it }
                    }
                    if (settingsQuery.isBlank()) {
                        item(key = "connection-card") {
                            UiV2SettingsConnectionCard(
                                authenticated = client.state.authenticated,
                                busy = client.state.busy,
                                onOpen = { openSetting("connections") }
                            )
                        }
                        val groups = uiV2SettingsIndex.filter {
                            it.group in listOf("学习与课程", "账户与数据", "其他") &&
                                it.route != "connections"
                        }.groupBy { it.group }
                        if (settingsTwoColumns) {
                            groups.entries.toList().chunked(2).forEach { pair ->
                                item(key = pair.first().key) {
                                    Row(Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        verticalAlignment = Alignment.Top) {
                                        pair.forEach { (group, entries) ->
                                            Column(Modifier.weight(1f)) {
                                                SettingsIndexedGroup(group, entries, onOpen = openSetting)
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            groups.forEach { (group, entries) ->
                                item(key = group) {
                                    SettingsIndexedGroup(group, entries, onOpen = openSetting)
                                }
                            }
                        }
                    } else {
                        val results = uiV2SearchSettings(settingsQuery)
                        item(key = "settings-results") {
                            if (results.isEmpty()) {
                                Text("没有匹配的设置", Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = XiaoxuPalette.Ink2)
                            } else {
                                SettingsIndexedGroup("搜索结果", results,
                                    showPath = true, onOpen = openSetting)
                            }
                        }
                    }
                }
                "connections" -> item {
                    ConnectionsPanel(client, openWebsite, openLocalSchool,
                        startSchoolAuthorization, recentImport)
                }
                "notifications", "notifications-course", "notifications-task" -> item {
                    NotificationSettingsContent(localTasks, animatedRoute, onRouteChange)
                }
                "backup" -> item { LocalTaskBackupPanel() }
                "about" -> item {
                    AboutPage(onOpenLicenses = { onRouteChange("licenses") })
                }
                "licenses" -> item { OpenSourceCreditsPanel() }
                "motion" -> item { UiV2MotionSettingsPage() }
            }
        }
    }
    }
    }
}

/** Shared indexed groups keep search results and the adaptive hub on the same row path. */
@Composable
private fun SettingsIndexedGroup(
    title: String, entries: List<UiV2Setting>, showPath: Boolean = false,
    onOpen: (String) -> Unit
) {
    UiV2SettingsGroup(title) {
        entries.forEachIndexed { index, entry ->
            if (index > 0) UiV2SettingsDivider()
            UiV2SettingsIndexedEntry(entry, showPath = showPath, onOpen = onOpen)
        }
    }
}

@Composable
private fun NotificationSettingsContent(
    tasks: List<LocalTask>,
    route: String,
    onRouteChange: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(LocalCourseReminders.enabled(context)) }
    var deadlineEnabled by remember { mutableStateOf(LocalDeadlineReminders.enabled(context)) }
    var leadMinutes by remember { mutableStateOf(LocalCourseReminders.leadMinutes(context)) }
    var leadPickerOpen by remember { mutableStateOf(false) }
    var reminderSchedule by remember { mutableStateOf(LocalDeadlineReminders.schedule(context)) }
    var taskTimePickerOpen by remember { mutableStateOf(false) }
    var quietHours by remember { mutableStateOf(LocalReminderQuietHours.load(context)) }
    // 0 = closed, 1 = start, 2 = end; only one picker is ever composed.
    var quietTimePicker by remember { mutableStateOf(0) }
    var permissionTarget by remember { mutableStateOf("course") }

    fun toggle(value: Boolean) {
        scope.launch {
            val success = withContext(Dispatchers.IO) {
                runCatching {
                    LocalCourseReminders.setEnabled(context, value)
                    !value || LocalCourseReminders.reconcileAfterSave(context,
                        LocalScheduleStore.load(context), LocalSemesterConfigStore.load(context))
                }.getOrDefault(false)
            }
            enabled = LocalCourseReminders.enabled(context)
            if (!success) Toast.makeText(context, "提醒调度失败，请重试", Toast.LENGTH_LONG).show()
        }
    }

    fun toggleDeadline(value: Boolean) {
        scope.launch {
            val success = withContext(Dispatchers.IO) {
                runCatching {
                    LocalDeadlineReminders.setEnabled(context, value)
                    if (value) LocalDeadlineReminders.reconcile(context, tasks)
                }.isSuccess
            }
            deadlineEnabled = LocalDeadlineReminders.enabled(context)
            if (!success) Toast.makeText(context, "本机截止提醒更新失败", Toast.LENGTH_LONG).show()
        }
    }

    fun updateReminderSchedule(value: DeadlineReminderSchedule) {
        scope.launch {
            val success = withContext(Dispatchers.IO) {
                runCatching {
                    if (!LocalDeadlineReminders.setSchedule(context, value)) false
                    else {
                        if (LocalDeadlineReminders.enabled(context)) {
                            LocalDeadlineReminders.reconcile(context, tasks)
                        }
                        true
                    }
                }.getOrDefault(false)
            }
            reminderSchedule = LocalDeadlineReminders.schedule(context)
            if (!success) Toast.makeText(context,
                "提醒配置未完全生效，请重试", Toast.LENGTH_LONG).show()
        }
    }

    fun updateQuietHours(value: LocalQuietHours) {
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                LocalReminderQuietHours.save(context, value)
            }
            quietHours = LocalReminderQuietHours.load(context)
            if (!saved) Toast.makeText(context,
                "免打扰设置保存失败，请重试", Toast.LENGTH_LONG).show()
        }
    }

    fun openPermissionSettings() {
        val intent = Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
        runCatching { context.startActivity(intent) }.onFailure {
            Toast.makeText(context, "无法打开系统通知设置", Toast.LENGTH_LONG).show()
        }
    }

    val requestPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            if (permissionTarget == "deadline" && LocalDeadlineReminders.canNotify(context)) {
                toggleDeadline(true)
            } else if (permissionTarget == "course" && LocalCourseReminders.canNotify(context)) {
                toggle(true)
            } else {
                Toast.makeText(context, "请检查校序系统通知开关", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "请允许校序发送通知", Toast.LENGTH_SHORT).show()
        }
    }

    // One Android 13+ permission flow; course/deadline scheduling stays independent.
    fun requestNotificationAccess(target: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) {
            permissionTarget = target
            requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openPermissionSettings()
        }
    }

    // A single short form replaces two nested reminder-settings routes.
    UiV2SettingsGroup(title = "课程") {
        UiV2SettingsToggle(
            title = "上课提醒", subtitle = "", checked = enabled,
            onCheckedChange = { value ->
                when {
                    !value -> toggle(false)
                    LocalCourseReminders.canNotify(context) -> toggle(true)
                    else -> requestNotificationAccess("course")
                }
            }
        )
        UiV2SettingsDivider()
        UiV2SettingsEntry("课前提醒", Icons.Outlined.DateRange, statusLabel = "$leadMinutes 分钟",
            onClick = { leadPickerOpen = true })
    }
    Spacer(Modifier.height(14.dp))
    UiV2SettingsGroup(title = "个人任务") {
        UiV2SettingsToggle(
            title = "截止提醒", subtitle = "", checked = deadlineEnabled,
            onCheckedChange = { value ->
                when {
                    !value -> toggleDeadline(false)
                    LocalDeadlineReminders.canNotify(context) -> toggleDeadline(true)
                    else -> requestNotificationAccess("deadline")
                }
            }
        )
        UiV2SettingsDivider()
        UiV2SettingsToggle("提前一天", "", reminderSchedule.dayBefore,
            onCheckedChange = { updateReminderSchedule(reminderSchedule.copy(dayBefore = it)) })
        UiV2SettingsDivider()
        UiV2SettingsToggle("截止当天", "", reminderSchedule.dueDay,
            onCheckedChange = { updateReminderSchedule(reminderSchedule.copy(dueDay = it)) })
        UiV2SettingsDivider()
        UiV2SettingsEntry(
            "提醒时间", Icons.Outlined.DateRange,
            statusLabel = "%02d:%02d".format(reminderSchedule.hour, reminderSchedule.minute),
            onClick = { taskTimePickerOpen = true }
        )
    }
    Spacer(Modifier.height(14.dp))
    UiV2SettingsGroup(title = "免打扰") {
        UiV2SettingsToggle(
            title = "启用本机免打扰",
            subtitle = "时段内跳过提醒，不补发；不修改系统免打扰",
            checked = quietHours.enabled,
            onCheckedChange = { updateQuietHours(quietHours.copy(enabled = it)) }
        )
        UiV2SettingsDivider()
        UiV2SettingsEntry(
            "开始时间", Icons.Outlined.DateRange,
            statusLabel = "%02d:%02d".format(
                quietHours.startMinutes / 60, quietHours.startMinutes % 60),
            onClick = { quietTimePicker = 1 }
        )
        UiV2SettingsDivider()
        UiV2SettingsEntry(
            "结束时间", Icons.Outlined.DateRange,
            statusLabel = "%02d:%02d".format(
                quietHours.endMinutes / 60, quietHours.endMinutes % 60),
            onClick = { quietTimePicker = 2 }
        )
    }
    Spacer(Modifier.height(14.dp))
    UiV2SettingsGroup(title = "系统") {
        UiV2SettingsEntry("通知权限与渠道", Icons.Outlined.NotificationsNone,
            onClick = ::openPermissionSettings)
    }
    Text("本机课程与个人任务 · 省电模式下可能延迟",
        style = MaterialTheme.typography.labelSmall, color = XiaoxuPalette.Ink3,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp))

    if (quietTimePicker != 0) {
        val pickingStart = quietTimePicker == 1
        val chosen = if (pickingStart) quietHours.startMinutes else quietHours.endMinutes
        AlertDialog(
            onDismissRequest = { quietTimePicker = 0 },
            title = { Text(if (pickingStart) "免打扰开始时间" else "免打扰结束时间") },
            text = {
                val listState = rememberLazyListState(
                    initialFirstVisibleItemIndex = (chosen / 30 - 2).coerceAtLeast(0))
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    state = listState
                ) {
                    items((0..47).toList()) { slot ->
                        val minutes = slot * 30
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .clickable {
                                quietTimePicker = 0
                                if (chosen != minutes) {
                                    updateQuietHours(if (pickingStart)
                                        quietHours.copy(startMinutes = minutes)
                                    else quietHours.copy(endMinutes = minutes))
                                }
                            }.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("%02d:%02d".format(minutes / 60, minutes % 60),
                                modifier = Modifier.weight(1f), color = XiaoxuPalette.Ink)
                            if (minutes == chosen) Icon(Icons.Outlined.CheckCircle,
                                contentDescription = "已选择", tint = XiaoxuPalette.Brand)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { quietTimePicker = 0 }) { Text("关闭") } }
        )
    }

    if (leadPickerOpen) {
        XiaoxuBottomActionSheet(
            title = "课前提醒时间",
            subtitle = "选择上课前的通知时间",
            onDismiss = { leadPickerOpen = false },
            actions = listOf(0, 5, 10, 15, 20, 30, 45, 60).map { minutes ->
                XiaoxuSheetAction(
                    label = if (minutes == 0) "上课时" else "提前 ${minutes} 分钟",
                    selected = minutes == leadMinutes,
                    onClick = {
                        leadPickerOpen = false
                        if (minutes != leadMinutes) {
                            scope.launch {
                                val success = withContext(Dispatchers.IO) {
                                    LocalCourseReminders.setLeadMinutes(context, minutes) &&
                                        LocalCourseReminders.reconcileAfterSave(
                                            context,
                                            LocalScheduleStore.load(context),
                                            LocalSemesterConfigStore.load(context)
                                        )
                                }
                                leadMinutes = LocalCourseReminders.leadMinutes(context)
                                if (!success) {
                                    Toast.makeText(context,
                                        "课前提醒时间保存或调度失败，请重试",
                                        Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                )
            }
        )
    }
        if (taskTimePickerOpen) {
        AlertDialog(
            onDismissRequest = { taskTimePickerOpen = false },
            title = { Text("任务提醒时间") },
            text = {
                val selectedSlot = reminderSchedule.hour * 2 + reminderSchedule.minute / 30
                val listState = rememberLazyListState(
                    initialFirstVisibleItemIndex = (selectedSlot - 2).coerceAtLeast(0)
                )
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)
                ) {
                    items((0..47).toList()) { slot ->
                        val hour = slot / 2
                        val minute = (slot % 2) * 30
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .clickable {
                                    taskTimePickerOpen = false
                                    if (hour != reminderSchedule.hour ||
                                        minute != reminderSchedule.minute) {
                                        updateReminderSchedule(reminderSchedule.copy(
                                            hour = hour, minute = minute))
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("%02d:%02d".format(hour, minute),
                                modifier = Modifier.weight(1f), color = XiaoxuPalette.Ink)
                            if (hour == reminderSchedule.hour && minute == reminderSchedule.minute) {
                                Icon(Icons.Outlined.CheckCircle, contentDescription = "已选择",
                                    tint = XiaoxuPalette.Brand)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { taskTimePickerOpen = false }) { Text("关闭") }
            }
        )
    }

}
