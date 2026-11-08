package de.koii.companion.ui

import android.graphics.Typeface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

/** Monochrome palette: no hues, no gradients. State is shown by inversion. */
object Ink {
    val Bg = Color(0xFF161616)
    val Surface = Color(0xFF222222)
    val Fg = Color(0xFFEDEDED)
    val Dim = Color(0xFF8A8A8A)
    val Faint = Color(0xFF3A3A3A)
}

/** System condensed grotesk; drop a Barlow Condensed font into res/font to replace it. */
val Grotesk = FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL))

private val base = TextStyle(fontFamily = Grotesk, color = Ink.Fg, letterSpacing = 0.5.sp)

private val typography = Typography(
    displayMedium = base.copy(fontSize = 40.sp, fontWeight = FontWeight.Bold, lineHeight = 44.sp),
    headlineSmall = base.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold),
    titleMedium = base.copy(fontSize = 20.sp, fontWeight = FontWeight.Medium),
    bodyLarge = base.copy(fontSize = 18.sp),
    bodyMedium = base.copy(fontSize = 16.sp),
    bodySmall = base.copy(fontSize = 13.sp, color = Ink.Dim),
    labelLarge = base.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp),
    labelSmall = base.copy(fontSize = 12.sp, letterSpacing = 1.sp),
)

@Composable
fun KoiiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Ink.Bg, surface = Ink.Surface, onBackground = Ink.Fg, onSurface = Ink.Fg,
            primary = Ink.Fg, onPrimary = Ink.Bg, secondary = Ink.Dim, onSecondary = Ink.Bg,
            outline = Ink.Fg, surfaceVariant = Ink.Surface, onSurfaceVariant = Ink.Dim,
        ),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(4.dp), small = RoundedCornerShape(4.dp),
            medium = RoundedCornerShape(4.dp), large = RoundedCornerShape(4.dp), extraLarge = RoundedCornerShape(4.dp),
        ),
        typography = typography,
        content = content,
    )
}
