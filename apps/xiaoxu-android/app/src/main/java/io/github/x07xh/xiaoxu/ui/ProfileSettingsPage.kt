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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
        "backup" -> "备份与恢复"
        "about" -> "关于校序"
        "licenses" -> "开源许可"
        else -> "设置"
    }
    val goBack = { onRouteChange(if (animatedRoute == "licenses") "about" else "home") }
    // Each animated route paints an opaque page, so the outgoing settings
    // labels/cards cannot bleed through the incoming full-screen page.
    Column(modifier = Modifier.fillMaxSize().background(XiaoxuPalette.PageGradient),
        verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (animatedRoute != "home") {
                IconButton(onClick = goBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回设置")
                }
            }
            Text(title, modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineSmall,
                color = XiaoxuPalette.Ink)
            if (animatedRoute == "home") {
                IconButton(onClick = onRefresh, enabled = !client.state.busy) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                }
                IconButton(onClick = onMessages) {
                    Icon(Icons.Outlined.NotificationsNone, contentDescription = "消息中心")
                }
            }
        }
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 18.dp, end = 18.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (animatedRoute) {
                "home" -> {
                    item {
                        XiaoxuGroupedSection {
                            SettingsEntry("校序 Academic OS", Icons.Outlined.School) {
                                onRouteChange("about")
                            }
                        }
                    }
                    item {
                        XiaoxuGroupedSection(title = "学习") {
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
                }
                "connections" -> item {
                    ConnectionsPanel(client, openWebsite, openLocalSchool,
                        startSchoolAuthorization, recentImport)
                }
                "notifications" -> item { NotificationSettingsContent(localTasks) }
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
private fun NotificationSettingsContent(tasks: List<LocalTask>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(LocalCourseReminders.enabled(context)) }
    var deadlineEnabled by remember { mutableStateOf(LocalDeadlineReminders.enabled(context)) }
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

    XiaoxuGroupedSection(title = "课程提醒") {
        ReminderToggleRow(
            title = "上课提醒",
            subtitle = "默认课前约 15 分钟，单门课程可单独设置",
            checked = enabled,
            onCheckedChange = { value ->
                when {
                    !value -> toggle(false)
                    LocalCourseReminders.canNotify(context) -> toggle(true)
                    else -> requestNotificationAccess("course")
                }
            }
        )
    }
    Spacer(Modifier.height(16.dp))
    XiaoxuGroupedSection(title = "任务提醒") {
        ReminderToggleRow(
            title = "本机截止提醒",
            subtitle = "仅提醒手动填写的日期，提前一天及当天约 09:00",
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
    XiaoxuGroupedSection(title = "系统") {
        SettingsEntry("通知权限", Icons.Outlined.NotificationsNone,
            onClick = ::openPermissionSettings)
    }
}
