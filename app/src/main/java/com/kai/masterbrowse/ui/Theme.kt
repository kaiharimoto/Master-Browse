package com.kai.masterbrowse.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The palette. Near-black surfaces in a few steps of lift, one cool accent for anything
 * "on" (active toggles, selection, the seek bar), and three text tones. Every screen
 * draws from these so the app reads as one thing.
 */
object Ui {
    val Bg = Color(0xFF0A0B0D)
    val Bar = Color(0xFF121418)
    val Raised = Color(0xFF1D2025)
    val RaisedHi = Color(0xFF272B32)
    val Hairline = Color(0x17FFFFFF)

    val Text = Color(0xFFEDEFF2)
    val TextDim = Color(0xFFA2A8B1)
    val TextFaint = Color(0xFF656A73)

    val Accent = Color(0xFF8EA6FF)
    val OnAccent = Color(0xFF0D1433)
    val AccentSoft = Color(0x268EA6FF)
    val Danger = Color(0xFFFF8080)

    /** Buttons floating over photos and video: dark glass instead of a solid fill. */
    val Glass = Color(0x8C101114)
    val GlassEdge = Color(0x1FFFFFFF)

    val Pill = RoundedCornerShape(50)
    val Card = RoundedCornerShape(16.dp)
    val Tile = RoundedCornerShape(10.dp)
}

/**
 * The app's one and only theme. Lives here rather than in MainActivity because the
 * floating overlay window hosts the same composables from a Service and would otherwise
 * render with Material3's default *light* scheme. The Material roles are mapped onto [Ui]
 * so dropdowns, dialogs and text fields pick up the same look without per-call styling.
 */
@Composable
fun MasterBrowseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Ui.Accent,
            onPrimary = Ui.OnAccent,
            background = Ui.Bg,
            onBackground = Ui.Text,
            surface = Ui.Bar,
            onSurface = Ui.Text,
            onSurfaceVariant = Ui.TextDim,
            surfaceContainer = Ui.Raised,
            surfaceContainerHigh = Ui.Raised,
            surfaceContainerHighest = Ui.RaisedHi,
            outline = Ui.TextFaint,
            outlineVariant = Ui.Hairline,
            error = Ui.Danger,
        ),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(12.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(20.dp),
            extraLarge = RoundedCornerShape(24.dp),
        ),
        content = content,
    )
}
