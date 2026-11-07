package io.github.x07xh.xiaoxu

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.x07xh.xiaoxu.data.LocalTaskDatabase
import io.github.x07xh.xiaoxu.data.NativeSchoolRecord
import io.github.x07xh.xiaoxu.data.SecondClassSaveReceipt
import io.github.x07xh.xiaoxu.ui.SectionHeader
import io.github.x07xh.xiaoxu.ui.SourceBadge
import io.github.x07xh.xiaoxu.ui.XiaoxuCard
import io.github.x07xh.xiaoxu.ui.XiaoxuGlassDialog
import io.github.x07xh.xiaoxu.ui.XiaoxuPalette
import io.github.x07xh.xiaoxu.ui.XiaoxuSegmentOption
import io.github.x07xh.xiaoxu.ui.XiaoxuSegmentedControl
import io.github.x07xh.xiaoxu.ui.XiaoxuTone
import java.text.DateFormat
import java.util.Date

/**
 * 第二课堂原生单页：学习概览 → 我的活动 → 学期积分 → 活动广场。
 * 仅使用既有 Room 快照和官方只读入口，不制造广场活动、不推断个人学期归属。
 * 明细弹层复用全 App XiaoxuGlassDialog，避免页面内展开重排。
 */
@Composable
fun SecondClassPage(
    onRefreshResult: ((Boolean) -> Unit)? = null,
    onSavedReceipt: ((SecondClassSaveReceipt) -> Unit)? = null,
    connectOnOpen: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dao = remember(context) { LocalTaskDatabase.open(context).nativeSchoolRecords() }
    val all by dao.observe().collectAsState(initial = emptyList())

    val scores = all.filter { it.provider == "second_class" && it.kind == "credits" }
    val total = scores.singleOrNull { it.title == "学校积分合计" }
    val semesters = scores.filter { semesterIndex(it.title) != null }
        .sortedBy { semesterIndex(it.title) ?: 0 }
    val candidateActivities = all.filter {
        it.provider == "second_class" && it.kind in setOf("activity", "activity_summary")
    }
    val activityBatch = candidateActivities.singleOrNull { it.kind == "activity_summary" }
    // A single matching capture timestamp is the existing "complete personal read" gate.
    val activitiesReady = activityBatch != null &&
        candidateActivities.all { it.capturedAt == activityBatch.capturedAt }
    val activities = if (activitiesReady) candidateActivities.filter { it.kind == "activity" }
        else emptyList()

    val catalogRows = all.filter { it.provider == "second_class" && it.kind == "catalog_empty" }
    val catalogComplete = catalogRows.size == 3 &&
        catalogRows.map { it.title }.toSet() ==
            setOf("报名中", "待验证", "即将开始报名") &&
        catalogRows.map { it.capturedAt }.distinct().size == 1

    var semesterSelection by rememberSaveable { mutableStateOf<String?>(null) }
    var semesterMenuOpen by rememberSaveable { mutableStateOf(false) }
    val selectedSemester = semesters.firstOrNull { it.title == semesterSelection }
        ?: semesters.lastOrNull()
    var catalogFilter by rememberSaveable { mutableIntStateOf(0) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    val reader = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        result ->
        refreshing = false
        val receipt = if (result.resultCode == Activity.RESULT_OK) SecondClassSaveReceipt.validate(
            result.data?.getStringExtra(NativeSchoolConnectActivity.EXTRA_PROVIDER),
            result.data?.getStringExtra(NativeSchoolConnectActivity.EXTRA_RETURN_KIND),
            result.data?.getIntExtra(NativeSchoolConnectActivity.EXTRA_SAVED_COUNT, 0) ?: 0
        ) else null
        val succeeded = receipt != null
        error = if (succeeded) null else "本次未更新，可重新读取"
        onRefreshResult?.invoke(succeeded)
        if (receipt != null) onSavedReceipt?.invoke(receipt)
    }
    fun refreshPersonalRecords() {
        if (refreshing) return
        refreshing = true
        error = null
        try {
            reader.launch(Intent(context, NativeSchoolConnectActivity::class.java)
                .putExtra(NativeSchoolConnectActivity.EXTRA_PROVIDER, "second_class")
                .putExtra(NativeSchoolConnectActivity.EXTRA_AUTO_SCORE, true))
        } catch (_: android.content.ActivityNotFoundException) {
            refreshing = false
            error = "学校读取页面无法打开"
            onRefreshResult?.invoke(false)
        }
    }
    fun openOfficialSite() {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse("https://dekt.ncwu.edu.cn/")))
        }.onFailure { error = "无法打开学校官网" }
    }
    // Only the explicit connection entry opens the school reader automatically.
    LaunchedEffect(connectOnOpen) {
        if (connectOnOpen && !attempted) {
            attempted = true
            refreshPersonalRecords()
        }
    }

    val catalogLabels = remember {
        listOf("报名中", "待验证", "即将开始报名")
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "overview-header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionHeader(title = "学习数据", modifier = Modifier.weight(1f))
                IconButton(onClick = ::refreshPersonalRecords, enabled = !refreshing) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "从学校重新读取第二课堂")
                }
            }
        }
        if (error != null) {
            item(key = "read-error") {
                Text(error!!, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error)
            }
        }
        item(key = "metrics") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OverviewMetric("累计素质分", scoreValue(total) ?: "—", Modifier.weight(1f))
                OverviewMetric("学期素质分", scoreValue(selectedSemester) ?: "—",
                    Modifier.weight(1f))
                OverviewMetric("我的活动", if (activitiesReady) activities.size.toString() else "—",
                    Modifier.weight(1f))
            }
        }

        item(key = "my-activities-heading") {
            SectionHeader(
                title = "我的活动",
                caption = if (activitiesReady) "已读取 ${activities.size} 项" else null
            )
        }
        if (!activitiesReady || activities.isEmpty()) {
            item(key = "my-activities-empty") {
                XiaoxuCard {
                    Text(
                        if (activitiesReady) "暂无报名活动" else "我的活动暂未获取",
                        style = MaterialTheme.typography.bodyMedium,
                        color = XiaoxuPalette.Ink2
                    )
                }
            }
        } else {
            items(activities, key = { "mine:" + it.fingerprint }) { row ->
                MyActivityCard(row)
            }
        }

        item(key = "score-heading") {
            SectionHeader(title = "积分明细")
        }
        item(key = "semester-scores") {
            XiaoxuCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("学期", style = MaterialTheme.typography.bodyMedium,
                        color = XiaoxuPalette.Ink2, modifier = Modifier.weight(1f))
                    Box {
                        androidx.compose.material3.TextButton(
                            onClick = { semesterMenuOpen = true },
                            enabled = semesters.isNotEmpty()
                        ) {
                            Text(selectedSemester?.title?.removeSuffix("积分") ?: "暂未获取",
                                color = XiaoxuPalette.Ink)
                            Icon(Icons.Outlined.ChevronRight,
                                contentDescription = null, tint = XiaoxuPalette.Ink2)
                        }
                        DropdownMenu(
                            expanded = semesterMenuOpen,
                            onDismissRequest = { semesterMenuOpen = false }
                        ) {
                            semesters.forEach { row ->
                                DropdownMenuItem(
                                    text = { Text(row.title.removeSuffix("积分")) },
                                    onClick = {
                                        semesterSelection = row.title
                                        semesterMenuOpen = false
                                    }
                                )
                            }
                        }
                    }
                }
                if (selectedSemester == null) {
                    Text("积分暂未获取", style = MaterialTheme.typography.bodyMedium,
                        color = XiaoxuPalette.Ink2)
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(selectedSemester.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = XiaoxuPalette.Ink)
                            val hours = volunteerHours(selectedSemester)
                            if (hours != null) Text("志愿服务 ${hours} 小时",
                                style = MaterialTheme.typography.bodySmall,
                                color = XiaoxuPalette.Ink2)
                        }
                        Text(scoreValue(selectedSemester) ?: "—",
                            style = MaterialTheme.typography.headlineSmall,
                            color = XiaoxuPalette.BrandDeep, fontWeight = FontWeight.SemiBold)
                    }
                    scoreCategories(selectedSemester).forEach { (label, value) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 34.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(label, style = MaterialTheme.typography.bodySmall,
                                color = XiaoxuPalette.Ink2, modifier = Modifier.weight(1f))
                            Text(value, style = MaterialTheme.typography.bodyMedium,
                                color = XiaoxuPalette.Ink, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }

        item(key = "catalog-heading") {
            SectionHeader(title = "活动广场", actionLabel = "学校官网",
                onAction = ::openOfficialSite)
        }
        item(key = "catalog-filter") {
            XiaoxuSegmentedControl(
                options = remember {
                    listOf(
                        XiaoxuSegmentOption("0", "报名中"),
                        XiaoxuSegmentOption("1", "待验证"),
                        XiaoxuSegmentOption("2", "即将报名")
                    )
                },
                selectedKey = catalogFilter.toString(),
                onSelected = { catalogFilter = it.toInt() }
            )
        }
        item(key = "catalog-result") {
            val record = if (catalogComplete)
                catalogRows.singleOrNull { it.title == catalogLabels[catalogFilter] }
                else null
            XiaoxuCard {
                Text(
                    if (record != null) "上次读取：暂无活动" else "活动暂未获取",
                    style = MaterialTheme.typography.bodyMedium,
                    color = XiaoxuPalette.Ink2
                )
                if (record != null) {
                    Text("更新于 " + DateFormat.getDateTimeInstance()
                        .format(Date(record.capturedAt)),
                        style = MaterialTheme.typography.labelSmall,
                        color = XiaoxuPalette.Ink3)
                }
            }
        }
    }
}

/** Text-only metric display: never turn an unavailable school number into zero. */
@Composable
private fun OverviewMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value,
            style = MaterialTheme.typography.headlineSmall,
            color = XiaoxuPalette.Ink,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis)
        Text(label,
            style = MaterialTheme.typography.labelMedium,
            color = XiaoxuPalette.Ink2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis)
    }
}

/** Display the school's own status and start time; do not infer verification or attendance. */
@Composable
private fun MyActivityCard(row: NativeSchoolRecord) {
    var detailsOpen by rememberSaveable(row.fingerprint) { mutableStateOf(false) }
    val start = row.details.lineSequence().firstOrNull()
        ?.takeIf { it.startsWith("开始时间：") }?.removePrefix("开始时间：")
    val stateLine = row.details.lineSequence().firstOrNull { it.startsWith("报名：") }
    val settlement = stateLine?.substringAfter("结算：", "")
        ?.substringBefore(" · ")?.takeIf { it.isNotBlank() }
    val signup = stateLine?.substringAfter("报名：", "")
        ?.substringBefore(" · ")?.takeIf { it.isNotBlank() }

    XiaoxuCard(onClick = { detailsOpen = true }) {
        Row(modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically) {
            Text(row.title, modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = XiaoxuPalette.Ink,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!settlement.isNullOrBlank() || !signup.isNullOrBlank()) {
                SourceBadge(settlement ?: signup ?: "", XiaoxuTone.Neutral)
            }
            Icon(Icons.Outlined.ChevronRight,
                contentDescription = "打开活动详情", tint = XiaoxuPalette.Ink2)
        }
        if (!start.isNullOrBlank() && start != "学校未提供") {
            Text(start, style = MaterialTheme.typography.bodySmall,
                color = XiaoxuPalette.Ink2)
        }
    }
    if (detailsOpen) {
        XiaoxuGlassDialog(
            title = row.title,
            onDismiss = { detailsOpen = false }
        ) {
            Text(row.details, style = MaterialTheme.typography.bodyMedium,
                color = XiaoxuPalette.Ink)
            Text("本机记录 · 更新于 " + DateFormat.getDateTimeInstance()
                .format(Date(row.capturedAt)), style = MaterialTheme.typography.labelSmall,
                color = XiaoxuPalette.Ink3)
        }
    }
}

private fun semesterIndex(title: String): Int? {
    if (!title.startsWith("第") || !title.endsWith("学期积分")) return null
    return title.removePrefix("第").removeSuffix("学期积分").toIntOrNull()
}

/** Parse only the exact, sanitized label emitted by LocalSecondClassScores. */
private fun scoreValue(row: NativeSchoolRecord?): String? {
    val raw = row?.details?.lineSequence()?.firstOrNull()
        ?.substringAfter("素质分：", "")?.substringBefore(" · ")?.trim()
    return raw?.takeIf { it.matches(Regex("[0-9]+(?:\\.[0-9]+)?")) }
}

private fun volunteerHours(row: NativeSchoolRecord?): String? {
    val raw = row?.details?.lineSequence()?.firstOrNull()
        ?.substringAfter("志愿服务时长：", "")?.trim()
    return raw?.takeIf { it.matches(Regex("[0-9]+(?:\\.[0-9]+)?")) }
}

private fun scoreCategories(row: NativeSchoolRecord?): List<Pair<String, String>> =
    row?.details?.substringAfter("\n", "")?.split(" · ")?.mapNotNull { item ->
        val pair = item.split("：", limit = 2)
        if (pair.size == 2 && pair[1].matches(Regex("[0-9]+(?:\\.[0-9]+)?")))
            pair[0] to pair[1] else null
    } ?: emptyList()
