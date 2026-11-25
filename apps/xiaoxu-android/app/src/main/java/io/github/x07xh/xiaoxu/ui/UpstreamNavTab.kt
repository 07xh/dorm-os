package io.github.x07xh.xiaoxu.ui

// SPDX-License-Identifier: GPL-3.0-only
// Source: znjhahaha/zhengfang-apk @ bb3b80a6d64e4c2057c0544bb417696ceaa2a9ed, app/src/main/java/com/tyust/course/ui/system/CapsuleNavigationBar.kt
// Copyright upstream authors and contributors. Adapted for Xiaoxu.
// NavTab extracted; local routes/theme replace upstream app-specific bindings.
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.x07xh.xiaoxu.MainDestination

internal val MainDestination.upstreamSymbol: AppSymbolSpec get() = when (this) {
    MainDestination.HOME -> AppSymbolSpec.Courses
    MainDestination.SCHEDULE -> AppSymbolSpec.Schedule
    MainDestination.ACADEMIC -> AppSymbolSpec.Grades
    MainDestination.TASKS -> AppSymbolSpec.Grab
    MainDestination.PROFILE -> AppSymbolSpec.Settings
}

@Composable
internal fun RowScope.UpstreamNavTab(
    item: MainDestination,
    selected: Boolean,
    accent: Color,
    selectionWeight: Float = if (selected) 1f else 0f,
    iconPhase: () -> Float,
    pressProgress: Float = 0f,
    onClick: (() -> Unit)?,
    forceAccent: Boolean = false
) {
    val weight = selectionWeight.coerceIn(0f, 1f)
    val iconTint = if (forceAccent) {
            accent
        } else {
            androidx.compose.ui.graphics.lerp(
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                accent,
                weight
            )
        }
    val labelColor = if (forceAccent) {
            accent
        } else {
            androidx.compose.ui.graphics.lerp(
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                MaterialTheme.colorScheme.onSurface,
                weight
            )
        }
    val clickModifier = if (onClick != null) {
        Modifier.selectable(
            selected = selected,
            role = Role.Tab,
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    } else {
        Modifier
    }

    Column(
        modifier = Modifier
            .then(if (onClick != null) Modifier.clip(RoundedCornerShape(16.dp)) else Modifier)
            .then(clickModifier)
            .fillMaxHeight()
            .weight(1f),
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PhosphorNavigationIcon(
            spec = item.upstreamSymbol,
            selection = weight,
            phase = iconPhase,
            tint = iconTint,
            modifier = Modifier
                .size(24.dp)
                .drawWithContent {
                    // Freeze transforms into the optical snapshot instead of retaining a
                    // mutable child hardware layer with an old icon frame.
                    val scale = (1f + 0.10f * weight) * (1f - 0.04f * pressProgress)
                    val offsetY = -2.dp.toPx() * weight + 1.dp.toPx() * pressProgress
                    withTransform({
                        translate(0f, offsetY)
                        scale(scale, scale, center)
                    }) { this@drawWithContent.drawContent() }
                }
        )
        // Both faces reserve their measured size throughout the transition.
        // This also keeps the optical sampling copy aligned with the visible label.
        val boldWeight = if (forceAccent) 1f else weight
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = item.label,
                color = labelColor.copy(alpha = labelColor.alpha * (1f - boldWeight)),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.label,
                color = labelColor.copy(alpha = labelColor.alpha * boldWeight),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clearAndSetSemantics { }
            )
        }
    }
}
