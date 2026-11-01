package xyz.dormos.nfc

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Optional glass-style bottom navigation, using native Compose graphics.
 * Inspired by openly documented Apache-2.0 PrismGlass design concepts,
 * but does not import its code or add a runtime dependency.
 * Translucent material and lighting, not real backdrop refraction.
 */
@Composable
internal fun TapFlowGlassNavigation(
    selected: Int,
    palette: V2Palette,
    night: Boolean,
    onSelect: (Int) -> Unit
) {
    val shape = RoundedCornerShape(26.dp)
    val background = if (night)
        listOf(Color(0xF0273B57), Color(0xE51C2C44))
    else listOf(Color(0xF7FFFFFF), Color(0xEBE9F2FF), Color(0xF5FAFCFF))
    val capsule = if (night)
        listOf(Color(0x774D80C7), Color(0x99426BA5))
    else listOf(Color(0xFFFFFFFF), Color(0xE2DCEAFF))
    Box(
        Modifier.fillMaxWidth().background(palette.background)
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 10.dp)
    ) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().height(69.dp)
                .shadow(9.dp, shape)
                .clip(shape)
                .background(Brush.verticalGradient(background))
        ) {
            val slot = maxWidth / 3
            val offset by animateDpAsState(
                targetValue = slot * tabNavigationOrder(selected),
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ), label = "Glass selected capsule"
            )
            Box(
                Modifier.offset(x = offset).width(slot).fillMaxHeight()
                    .padding(horizontal = 5.dp, vertical = 5.dp)
                    .clip(RoundedCornerShape(21.dp))
                    .background(Brush.verticalGradient(capsule))
            )
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                listOf(Triple(0, "首页", 0), Triple(2, "场景", 1), Triple(1, "工具", 2))
                    .forEach { (tab, title, icon) ->
                        val active = selected == tab
                        val color by animateColorAsState(
                            targetValue = if (active) palette.blue else palette.muted,
                            label = "Glass navigation tint"
                        )
                        val scale by animateFloatAsState(
                            targetValue = if (active) 1.08f else 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium
                            ), label = "Glass navigation icon"
                        )
                        Column(
                            Modifier.weight(1f).fillMaxHeight()
                                .selectable(
                                    selected = active,
                                    role = Role.Tab,
                                    onClick = { onSelect(tab) }
                                )
                                .padding(vertical = 7.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            V2NavIcon(
                                which = icon, color = color,
                                modifier = Modifier.graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(title, color = color, fontSize = 11.sp,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
                        }
                    }
            }
        }
    }
}
