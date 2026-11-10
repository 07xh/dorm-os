package io.github.x07xh.xiaoxu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.x07xh.xiaoxu.data.NcwuElectiveWish
import io.github.x07xh.xiaoxu.data.NcwuElectiveWishPlan
import io.github.x07xh.xiaoxu.data.NcwuElectiveWishStore
import io.github.x07xh.xiaoxu.data.NcwuWishConflict

/**
 * Manual notes only. This screen never pretends to fetch real courses or
 * submit an enrollment. The official school page remains the source of truth.
 */
@Composable
fun NcwuElectivePlannerPage(onOpenOfficial: () -> Unit) {
    val context = LocalContext.current
    val initial = remember(context) { runCatching { NcwuElectiveWishStore.load(context) } }
    val storageBlocked = initial.isFailure
    var wishes by remember(context) { mutableStateOf(initial.getOrDefault(emptyList())) }
    var error by remember { mutableStateOf(if (storageBlocked)
        "本机志愿数据校验失败。为了保护原记录，已禁止修改；请先检查原数据。" else "") }
    var adding by remember { mutableStateOf(false) }

    var term by remember { mutableStateOf("") }
    var courseCode by remember { mutableStateOf("") }
    var classCode by remember { mutableStateOf("") }
    var courseName by remember { mutableStateOf("") }
    var teacher by remember { mutableStateOf("") }
    var weekday by remember { mutableStateOf("") }
    var startSection by remember { mutableStateOf("") }
    var endSection by remember { mutableStateOf("") }
    var weeks by remember { mutableStateOf("") }

    fun persist(next: List<NcwuElectiveWish>): Boolean {
        if (storageBlocked) return false
        return try {
            NcwuElectiveWishStore.save(context, next)
            wishes = next
            error = ""
            true
        } catch (failure: Exception) {
            error = failure.message ?: "本机保存失败，旧志愿保持不变"
            false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("华水青果 · 选课助手", style = MaterialTheme.typography.titleLarge)
        Text("当前版本仅能保存本人手动输入的选课计划；没有连接真实选课课程列表，不显示实时名额，也不会自动提交或退课。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = onOpenOfficial, modifier = Modifier.fillMaxWidth()) {
            Text("打开华水官网 · 检查选课页面结构")
        }

        if (error.isNotBlank()) {
            Text(error, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("本机志愿（${wishes.size}）", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { adding = !adding }, enabled = !storageBlocked) {
                Text(if (adding) "收起录入" else "手动添加")
            }
        }

        if (adding && !storageBlocked) {
            Surface(shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("仅填写在本人官网确认过的信息", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(value = term, onValueChange = { term = it },
                        label = { Text("学年学期，如 2026-2027-1") },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = courseName, onValueChange = { courseName = it },
                        label = { Text("课程名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = courseCode, onValueChange = { courseCode = it },
                            label = { Text("课程代码") }, singleLine = true,
                            modifier = Modifier.weight(1f))
                        OutlinedTextField(value = classCode, onValueChange = { classCode = it },
                            label = { Text("教学班代码") }, singleLine = true,
                            modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(value = teacher, onValueChange = { teacher = it },
                        label = { Text("教师（可选）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("时间均为可选信息；不填则显示“时间未知”，不能判断冲突。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = weekday, onValueChange = { weekday = it },
                            label = { Text("星期 1–7") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = startSection, onValueChange = { startSection = it },
                            label = { Text("开始节") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = endSection, onValueChange = { endSection = it },
                            label = { Text("结束节") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(value = weeks, onValueChange = { weeks = it },
                        label = { Text("教学周（可选），如 1-16 或 1,3,5") },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(
                        enabled = wishes.size < 60,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            try {
                                fun optionalInt(input: String): Int? {
                                    if (input.isBlank()) return null
                                    return input.trim().toIntOrNull()
                                        ?: throw IllegalArgumentException("星期和节次必须是整数")
                                }
                                val newWish = NcwuElectiveWish(
                                    term = term, courseCode = courseCode, classCode = classCode,
                                    courseName = courseName, teacher = teacher,
                                    weekday = optionalInt(weekday),
                                    startSection = optionalInt(startSection),
                                    endSection = optionalInt(endSection),
                                    weeks = NcwuElectiveWishPlan.parseWeeks(weeks)
                                )
                                val updated = NcwuElectiveWishPlan.add(wishes, newWish)
                                if (persist(updated)) {
                                    courseCode = ""
                                    classCode = ""
                                    courseName = ""
                                    teacher = ""
                                    weekday = ""
                                    startSection = ""
                                    endSection = ""
                                    weeks = ""
                                    adding = false
                                }
                            } catch (failure: Exception) {
                                error = failure.message ?: "志愿字段不完整"
                            }
                        }
                    ) { Text("保存到本机志愿") }
                }
            }
        }

        if (wishes.isEmpty()) {
            Text("还没有选课志愿。先到华水官网查看课程和教学班，再手动添加；此处不会生成模拟课程。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            wishes.forEachIndexed { index, wish ->
                Surface(shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${index + 1}. ${wish.courseName}", style = MaterialTheme.typography.titleMedium)
                        Text("学期 ${wish.term} · ${wish.courseCode} / ${wish.classCode}",
                            style = MaterialTheme.typography.bodySmall)
                        if (wish.teacher.isNotBlank()) Text("教师：${wish.teacher}",
                            style = MaterialTheme.typography.bodySmall)
                        Text(if (wish.hasFullMeeting)
                            "周${wish.weekday} 第${wish.startSection}–${wish.endSection}节 · ${wish.weeks.joinToString(",")}周"
                            else "上课时间未知或周次不完整",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val conflict = NcwuElectiveWishPlan.conflict(wish, wishes)
                        Text(when (conflict) {
                            NcwuWishConflict.OVERLAP -> "提示：与另一课程的手动记录存在周次及节次重叠"
                            NcwuWishConflict.NO_KNOWN_CONFLICT -> "手动记录未发现时间重叠，不代表符合官方选课资格"
                            NcwuWishConflict.INSUFFICIENT_DATA -> "无法完整检查冲突；需以官网课程及当前学期课表核对"
                        }, style = MaterialTheme.typography.bodySmall,
                            color = if (conflict == NcwuWishConflict.OVERLAP)
                                MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        HorizontalDivider()
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(enabled = !storageBlocked && index > 0,
                                onClick = { persist(NcwuElectiveWishPlan.move(wishes,index,index - 1)) }) {
                                Text("上移")
                            }
                            OutlinedButton(enabled = !storageBlocked && index < wishes.lastIndex,
                                onClick = { persist(NcwuElectiveWishPlan.move(wishes,index,index + 1)) }) {
                                Text("下移")
                            }
                            Spacer(Modifier.weight(1f))
                            OutlinedButton(enabled = !storageBlocked,
                                onClick = { persist(NcwuElectiveWishPlan.remove(wishes,wish.stableKey)) }) {
                                Text("移除")
                            }
                        }
                    }
                }
            }
        }

        Text("说明：计划仅保存在此设备，不会改变课表、教务连接或选课结果。不同账号的正式隔离与官方课程读取尚未接入；请勿将手动志愿视为已选课程。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall)
    }
}
