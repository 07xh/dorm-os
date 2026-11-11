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
                    val version = remember(context) {
                        runCatching {
                            context.packageManager.getPackageInfo(context.packageName, 0).versionName
                        }.getOrNull() ?: "未知"
                    }
                    XiaoxuGroupedSection {
                        Column(Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("校序 Academic OS", style = MaterialTheme.typography.titleMedium,
                                color = XiaoxuPalette.Ink)
                            Text("版本 " + version, style = MaterialTheme.typography.bodySmall,
                                color = XiaoxuPalette.Ink2)
                        }
                        SettingsDivider()
                        SettingsEntry("开源许可", Icons.Outlined.Info) {
                            onRouteChange("licenses")
                        }
                    }
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
private fun SettingsEntry(title: String, icon: ImageVector, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp)
        .clickable(role = Role.Button, onClickLabel = "打开" + title, onClick = onClick)
        .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null,
            tint = XiaoxuPalette.Brand, modifier = Modifier.size(22.dp))
        Spacer(Modifier.size(16.dp))
        Text(title, modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            color = XiaoxuPalette.Ink)
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
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = XiaoxuPalette.Ink2)
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

    when (route) {
        "notifications" -> {
            XiaoxuGroupedSection(title = "提醒类别") {
                SettingsEntry("课程提醒", Icons.Outlined.DateRange) {
                    onRouteChange("notifications-course")
                }
                SettingsDivider()
                SettingsEntry("任务提醒", Icons.Outlined.CheckCircle) {
                    onRouteChange("notifications-task")
                }
            }
            Spacer(Modifier.height(16.dp))
            XiaoxuGroupedSection(title = "系统通知") {
                SettingsEntry("管理系统通知权限", Icons.Outlined.NotificationsNone,
                    onClick = ::openPermissionSettings)
            }
            Spacer(Modifier.height(16.dp))
            XiaoxuGroupedSection(title = "其他学业来源") {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Text("学习通作业、考试、第二课堂和微信任务",
                        style = MaterialTheme.typography.titleMedium, color = XiaoxuPalette.Ink)
                    Text("待本人真实数据与状态核验后接入系统提醒；现阶段仍可在原有消息或任务页面查看，不提供无效开关。",
                        style = MaterialTheme.typography.bodySmall, color = XiaoxuPalette.Ink2)
                }
            }
        }
        "notifications-course" -> {
            XiaoxuGroupedSection(title = "本机课表") {
                ReminderToggleRow(
                    title = "上课提醒",
                    subtitle = "根据本机课表安排，单门课程可单独设置",
                    checked = enabled,
                    onCheckedChange = { value ->
                        when {
                            !value -> toggle(false)
                            LocalCourseReminders.canNotify(context) -> toggle(true)
                            else -> requestNotificationAccess("course")
                        }
                    }
                )
                SettingsDivider()
                SettingsEntry("提前提醒 · ${leadMinutes} 分钟", Icons.Outlined.DateRange) {
                    leadPickerOpen = true
                }
            }
            Spacer(Modifier.height(16.dp))
            XiaoxuGroupedSection(title = "送达说明") {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Text("提醒由 WorkManager 在本机排定，受系统省电策略影响，实际到达可能延迟。",
                        style = MaterialTheme.typography.bodySmall, color = XiaoxuPalette.Ink2)
                }
            }
        }
        "notifications-task" -> {
            XiaoxuGroupedSection(title = "本机待办") {
                ReminderToggleRow(
                    title = "本机截止提醒",
                    subtitle = "仅提醒本人填写的日期，截止前一天及当天约 09:00",
                    checked = deadlineEnabled,
                    onCheckedChange = { value ->
                        when {
                            !value -> toggleDeadline(false)
                            LocalDeadlineReminders.canNotify(context) -> toggleDeadline(true)
                            else -> requestNotificationAccess("deadline")
                        }
                    }
                )
            }
            Spacer(Modifier.height(16.dp))
            XiaoxuGroupedSection(title = "数据范围") {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Text("本机提醒不会把未核对的学习通作业当作未完成任务，也不会修改学校平台的提交状态。",
                        style = MaterialTheme.typography.bodySmall, color = XiaoxuPalette.Ink2)
                }
            }
        }
    }

    if (route == "notifications-course" && leadPickerOpen) {
        AlertDialog(
            onDismissRequest = { leadPickerOpen = false },
            title = { Text("课前提醒时间") },
            text = {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    items(listOf(0, 5, 10, 15, 20, 30, 45, 60)) { minutes ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .clickable {
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
                                            if (!success) Toast.makeText(context,
                                                "课前提醒时间保存或调度失败，请重试", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(if (minutes == 0) "上课时" else "提前 ${minutes} 分钟",
                                modifier = Modifier.weight(1f), color = XiaoxuPalette.Ink)
                            if (minutes == leadMinutes) {
                                Icon(Icons.Outlined.CheckCircle, contentDescription = "已选择",
                                    tint = XiaoxuPalette.Brand)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { leadPickerOpen = false }) { Text("关闭") }
            }
        )
    }
}
