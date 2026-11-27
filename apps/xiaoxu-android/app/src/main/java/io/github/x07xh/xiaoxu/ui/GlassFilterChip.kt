package io.github.x07xh.xiaoxu.ui

// SPDX-License-Identifier: GPL-3.0-only
// Source: znjhahaha/zhengfang-apk @ bb3b80a6d64e4c2057c0544bb417696ceaa2a9ed, ui/screen/CourseFilterPanel.kt.
// Original authors and contributors.

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.shapes.Capsule
import com.tyust.course.ui.theme.NeuPrimary

@Composable
fun GlassFilterChip(
    label: String,
    selected: Boolean,
    onClick: (() -> Unit)? = null,
    compact: Boolean = false
) {
    val optics = rememberInteractiveOptics()
    val accessibility = rememberGlassAccessibilityMode()
    val interactive = onClick != null && !accessibility.reduceMotion
    val shape = Capsule()
    val textColor = if (selected) NeuPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .glassChip(
                shape = shape,
                rimIntensity = if (selected) 1.25f else 1f,
                pressProgress = { optics.pressProgress }
            )
            // 选中态在玻璃之上叠一层主题色，而不是换掉整块表面：
            // 换表面会把边缘光一起盖掉，选中的芯片反而比未选中的更平。
            .then(
                if (selected) {
                    Modifier.background(NeuPrimary.copy(alpha = 0.16f), shape)
                } else {
                    Modifier
                }
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            )
            .then(if (interactive) optics.gestureModifier else Modifier)
    ) {
        Text(
            text = label,
            modifier = Modifier
                .padding(
                    horizontal = if (compact) 9.dp else 12.dp,
                    vertical = if (compact) 4.dp else 6.dp
                )
                .graphicsLayer {
                    if (!interactive) return@graphicsLayer
                    applyChipContentDeformation(
                        optics = optics,
                        travelPx = GlassRecipe.ChipDragTravelDp.dp.toPx(),
                        stretch = GlassRecipe.ChipDragStretch,
                        pressDepth = GlassRecipe.ChipIconPressDepth,
                        damping = GlassRecipe.ChipContentDeformDamping
                    )
                },
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = textColor,
            fontSize = if (compact) 11.sp else 12.5.sp,
            maxLines = 1
        )
    }
}
