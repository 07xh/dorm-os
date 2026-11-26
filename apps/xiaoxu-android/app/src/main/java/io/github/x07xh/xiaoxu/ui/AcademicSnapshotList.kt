package io.github.x07xh.xiaoxu.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun AcademicTermPicker(
    terms: List<String>,
    selected: String?,
    allLabel: String = "全部学期",
    onSelect: (String?) -> Unit
) {
    SystemPicker(
        options = listOf(allLabel) + terms,
        selectedIndex = (selected?.let(terms::indexOf) ?: -1) + 1,
        onSelect = { index -> onSelect(terms.getOrNull(index - 1)) },
        modifier = Modifier.widthIn(max = 156.dp),
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
