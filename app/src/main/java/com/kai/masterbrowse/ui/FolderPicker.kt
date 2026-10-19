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
import androidx.compose.ui.text.font.FontWeight
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
    val floating = LocalAppHost.current.floating
    var pickDir by remember { mutableStateOf(start) }
    // Back walks up the folder tree (destinations can be anywhere, including ancestors);
    // CANCEL aborts, and back at the filesystem root also aborts.
    BackHandler {
        val parent = FileRepo.parentOf(pickDir)
        if (parent != null) pickDir = parent else onCancel()
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(Ui.Bg)
            .then(if (floating) Modifier else Modifier.windowInsetsPadding(WindowInsets.safeDrawing))
    ) {
        ToolBar {
            IconPill(MbIcons.Close, "Cancel") { onCancel() }
            Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                Text(
                    title,
                    color = Ui.Accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    pickDir.absolutePath,
                    color = Ui.Text,
                    fontSize = if (floating) 13.sp else 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconPill(MbIcons.Up, "Up one folder") { FileRepo.parentOf(pickDir)?.let { pickDir = it } }
            PaneButton("Use this folder", active = true, icon = MbIcons.Check) { onSelect(pickDir) }
        }
        BrowserGrid(
            dir = pickDir,
            dirsOnly = true,
            onOpenDir = { pickDir = it },
            onOpenMedia = { _, _ -> },
        )
    }
}
