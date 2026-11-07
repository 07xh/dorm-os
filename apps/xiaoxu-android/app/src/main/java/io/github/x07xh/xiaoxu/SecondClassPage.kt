package io.github.x07xh.xiaoxu

import android.app.Activity
import android.content.Intent
import io.github.x07xh.xiaoxu.data.SecondClassSaveReceipt
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.x07xh.xiaoxu.data.LocalTaskDatabase
import io.github.x07xh.xiaoxu.ui.XiaoxuCard
import io.github.x07xh.xiaoxu.ui.XiaoxuExpandableGlassRow
import io.github.x07xh.xiaoxu.ui.XiaoxuPalette
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
    val catalogRows = all.filter { it.provider == "second_class" && it.kind == "catalog_empty" }
    val catalogComplete = catalogRows.size == 3 &&
        catalogRows.map { it.title }.toSet() ==
            setOf("报名中", "待验证", "即将开始报名") &&
        catalogRows.map { it.capturedAt }.distinct().size == 1

    var section by rememberSaveable { mutableIntStateOf(if (connectOnOpen) 2 else 0) }
    var catalogFilter by rememberSaveable { mutableIntStateOf(0) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    // Configuration changes must not open a second official reader.

    var refreshing by remember { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    // Each section owns its scroll position; switching never jumps to a stale offset.
    val catalogScroll = rememberScrollState()
    val activitiesScroll = rememberScrollState()
    val scoresScroll = rememberScrollState()
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

    Column(modifier.fillMaxSize()) {
        // Shared soft-glass segmented control; no nested website-style tabs.
        val sectionOptions = remember {
            listOf(
                XiaoxuSegmentOption("0", "活动"),
                XiaoxuSegmentOption("1", "我的活动"),
                XiaoxuSegmentOption("2", "积分")
            )
        }
        XiaoxuSegmentedControl(
            options = sectionOptions,
            selectedKey = section.toString(),
            onSelected = { section = it.toInt() },
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
        )
        val currentScroll = when (section) {
            0 -> catalogScroll
            1 -> activitiesScroll
            else -> scoresScroll
        }
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(currentScroll)
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (section == 0) {
                // Equal-width source filters; their display names do not change
                // the three official result keys used by the validated parser.
                val filters = listOf(
                    "报名中" to "报名中",
                    "待验证" to "待验证",
                    "即将报名" to "即将开始报名"
                )
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
                val record = if (catalogComplete)
                    catalogRows.singleOrNull { it.title == filters[catalogFilter].second }
                    else null
                XiaoxuCard {
                    Text(
                        if (record != null) "上次读取：暂无活动" else "活动暂未获取",
                        style = MaterialTheme.typography.titleMedium,
                        color = XiaoxuPalette.Ink
                    )
                    if (record != null) {
                        Text(
                            "更新于 " + DateFormat.getDateTimeInstance()
                                .format(Date(record.capturedAt)),
                            style = MaterialTheme.typography.labelSmall,
                            color = XiaoxuPalette.Ink2
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = ::refreshPersonalRecords,
                            enabled = !refreshing,
                            modifier = Modifier.weight(1f)
                        ) { Text(if (refreshing) "读取中…" else "读取学校活动") }
                        TextButton(onClick = ::openOfficialSite) { Text("学校官网") }
                    }
                }
            } else {
                val rows = if (section == 2) {
                    scores.sortedBy { if (it.title == "学校积分合计") 0 else 1 }
                } else {
                    activities.sortedBy { if (it.kind == "activity_summary") 0 else 1 }
                }
                if (rows.isEmpty()) {
                    XiaoxuCard {
                        Text(
                            if (section == 2) "积分暂未获取" else "我的活动暂未获取",
                            style = MaterialTheme.typography.titleMedium,
                            color = XiaoxuPalette.Ink
                        )
                        Button(onClick = ::refreshPersonalRecords,
                            enabled = !refreshing,
                            modifier = Modifier.fillMaxWidth()) {
                            Text(if (refreshing) "读取中…" else "读取本人数据")
                        }
                    }
                } else {
                    TextButton(onClick = ::refreshPersonalRecords, enabled = !refreshing) {
                        Text(if (refreshing) "读取中…" else "刷新")
                    }
                    rows.forEach { row ->
                        // Only real, stored school details expand. Empty details remain static.
                        androidx.compose.runtime.key(row.provider, row.kind, row.fingerprint) {
                            if (row.details.isNotBlank()) {
                                var expanded by rememberSaveable { mutableStateOf(false) }
                                XiaoxuExpandableGlassRow(
                                    title = row.title,
                                    expanded = expanded,
                                    onToggle = { expanded = !expanded }
                                ) {
                                    Text(
                                        row.details,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = XiaoxuPalette.Ink2
                                    )
                                }
                            } else {
                                XiaoxuCard {
                                    Text(row.title, style = MaterialTheme.typography.titleSmall,
                                        color = XiaoxuPalette.Ink)
                                }
                            }
                        }
                    }
                }
            }
            if (error != null) {
                Text(error!!, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
