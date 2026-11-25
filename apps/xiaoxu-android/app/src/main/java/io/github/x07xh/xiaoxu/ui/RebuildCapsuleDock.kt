package io.github.x07xh.xiaoxu.ui

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule
import io.github.x07xh.xiaoxu.MainDestination

internal object RebuildDockMetrics {
    val trackHeight = 60.dp
    val outerGap = 8.dp
    val contentReserve = trackHeight + outerGap * 2
}

@Composable
internal fun rememberNavigationMaterialTier(): NavigationMaterialTier {
    val context = LocalContext.current
    return remember(context) {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        navigationMaterialTier(Build.VERSION.SDK_INT, manager?.isLowRamDevice ?: true,
            Build.SUPPORTED_ABIS.toList())
    }
}

/** Single overlay dock. Route changes are immediate; only selected colors animate.
 * No second page capture, animated page bitmap, or separate navigation state.
 */
@Composable
internal fun RebuildCapsuleDock(
    selected: MainDestination, onSelect: (MainDestination) -> Unit,
    tier: NavigationMaterialTier, backdrop: Backdrop, modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(32.dp)
    val tint = scheme.surface.copy(alpha = if (tier == NavigationMaterialTier.Opaque) 1f else 0.90f)
    val material = if (tier == NavigationMaterialTier.Refractive) {
        Modifier.drawBackdrop(
            backdrop = backdrop, shape = { Capsule() },
            effects = { vibrancy(); blur(12.dp.toPx()) },
            onDrawSurface = { drawRect(tint.copy(alpha = 0.72f)) }
        )
    } else Modifier.clip(shape).background(tint)
    Box(modifier.fillMaxWidth().navigationBarsPadding()
        .padding(horizontal = 12.dp, vertical = RebuildDockMetrics.outerGap),
        contentAlignment = Alignment.Center) {
        Row(Modifier.widthIn(max = 480.dp).fillMaxWidth()
            .height(RebuildDockMetrics.trackHeight).then(material)
            .border(0.5.dp, scheme.outlineVariant, shape)
            .padding(4.dp).selectableGroup(),
            verticalAlignment = Alignment.CenterVertically) {
            MainDestination.entries.forEach { destination ->
                val active = selected == destination
                val indicator by animateColorAsState(
                    if (active) scheme.primaryContainer else Color.Transparent,
                    label = "dock-indicator")
                val ink by animateColorAsState(
                    if (active) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                    label = "dock-ink")
                Column(Modifier.weight(1f).fillMaxHeight().clip(shape)
                    .background(indicator)
                    .selectable(selected = active, role = Role.Tab,
                        onClick = { if (!active) onSelect(destination) }),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(destination.icon, contentDescription = null, tint = ink,
                        modifier = Modifier.size(22.dp))
                    Text(destination.label, color = ink, maxLines = 1,
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
