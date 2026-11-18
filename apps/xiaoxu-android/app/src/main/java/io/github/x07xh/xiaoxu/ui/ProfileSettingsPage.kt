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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
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
        else -> "设置"
    }
    val goBack = { onRouteChange(when (animatedRoute) {
        "licenses" -> "about"
        "notifications-course", "notifications-task" -> "notifications"
        else -> "home"
    }) }
    // Each animated route paints an opaque page, so the outgoing settings
    // labels/cards cannot bleed through the incoming full-screen page.
    Column(
        modifier = Modifier.fillMaxSize().background(XiaoxuPalette.PageGradient),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Row(Modifier.xiaoxuPageWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (animatedRoute != "home") {
                XiaoxuSoftIconButton(onClick = goBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回设置")
                }
            }
            Text(title, modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineSmall,
                color = XiaoxuPalette.Ink)
            if (animatedRoute == "home") {
                XiaoxuSoftIconButton(onClick = onRefresh, enabled = !client.state.busy) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                }
                XiaoxuSoftIconButton(onClick = onMessages) {
                    Icon(Icons.Outlined.NotificationsNone, contentDescription = "消息中心")
                }
            }
        }
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.weight(1f).xiaoxuPageWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 18.dp, end = 18.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (animatedRoute) {
                "home" -> {
                    item {
                        XiaoxuGroupedSection(title = "学习与课程") {
                            SettingsEntry("课表设置", Icons.Outlined.DateRange,
                                onClick = onScheduleSettings)
                            SettingsDivider()
                            SettingsEntry("通知与提醒", Icons.Outlined.NotificationsNone) {
                                onRouteChange("notifications")
                            }
                        }
                    }
                    item {
                        XiaoxuGroupedSection(title = "账户与数据") {
                            SettingsEntry("数据连接", Icons.Outlined.School) {
                                onRouteChange("connections")
                            }
                            SettingsDivider()
                            SettingsEntry("备份与恢复", Icons.Outlined.CheckCircle) {
                                onRouteChange("backup")
                            }
                        }
                    }
                    item {
                        XiaoxuGroupedSection(title = "其他") {
                            SettingsEntry("关于校序", Icons.Outlined.Info) {
                                onRouteChange("about")
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
            }
        }
    }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(color = XiaoxuPalette.Line, thickness = 0.5.dp,
        modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun SettingsEntry(
    title: String,
    icon: ImageVector,
    subtitle: String? = null,
    statusLabel: String? = null,
    onClick: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth().heightIn(min = if (subtitle == null) 58.dp else 70.dp)
        .clickable(role = Role.Button, onClickLabel = "打开" + title, onClick = onClick)
        .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null,
            tint = XiaoxuPalette.Brand, modifier = Modifier.size(22.dp))
        Spacer(Modifier.size(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium,
                color = XiaoxuPalette.Ink)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = XiaoxuPalette.Ink2)
            }
        }
        if (!statusLabel.isNullOrBlank()) {
            Text(statusLabel, style = MaterialTheme.typography.labelSmall,
                color = XiaoxuPalette.Ink2, modifier = Modifier.padding(end = 8.dp))
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null,
            tint = XiaoxuPalette.Ink3, modifier = Modifier.size(20.dp))
    }
}

/** Shared reminder row; each caller owns its schedule and Android notification permission checks. */
@Composable
private fun ReminderToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth().heightIn(min = 70.dp)
        .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = XiaoxuPalette.Ink)
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = XiaoxuPalette.Ink2)
        }
        XiaoxuSettingsSwitch(checked = checked, onCheckedChange = onCheckedChange)
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
    XiaoxuGroupedSection(title = "课程") {
        ReminderToggleRow(
            title = "上课提醒", subtitle = "", checked = enabled,
            onCheckedChange = { value ->
                when {
                    !value -> toggle(false)
                    LocalCourseReminders.canNotify(context) -> toggle(true)
                    else -> requestNotificationAccess("course")
                }
            }
        )
        SettingsDivider()
        SettingsEntry("课前 · " + leadMinutes + " 分钟", Icons.Outlined.DateRange) {
            leadPickerOpen = true
        }
    }
    Spacer(Modifier.height(14.dp))
    XiaoxuGroupedSection(title = "个人任务") {
        ReminderToggleRow(
            title = "截止提醒", subtitle = "", checked = deadlineEnabled,
            onCheckedChange = { value ->
                when {
                    !value -> toggleDeadline(false)
                    LocalDeadlineReminders.canNotify(context) -> toggleDeadline(true)
                    else -> requestNotificationAccess("deadline")
                }
            }
        )
        SettingsDivider()
        ReminderToggleRow("提前一天", "", reminderSchedule.dayBefore,
            onCheckedChange = { updateReminderSchedule(reminderSchedule.copy(dayBefore = it)) })
        SettingsDivider()
        ReminderToggleRow("截止当天", "", reminderSchedule.dueDay,
            onCheckedChange = { updateReminderSchedule(reminderSchedule.copy(dueDay = it)) })
        SettingsDivider()
        SettingsEntry(
            "提醒时间 · " + "%02d:%02d".format(reminderSchedule.hour, reminderSchedule.minute),
            Icons.Outlined.DateRange
        ) { taskTimePickerOpen = true }
    }
    Spacer(Modifier.height(14.dp))
    XiaoxuGroupedSection(title = "免打扰") {
        ReminderToggleRow(
            title = "启用本机免打扰",
            subtitle = "时段内跳过提醒，不补发；不修改系统免打扰",
            checked = quietHours.enabled,
            onCheckedChange = { updateQuietHours(quietHours.copy(enabled = it)) }
        )
        SettingsDivider()
        SettingsEntry(
            "开始 · " + "%02d:%02d".format(
                quietHours.startMinutes / 60, quietHours.startMinutes % 60),
            Icons.Outlined.DateRange
        ) { quietTimePicker = 1 }
        SettingsDivider()
        SettingsEntry(
            "结束 · " + "%02d:%02d".format(
                quietHours.endMinutes / 60, quietHours.endMinutes % 60),
            Icons.Outlined.DateRange
        ) { quietTimePicker = 2 }
    }
    Spacer(Modifier.height(14.dp))
    XiaoxuGroupedSection(title = "系统") {
        SettingsEntry("通知权限与渠道", Icons.Outlined.NotificationsNone,
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
