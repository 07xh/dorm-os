package io.github.x07xh.xiaoxu.ui

// SPDX-License-Identifier: GPL-3.0-only
// Source layout: znjhahaha/zhengfang-apk @ bb3b80a6d64e4c2057c0544bb417696ceaa2a9ed, GradesScreen.kt (SemesterGradesContent, ExamScheduleContent).
// Original authors and contributors; only shared term picker and read action remain.

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
internal fun AcademicTermPicker(terms: List<String>, selected: String?, allLabel: String = "全部学期",
    onSelect: (String?) -> Unit) {
    val options = listOf(allLabel) + terms
    val index = (selected?.let { terms.indexOf(it) } ?: -1) + 1
    SystemPicker(
        options = options,
        selectedIndex = index,
        onSelect = { choice -> onSelect(terms.getOrNull(choice - 1)) },
        modifier = Modifier.fillMaxWidth(),
        label = "学期",
        enabled = terms.isNotEmpty(),
        maxLabelLines = 2,
        maxSelectedLabelLines = 1
    )
}

@Composable
internal fun AcademicReadAction(label: String, busy: Boolean, onRead: () -> Unit) {
    TextButton(onClick = onRead, enabled = !busy) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(if (busy) "读取中" else label)
    }
}

