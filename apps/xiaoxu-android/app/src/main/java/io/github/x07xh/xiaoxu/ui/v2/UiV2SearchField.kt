package io.github.x07xh.xiaoxu.ui.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.x07xh.xiaoxu.ui.XiaoxuPalette
import io.github.x07xh.xiaoxu.ui.XiaoxuSoftIconButton

/** One controlled search input for settings and task filters. */
@Composable
internal fun UiV2SearchField(query: String, onQuery: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    BasicTextField(
        value = query, onValueChange = onQuery, singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = XiaoxuPalette.Ink),
        cursorBrush = SolidColor(XiaoxuPalette.Brand),
        modifier = modifier.fillMaxWidth().semantics { contentDescription = placeholder },
        decorationBox = { field ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .clip(CircleShape).background(UiV2Theme.SearchSurface)
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Outlined.Search, contentDescription = null, tint = XiaoxuPalette.Ink3,
                    modifier = Modifier.size(23.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge,
                        color = XiaoxuPalette.Ink3)
                    field()
                }
                if (query.isNotEmpty()) {
                    XiaoxuSoftIconButton(onClick = { onQuery("") }) {
                        Icon(Icons.Outlined.Close, contentDescription = "清空搜索")
                    }
                }
            }
        }
    )
}

