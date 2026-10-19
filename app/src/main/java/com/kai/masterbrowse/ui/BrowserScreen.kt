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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kai.masterbrowse.Dataset
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
    var deleteTargets by remember { mutableStateOf<List<File>?>(null) }
    var showUpdate by remember { mutableStateOf(false) }
    var sortMode by remember { mutableStateOf(Prefs.sortMode) }
    var sortDesc by remember { mutableStateOf(Prefs.sortDesc) }
    var aspectMode by remember { mutableStateOf(Prefs.aspectMode) }
    var filterOpen by remember(dir) { mutableStateOf(false) }
    var filter by remember(dir) { mutableStateOf("") }
    var pinned by remember { mutableStateOf(Prefs.pinnedFolders) }
    var showHelp by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<File?>(null) }
    var showNames by remember { mutableStateOf(Prefs.showNames) }
    // Pending move/copy: the items, and whether it's a copy (true) or move (false).
    var transfer by remember { mutableStateOf<Pair<List<File>, Boolean>?>(null) }
    // Multi-select mode: while active, tapping tiles toggles membership instead of opening.
    var selecting by remember(dir) { mutableStateOf(false) }
    val selected = remember(dir) { mutableStateListOf<String>() }
    var datasetPick by remember { mutableStateOf(false) }

    val floating = LocalAppHost.current.floating
    val menuController = LocalMenuController.current

    fun runDelete(targets: List<File>) {
        deleteTargets = null
        selecting = false
        selected.clear()
        scope.launch {
            val failed = withContext(Dispatchers.IO) { targets.count { !FileRepo.moveToTrash(it) } }
            if (failed > 0) {
                Toast.makeText(context, "$failed of ${targets.size} failed", Toast.LENGTH_SHORT).show()
            }
            thumbVersion++
        }
    }

    fun toggleSelect(f: File) {
        val p = f.absolutePath
        if (!selected.remove(p)) selected.add(p)
        if (selected.isEmpty()) selecting = false
    }

    /** Copies the selected media (folders are skipped) into a dataset category. */
    fun sendToCategory(category: File) {
        val files = selected.map(::File).filter { it.isFile }
        selecting = false
        selected.clear()
        scope.launch {
            val failed = withContext(Dispatchers.IO) { files.count { !Dataset.add(category, it) } }
            val msg = "${files.size - failed} copied to ${category.name}" +
                if (failed > 0) ", $failed failed" else ""
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    fun datasetEntries(): List<MenuEntry> {
        val root = Prefs.datasetRoot?.let(::File)?.takeIf { it.isDirectory }
            ?: return listOf(MenuEntry("Choose dataset folder…", highlighted = true) { datasetPick = true })
        val cats = Dataset.categories(root)
        return buildList {
            if (cats.isEmpty()) add(MenuEntry("No categories — add subfolders to ${root.name}") { })
            cats.forEach { c -> add(MenuEntry(c.name, highlighted = true) { sendToCategory(c) }) }
            add(MenuEntry("Change dataset folder…") { datasetPick = true })
        }
    }

    fun setPins(p: List<String>) {
        Prefs.pinnedFolders = p
        pinned = p
    }

    fun togglePin(path: String) {
        setPins(if (path in pinned) pinned - path else pinned + path)
    }

    transfer?.let { (files, isCopy) ->
        val what = if (files.size == 1) "\"${files.first().name}\"" else "${files.size} items"
        FolderPicker(
            title = (if (isCopy) "Copy" else "Move") + " $what to…",
            start = dir,
            onCancel = { transfer = null },
            onSelect = { dest ->
                transfer = null
                selecting = false
                selected.clear()
                Toast.makeText(context, if (isCopy) "Copying…" else "Moving…", Toast.LENGTH_SHORT).show()
                scope.launch {
                    val failed = withContext(Dispatchers.IO) {
                        files.count {
                            !(if (isCopy) FileRepo.copyEntry(it, dest) else FileRepo.moveEntry(it, dest))
                        }
                    }
                    if (failed > 0) {
                        Toast.makeText(context, "$failed of ${files.size} failed", Toast.LENGTH_SHORT).show()
                    }
                    thumbVersion++
                }
            },
        )
        return
    }

    if (datasetPick) {
        FolderPicker(
            title = "Choose dataset folder (its subfolders are the categories)…",
            start = Prefs.datasetRoot?.let(::File)?.takeIf { it.isDirectory } ?: dir,
            onCancel = { datasetPick = false },
            onSelect = { dest ->
                Prefs.datasetRoot = dest.absolutePath
                datasetPick = false
            },
        )
        return
    }

    /**
     * Anything that opens its own window (a Dialog, or a text field that needs the IME)
     * cannot work inside the floating overlay window, so those entries are left out
     * there. Menus route through [LocalMenuController] instead, which draws them inline.
     */
    fun confirmDeleteOf(targets: List<File>) {
        if (menuController == null) {
            deleteTargets = targets
            return
        }
        val what = if (targets.size == 1) "\"${targets.first().name}\"" else "${targets.size} items"
        menuController.show(
            title = "Move $what to trash? Trashed items are kept in .MasterBrowseTrash " +
                "for 30 days, then deleted permanently.",
            entries = listOf(
                MenuEntry("Delete", highlighted = true) { runDelete(targets) },
                MenuEntry("Cancel") { },
            ),
        )
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
    }

    BackHandler(enabled = selecting || pickThumbFor != null || dir.absolutePath != home.absolutePath) {
        if (selecting) {
            selecting = false
            selected.clear()
        } else if (pickThumbFor != null) {
            pickThumbFor = null
        } else {
            FileRepo.parentOf(dir)?.let(onDirChange) ?: onDirChange(home)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Ui.Bg)
            // Insets dispatched to a freely-positioned overlay window are meaningless and
            // would eat a big slice of a small floating window.
            .then(if (floating) Modifier else Modifier.windowInsetsPadding(WindowInsets.safeDrawing))
    ) {
        ToolBar {
            IconPill(MbIcons.Up, "Up one folder") { FileRepo.parentOf(dir)?.let(onDirChange) }
            IconPill(MbIcons.Home, "Home folder") { onDirChange(home) }
            MenuButton("Pinned folders", icon = MbIcons.Star, iconOnly = true) {
                if (pinned.isEmpty()) {
                    listOf(MenuEntry("No pinned folders") { })
                } else {
                    pinned.map { path ->
                        val f = File(path)
                        val alive = f.isDirectory
                        MenuEntry(
                            if (alive) f.name else "${f.name} (missing)",
                            highlighted = alive,
                        ) {
                            if (alive) {
                                onDirChange(f)
                            } else {
                                // Tapping a dead entry unpins it explicitly, so an
                                // unmounted SD card doesn't silently lose pins.
                                setPins(pinned - path)
                                Toast.makeText(context, "Unpinned missing folder", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            FolderTitle(dir, Modifier.weight(1f).padding(horizontal = 8.dp))
            if (!floating) IconPill(MbIcons.Search, "Filter by name", active = filterOpen) {
                if (filterOpen) {
                    filter = ""
                    filterOpen = false
                } else {
                    filterOpen = true
                }
            }
            MenuButton("Sort", icon = MbIcons.Sort, iconOnly = true) {
                listOf(
                    sortEntry("Name", SortMode.NAME, sortMode, sortDesc) { m -> selectSort(m) },
                    sortEntry("Date modified", SortMode.DATE, sortMode, sortDesc) { m -> selectSort(m) },
                    sortEntry("File size", SortMode.SIZE, sortMode, sortDesc) { m -> selectSort(m) },
                )
            }
            // Shows the layout a tap switches to, as the old SQUARE/ASPECT label did.
            IconPill(
                if (aspectMode) MbIcons.GridSquare else MbIcons.GridJustified,
                if (aspectMode) "Square tiles" else "True-aspect tiles",
            ) {
                aspectMode = !aspectMode
                Prefs.aspectMode = aspectMode
            }
            val onInternal = dir.absolutePath.startsWith("/storage/emulated")
            IconPill(
                if (onInternal) MbIcons.SdCard else MbIcons.Phone,
                if (onInternal) "SD card" else "Internal storage",
            ) {
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
            MenuButton("Menu", icon = MbIcons.More, iconOnly = true) {
                buildList {
                    add(MenuEntry("Set this folder as home") { onSetHome(dir) })
                    add(MenuEntry("Set dataset folder…") { datasetPick = true })
                    add(
                        MenuEntry("Clear this folder's thumbnail") {
                            Prefs.setFolderThumb(dir.absolutePath, null)
                            thumbVersion++
                        }
                    )
                    add(
                        MenuEntry(
                            if (dir.absolutePath in pinned) "Unpin this folder" else "Pin this folder"
                        ) { togglePin(dir.absolutePath) }
                    )
                    add(
                        MenuEntry(if (showNames) "Hide filenames" else "Show filenames") {
                            showNames = !showNames
                            Prefs.showNames = showNames
                        }
                    )
                    // Help is a Dialog and the updater both opens one and installs an
                    // APK — neither belongs in (or works from) the floating window.
                    if (!floating) {
                        add(MenuEntry("Help") { showHelp = true })
                        add(MenuEntry("Update from GitHub") { showUpdate = true })
                    }
                }
            }
        }

        if (filterOpen) {
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Ui.Raised)
                    .padding(start = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(MbIcons.Search, null, Modifier.size(18.dp), tint = Ui.TextDim)
                BasicTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Ui.Text, fontSize = 14.sp),
                    cursorBrush = SolidColor(Ui.Accent),
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 12.dp)
                        .focusRequester(focusRequester),
                    decorationBox = { inner ->
                        Box {
                            if (filter.isEmpty()) {
                                Text("Filter by name", color = Ui.TextFaint, fontSize = 14.sp)
                            }
                            inner()
                        }
                    },
                )
                // Clears the text; on an already-empty box, closes it.
                IconPill(MbIcons.Close, "Clear filter") {
                    if (filter.isEmpty()) filterOpen = false else filter = ""
                }
            }
        }

        val picking = pickThumbFor
        if (picking != null) {
            Banner("Tap a photo to use as the thumbnail for \"${picking.name}\"") {
                PaneButton("Cancel") { pickThumbFor = null }
            }
        }

        if (selecting) {
            Banner("${selected.size} selected") {
                PaneButton("Move", icon = MbIcons.Move) { if (selected.isNotEmpty()) transfer = selected.map(::File) to false }
                PaneButton("Copy", icon = MbIcons.Copy) { if (selected.isNotEmpty()) transfer = selected.map(::File) to true }
                MenuButton("Dataset", icon = MbIcons.Dataset) { datasetEntries() }
                PaneButton("Delete", icon = MbIcons.Delete) { if (selected.isNotEmpty()) confirmDeleteOf(selected.map(::File)) }
                IconPill(MbIcons.Close, "Cancel selection") {
                    selecting = false
                    selected.clear()
                }
            }
        }

        BrowserGrid(
            dir = dir,
            thumbVersion = thumbVersion,
            sortMode = sortMode,
            sortDesc = sortDesc,
            aspectMode = aspectMode,
            filter = filter,
            showNames = showNames,
            selectedPaths = selected.toSet(),
            onOpenDir = { if (selecting) toggleSelect(it) else onDirChange(it) },
            onOpenMedia = { list, i ->
                val target = pickThumbFor
                if (selecting) {
                    toggleSelect(list[i])
                } else if (target != null) {
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
                    "Select" to {
                        selecting = true
                        toggleSelect(d)
                    },
                    *(if (floating) emptyArray() else arrayOf("Rename" to { renameTarget = d })),
                    "Move…" to { transfer = listOf(d) to false },
                    "Copy…" to { transfer = listOf(d) to true },
                    "Delete" to { confirmDeleteOf(listOf(d)) },
                )
            },
            fileMenu = { f ->
                listOf(
                    "Set as thumbnail for this folder" to {
                        Prefs.setFolderThumb(dir.absolutePath, f.absolutePath)
                        thumbVersion++
                    },
                    "Select" to {
                        selecting = true
                        toggleSelect(f)
                    },
                    *(if (floating) emptyArray() else arrayOf("Rename" to { renameTarget = f })),
                    "Move…" to { transfer = listOf(f) to false },
                    "Copy…" to { transfer = listOf(f) to true },
                    "Delete" to { confirmDeleteOf(listOf(f)) },
                )
            },
        )
    }

    deleteTargets?.let { targets ->
        AlertDialog(
            onDismissRequest = { deleteTargets = null },
            title = { Text("Delete") },
            text = {
                val what = if (targets.size == 1) "\"${targets.first().name}\"" else "${targets.size} items"
                Text(
                    "Move $what to trash?\n\n" +
                        "Trashed items are kept in .MasterBrowseTrash for 30 days, then deleted permanently."
                )
            },
            confirmButton = {
                TextButton(onClick = { runDelete(targets) }) { Text("Delete", color = Ui.Danger) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTargets = null }) { Text("Cancel") }
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
                        Text(it, color = Ui.Danger, fontSize = 12.sp)
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
                }) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
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

/** One row of the SORT menu; shows ▲/▼ on the active sort key. */
private fun sortEntry(
    label: String,
    mode: SortMode,
    active: SortMode,
    desc: Boolean,
    onSelect: (SortMode) -> Unit,
): MenuEntry {
    val isActive = active == mode
    val suffix = if (isActive) (if (desc) "  ↓" else "  ↑") else ""
    return MenuEntry(label + suffix, highlighted = isActive) { onSelect(mode) }
}

/** The current folder's name, with the path leading to it in small type underneath. */
@Composable
private fun FolderTitle(dir: File, modifier: Modifier) {
    val compact = LocalAppHost.current.floating
    Column(modifier) {
        Text(
            dir.name.ifEmpty { dir.absolutePath },
            color = Ui.Text,
            fontSize = if (compact) 13.sp else 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!compact) {
            Text(
                dir.parent ?: "",
                color = Ui.TextFaint,
                fontSize = 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
