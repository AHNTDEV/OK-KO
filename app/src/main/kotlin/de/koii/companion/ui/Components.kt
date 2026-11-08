package de.koii.companion.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Radius follows role: keys are mid, pads are large, chips are round. */
private val shape = RoundedCornerShape(10.dp)

/** Filled surface with an outline; pressed and selected states fill with the accent colour. */
@Composable
fun TeButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    tint: Color? = null,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val on = enabled && (selected != pressed)
    val accent = tint ?: Ink.Accent
    val bg = if (on) accent else Ink.Surface
    val fg = if (!enabled) Ink.Faint else if (on) (if (tint != null) Ink.Bg else Ink.OnAccent) else Ink.Fg
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(bg)
            .border(BorderStroke(1.dp, if (on) accent else if (enabled) Ink.Edge else Ink.Faint), shape)
            .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 1)
    }
}

/** Grid cell / pad. [tint] colours its border and fill when filled. */
@Composable
fun TeCell(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    filled: Boolean = false,
    tint: Color? = null,
    corner: androidx.compose.ui.unit.Dp = 10.dp,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable (fg: Color) -> Unit,
) {
    val cellShape = RoundedCornerShape(corner)
    val accent = tint ?: Ink.Accent
    val bg = if (selected) accent else if (filled) Ink.Surface else Ink.Bg
    val fg = if (selected) (if (tint != null) Ink.Bg else Ink.OnAccent) else if (filled) Ink.Fg else Ink.Dim
    val edge = if (selected) accent else if (filled) (tint ?: Ink.Edge) else Ink.Faint
    Box(
        modifier
            .clip(cellShape)
            .background(bg)
            .border(BorderStroke(if (filled && !selected) 1.5.dp else 1.dp, edge), cellShape)
            .then(
                if (onClick != null || onLongClick != null) {
                    Modifier.combinedClickable(onLongClick = onLongClick, onClick = onClick ?: {})
                } else Modifier,
            )
            .padding(6.dp),
    ) { content(fg) }
}

@Composable
fun Screen(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            actions()
        }
        content()
    }
}

/** Section heading. The rule above marks where a group of controls starts. */
@Composable
fun Label(text: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp)) {
        Rule()
        Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
fun KeyValue(key: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(key, style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Rule()
}

@Composable
fun Rule() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.Faint))
}

@Composable
fun Gap(h: Int = 12) {
    Spacer(Modifier.height(h.dp))
}

@Composable
fun Check(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(22.dp).border(1.5.dp, if (checked) Ink.Accent else Ink.Edge, RoundedCornerShape(4.dp))
                .background(if (checked) Ink.Accent else Ink.Bg),
        )
        Text(text, modifier = Modifier.padding(start = 12.dp), style = MaterialTheme.typography.labelLarge)
    }
}

/** Thin horizontal bar, 0..1. */
@Composable
fun Meter(fraction: Float, modifier: Modifier = Modifier, color: Color = Ink.Accent) {
    Box(modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Ink.Faint)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(8.dp).background(color))
    }
}

/** Small pill: status or tag. */
@Composable
fun Chip(text: String, color: Color = Ink.Accent, modifier: Modifier = Modifier, dot: Boolean = true, onClick: (() -> Unit)? = null) {
    Row(
        modifier.clip(CircleShape).border(1.dp, color, CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot) Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = if (dot) 6.dp else 0.dp), maxLines = 1)
    }
}

@Composable
fun TabBar(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.background(Ink.Surface)) {
        Rule()
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, l ->
                val on = i == selected
                Column(
                    Modifier.weight(1f).heightIn(min = 56.dp).clickable { onSelect(i) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(Modifier.size(width = 24.dp, height = 3.dp).clip(CircleShape).background(if (on) Ink.Accent else Ink.Surface))
                    Text(
                        l, color = if (on) Ink.Fg else Ink.Dim,
                        style = MaterialTheme.typography.labelLarge, maxLines = 1, modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

/** Min/max peaks of audio as a mirrored bar waveform, with optional loop markers (0..1). */
@Composable
fun Waveform(
    peaks: FloatArray,
    modifier: Modifier = Modifier,
    color: Color = Ink.Accent,
    loopStart: Float? = null,
    loopEnd: Float? = null,
) {
    val faint = Ink.Faint
    val edge = Ink.Fg
    Canvas(modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(6.dp)).background(Ink.Surface)) {
        val mid = size.height / 2f
        drawLine(faint, Offset(0f, mid), Offset(size.width, mid), strokeWidth = 1f)
        if (peaks.isEmpty()) return@Canvas
        val step = size.width / peaks.size
        for (i in peaks.indices) {
            val h = (peaks[i].coerceIn(0f, 1f) * mid * 0.95f).coerceAtLeast(1f)
            val x = i * step + step / 2f
            drawLine(color, Offset(x, mid - h), Offset(x, mid + h), strokeWidth = (step * 0.7f).coerceAtLeast(1f), cap = StrokeCap.Butt)
        }
        loopStart?.let { drawLine(edge, Offset(it * size.width, 0f), Offset(it * size.width, size.height), strokeWidth = 2f) }
        loopEnd?.let { drawLine(edge, Offset(it * size.width, 0f), Offset(it * size.width, size.height), strokeWidth = 2f) }
    }
}

/** Material slider in theme colours. */
@Composable
fun TeSlider(value: Float, onChange: (Float) -> Unit, valueRange: ClosedFloatingPointRange<Float>, modifier: Modifier = Modifier) {
    androidx.compose.material3.Slider(
        value = value, onValueChange = onChange, valueRange = valueRange, modifier = modifier,
        colors = androidx.compose.material3.SliderDefaults.colors(
            thumbColor = Ink.Accent, activeTrackColor = Ink.Accent, inactiveTrackColor = Ink.Faint,
        ),
    )
}

/** Inset readout after the device's display: the one place the app uses a monospaced face and capitals. */
@Composable
fun LcdReadout(text: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val panel = RoundedCornerShape(6.dp)
    Box(
        modifier.clip(panel).background(Ink.LcdBg).border(1.dp, Ink.Faint, panel)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Lcd, color = Ink.LcdFg, letterSpacing = 1.sp),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}
