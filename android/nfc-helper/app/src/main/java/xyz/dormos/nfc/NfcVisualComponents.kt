package xyz.dormos.nfc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shared UI for scanner and writer, avoiding duplicate action panels.
 * Neutral blue/white palette used throughout the personal DORM NFC app.
 */
internal enum class NfcPanelMood { IDLE, ACTIVE, VERIFIED, NEEDS_ATTENTION }

@Composable
internal fun NfcPageHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit
) {
    Column {
        TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)) {
            Text("‹ 返回 NFC", color = Color(0xFF516A9F))
        }
        Spacer(Modifier.height(6.dp))
        Text(title, fontSize = 28.sp, color = Color(0xFF26334C), fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, color = Color(0xFF718097), fontSize = 13.sp)
    }
}

@Composable
internal fun NfcSessionPanel(
    mood: NfcPanelMood,
    title: String,
    subtitle: String,
    working: Boolean,
    actions: @Composable ColumnScope.() -> Unit
) {
    val hue = when (mood) {
        NfcPanelMood.IDLE -> Color(0xFF4769B5)
        NfcPanelMood.ACTIVE -> Color(0xFF4162AC)
        NfcPanelMood.VERIFIED -> Color(0xFF287C61)
        NfcPanelMood.NEEDS_ATTENTION -> Color(0xFFAD692E)
    }
    val marker = when (mood) {
        NfcPanelMood.IDLE -> "⌁"
        NfcPanelMood.ACTIVE -> "⌁"
        NfcPanelMood.VERIFIED -> "✓"
        NfcPanelMood.NEEDS_ATTENTION -> "!"
    }
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(54.dp)
                        .background(hue.copy(alpha = 0.12f), RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(marker, fontSize = 30.sp, color = hue, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.width(15.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        color = Color(0xFF26334C),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(subtitle, color = Color(0xFF718097), fontSize = 12.sp)
                }
            }
            if (working) {
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = hue,
                    trackColor = hue.copy(alpha = 0.15f)
                )
            }
            Spacer(Modifier.height(15.dp))
            actions()
        }
    }
}
