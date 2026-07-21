package com.kai.masterbrowse.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.kai.masterbrowse.Prefs
import java.io.File

@Composable
fun BrowserScreen(
    dir: File,
    home: File,
    onDirChange: (File) -> Unit,
    onOpenMedia: (List<File>, Int) -> Unit,
    onSetHome: (File) -> Unit,
) {
    var pickThumbFor by remember { mutableStateOf<File?>(null) }
    var thumbVersion by remember { mutableIntStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }

    BackHandler(enabled = pickThumbFor != null || dir.absolutePath != home.absolutePath) {
        if (pickThumbFor != null) {
            pickThumbFor = null
        } else {
            FileRepo.parentOf(dir)?.let(onDirChange) ?: onDirChange(home)
        }
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF101114))
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PaneButton("UP") { FileRepo.parentOf(dir)?.let(onDirChange) }
            PaneButton("HOME") { onDirChange(home) }
            Text(
                dir.absolutePath,
                Modifier.weight(1f).padding(horizontal = 4.dp),
                color = Color(0xFFB8BCC2),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box {
                PaneButton("MENU") { menuOpen = true }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Set this folder as home") },
                        onClick = {
                            menuOpen = false
                            onSetHome(dir)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Clear this folder's thumbnail") },
                        onClick = {
                            menuOpen = false
                            Prefs.setFolderThumb(dir.absolutePath, null)
                            thumbVersion++
                        },
                    )
                }
            }
        }

        val picking = pickThumbFor
        if (picking != null) {
            Row(
                Modifier.fillMaxWidth().background(Color(0xFF3A3320)).padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Tap a media tile to set the thumbnail for \"${picking.name}\"",
                    Modifier.weight(1f),
                    color = Color(0xFFE8D9A0),
                    fontSize = 13.sp,
                )
                PaneButton("CANCEL") { pickThumbFor = null }
            }
        }

        BrowserGrid(
            dir = dir,
            thumbVersion = thumbVersion,
            onOpenDir = onDirChange,
            onOpenMedia = { list, i ->
                val target = pickThumbFor
                if (target != null) {
                    Prefs.setFolderThumb(target.absolutePath, list[i].absolutePath)
                    pickThumbFor = null
                    thumbVersion++
                } else {
                    onOpenMedia(list, i)
                }
            },
            dirMenu = { d ->
                listOf(
                    "Pick thumbnail for this folder" to {
                        pickThumbFor = d
                        onDirChange(d)
                    },
                    "Set as home folder" to { onSetHome(d) },
                    "Clear thumbnail" to {
                        Prefs.setFolderThumb(d.absolutePath, null)
                        thumbVersion++
                    },
                )
            },
            fileMenu = { f ->
                listOf(
                    "Set as thumbnail for this folder" to {
                        Prefs.setFolderThumb(dir.absolutePath, f.absolutePath)
                        thumbVersion++
                    },
                )
            },
        )
    }
}
