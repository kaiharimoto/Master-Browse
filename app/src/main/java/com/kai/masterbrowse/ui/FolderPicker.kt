package com.kai.masterbrowse.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kai.masterbrowse.FileRepo
import java.io.File

/**
 * Full-screen destination-folder chooser for move/copy, following the split-view
 * PickerPane pattern: browse folders only, SELECT confirms the folder being viewed.
 */
@Composable
fun FolderPicker(
    title: String,
    start: File,
    onCancel: () -> Unit,
    onSelect: (File) -> Unit,
) {
    var pickDir by remember { mutableStateOf(start) }
    BackHandler(onBack = onCancel)
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Row(
            Modifier.fillMaxWidth().background(Color(0xFF3A3320)).padding(6.dp),
        ) {
            Text(title, color = Color(0xFFE8D9A0), fontSize = 13.sp)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF101114))
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PaneButton("UP") { FileRepo.parentOf(pickDir)?.let { pickDir = it } }
            Text(
                pickDir.absolutePath,
                Modifier.weight(1f).padding(horizontal = 4.dp),
                color = Color(0xFFB8BCC2),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            PaneButton("SELECT") { onSelect(pickDir) }
            PaneButton("CANCEL") { onCancel() }
        }
        BrowserGrid(
            dir = pickDir,
            dirsOnly = true,
            onOpenDir = { pickDir = it },
            onOpenMedia = { _, _ -> },
        )
    }
}
