package io.github.x07xh.xiaoxu.ui

// SPDX-License-Identifier: GPL-3.0-only
// Source: znjhahaha/zhengfang-apk @ bb3b80a6d64e4c2057c0544bb417696ceaa2a9ed, ui/screen/ScheduleSettingsScreen.kt.
// Original authors and contributors. Basic settings and period row layout reused;
// local settings contract, validation, existing persistence and picker hosts adapted.
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.x07xh.xiaoxu.data.SemesterCalendar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun ScheduleSettingsFeature(data: AppWorkspaceState, actions: AppPageActions) {
    if (!data.timetableSettingsLoaded) {
        AppNotice("正在读取课表设置")
        return
    }
    val scope = rememberCoroutineScope()
    val saved = data.semesterCalendar
    var dateText by rememberSaveable { mutableStateOf(saved?.firstMonday?.toString().orEmpty()) }
    var weeksText by rememberSaveable { mutableStateOf(saved?.totalWeeks?.toString().orEmpty()) }
    var periodCount by rememberSaveable { mutableIntStateOf(data.timetableSections) }
    var periodsText by rememberSaveable { mutableStateOf(saved?.editableTimes().orEmpty()) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var status by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    val rows = remember(periodsText) {
        periodsText.lineSequence().filter { it.isNotBlank() }.mapNotNull { row ->
            val parts = row.split(' ', limit = 2)
            parts.firstOrNull()?.toIntOrNull()?.let { period ->
                val range = parts.getOrNull(1).orEmpty().split('-', limit = 2)
                PeriodTimeUi(period, range.firstOrNull().orEmpty(), range.getOrNull(1).orEmpty())
            }
        }.associateBy { it.period }
    }
    // Reducing display rows does not discard already saved times.
    val periodTimes = remember(rows, periodCount) {
        (1..maxOf(periodCount, rows.keys.maxOrNull() ?: 1)).map { rows[it] ?: PeriodTimeUi(it, "", "") }
    }
    fun updatePeriod(period: Int, start: String, end: String) {
        val next = rows.toMutableMap()
        if (start.isBlank() && end.isBlank()) next.remove(period)
        else next[period] = PeriodTimeUi(period, start.trim(), end.trim())
        periodsText = next.toSortedMap().values.joinToString("\n") { "${it.period} ${it.startTime}-${it.endTime}" }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.section)) {
        InsetGroupedSection(header = "基础设置") {
                            InsetGroupedRow(
                                icon = Icons.Filled.DateRange,
                                iconTint = Color(0xFF0A84FF),
                                title = "第一周开始日期",
                                subtitle = "以已确认的第一周周一计算",
                                trailing = {
                                    Text(
                                        text = dateText.ifBlank { "未设置" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                },
                                onClick = { editing = "date" }
                            )
                            InsetGroupedRow(
                                icon = Icons.Outlined.AccessTime,
                                iconTint = Color(0xFFFF9F0A),
                                title = "每天节数",
                                subtitle = "课表纵向的节次总数",
                                trailing = {
                                    Text(
                                        text = "$periodCount 节",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                },
                                showDivider = true,
                                // 原先是 Material DropdownMenu；换成与节次时间同一种
                                // 滚轮弹窗，两处交互一致
                                onClick = { editing = "sections" }
                            )
            InsetGroupedRow(title = "教学周数", subtitle = "按学校实际学期填写",
                trailing = { Text(weeksText.ifBlank { "未设置" }, color = MaterialTheme.colorScheme.primary) },
                showDivider = false, onClick = { editing = "weeks" })
        }
        InsetGroupedSection(header = "节次时间", footer = "点按节次修改起止时间；未设置的节次不会推算时间") {
                            periodTimes.forEachIndexed { index, periodTime ->
                                val hasTime = periodTime.startTime.isNotBlank() &&
                                    periodTime.endTime.isNotBlank()
                                // 不给图标：十几行同一枚时钟图标只是噪声，
                                // 「第 N 节」本身已经是最强的识别信息
                                InsetGroupedRow(
                                    title = "第 ${periodTime.period} 节",
                                    trailing = {
                                        Text(if (hasTime) "${periodTime.startTime} - ${periodTime.endTime}" else "未设置",
                                            color = if (hasTime) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodyMedium)
                                    },
                                    showDivider = index != periodTimes.lastIndex,
                                    onClick = { editing = "period:${periodTime.period}" }
                                )
                            }
        }
        error?.let { AppNotice(it, error = true) }
        status?.let { AppNotice(it) }
        Button(modifier = Modifier.fillMaxWidth(), enabled = !saving, onClick = {
            val config = try {
                SemesterCalendar.parse(dateText, weeksText, periodsText)
            } catch (e: IllegalArgumentException) {
                error = e.message ?: "请检查设置"; null
            }
            if (config != null) {
                saving = true
                error = null
                status = null
                scope.launch {
                    try {
                        val remindersOk = actions.saveScheduleSettings(config, periodCount)
                        status = if (remindersOk) "已保存" else "课表设置已保存，提醒调度失败"
                    } catch (cancel: CancellationException) { throw cancel }
                    catch (_: Exception) { error = "保存未完成，请重试" }
                    finally { saving = false }
                }
            }
        }) { Text(if (saving) "保存中" else "保存设置") }
        Spacer(Modifier.height(12.dp))
    }
    editing?.let { field ->
        fun accepted() { editing = null; error = null; status = null }
        when (field) {
            "date" -> GlassDatePickerDialog(
                initialDate = runCatching { java.time.LocalDate.parse(dateText) }
                    .getOrElse { java.time.LocalDate.now() },
                title = "第一周周一", preserveInitial = true,
                onDismiss = { editing = null },
                onConfirm = { date ->
                    if (date.dayOfWeek != java.time.DayOfWeek.MONDAY) {
                        error = "第一周开始日期必须为周一"
                        status = null
                        editing = null
                    } else { dateText = date.toString(); accepted() }
                })
            "weeks" -> GlassOptionWheelDialog(
                options = (1..35).map { "$it 周" },
                selectedIndex = (weeksText.toIntOrNull() ?: 1) - 1,
                title = "教学周数", onDismiss = { editing = null },
                onConfirm = { weeksText = (it + 1).toString(); accepted() })
            "sections" -> GlassOptionWheelDialog(
                options = (1..24).map { "$it 节" },
                selectedIndex = periodCount - 1,
                title = "每天节数", onDismiss = { editing = null },
                onConfirm = { periodCount = it + 1; accepted() })
            else -> {
                val period = field.substringAfter("period:", "").toIntOrNull()
                if (period != null) {
                    GlassTimeRangePickerDialog(
                        initialStart = rows[period]?.startTime.orEmpty(),
                        initialEnd = rows[period]?.endTime.orEmpty(),
                        title = "第 ${period} 节", minuteStep = 1, preserveInitial = true,
                        onDismiss = { editing = null },
                        onClear = { updatePeriod(period, "", ""); accepted() },
                        onConfirm = { start, end ->
                            if (java.time.LocalTime.parse(end).isAfter(java.time.LocalTime.parse(start))) {
                                updatePeriod(period, start, end); accepted()
                            } else {
                                error = "结束时间必须晚于开始时间"
                                status = null
                                editing = null
                            }
                        })
                }
            }
        }
    }
}
