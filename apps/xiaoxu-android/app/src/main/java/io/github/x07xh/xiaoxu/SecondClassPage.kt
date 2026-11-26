package io.github.x07xh.xiaoxu

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.x07xh.xiaoxu.data.*
import io.github.x07xh.xiaoxu.ui.*
import androidx.compose.ui.Alignment
import java.text.DateFormat
import java.util.Date

/** New read-only local snapshot surface. Official authorization remains in the original Activity. */
@Composable
fun SecondClassPage(onRefreshResult: ((Boolean) -> Unit)? = null,
    onSavedReceipt: ((SecondClassSaveReceipt) -> Unit)? = null,
    connectOnOpen: Boolean = false, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dao = remember(context) { LocalTaskDatabase.open(context).nativeSchoolRecords() }
    val all by rememberSnapshotRows(dao) { dao.observe() }
    SecondClassContent(pageSnapshot(all), onRefreshResult, onSavedReceipt, connectOnOpen, modifier)
}

/** Main shell supplies the existing shared Room snapshot. Standalone Activity uses
 * the same content and read-state contract through the compatibility wrapper. */
@Composable
internal fun SecondClassContent(snapshot: PageSnapshot<List<NativeSchoolRecord>>,
    onRefreshResult: ((Boolean) -> Unit)? = null,
    onSavedReceipt: ((SecondClassSaveReceipt) -> Unit)? = null,
    connectOnOpen: Boolean = false, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var attempted by rememberSaveable { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val reader = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        refreshing = false
        val receipt = if (result.resultCode == Activity.RESULT_OK) SecondClassSaveReceipt.validate(
            result.data?.getStringExtra(NativeSchoolConnectActivity.EXTRA_PROVIDER),
            result.data?.getStringExtra(NativeSchoolConnectActivity.EXTRA_RETURN_KIND),
            result.data?.getIntExtra(NativeSchoolConnectActivity.EXTRA_SAVED_COUNT, 0) ?: 0
        ) else null
        val succeeded = receipt != null
        error = if (succeeded) null else "本次未更新"
        onRefreshResult?.invoke(succeeded)
        receipt?.let { onSavedReceipt?.invoke(it) }
    }
    fun refresh() {
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
    LaunchedEffect(connectOnOpen) { if(connectOnOpen && !attempted) { attempted = true; refresh() } }
    val appContext = context.applicationContext
    val autoPrefs = remember(appContext) { appContext.getSharedPreferences(SecondClassAutoRefresh.SETTINGS, 0) }
    var autoRevision by remember { mutableIntStateOf(0) }
    DisposableEffect(autoPrefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> autoRevision++ }
        autoPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { autoPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val autoStatus = remember(autoRevision) { SecondClassAutoRefresh.status(appContext) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppSpacing.item)) {
        if (snapshot !is PageSnapshot.Ready) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            AcademicReadAction("手动读取", refreshing) { refresh() }
        }
        error?.let { AppNotice(it, error = true) }
        AppSnapshotContent(snapshot) { all ->
            val records = remember(all) { all.filter { it.provider == "second_class" && it.sourceHost == "dekt.ncwu.edu.cn" } }
            val credits = remember(records) { records.filter { it.kind == "credits" } }
            val capture = credits.map { it.capturedAt }.distinct().singleOrNull()
            val binding = records.singleOrNull { it.kind == "account_binding" && it.fingerprint == "current" &&
                it.details.matches(Regex("[a-f0-9]{64}")) }
            val confirmed = capture != null && capture > 0 && binding?.capturedAt == capture
            if (!confirmed) {
                SystemEmptyState("尚无已确认积分快照", "需要完成一次本人身份核验和积分读取。")
                AcademicReadAction("手动读取", refreshing) { refresh() }
                return@AppSnapshotContent
            }
            val stamp = capture!!
            val terms = credits.filter { Regex("^第[1-9][0-9]*学期积分$").matches(it.title) }
                .sortedByDescending { it.title.removePrefix("第").removeSuffix("学期积分").toIntOrNull() ?: 0 }
            var requestedTerm by rememberSaveable(binding!!.details) { mutableStateOf<String?>(null) }
            val selected = requestedTerm?.takeIf { requested -> terms.any { it.title == requested } }
            val total = credits.singleOrNull { it.title == "学校积分合计" }
            val chosen = if (selected == null) total else terms.firstOrNull { it.title == selected }
            val personal = remember(records, stamp) { records.filter { it.kind in setOf("activity", "activity_summary") && it.capturedAt == stamp } }
            val summary = personal.singleOrNull { it.kind == "activity_summary" }
            val activities = remember(personal) { personal.filter { it.kind == "activity" } }
            val catalog = remember(records) { records.filter { it.kind in setOf("catalog_empty", "catalog_activity") } }
            val catalogTime = catalog.map { it.capturedAt }.distinct().singleOrNull()
            val catalogKeys = listOf("报名中", "待验证", "即将开始报名")
            var catalogFilter by rememberSaveable(binding!!.details) { mutableIntStateOf(0) }
            val activeCatalogFilter = catalogKeys[catalogFilter.coerceIn(0, catalogKeys.lastIndex)]
            val catalogRecord = catalog.singleOrNull { it.kind == "catalog_empty" && it.title == activeCatalogFilter }
            val visibleCatalogActivities = catalog.filter {
                it.kind == "catalog_activity" && it.details.startsWith("筛选：$activeCatalogFilter\n")
            }
            var detailKey by rememberSaveable(binding!!.details) { mutableStateOf<String?>(null) }
            val detail = (credits + activities + catalog).firstOrNull { it.kind + ":" + it.fingerprint == detailKey }
            val stampLabel = remember(stamp) { android.text.format.DateFormat.format("MM-dd HH:mm", stamp).toString() }
            val reading = refreshing || autoStatus == "reading"
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp)) {
                item(key = "credits-overview") {
                    InsetGroupedSection(header = "积分概览", animateSize = false) {
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                                Text("更新于 $stampLabel", style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (autoStatus == "reading") Text("正在同步 · 保留当前数据", style = MaterialTheme.typography.labelSmall)
                                else if (autoStatus == "failed") Text("上次同步失败", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error)
                                else if (autoStatus in setOf("needs_reconnect", "needs_confirmation"))
                                    Text("请重新核验学校账号", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            }
                            AcademicReadAction("手动读取", reading) { refresh() }
                        }
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(secondClassPoints(chosen) ?: "—", style = MaterialTheme.typography.headlineLarge,
                                    color = MaterialTheme.colorScheme.primary)
                                Text(if (selected == null) "累计素质分" else "本学期素质分",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            AcademicTermPicker(terms.map { it.title }, selected, allLabel = "全部学期",
                                onSelect = { requestedTerm = it })
                        }
                        if (selected != null) InsetGroupedRow(title = "学校累计素质分",
                            trailing = { Text(secondClassPoints(total) ?: "—") })
                        InsetGroupedRow(title = "积分明细",
                            subtitle = chosen?.title ?: "暂未获取",
                            showDivider = false, enabled = chosen != null,
                            trailing = { Icon(androidx.compose.material.icons.Icons.Outlined.ChevronRight, contentDescription = null) },
                            onClick = { if (chosen != null) detailKey = chosen.kind + ":" + chosen.fingerprint })
                    }
                }
                item(key = "activity-header") {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { SystemSectionHeader("我的活动") }
                        Text(if (summary == null) "未读取" else "${activities.size} 项",
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (summary == null) item(key = "activity-unknown") { AppNotice("尚无已确认活动快照") }
                else {
                    if (activities.isEmpty()) item(key = "activity-summary") { AppNotice("已确认暂无报名活动") }
                    items(activities, key = { "activity:${it.fingerprint}" }) { row ->
                        val points = secondClassActivityPoints(row)
                        val time = row.details.lineSequence().firstOrNull { it.startsWith("开始时间：") }
                            ?.removePrefix("开始时间：")?.takeUnless { it == "学校未提供" }?.take(16)
                        val settlement = row.details.lineSequence()
                            .firstOrNull { it.startsWith("报名：") }
                            ?.substringAfter("结算：", "")?.takeIf { it.isNotBlank() }
                        InsetGroupedSection(animateSize = false) {
                            InsetGroupedRow(title = row.title,
                                subtitle = listOfNotNull(time, settlement).joinToString(" · ").ifBlank { "学校未提供活动状态" },
                                trailing = {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(points ?: "—", style = MaterialTheme.typography.titleMedium,
                                            color = if (points != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(if (points != null) "已结算分" else "分数未获取",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                showDivider = false,
                                onClick = { detailKey = row.kind + ":" + row.fingerprint })
                        }
                    }
                }
                item(key = "catalog-header") {
                    SystemSectionHeader("活动广场")
                    if (catalogTime != null && catalogTime != stamp) AppNotice("目录为上次保存的快照")
                }
                item(key = "catalog-filter") {
                    LiquidSegmentedControl(listOf("报名中", "待验证", "即将报名"), catalogFilter,
                        onSelect = { catalogFilter = it })
                }
                if (catalogTime == null || visibleCatalogActivities.isEmpty()) item(key = "catalog-status") {
                    InsetGroupedSection(animateSize = false) {
                    InsetGroupedRow(title = if (catalogTime != null && catalogRecord != null) "上次读取：暂无活动" else "活动暂未获取",
                        subtitle = "可手动读取或前往学校官网核对", showDivider = false)
                    TextButton(onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://dekt.ncwu.edu.cn/"))) }
                            .onFailure { error = "无法打开学校官网" }
                    }, modifier = Modifier.padding(horizontal = 8.dp)) { Text("学校官网") }
                    }
                } else items(visibleCatalogActivities, key = { "${it.kind}:${it.fingerprint}" }) { row ->
                    InsetGroupedSection {
                        InsetGroupedRow(title = row.title, subtitle = row.details, showDivider = false,
                            onClick = { detailKey = row.kind + ":" + row.fingerprint })
                    }
                }
            }
            detail?.let { row ->
                AppDialog(onDismissRequest = { detailKey = null },
                    title = { Text(row.title) },
                    text = { Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                        if (row.kind == "credits") {
                            Text("素质分：" + (secondClassPoints(row) ?: "—"), style = MaterialTheme.typography.titleMedium)
                            secondClassCategories(row).forEach { (name, points) ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(name, Modifier.weight(1f)); Text(points)
                                }
                            }
                            row.details.substringAfter("志愿服务时长：", "").substringBefore("\n").takeIf { it.isNotBlank() }?.let { Text("志愿服务时长：$it") }
                        } else Text(row.details)
                        AppNotice("快照时间：" + DateFormat.getDateTimeInstance().format(Date(row.capturedAt)))
                    } },
                    confirmButton = { TextButton(onClick = { detailKey = null }) { Text("关闭") } })
            }
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



/** The parser records a score only when the school's activity is settled.
 * Catalog rewards, signup states, and aggregate scores cannot substitute for earned points. */
internal fun secondClassActivityPoints(record: NativeSchoolRecord): String? {
    if (record.provider != "second_class" || record.kind != "activity" ||
        record.sourceHost != "dekt.ncwu.edu.cn") return null
    val settlement = record.details.lineSequence().singleOrNull { it.startsWith("报名：") }
        ?.substringAfter("结算：", "") ?: return null
    if (settlement != "已结算") return null
    val raw = record.details.lineSequence().singleOrNull { it.startsWith("已结算素质分：") }
        ?.removePrefix("已结算素质分：")?.trim() ?: return null
    if (!raw.matches(Regex("[0-9]+(?:\\.[0-9]+)?"))) return null
    return raw.takeIf { it.toBigDecimalOrNull()?.let { value ->
        value.signum() >= 0 && value <= java.math.BigDecimal("100000")
    } == true }
}
