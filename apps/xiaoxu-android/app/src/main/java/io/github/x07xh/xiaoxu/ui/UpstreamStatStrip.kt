package io.github.x07xh.xiaoxu.ui

// SPDX-License-Identifier: GPL-3.0-only
// Source: znjhahaha/zhengfang-apk @ bb3b80a6d64e4c2057c0544bb417696ceaa2a9ed, ui/system/SystemUi.kt
// Original contributors. SystemStatStrip layout retained, theme binding adapted.
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SystemStatStrip(
    modifier: Modifier = Modifier,
    items: List<Pair<String, String>>
) {
    val textAppearance = MaterialTheme.colorScheme

    Row(
        modifier = modifier.fillMaxWidth().padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items.forEach { (label, value) ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(value, style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold, color = textAppearance.onSurface)
                Text(label, style = MaterialTheme.typography.bodySmall,
                    color = textAppearance.onSurfaceVariant)
            }
        }
    }
}
