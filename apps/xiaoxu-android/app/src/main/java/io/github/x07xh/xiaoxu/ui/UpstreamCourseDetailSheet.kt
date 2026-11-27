package io.github.x07xh.xiaoxu.ui

// SPDX-License-Identifier: GPL-3.0-only
// Source: znjhahaha/zhengfang-apk @ bb3b80a6d64e4c2057c0544bb417696ceaa2a9ed, ui/screen/CourseDetailContent.kt
// Original authors and contributors. Extracted upstream info layout and tiles.
// Existing Material sheet host owns drag; existing Xiaoxu reminders own persistence.

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.x07xh.xiaoxu.data.LocalCourseReminders
import io.github.x07xh.xiaoxu.data.LocalScheduleStore
import io.github.x07xh.xiaoxu.data.LocalSemesterConfigStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UpstreamCourseDetailSheet(course: ScheduleCourseUi, allCourses: List<ScheduleCourseUi>,
    timeRange: String, currentWeek: Int, onEdit: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val conflicts = remember(course, allCourses) { scheduleConflicts(course.record(), allCourses.map { it.record() }) }
    var reminderSelected by remember(course.id) {
        mutableStateOf(LocalCourseReminders.enabledForCourse(context, course.id))
    }
    val lead = LocalCourseReminders.leadMinutes(context)
    fun applyReminder(enabled: Boolean) {
        LocalCourseReminders.setCourseEnabled(context, course.id, enabled)
        reminderSelected = enabled
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                LocalCourseReminders.reconcileAfterSave(context, LocalScheduleStore.load(context),
                    LocalSemesterConfigStore.load(context))
            }
            if (!ok) Toast.makeText(context, "提醒设置已保存，但调度失败", Toast.LENGTH_LONG).show()
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && LocalCourseReminders.canNotify(context)) applyReminder(true)
        else Toast.makeText(context, "未获得通知权限，课程提醒未开启", Toast.LENGTH_SHORT).show()
    }
    ModalBottomSheet(onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = colors.surface) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
            val wideDetails = maxWidth >= 360.dp && density.fontScale <= 1.2f && course.location.length <= 18
            Column(Modifier.fillMaxWidth().heightIn(max = 580.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f).padding(top = 5.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            Box(Modifier.size(22.dp, 4.dp).background(course.color, RoundedCornerShape(2.dp)))
                            Text("课程详情", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                        }
                        Text(course.name, Modifier.padding(top = 7.dp).semantics { heading() },
                            fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onEdit, Modifier.size(48.dp)) { Icon(Icons.Outlined.Edit, contentDescription = "编辑课程") }
                    IconButton(onClose, Modifier.size(48.dp)) { Icon(Icons.Outlined.Close, contentDescription = "关闭课程详情") }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val weekday = "一二三四五六日".getOrElse(course.day - 1) { ' ' }
                        val time = (timeRange.takeIf { it.isNotBlank() }?.plus("\n").orEmpty()) +
                            "周$weekday · 第 ${course.startPeriod}–${course.endPeriod} 节"
                        if (wideDetails) Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            DetailInfoTile("上课时间", time, Icons.Outlined.AccessTime, Modifier.weight(1f).fillMaxHeight())
                            DetailInfoTile("上课地点", course.location.ifBlank { "未指定地点" }, Icons.Outlined.LocationOn,
                                Modifier.weight(1f).fillMaxHeight())
                        } else {
                            DetailInfoTile("上课时间", time, Icons.Outlined.AccessTime)
                            DetailInfoTile("上课地点", course.location.ifBlank { "未指定地点" }, Icons.Outlined.LocationOn)
                        }
                        Column(Modifier.padding(vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            DetailInfoLine("教师", course.teacher.ifBlank { "未指定教师" }, Icons.Outlined.Person)
                            DetailInfoLine("周次", course.weeks.ifBlank { "待补全" }, Icons.Outlined.DateRange)
                        }
                        var allWeeks by remember(course.id) { mutableStateOf(false) }
                        val details = course.details.filter { allWeeks || currentWeek == 0 || it.weeks == null || currentWeek in it.weeks }
                        details.filter { it.value.isNotBlank() }.forEach { field ->
                            Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                Text(field.label + (field.weeks?.let { " · 第 ${it.joinToString("、")} 周" } ?: ""), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                                androidx.compose.foundation.text.selection.SelectionContainer { Text(field.value, style = MaterialTheme.typography.bodyMedium) }
                            }
                        }
                        if (course.details.any { it.weeks != null }) TextButton(onClick = { allWeeks = !allWeeks }) { Text(if (allWeeks) "只看当前周" else "查看其他周次的课程信息") }
                        if (!ScheduleWeeks.parse(course.weeks).valid) Text("周次待核对，暂不能安排提醒。", color = colors.error, style = MaterialTheme.typography.bodySmall)
                        conflicts.forEach { conflict ->
                            Surface(color = colors.errorContainer, shape = RoundedCornerShape(14.dp)) {
                                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Outlined.Warning, contentDescription = null, modifier = Modifier.size(18.dp), tint = colors.onErrorContainer)
                                    Text("与「${conflict.otherName}」在第 ${conflict.weeks.joinToString("、")} 周、第 " +
                                        "${conflict.startPeriod}–${conflict.endPeriod} 节重叠", color = colors.onErrorContainer,
                                        style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                }
                Surface(Modifier.fillMaxWidth(), color = scheduleCardColor(course.color, 0.025f),
                    border = scheduleCardBorder(course.color), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Outlined.Notifications, contentDescription = null, modifier = Modifier.size(20.dp),
                                tint = if (reminderSelected) colors.primary else colors.onSurfaceVariant)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("课程提醒", style = MaterialTheme.typography.titleSmall)
                                Text(if (lead == 0) "上课时通知" else "提前 $lead 分钟",
                                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                            }
                            LiquidSwitch(checked = reminderSelected, onCheckedChange = { enable ->
                                if (!enable || LocalCourseReminders.canNotify(context)) applyReminder(enable)
                                else if (Build.VERSION.SDK_INT >= 33 &&
                                    androidx.core.content.ContextCompat.checkSelfPermission(context,
                                        Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    val settings = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    runCatching { context.startActivity(settings) }.onFailure {
                                        Toast.makeText(context, "请在系统设置中开启通知权限", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            })
                        }
                        if (timeRange.isBlank()) Text("尚未配置完整节次时间，提醒可能无法调度。",
                            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun DetailInfoTile(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), color = scheduleCardColor(MaterialTheme.colorScheme.primary, 0.02f),
        border = scheduleCardBorder(MaterialTheme.colorScheme.primary), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.scheduleGlassSheen().padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary)
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(value, style = MaterialTheme.typography.titleSmall, lineHeight = 21.sp)
        }
    }
}

@Composable
private fun DetailInfoLine(label: String, value: String, icon: ImageVector) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 3.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, modifier = Modifier.padding(top = 1.dp).size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}
