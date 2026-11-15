package com.shoqremote.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.shoqremote.app.R

/** Neutral charcoal surfaces in three steps, hairline borders, and one soft indigo accent used sparingly. */
object Palette {
    val Background = Color(0xFF09090B)
    val Panel = Color(0xFF141417)
    val Sheet = Color(0xFF17171A)
    val Raised = Color(0xFF1E1E22)
    val Line = Color(0xFF2A2A30)
    val Text = Color(0xFFF4F4F6)
    val Muted = Color(0xFF8E8E98)
    val Indigo = Color(0xFF7C7CFF)
    val Cyan = Color(0xFF9AB8FF)
    val Danger = Color(0xFFFF5B57)
    val Good = Color(0xFF34D27B)
    val Warn = Color(0xFFFFC542)
    val Gold = Color(0xFFFACC15)

    /** A quiet top-to-bottom shade of the accent, for the few things that should stand out. */
    fun accent(): Brush = Brush.verticalGradient(listOf(Color(0xFF8C8CFF), Color(0xFF6560F2)))
}

@Composable
fun MyTvTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = darkColorScheme(
        primary = Palette.Indigo,
        onPrimary = Color.White,
        secondary = Palette.Cyan,
        background = Palette.Background,
        onBackground = Palette.Text,
        surface = Palette.Panel,
        onSurface = Palette.Text,
        surfaceContainer = Palette.Raised,
        surfaceContainerHigh = Palette.Raised,
        surfaceContainerHighest = Palette.Raised,
        onSurfaceVariant = Palette.Muted,
        outline = Palette.Line,
        outlineVariant = Palette.Line,
        inverseSurface = Palette.Text,
        inverseOnSurface = Palette.Background,
        error = Palette.Danger,
    ),
    content = content,
)

/** The app's mark: the launcher icon's TV glyph on its indigo-to-cyan gradient. */
@Composable
fun Logo(size: Dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(Palette.accent()),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_tile), null, Modifier.size(size * 0.64f), tint = Color.White)
    }
}
