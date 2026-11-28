package io.github.x07xh.xiaoxu.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.x07xh.xiaoxu.data.safeCloudLinks

/**
 * Shared inline link actions in existing WeChat review and task detail sheets.
 * No preview/fetch, second navigation destination, or automatic opening.
 */
@Composable
internal fun WechatTaskLinkActions(links: List<String>) {
    val safeLinks = safeCloudLinks(links)
    if (safeLinks.isEmpty()) return
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        safeLinks.forEachIndexed { index, link ->
            Column(
                modifier = Modifier.fillMaxWidth()
                    .background(XiaoxuPalette.SurfaceAlt, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    if (safeLinks.size == 1) "消息链接" else "消息链接 ${index + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = XiaoxuPalette.Ink2
                )
                Text(
                    link,
                    style = MaterialTheme.typography.bodySmall,
                    color = XiaoxuPalette.Ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(
                        modifier = Modifier.heightIn(min = 44.dp),
                        onClick = {
                            // A second local check protects against stale/untrusted UI state.
                            if (safeCloudLinks(listOf(link)).isEmpty()) return@TextButton
                            try {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(link))
                                        .addCategory(Intent.CATEGORY_BROWSABLE)
                                )
                            } catch (_: Exception) {
                                Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) { Text("打开") }
                    TextButton(
                        modifier = Modifier.heightIn(min = 44.dp),
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                as? ClipboardManager
                            if (clipboard != null) {
                                clipboard.setPrimaryClip(ClipData.newPlainText("校序任务链接", link))
                                Toast.makeText(context, "已复制链接", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) { Text("复制") }
                }
            }
        }
    }
}
