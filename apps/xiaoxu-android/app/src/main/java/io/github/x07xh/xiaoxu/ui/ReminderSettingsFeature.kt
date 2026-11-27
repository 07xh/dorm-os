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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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

// Recovered reminder settings from the pre-rebuild ProfileSettingsPage.
// Existing WorkManager, permission and storage contracts remain unchanged.
@Composable
private fun ReminderSettingsEntry(title: String, icon: ImageVector, onClick: () -> Unit) {
    InsetGroupedRow(title = title, icon = icon, onClick = onClick, showDivider = false)
}

@Composable
internal fun ReminderSettingsFeature(data: AppWorkspaceState) {
    AppSnapshotContent(data.localTasks) { tasks ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            NotificationSettingsContent(tasks)
        }
    }
}

@Composable
private fun ReminderToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    InsetGroupedRow(
        title = title,
        subtitle = subtitle.takeIf { it.isNotBlank() },
        showDivider = false,
        trailing = { LiquidSwitch(checked = checked, onCheckedChange = onCheckedChange) }
    )
}

@Composable
internal fun NotificationSettingsContent(tasks: List<LocalTask>) {
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
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        ReminderSettingsEntry("课前 · " + leadMinutes + " 分钟", Icons.Outlined.DateRange) {
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
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        ReminderToggleRow("提前一天", "", reminderSchedule.dayBefore,
            onCheckedChange = { updateReminderSchedule(reminderSchedule.copy(dayBefore = it)) })
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        ReminderToggleRow("截止当天", "", reminderSchedule.dueDay,
            onCheckedChange = { updateReminderSchedule(reminderSchedule.copy(dueDay = it)) })
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        ReminderSettingsEntry(
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
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        ReminderSettingsEntry(
            "开始 · " + "%02d:%02d".format(
                quietHours.startMinutes / 60, quietHours.startMinutes % 60),
            Icons.Outlined.DateRange
        ) { quietTimePicker = 1 }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        ReminderSettingsEntry(
            "结束 · " + "%02d:%02d".format(
                quietHours.endMinutes / 60, quietHours.endMinutes % 60),
            Icons.Outlined.DateRange
        ) { quietTimePicker = 2 }
    }
    Spacer(Modifier.height(14.dp))
    XiaoxuGroupedSection(title = "系统") {
        ReminderSettingsEntry("通知权限与渠道", Icons.Outlined.NotificationsNone,
            onClick = ::openPermissionSettings)
    }
    Text("本机课程与个人任务 · 省电模式下可能延迟",
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp))

    if (quietTimePicker != 0) {
        val pickingStart = quietTimePicker == 1
        val chosen = if (pickingStart) quietHours.startMinutes else quietHours.endMinutes
        GlassTimePickerDialog(initialTime = "%02d:%02d".format(chosen / 60, chosen % 60),
            title = if (pickingStart) "免打扰开始时间" else "免打扰结束时间",
            minuteStep = 30, preserveInitial = true, onDismiss = { quietTimePicker = 0 },
            onConfirm = { value ->
                val time = java.time.LocalTime.parse(value)
                val minutes = time.hour * 60 + time.minute
                quietTimePicker = 0
                if (minutes != chosen) updateQuietHours(if (pickingStart)
                    quietHours.copy(startMinutes = minutes) else quietHours.copy(endMinutes = minutes))
            })
    }

    if (leadPickerOpen) {
        AppDialog(onDismissRequest = { leadPickerOpen = false }, title = { Text("课前提醒时间") },
            text = {
                Column {
                    listOf(0, 5, 10, 15, 20, 30, 45, 60).forEach { minutes ->
                        ReminderSettingsEntry(if (minutes == 0) "上课时" else "提前 ${minutes} 分钟",
                            if (minutes == leadMinutes) Icons.Outlined.CheckCircle else Icons.Outlined.DateRange) {
                            leadPickerOpen = false
                            if (minutes != leadMinutes) scope.launch {
                                val success = withContext(Dispatchers.IO) {
                                    LocalCourseReminders.setLeadMinutes(context, minutes) &&
                                        LocalCourseReminders.reconcileAfterSave(context, LocalScheduleStore.load(context),
                                            LocalSemesterConfigStore.load(context))
                                }
                                leadMinutes = LocalCourseReminders.leadMinutes(context)
                                if (!success) Toast.makeText(context, "课前提醒时间保存或调度失败，请重试", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }, confirmButton = { TextButton(onClick = { leadPickerOpen = false }) { Text("关闭") } })

    }
        if (taskTimePickerOpen) {
        GlassTimePickerDialog(initialTime = "%02d:%02d".format(reminderSchedule.hour, reminderSchedule.minute),
            title = "任务提醒时间", minuteStep = 30, preserveInitial = true,
            onDismiss = { taskTimePickerOpen = false },
            onConfirm = { value ->
                val time = java.time.LocalTime.parse(value)
                taskTimePickerOpen = false
                if (time.hour != reminderSchedule.hour || time.minute != reminderSchedule.minute)
                    updateReminderSchedule(reminderSchedule.copy(hour = time.hour, minute = time.minute))
            })
    }
}
