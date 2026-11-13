package com.alinawaz.pixeldl

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Px {
    val Bg = Color(0xFF171822)
    val Panel = Color(0xFF262738)
    val PanelDark = Color(0xFF1B1C28)
    val Ink = Color(0xFF0B0B10)
    val Paper = Color(0xFFF2F0DF)
    val Green = Color(0xFF41CA5F)
    val Yellow = Color(0xFFFFC93C)
    val Red = Color(0xFFE94560)
    val Blue = Color(0xFF4CC9F0)
    val Grey = Color(0xFF8A8FA3)
    val DarkGreen = Color(0xFF1E4630)
    val DarkRed = Color(0xFF571F2B)
}

val PixelHead = FontFamily(Font(R.font.press_start_2p, FontWeight.Normal))
val PixelBody = FontFamily(Font(R.font.vt323, FontWeight.Normal))

/** A chunky pixel panel: hard black shadow, thick black border, sharp corners. */
@Composable
fun PixelPanel(
    modifier: Modifier = Modifier,
    color: Color = Px.Panel,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier.padding(end = 4.dp, bottom = 4.dp)) {
        Box(
            Modifier
                .matchParentSize()
                .offset(x = 4.dp, y = 4.dp)
                .background(Px.Ink)
        )
        Box(
            Modifier
                .fillMaxWidth()
                .background(color)
                .border(3.dp, Px.Ink)
                .padding(10.dp),
            content = content
        )
    }
}

/** A chunky pixel button with a hard shadow. */
@Composable
fun PixelButton(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Px.Green,
    textColor: Color = Px.Ink,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(modifier = modifier.padding(end = 4.dp, bottom = 4.dp)) {
        Box(
            Modifier
                .matchParentSize()
                .offset(x = 4.dp, y = 4.dp)
                .background(Px.Ink)
        )
        Box(
            Modifier
                .background(if (enabled) color else Px.Grey)
                .border(3.dp, Px.Ink)
                .clickable(enabled = enabled) { onClick() }
                .padding(horizontal = 10.dp, vertical = 11.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text, fontFamily = PixelHead, fontSize = 10.sp, color = if (enabled) textColor else Px.Ink)
        }
    }
}

/** A segmented pixel progress bar: 20 little blocks that fill up. */
@Composable
fun PixelProgress(fraction: Float, modifier: Modifier = Modifier, segments: Int = 20) {
    val filled = (fraction.coerceIn(0f, 1f) * segments).toInt()
    Row(
        modifier
            .background(Px.PanelDark)
            .border(3.dp, Px.Ink)
            .padding(4.dp)
            .height(18.dp)
    ) {
        for (i in 0 until segments) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(end = if (i == segments - 1) 0.dp else 2.dp)
                    .background(if (i < filled) Px.Green else Px.Panel)
            )
        }
    }
}
