package io.github.x07xh.xiaoxu

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.x07xh.xiaoxu.data.LocalTaskDatabase
import io.github.x07xh.xiaoxu.ui.XiaoxuCard
import io.github.x07xh.xiaoxu.ui.XiaoxuPalette
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

    var section by rememberSaveable { mutableIntStateOf(0) }
    var catalogFilter by rememberSaveable { mutableIntStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    // Each section owns its scroll position; switching never jumps to a stale offset.
    val catalogScroll = rememberScrollState()
    val activityScroll = rememberScrollState()
    val scoreScroll = rememberScrollState()
    val reader = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        result ->
        refreshing = false
        val saved = result.resultCode == Activity.RESULT_OK
        error = if (saved) null else "本次未更新，可重新读取"
        onRefreshResult?.invoke(saved)
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

    Column(modifier.fillMaxSize()) {
        // Not a nested website-style TabRow: identical surface, inset and radius
        // to the surrounding native AcademicPage.
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(XiaoxuPalette.SurfaceAlt)
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            listOf("活动", "我的活动", "积分").forEachIndexed { index, label ->
                Box(
                    modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (section == index) XiaoxuPalette.Surface else Color.Transparent)
                        .clickable(onClick = { section = index }),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, style = MaterialTheme.typography.labelLarge,
                        color = if (section == index) XiaoxuPalette.BrandDeep else XiaoxuPalette.Ink2,
                        maxLines = 1)
                }
            }
        }
        val scroll = when (section) {
            0 -> catalogScroll
            1 -> activityScroll
            else -> scoreScroll
        }
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scroll)
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    filters.forEachIndexed { index, (label, _) ->
                        Box(
                            modifier = Modifier.weight(1f).heightIn(min = 42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (catalogFilter == index)
                                    XiaoxuPalette.BrandSoft else XiaoxuPalette.SurfaceAlt)
                                .clickable(onClick = { catalogFilter = index }),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, style = MaterialTheme.typography.labelMedium,
                                color = if (catalogFilter == index)
                                    XiaoxuPalette.BrandDeep else XiaoxuPalette.Ink2,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
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
                        XiaoxuCard {
                            Text(row.title, style = MaterialTheme.typography.titleSmall,
                                color = XiaoxuPalette.Ink)
                            if (row.details.isNotBlank()) {
                                Text(row.details, style = MaterialTheme.typography.bodyMedium,
                                    color = XiaoxuPalette.Ink2)
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
