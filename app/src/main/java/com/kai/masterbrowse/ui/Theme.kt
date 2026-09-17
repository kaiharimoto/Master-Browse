package com.kai.masterbrowse.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * The app's one and only theme. Lives here rather than in MainActivity because the
 * floating overlay window hosts the same composables from a Service and would otherwise
 * render with Material3's default *light* scheme.
 */
@Composable
fun MasterBrowseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color.Black,
            surface = Color(0xFF141518),
            onBackground = Color.White,
            onSurface = Color.White,
        ),
        content = content,
    )
}
