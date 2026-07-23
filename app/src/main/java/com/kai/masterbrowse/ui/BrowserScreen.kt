package com.kai.masterbrowse.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kai.masterbrowse.FileRepo
import com.kai.masterbrowse.Prefs
import com.kai.masterbrowse.SortMode
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BrowserScreen(
    dir: File,
    home: File,
    onDirChange: (File) -> Unit,
    onOpenMedia: (List<File>, Int) -> Unit,
    onSetHome: (File) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pickThumbFor by remember { mutableStateOf<File?>(null) }
    var thumbVersion by remember { mutableIntStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<File?>(null) }
    var showUpdate by remember { mutableStateOf(false) }
    var sortMode by remember { mutableStateOf(Prefs.sortMode) }
    var sortDesc by remember { mutableStateOf(Prefs.sortDesc) }
    var aspectMode by remember { mutableStateOf(Prefs.aspectMode) }
    var sortOpen by remember { mutableStateOf(false) }
    var filterOpen by remember(dir) { mutableStateOf(false) }
    var filter by remember(dir) { mutableStateOf("") }
    var pinned by remember { mutableStateOf(Prefs.pinnedFolders) }
    var pinsOpen by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<File?>(null) }
    // Pending move/copy: the item, and whether it's a copy (true) or move (false).
    var transfer by remember { mutableStateOf<Pair<File, Boolean>?>(null) }

    fun setPins(p: List<String>) {
        Prefs.pinnedFolders = p
        pinned = p
    }

    fun togglePin(path: String) {
        setPins(if (path in pinned) pinned - path else pinned + path)
    }

    transfer?.let { (src, isCopy) ->
        FolderPicker(
            title = (if (isCopy) "Copy" else "Move") + " \"${src.name}\" to…",
            start = dir,
            onCancel = { transfer = null },
            onSelect = { dest ->
                transfer = null
                Toast.makeText(context, if (isCopy) "Copying…" else "Moving…", Toast.LENGTH_SHORT).show()
                scope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        if (isCopy) FileRepo.copyEntry(src, dest) else FileRepo.moveEntry(src, dest)
                    }
                    if (!ok) {
                        Toast.makeText(
                            context,
                            if (isCopy) "Copy failed" else "Move failed",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    thumbVersion++
                }
            },
        )
        return
    }

    fun selectSort(mode: SortMode) {
        if (sortMode == mode) {
            sortDesc = !sortDesc
        } else {
            sortMode = mode
            sortDesc = mode != SortMode.NAME // name defaults A→Z; date/size default newest/largest first
        }
        Prefs.sortMode = sortMode
        Prefs.sortDesc = sortDesc
        sortOpen = false
    }

    BackHandler(enabled = pickThumbFor != null || dir.absolutePath != home.absolutePath) {
        if (pickThumbFor != null) {
            pickThumbFor = null
        } else {
            FileRepo.parentOf(dir)?.let(onDirChange) ?: onDirChange(home)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
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
            Box {
                PaneButton("★") { pinsOpen = true }
                DropdownMenu(expanded = pinsOpen, onDismissRequest = { pinsOpen = false }) {
                    if (pinned.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("No pinned folders", color = Color(0xFF6B6F76)) },
                            onClick = { pinsOpen = false },
                        )
                    }
                    pinned.forEach { path ->
                        val f = File(path)
                        val alive = f.isDirectory
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (alive) f.name else "${f.name} (missing)",
                                    color = if (alive) Color.White else Color(0xFF6B6F76),
                                )
                            },
                            onClick = {
                                pinsOpen = false
                                if (alive) {
                                    onDirChange(f)
                                } else {
                                    // Tapping a dead entry unpins it explicitly, so an
                                    // unmounted SD card doesn't silently lose pins.
                                    setPins(pinned - path)
                                    Toast.makeText(context, "Unpinned missing folder", Toast.LENGTH_SHORT).show()
                                }
                            },
                        )
                    }
                }
            }
            Text(
                dir.absolutePath,
                Modifier.weight(1f).padding(horizontal = 4.dp),
                color = Color(0xFFB8BCC2),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box {
                PaneButton("SORT") { sortOpen = true }
                DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                    SortMenuItem("Name", sortMode == SortMode.NAME, sortDesc) { selectSort(SortMode.NAME) }
                    SortMenuItem("Date modified", sortMode == SortMode.DATE, sortDesc) { selectSort(SortMode.DATE) }
                    SortMenuItem("File size", sortMode == SortMode.SIZE, sortDesc) { selectSort(SortMode.SIZE) }
                }
            }
            PaneButton("FILTER") {
                if (filterOpen) {
                    filter = ""
                    filterOpen = false
                } else {
                    filterOpen = true
                }
            }
            PaneButton(if (aspectMode) "SQUARE" else "ASPECT") {
                aspectMode = !aspectMode
                Prefs.aspectMode = aspectMode
            }
            val onInternal = dir.absolutePath.startsWith("/storage/emulated")
            PaneButton(if (onInternal) "SD" else "INT") {
                if (onInternal) {
                    val sd = FileRepo.sdCardRoot(context)
                    if (sd != null) {
                        onDirChange(sd)
                    } else {
                        Toast.makeText(context, "No SD card found", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    onDirChange(FileRepo.internalRoot())
                }
            }
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
                    DropdownMenuItem(
                        text = { Text(if (dir.absolutePath in pinned) "Unpin this folder" else "Pin this folder") },
                        onClick = {
                            menuOpen = false
                            togglePin(dir.absolutePath)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Help") },
                        onClick = {
                            menuOpen = false
                            showHelp = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Update from GitHub") },
                        onClick = {
                            menuOpen = false
                            showUpdate = true
                        },
                    )
                }
            }
        }

        if (filterOpen) {
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
            Row(
                Modifier.fillMaxWidth().background(Color(0xFF16181C)).padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BasicTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                        .focusRequester(focusRequester),
                    decorationBox = { inner ->
                        Box {
                            if (filter.isEmpty()) {
                                Text("Filter by name…", color = Color(0xFF6B6F76), fontSize = 13.sp)
                            }
                            inner()
                        }
                    },
                )
                PaneButton("CLEAR") { filter = "" }
                PaneButton("CLOSE") {
                    filter = ""
                    filterOpen = false
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
            sortMode = sortMode,
            sortDesc = sortDesc,
            aspectMode = aspectMode,
            filter = filter,
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
                    (if (d.absolutePath in pinned) "Unpin folder" else "Pin folder") to {
                        togglePin(d.absolutePath)
                    },
                    "Rename" to { renameTarget = d },
                    "Move…" to { transfer = d to false },
                    "Copy…" to { transfer = d to true },
                    "Delete" to { deleteTarget = d },
                )
            },
            fileMenu = { f ->
                listOf(
                    "Set as thumbnail for this folder" to {
                        Prefs.setFolderThumb(dir.absolutePath, f.absolutePath)
                        thumbVersion++
                    },
                    "Rename" to { renameTarget = f },
                    "Move…" to { transfer = f to false },
                    "Copy…" to { transfer = f to true },
                    "Delete" to { deleteTarget = f },
                )
            },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete") },
            text = {
                Text(
                    "Move \"${target.name}\" to trash?\n\n" +
                        "Trashed items are kept in .MasterBrowseTrash for 30 days, then deleted permanently."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { FileRepo.moveToTrash(target) }
                        if (!ok) Toast.makeText(context, "Delete failed", Toast.LENGTH_SHORT).show()
                        thumbVersion++
                    }
                }) { Text("DELETE") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("CANCEL") }
            },
        )
    }

    renameTarget?.let { target ->
        var newName by remember(target) { mutableStateOf(target.name) }
        var renameError by remember(target) { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = {
                            newName = it
                            renameError = null
                        },
                        singleLine = true,
                    )
                    renameError?.let {
                        Text(it, color = Color(0xFFE57373), fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = newName.trim()
                    when {
                        name.isEmpty() || name.contains('/') -> renameError = "Invalid name"
                        name == target.name -> renameTarget = null
                        File(target.parentFile, name).exists() -> renameError = "Name already exists"
                        else -> {
                            renameTarget = null
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) { FileRepo.rename(target, name) }
                                if (!ok) Toast.makeText(context, "Rename failed", Toast.LENGTH_SHORT).show()
                                thumbVersion++
                            }
                        }
                    }
                }) { Text("RENAME") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("CANCEL") }
            },
        )
    }

    if (showUpdate) {
        UpdateDialog { showUpdate = false }
    }

    if (showHelp) {
        HelpOverlay { showHelp = false }
    }
}

/** One row of the SORT dropdown; shows ▲/▼ on the active sort key. */
@Composable
private fun SortMenuItem(label: String, active: Boolean, desc: Boolean, onClick: () -> Unit) {
    val prefix = if (active) (if (desc) "▼ " else "▲ ") else "     "
    DropdownMenuItem(
        text = { Text(prefix + label, color = if (active) Color.White else Color(0xFFB8BCC2)) },
        onClick = onClick,
    )
}
