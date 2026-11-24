package io.github.x07xh.xiaoxu.ui.v2

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Presentation tokens. No business-derived colors or platform-specific fonts. */
internal object UiV2Theme {
    val PageSurface = Brush.verticalGradient(
        listOf(Color(0xFFF8FAFD), Color(0xFFF4F7FD), Color(0xFFF7F8FB))
    )
    val SegmentTrack = Color(0xFFE9EDF4)
    val SegmentThumb = Color.White
    val GroupShape = RoundedCornerShape(24.dp)
    val GroupOpacity = 0.9f
    val GroupGap = 8.dp
    val PageGutter = 16.dp
    val HubSectionGap = 24.dp
    val DetailSectionGap = 16.dp
    val SearchSurface = Color(0xFFE5EAF3)
    val CoolContainer = Color(0xFFDDEDFC)
    val CoolInk = Color(0xFF245B86)
    val WarmContainer = Color(0xFFF7E8D0)
    val WarmInk = Color(0xFF805717)
    val NeutralContainer = Color(0xFFE8EAF0)
    val NeutralInk = Color(0xFF525F75)
    val Shapes = Shapes(
        extraSmall = RoundedCornerShape(6.dp),
        small = RoundedCornerShape(10.dp),
        medium = RoundedCornerShape(14.dp),
        large = RoundedCornerShape(20.dp),
        extraLarge = RoundedCornerShape(28.dp)
    )

    // System sans-serif, two primary weights; honor Android font scaling.
    private fun type(size: Int, line: Int, emphasis: Boolean = false) = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = size.sp,
        lineHeight = line.sp,
        fontWeight = if (emphasis) FontWeight.SemiBold else FontWeight.Normal
    )

    val Typography = Typography(
        displayLarge = type(36, 44),
        displayMedium = type(32, 40),
        displaySmall = type(28, 36),
        headlineLarge = type(26, 34, true),
        headlineMedium = type(22, 30, true),
        headlineSmall = type(20, 28, true),
        titleLarge = type(20, 26, true),
        titleMedium = type(16, 23, true),
        titleSmall = type(14, 20, true),
        bodyLarge = type(16, 25),
        bodyMedium = type(14, 22),
        bodySmall = type(12, 18),
        labelLarge = type(14, 20, true),
        labelMedium = type(12, 17, true),
        labelSmall = type(11, 16)
    )
}
