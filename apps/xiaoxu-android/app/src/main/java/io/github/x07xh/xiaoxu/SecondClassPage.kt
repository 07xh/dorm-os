package io.github.x07xh.xiaoxu

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.x07xh.xiaoxu.data.*
import io.github.x07xh.xiaoxu.ui.rememberLocalRows

/** New read-only local snapshot surface. Official authorization remains in the original Activity. */
@Composable
fun SecondClassPage(onRefreshResult: ((Boolean) -> Unit)? = null,
    onSavedReceipt: ((SecondClassSaveReceipt) -> Unit)? = null,
    connectOnOpen: Boolean = false, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dao = remember(context) { LocalTaskDatabase.open(context).nativeSchoolRecords() }
    val all by rememberLocalRows(dao) { dao.observe() }
    val rows = all.filter { it.provider == "second_class" && it.kind == "credits" }
    var attempted by rememberSaveable { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val reader = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        refreshing = false
        val receipt = if(result.resultCode == Activity.RESULT_OK) SecondClassSaveReceipt.validate(
            result.data?.getStringExtra(NativeSchoolConnectActivity.EXTRA_PROVIDER),
            result.data?.getStringExtra(NativeSchoolConnectActivity.EXTRA_RETURN_KIND),
            result.data?.getIntExtra(NativeSchoolConnectActivity.EXTRA_SAVED_COUNT, 0) ?: 0
        ) else null
        error = if(receipt == null) "本次未更新" else null
        onRefreshResult?.invoke(receipt != null)
        receipt?.let { onSavedReceipt?.invoke(it) }
    }
    fun refresh() {
        if(refreshing) return
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
    Column(modifier.fillMaxWidth()) {
        TextButton(onClick = { refresh() }, enabled = !refreshing) {
            Text(if(refreshing) "读取中" else "手动读取积分")
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if(rows.isEmpty()) Text("尚未读取本机积分")
        LazyColumn(Modifier.weight(1f)) {
            items(rows) { record ->
                ListItem(headlineContent = { Text(record.title) },
                    supportingContent = { Text(record.details) })
                HorizontalDivider()
            }
        }
    }
}
