package de.koii.companion.ui

import android.graphics.Typeface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class Palette(
    val id: String,
    val label: String,
    val dark: Boolean,
    val bg: Color,
    val surface: Color,
    val fg: Color,
    val dim: Color,
    val faint: Color,
    /** Outline of buttons and cells. */
    val edge: Color,
    val accent: Color,
    val onAccent: Color,
    /** One colour per pad group A–D. */
    val groups: List<Color>,
)

object Palettes {
    /** Navy with blue accent. */
    val Slate = Palette(
        "slate", "Slate", true,
        bg = Color(0xFF1E293B), surface = Color(0xFF273449), fg = Color(0xFFE2E8F0),
        dim = Color(0xFF94A3B8), faint = Color(0xFF3B4A63), edge = Color(0xFF475569),
        accent = Color(0xFF3B82F6), onAccent = Color(0xFFFFFFFF),
        groups = listOf(Color(0xFF38BDF8), Color(0xFF4ADE80), Color(0xFFFACC15), Color(0xFFFB7185)),
    )

    /** Warm light grey with the orange of the hardware. */
    val KoII = Palette(
        "koii", "K.O. II", false,
        bg = Color(0xFFE6E3DC), surface = Color(0xFFD7D3CA), fg = Color(0xFF1B1B1B),
        dim = Color(0xFF6B675F), faint = Color(0xFFBEB9AE), edge = Color(0xFF1B1B1B),
        accent = Color(0xFFF26A1B), onAccent = Color(0xFFFFFFFF),
        groups = listOf(Color(0xFFE5432D), Color(0xFFF28A1B), Color(0xFF2F6FDB), Color(0xFF2E9E5B)),
    )

    /** The original greyscale look. */
    val Mono = Palette(
        "mono", "Mono", true,
        bg = Color(0xFF161616), surface = Color(0xFF222222), fg = Color(0xFFEDEDED),
        dim = Color(0xFF8A8A8A), faint = Color(0xFF3A3A3A), edge = Color(0xFFEDEDED),
        accent = Color(0xFFEDEDED), onAccent = Color(0xFF161616),
        groups = listOf(Color(0xFFEDEDED), Color(0xFFBDBDBD), Color(0xFF8E8E8E), Color(0xFF626262)),
    )

    val all = listOf(Slate, KoII, Mono)
    fun byId(id: String?): Palette = all.firstOrNull { it.id == id } ?: Slate
}

/** Current colours. Reading a value inside a composable subscribes it to theme changes. */
object Ink {
    var palette by mutableStateOf(Palettes.Slate)
    val Bg get() = palette.bg
    val Surface get() = palette.surface
    val Fg get() = palette.fg
    val Dim get() = palette.dim
    val Faint get() = palette.faint
    val Edge get() = palette.edge
    val Accent get() = palette.accent
    val OnAccent get() = palette.onAccent
    fun group(i: Int): Color = palette.groups[i.coerceIn(0, palette.groups.lastIndex)]
}

/** System condensed grotesk; drop a Barlow Condensed font into res/font to replace it. */
val Grotesk = FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL))

private fun typography(p: Palette): Typography {
    val base = TextStyle(fontFamily = Grotesk, color = p.fg, letterSpacing = 0.5.sp)
    return Typography(
        displayMedium = base.copy(fontSize = 40.sp, fontWeight = FontWeight.Bold, lineHeight = 44.sp),
        headlineSmall = base.copy(fontSize = 26.sp, fontWeight = FontWeight.Bold),
        titleMedium = base.copy(fontSize = 20.sp, fontWeight = FontWeight.Medium),
        bodyLarge = base.copy(fontSize = 18.sp),
        bodyMedium = base.copy(fontSize = 16.sp),
        bodySmall = base.copy(fontSize = 13.sp, color = p.dim),
        labelLarge = base.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp),
        labelSmall = base.copy(fontSize = 12.sp, letterSpacing = 1.sp),
    )
}

@Composable
fun KoiiTheme(palette: Palette, content: @Composable () -> Unit) {
    Ink.palette = palette
    val scheme = if (palette.dark) {
        darkColorScheme(
            background = palette.bg, surface = palette.surface, onBackground = palette.fg, onSurface = palette.fg,
            primary = palette.accent, onPrimary = palette.onAccent, secondary = palette.dim, onSecondary = palette.bg,
            outline = palette.edge, surfaceVariant = palette.surface, onSurfaceVariant = palette.dim,
        )
    } else {
        lightColorScheme(
            background = palette.bg, surface = palette.surface, onBackground = palette.fg, onSurface = palette.fg,
            primary = palette.accent, onPrimary = palette.onAccent, secondary = palette.dim, onSecondary = palette.bg,
            outline = palette.edge, surfaceVariant = palette.surface, onSurfaceVariant = palette.dim,
        )
    }
    val type = remember(palette) { typography(palette) }
    MaterialTheme(
        colorScheme = scheme,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(4.dp), small = RoundedCornerShape(6.dp),
            medium = RoundedCornerShape(8.dp), large = RoundedCornerShape(10.dp), extraLarge = RoundedCornerShape(12.dp),
        ),
        typography = type,
        content = content,
    )
}
