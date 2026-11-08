package de.koii.companion.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val shape = RoundedCornerShape(4.dp)

/** Large dark surface with a white outline; pressed and selected states invert instead of changing colour. */
@Composable
fun TeButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val inverted = enabled && (selected != pressed)
    val bg = if (inverted) Ink.Fg else Ink.Bg
    val fg = if (!enabled) Ink.Dim else if (inverted) Ink.Bg else Ink.Fg
    Box(
        modifier
            .heightIn(min = 52.dp)
            .clip(shape)
            .background(bg)
            .border(BorderStroke(1.5.dp, if (enabled) Ink.Fg else Ink.Faint), shape)
            .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text.uppercase(), color = fg, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

/** Square pad / grid cell with the same inversion logic. */
@Composable
fun TeCell(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    filled: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable (fg: Color) -> Unit,
) {
    val inverted = selected
    val bg = if (inverted) Ink.Fg else if (filled) Ink.Surface else Ink.Bg
    val fg = if (inverted) Ink.Bg else if (filled) Ink.Fg else Ink.Dim
    Box(
        modifier
            .clip(shape)
            .background(bg)
            .border(BorderStroke(1.dp, if (filled || selected) Ink.Fg else Ink.Faint), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(4.dp),
    ) { content(fg) }
}

@Composable
fun Screen(title: String, modifier: Modifier = Modifier, actions: @Composable () -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title.uppercase(), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            actions()
        }
        content()
    }
}

@Composable
fun Label(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = Ink.Dim, modifier = modifier.padding(top = 12.dp, bottom = 4.dp))
}

@Composable
fun KeyValue(key: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(key.uppercase(), style = MaterialTheme.typography.bodySmall)
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
            Modifier.size(22.dp).border(1.5.dp, Ink.Fg, RoundedCornerShape(2.dp)).background(if (checked) Ink.Fg else Ink.Bg),
        )
        Text(text.uppercase(), modifier = Modifier.padding(start = 12.dp), style = MaterialTheme.typography.labelLarge)
    }
}

/** Thin horizontal bar, 0..1. */
@Composable
fun Meter(fraction: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(10.dp).border(1.dp, Ink.Fg, RoundedCornerShape(2.dp))) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(10.dp).background(Ink.Fg))
    }
}

@Composable
fun TabBar(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column {
        Rule()
        Row(Modifier.fillMaxWidth().background(Ink.Bg)) {
            labels.forEachIndexed { i, l ->
                val on = i == selected
                Box(
                    Modifier.weight(1f).heightIn(min = 56.dp)
                        .background(if (on) Ink.Fg else Ink.Bg)
                        .clickable { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(l.uppercase(), color = if (on) Ink.Bg else Ink.Fg, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }
    }
}
