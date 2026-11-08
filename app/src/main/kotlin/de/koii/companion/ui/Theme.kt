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
    /** The inset readout in the top bar, after the device's own display. */
    val lcdBg: Color,
    val lcdFg: Color,
)

object Palettes {
    /** Warm light grey with the orange of the hardware. */
    val KoII = Palette(
        "koii", "K.O. II", false,
        bg = Color(0xFFE6E3DC), surface = Color(0xFFD7D3CA), fg = Color(0xFF1B1B1B),
        dim = Color(0xFF6B675F), faint = Color(0xFFBEB9AE), edge = Color(0xFF1B1B1B),
        accent = Color(0xFFF26A1B), onAccent = Color(0xFFFFFFFF),
        groups = listOf(Color(0xFFE5432D), Color(0xFFF28A1B), Color(0xFF2F6FDB), Color(0xFF2E9E5B)),
        lcdBg = Color(0xFF2A2723), lcdFg = Color(0xFFFF7A2B),
    )

    /** The original greyscale look. */
    val Mono = Palette(
        "mono", "Mono", true,
        bg = Color(0xFF161616), surface = Color(0xFF222222), fg = Color(0xFFEDEDED),
        dim = Color(0xFF8A8A8A), faint = Color(0xFF3A3A3A), edge = Color(0xFFEDEDED),
        accent = Color(0xFFEDEDED), onAccent = Color(0xFF161616),
        groups = listOf(Color(0xFFEDEDED), Color(0xFFBDBDBD), Color(0xFF8E8E8E), Color(0xFF626262)),
        lcdBg = Color(0xFF0B0B0B), lcdFg = Color(0xFFEDEDED),
    )

    val all = listOf(KoII, Mono)
    fun byId(id: String?): Palette = all.firstOrNull { it.id == id } ?: KoII
}

/** Current colours. Reading a value inside a composable subscribes it to theme changes. */
object Ink {
    var palette by mutableStateOf(Palettes.KoII)
    val Bg get() = palette.bg
    val Surface get() = palette.surface
    val Fg get() = palette.fg
    val Dim get() = palette.dim
    val Faint get() = palette.faint
    val Edge get() = palette.edge
    val Accent get() = palette.accent
    val OnAccent get() = palette.onAccent
    val LcdBg get() = palette.lcdBg
    val LcdFg get() = palette.lcdFg
    fun group(i: Int): Color = palette.groups[i.coerceIn(0, palette.groups.lastIndex)]
}

/** System condensed grotesk for titles, numbers and keys; the default system sans for running text. */
val Condensed = FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL))
val CondensedMedium = FontFamily(Typeface.create("sans-serif-condensed", Typeface.BOLD))

/** The single monospaced face in the app: the display readout. */
val Lcd = FontFamily.Monospace

private fun typography(p: Palette): Typography {
    val body = TextStyle(fontFamily = FontFamily.SansSerif, color = p.fg)
    val head = TextStyle(fontFamily = Condensed, color = p.fg)
    return Typography(
        displayMedium = head.copy(fontSize = 44.sp, fontWeight = FontWeight.Bold, lineHeight = 48.sp),
        headlineSmall = head.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp),
        titleMedium = head.copy(fontSize = 20.sp, fontWeight = FontWeight.Medium),
        titleSmall = head.copy(fontSize = 17.sp, fontWeight = FontWeight.Medium),
        bodyLarge = body.copy(fontSize = 17.sp, lineHeight = 24.sp),
        bodyMedium = body.copy(fontSize = 15.sp, lineHeight = 22.sp),
        bodySmall = body.copy(fontSize = 13.sp, lineHeight = 19.sp, color = p.dim),
        labelLarge = head.copy(fontSize = 17.sp, fontWeight = FontWeight.Medium),
        labelSmall = head.copy(fontSize = 14.sp, color = p.dim),
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
