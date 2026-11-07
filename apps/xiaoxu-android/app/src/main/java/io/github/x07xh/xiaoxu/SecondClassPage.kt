package io.github.x07xh.xiaoxu

import android.app.Activity
import android.content.Intent
import io.github.x07xh.xiaoxu.data.SecondClassSaveReceipt
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.x07xh.xiaoxu.data.LocalTaskDatabase
import io.github.x07xh.xiaoxu.ui.XiaoxuCard
import io.github.x07xh.xiaoxu.ui.XiaoxuGlassDialog
import io.github.x07xh.xiaoxu.ui.XiaoxuPalette
import io.github.x07xh.xiaoxu.ui.XiaoxuSpacing
import io.github.x07xh.xiaoxu.ui.XiaoxuSegmentOption
import io.github.x07xh.xiaoxu.ui.XiaoxuSegmentedControl
import java.text.DateFormat
import java.util.Date

/**
 * One native Compose section inside AcademicPage. School login opens only
 * on demand; its WebView is NOT a content view embedded in this page.
 * We never turn "not fetched" into "no activities".
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
    val candidates = all.filter {
        it.provider == "second_class" && it.kind in setOf("activity", "activity_summary")
    }
    val batch = candidates.singleOrNull { it.kind == "activity_summary" }
    val activities = if (batch != null &&
        candidates.all { it.capturedAt == batch.capturedAt }) candidates else emptyList()
    val catalogRows = all.filter {
        it.provider == "second_class" && it.kind == "catalog_empty"
    }
    val catalogActivities = all.filter {
        it.provider == "second_class" && it.kind == "catalog_activity"
    }

    var catalogFilter by rememberSaveable { mutableIntStateOf(0) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    // Configuration changes must not open a second official reader.

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

    // Only the explicit connection entry opens authorization automatically.
    // Browsing native sections alone never launches the school reader.
    LaunchedEffect(connectOnOpen) {
        if (connectOnOpen && !attempted) {
            attempted = true
            refreshPersonalRecords()
        }

    }

// The official parser is the only source of truth: never infer today's term,
    // approval status, score totals or catalog entries from a missing snapshot.
    val termRows = scores.filter {
        Regex("^第[1-9][0-9]*学期积分$").matches(it.title)
    }.sortedByDescending {
        it.title.removePrefix("第").removeSuffix("学期积分").toIntOrNull() ?: 0
    }
    var selectedTermTitle by rememberSaveable { mutableStateOf<String?>(null) }
    var termMenuOpen by rememberSaveable { mutableStateOf(false) }
    val selectedTerm = termRows.firstOrNull { it.title == selectedTermTitle }
        ?: termRows.firstOrNull()
    val schoolTotal = scores.firstOrNull { it.title == "学校积分合计" }
    val myActivities = activities.filter { it.kind == "activity" }
    val countConfirmed = batch != null &&
        candidates.all { it.capturedAt == batch.capturedAt }
    var detailKey by rememberSaveable { mutableStateOf<String?>(null) }
    val detailRecord = (scores + myActivities + catalogActivities).firstOrNull {
        it.kind + ":" + it.fingerprint == detailKey
    }
    val catalogOptions = remember {
        listOf(
            XiaoxuSegmentOption("0", "报名中"),
            XiaoxuSegmentOption("1", "待验证"),
            XiaoxuSegmentOption("2", "即将报名")
        )
    }
    val catalogKeys = listOf("报名中", "待验证", "即将开始报名")
    val activeCatalogFilter = catalogKeys[catalogFilter.coerceIn(0, 2)]
    val catalogRecord = catalogRows.singleOrNull {
        it.title == activeCatalogFilter
    }
    val visibleCatalogActivities = catalogActivities.filter {
        it.details.startsWith("筛选：$activeCatalogFilter\n")
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "points_header") {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("积分概览", modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = XiaoxuPalette.Ink)
                // 学期筛选紧贴概览标题；只选择已有的真实学期快照。
                Box {
                    val pillShape = RoundedCornerShape(999.dp)
                    Row(
                        modifier = Modifier
                            .clip(pillShape)
                            .background(XiaoxuPalette.GlassCard)
                            .border(XiaoxuSpacing.glassStroke,
                                XiaoxuPalette.GlassBorder, pillShape)
                            .clickable(enabled = termRows.isNotEmpty()) {
                                termMenuOpen = true
                            }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            selectedTerm?.title?.removeSuffix("积分") ?: "未获取",
                            style = MaterialTheme.typography.labelLarge,
                            color = XiaoxuPalette.Ink,
                            maxLines = 1
                        )
                        Icon(
                            Icons.Outlined.KeyboardArrowDown,
                            contentDescription = "选择学期",
                            tint = XiaoxuPalette.Ink2
                        )
                    }
                    DropdownMenu(
                        expanded = termMenuOpen && termRows.isNotEmpty(),
                        onDismissRequest = { termMenuOpen = false }
                    ) {
                        termRows.forEach { term ->
                            DropdownMenuItem(
                                text = { Text(term.title.removeSuffix("积分")) },
                                onClick = {
                                    selectedTermTitle = term.title
                                    termMenuOpen = false
                                }
                            )
                        }
                    }
                }
                IconButton(onClick = ::refreshPersonalRecords, enabled = !refreshing) {
                    Icon(Icons.Outlined.Refresh,
                        contentDescription = "从学校重新读取第二课堂")
                }
            }
            if (refreshing) {
                Text("读取中…", style = MaterialTheme.typography.labelSmall,
                    color = XiaoxuPalette.Ink2)
            }
        }
        item(key = "metrics") {
            XiaoxuCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SecondClassMetric(
                        "学校累计",
                        secondClassPoints(schoolTotal) ?: "—",
                        Modifier.weight(1f)
                    )
                    SecondClassMetric(
                        "所选学期",
                        secondClassPoints(selectedTerm) ?: "—",
                        Modifier.weight(1f)
                    )
                    SecondClassMetric(
                        "我的活动",
                        if (countConfirmed) myActivities.size.toString() else "—",
                        Modifier.weight(1f)
                    )
                }
            }
        }
        item(key = "term_breakdown") {
            // 默认折叠为单行，明细只在居中玻璃弹窗内展示；
            // 不插入六条内容把页面和底栏向下推。
            XiaoxuCard(
                onClick = if (selectedTerm != null) ({
                    detailKey = selectedTerm.kind + ":" + selectedTerm.fingerprint
                }) else null,
                contentPadding = 16.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("积分明细", modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        color = XiaoxuPalette.Ink, fontWeight = FontWeight.SemiBold)
                    if (selectedTerm == null) {
                        Text("暂未获取", style = MaterialTheme.typography.labelSmall,
                            color = XiaoxuPalette.Ink2)
                    } else {
                        Icon(Icons.Outlined.ChevronRight,
                            contentDescription = "查看六类积分明细",
                            tint = XiaoxuPalette.Ink2)
                    }
                }
            }
        }
        item(key = "activities_header") {
            Row(modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically) {
                Text("我的活动", modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    color = XiaoxuPalette.Ink, fontWeight = FontWeight.SemiBold)
                if (countConfirmed) {
                    Text(myActivities.size.toString() + " 条",
                        style = MaterialTheme.typography.labelMedium,
                        color = XiaoxuPalette.Ink2)
                }
            }
        }
        if (!countConfirmed || myActivities.isEmpty()) {
            item(key = "activities_empty") {
                XiaoxuCard {
                    Text(
                        if (countConfirmed) "上次读取：暂无报名活动" else "我的活动暂未获取",
                        style = MaterialTheme.typography.bodyMedium,
                        color = XiaoxuPalette.Ink2
                    )
                }
            }
        } else {
            items(
                items = myActivities,
                key = { "secondclass-activity-" + it.fingerprint }
            ) { record ->
                val lines = record.details.lines()
                val time = lines.firstOrNull { it.startsWith("开始时间：") }
                val status = lines.firstOrNull { it.startsWith("报名：") }
                XiaoxuCard(
                    onClick = {
                        detailKey = record.kind + ":" + record.fingerprint
                    },
                    contentPadding = 16.dp
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(record.title, modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            color = XiaoxuPalette.Ink, maxLines = 2,
                            overflow = TextOverflow.Ellipsis)
                        Icon(Icons.Outlined.ChevronRight,
                            contentDescription = "查看活动详情",
                            tint = XiaoxuPalette.Ink2)
                    }
                    if (time != null) {
                        Text(time, style = MaterialTheme.typography.bodySmall,
                            color = XiaoxuPalette.Ink2)
                    }
                    if (status != null) {
                        Text(status, style = MaterialTheme.typography.bodySmall,
                            color = XiaoxuPalette.BrandDeep)
                    }
                }
            }
        }
        item(key = "catalog_header") {
            Text("活动广场", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold, color = XiaoxuPalette.Ink)
        }
        item(key = "catalog_filter") {
            XiaoxuSegmentedControl(
                options = catalogOptions,
                selectedKey = catalogFilter.toString(),
                onSelected = { catalogFilter = it.toInt() }
            )
        }
        if (visibleCatalogActivities.isNotEmpty()) {
            items(
                items = visibleCatalogActivities,
                key = { "secondclass-catalog-" + it.fingerprint }
            ) { record ->
                XiaoxuCard(
                    onClick = {
                        detailKey = record.kind + ":" + record.fingerprint
                    },
                    contentPadding = 16.dp
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(record.title, modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            color = XiaoxuPalette.Ink,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis)
                        Icon(Icons.Outlined.ChevronRight,
                            contentDescription = "查看学校活动",
                            tint = XiaoxuPalette.Ink2)
                    }
                    Text("上次从学校活动广场读取 · $activeCatalogFilter",
                        style = MaterialTheme.typography.labelSmall,
                        color = XiaoxuPalette.Ink2)
                }
            }
        } else {
            item(key = "catalog_status") {
                XiaoxuCard {
                    Text(
                        if (catalogRecord != null) "上次读取：暂无活动" else "活动暂未获取",
                        style = MaterialTheme.typography.bodyMedium,
                        color = XiaoxuPalette.Ink
                    )
                    if (catalogRecord != null) {
                        Text("更新于 " + DateFormat.getDateTimeInstance()
                            .format(Date(catalogRecord.capturedAt)),
                            style = MaterialTheme.typography.labelSmall,
                            color = XiaoxuPalette.Ink2)
                    }
                    TextButton(onClick = ::openOfficialSite) { Text("学校官网") }
                }
            }
        }
        if (error != null) {
            item(key = "reader_error") {
                Text(error ?: "", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (detailRecord != null) {
        XiaoxuGlassDialog(
            title = if (detailRecord.kind == "credits") "积分明细 · " +
                detailRecord.title.removeSuffix("积分") else detailRecord.title,
            onDismiss = { detailKey = null }
        ) {
            if (detailRecord.kind == "credits") {
                Text("素质分：" + (secondClassPoints(detailRecord) ?: "—"),
                    style = MaterialTheme.typography.titleMedium,
                    color = XiaoxuPalette.Ink)
                secondClassCategories(detailRecord).forEach { (name, points) ->
                    Row(modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(name, modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = XiaoxuPalette.Ink2)
                        Text(points, style = MaterialTheme.typography.bodyMedium,
                            color = XiaoxuPalette.Ink)
                    }
                }
                detailRecord.details.substringAfter("志愿服务时长：", "")
                    .substringBefore("\n").takeIf { it.isNotBlank() }?.let { hours ->
                        Text("志愿服务时长：$hours",
                            style = MaterialTheme.typography.bodySmall,
                            color = XiaoxuPalette.Ink2)
                    }
            } else {
                Text(detailRecord.details, style = MaterialTheme.typography.bodyMedium,
                    color = XiaoxuPalette.Ink2)
            }
            Text("本机读取时间：" + DateFormat.getDateTimeInstance()
                .format(Date(detailRecord.capturedAt)),
                style = MaterialTheme.typography.labelSmall,
                color = XiaoxuPalette.Ink3)
        }
    }
}

private fun secondClassPoints(record: io.github.x07xh.xiaoxu.data.NativeSchoolRecord?): String? {
    val raw = record?.details?.substringAfter("素质分：", "")
        ?.substringBefore(" · ")?.trim() ?: return null
    return raw.takeIf { it.matches(Regex("[0-9]+(?:\\.[0-9]+)?")) }
}

private fun secondClassCategories(
    record: io.github.x07xh.xiaoxu.data.NativeSchoolRecord
): List<Pair<String, String>> {
    return record.details.substringAfter("\n", "").split(" · ").mapNotNull { item ->
        val index = item.indexOf('：')
        if (index < 1 || index >= item.lastIndex) null
        else item.substring(0, index) to item.substring(index + 1)
    }
}

@Composable
private fun SecondClassMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(value, style = MaterialTheme.typography.headlineSmall,
            color = XiaoxuPalette.Ink, fontWeight = FontWeight.SemiBold,
            maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = XiaoxuPalette.Ink2, maxLines = 1)
    }
}
