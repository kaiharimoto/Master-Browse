package com.kai.masterbrowse.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Full-screen cheat sheet of every gesture and key the app understands.
 * Dismiss via CLOSE, back, or tapping anywhere.
 */
@Composable
fun HelpOverlay(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        HelpContent(onDismiss)
    }
}

@Composable
private fun HelpContent(onDismiss: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xF510121A))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("GESTURES & KEYS", Modifier.weight(1f), color = Color.White, fontSize = 15.sp)
            PaneButton("CLOSE", onDismiss)
        }
        Spacer(Modifier.height(16.dp))

        HelpSection("BROWSER")
        HelpRow("Tap tile", "Open folder, or open media fullscreen")
        HelpRow("Long-press tile", "Menu: thumbnail, pin, select, rename, move, copy, delete")
        HelpRow("Long-press → Select", "Multi-select mode: tap tiles to add, then MOVE / COPY / DELETE them together")
        HelpRow("MENU → Hide filenames", "Toggle the name bar on tiles")
        HelpRow("Pinch grid", "Grow / shrink the tiles (remembered)")
        HelpRow("Drag right-edge bar", "Fast-scroll through a long folder")
        HelpRow("SORT, tap again", "Same sort key twice flips the direction")
        HelpRow("FILTER", "Type to narrow the folder by filename")
        HelpRow("★", "Jump to a pinned folder")
        HelpRow("Back", "Up one folder; exits the app at home")

        Spacer(Modifier.height(16.dp))
        HelpSection("VIEWER")
        HelpRow("Tap", "Show / hide the controls (they auto-hide after 3s)")
        HelpRow("Double-tap left / right", "Previous / next item, instantly")
        HelpRow("Swipe left / right", "Page to the next / previous item")
        HelpRow("Pinch", "Zoom; drag to pan while zoomed")
        HelpRow("1:1 / FIT", "Pixel-perfect zoom vs fit-to-screen")
        HelpRow("SPLIT", "Two items side by side; PICK re-chooses the right one")
        HelpRow("Hold left / right half", "Rewind / fast-forward video at 3× until released")
        HelpRow("Drag seek bar", "Live video scrubbing, frame-accurate")
        HelpRow("← / → keys", "Previous / next item")
        HelpRow("Hold J / K", "Rewind / fast-forward video, accelerating")
    }
}

@Composable
private fun HelpSection(title: String) {
    Text(title, color = Color(0xFF9BA3AE), fontSize = 12.sp)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun HelpRow(gesture: String, desc: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(gesture, Modifier.width(210.dp), color = Color(0xFF9BA3AE), fontSize = 13.sp)
        Text(desc, Modifier.weight(1f), color = Color.White, fontSize = 13.sp)
    }
}
