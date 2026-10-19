package com.kai.masterbrowse.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.Player
import com.kai.masterbrowse.Dataset
import com.kai.masterbrowse.FileRepo
import com.kai.masterbrowse.Frames
import com.kai.masterbrowse.Prefs
import com.kai.masterbrowse.isVideoFile
import com.kai.masterbrowse.overlay.Handoff
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Fullscreen viewer. Shows one media pane, or two side by side in split mode.
 * In split mode the right half starts as a tile picker to choose the second item.
 *
 * Split works in the floating window too; the window widens to hold both panes.
 * [onIndexChange] keeps the host told which item is on screen so POP OUT / EXPAND can
 * carry it across, and [onMediaSizeChange] reports the shape the host should give the
 * window — the primary item's, widened by the number of panes on screen.
 *
 * Dataset mode adds a sort bar under the panes: one button per category folder of the
 * dataset root, lit when the primary pane's item has a copy there. Tapping (or pressing
 * 1–9) copies the item in, or trashes that copy if it was already there. See [Dataset].
 * On a video it saves the frame on screen as a PNG instead, and FRAMES opens
 * [FramePicker] to pull many stills from it at once.
 */
@Composable
fun ViewerScreen(
    items: List<File>,
    startIndex: Int,
    browseStart: File,
    onClose: () -> Unit,
    onIndexChange: (Int) -> Unit = {},
    onMediaSizeChange: (Size) -> Unit = {},
) {
    val host = LocalAppHost.current
    var currentIndex by remember(items, startIndex) { mutableIntStateOf(startIndex) }
    var split by remember { mutableStateOf(false) }
    var secondary by remember { mutableStateOf<Pair<List<File>, Int>?>(null) }
    var pickerDir by remember { mutableStateOf(browseStart) }
    var primarySize by remember { mutableStateOf(Size.Zero) }
    // Where the primary pane resumes after the dataset folder picker has replaced it.
    // Only ever changed while the pane is gone — MediaPane remembers its index per
    // startIndex value, so feeding the live index in would reset zoom on every page turn.
    var paneStart by remember(items, startIndex) { mutableIntStateOf(startIndex) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var datasetOn by remember { mutableStateOf(Prefs.datasetMode) }
    var datasetRoot by remember { mutableStateOf(Prefs.datasetRoot?.let(::File)) }
    var pickingRoot by remember { mutableStateOf(false) }
    var newCategoryOpen by remember { mutableStateOf(false) }
    var categoryVersion by remember { mutableIntStateOf(0) }
    var membershipVersion by remember { mutableIntStateOf(0) }
    // Serialises copies/trash moves: two quick taps must not both see "not there yet"
    // and copy the item in twice.
    val datasetLock = remember { Mutex() }
    val root = datasetRoot
    val categories by produceState(emptyList<File>(), datasetOn, root, categoryVersion) {
        value = if (datasetOn && root != null) {
            withContext(Dispatchers.IO) { Dataset.categories(root) }
        } else {
            emptyList()
        }
    }
    val current = items.getOrNull(currentIndex)
    val currentIsVideo = current?.isVideoFile() == true
    // Category path -> what it holds of the current item: for an image, an entry means it
    // has a copy; for a video, the times of the stills saved from it. Tagged with the item
    // it was computed for, so a page turn never lights the previous item's categories.
    val membership by produceState<Pair<File?, Map<String, List<Long>>>>(
        null to emptyMap(), current, categories, membershipVersion,
    ) {
        val f = current ?: return@produceState
        val video = f.isVideoFile()
        value = f to withContext(Dispatchers.IO) {
            buildMap {
                for (c in categories) {
                    if (video) {
                        Dataset.framesIn(c, f).takeIf { it.isNotEmpty() }?.let { put(c.absolutePath, it) }
                    } else if (Dataset.contains(c, f)) {
                        put(c.absolutePath, emptyList())
                    }
                }
            }
        }
    }
    // A video category lights up when the paused frame is one already saved there, so a
    // second tap on the same moment undoes it — the same toggle images get.
    var primaryPlayer by remember { mutableStateOf<Player?>(null) }
    var videoPos by remember { mutableLongStateOf(-1L) }
    LaunchedEffect(primaryPlayer, datasetOn) {
        val p = primaryPlayer
        if (p == null || !datasetOn) {
            videoPos = -1L
            return@LaunchedEffect
        }
        while (true) {
            videoPos = p.currentPosition
            delay(200)
        }
    }
    var framesFor by remember { mutableStateOf<File?>(null) }

    fun isMember(category: File): Boolean {
        val (f, m) = membership
        if (f != current) return false
        val held = m[category.absolutePath] ?: return false
        return if (currentIsVideo) videoPos in held else true
    }

    fun frameCount(category: File): Int =
        if (currentIsVideo && membership.first == current) membership.second[category.absolutePath]?.size ?: 0 else 0

    fun toggleCategory(category: File) {
        val f = items.getOrNull(currentIndex) ?: return
        // On a video the category gets the frame on screen, not the file. Read on the main
        // thread: ExoPlayer may only be touched from the thread that created it.
        val frameAt = if (f.isVideoFile()) primaryPlayer?.currentPosition ?: return else null
        scope.launch {
            val ok = datasetLock.withLock {
                withContext(Dispatchers.IO) {
                    if (frameAt != null) {
                        val still = File(category, Dataset.frameName(f, frameAt))
                        if (still.exists()) FileRepo.moveToTrash(still) else Frames.save(category, f, frameAt)
                    } else {
                        // Decide from disk, not from the button: it may still show the last item.
                        if (Dataset.contains(category, f)) Dataset.remove(category, f) else Dataset.add(category, f)
                    }
                }
            }
            if (!ok) Toast.makeText(context, "Failed: ${category.name}", Toast.LENGTH_SHORT).show()
            membershipVersion++
        }
    }

    val frameVideo = framesFor
    if (frameVideo != null) {
        FramePicker(
            video = frameVideo,
            categories = categories,
            onClose = {
                framesFor = null
                membershipVersion++
            },
        )
        return
    }

    if (pickingRoot) {
        FolderPicker(
            title = "Choose dataset folder (its subfolders are the categories)…",
            start = root?.takeIf { it.isDirectory } ?: browseStart,
            onCancel = { pickingRoot = false },
            onSelect = { dest ->
                Prefs.datasetRoot = dest.absolutePath
                Prefs.datasetMode = true
                datasetRoot = dest
                datasetOn = true
                pickingRoot = false
            },
        )
        return
    }

    fun openRootPicker() {
        paneStart = currentIndex
        pickingRoot = true
    }

    // Two panes side by side want twice the width for the same item, so the reported
    // shape follows the split toggle as well as the media itself.
    LaunchedEffect(primarySize, split) {
        if (primarySize.width > 0f && primarySize.height > 0f) {
            val panes = if (split) 2 else 1
            onMediaSizeChange(Size(primarySize.width * panes, primarySize.height))
        }
    }

    BackHandler {
        if (split) {
            split = false
            secondary = null
        } else {
            onClose()
        }
    }

    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, view) }
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        view.keepScreenOn = true
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            view.keepScreenOn = false
        }
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(Modifier.weight(1f).fillMaxWidth()) {
            MediaPane(
                items = items,
                startIndex = paneStart,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onIndexChange = {
                    currentIndex = it
                    onIndexChange(it)
                },
                // Only the primary pane drives the window shape.
                onMediaSizeChange = { primarySize = it },
                onPlayerChange = { primaryPlayer = it },
                onKey = { key ->
                    val n = DIGIT_KEYS.indexOf(key).takeIf { it >= 0 } ?: NUMPAD_KEYS.indexOf(key)
                    val category = if (datasetOn && n >= 0) categories.getOrNull(n) else null
                    if (category != null) toggleCategory(category)
                    category != null
                },
            ) {
                PaneButton("DATASET", active = datasetOn) {
                    when {
                        datasetOn -> datasetOn = false
                        root?.isDirectory != true -> openRootPicker()
                        else -> datasetOn = true
                    }
                    Prefs.datasetMode = datasetOn
                }
                if (datasetOn && currentIsVideo) {
                    PaneButton("FRAMES") {
                        paneStart = currentIndex
                        framesFor = current
                    }
                }
                PaneButton(if (split) "SINGLE" else "SPLIT") {
                    split = !split
                    if (!split) secondary = null
                }
                if (host.floating) {
                    PaneButton("GRID") { onClose() }
                } else {
                    host.onPopOut?.let { popOut ->
                        PaneButton("POP OUT") {
                            val session = Handoff(browseStart, items, currentIndex)
                            // Close here first: the mini window takes over playback, and a
                            // second player left behind in the backgrounded activity would
                            // come back to life the next time the app is resumed.
                            onClose()
                            popOut(session)
                        }
                    }
                    PaneButton("CLOSE") { onClose() }
                }
            }
            if (split) {
                Box(Modifier.width(1.dp).fillMaxHeight().background(Color(0xFF333333)))
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    val sec = secondary
                    if (sec == null) {
                        PickerPane(
                            dir = pickerDir,
                            onDirChange = { pickerDir = it },
                            onPick = { list, i -> secondary = list to i },
                        )
                    } else {
                        MediaPane(
                            items = sec.first,
                            startIndex = sec.second,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            PaneButton("PICK") { secondary = null }
                        }
                    }
                }
            }
        }
        if (datasetOn) {
            DatasetBar(
                root = root,
                categories = categories,
                isMember = ::isMember,
                frameCount = ::frameCount,
                onToggle = ::toggleCategory,
                onNew = { newCategoryOpen = true },
                onChangeRoot = ::openRootPicker,
            )
        }
    }

    if (newCategoryOpen && root != null) {
        NewCategoryDialog(
            root = root,
            onDismiss = { newCategoryOpen = false },
            onCreated = { category ->
                newCategoryOpen = false
                categoryVersion++
                toggleCategory(category)
            },
        )
    }
}

private val DIGIT_KEYS = listOf(
    Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine,
)
private val NUMPAD_KEYS = listOf(
    Key.NumPad1, Key.NumPad2, Key.NumPad3, Key.NumPad4, Key.NumPad5,
    Key.NumPad6, Key.NumPad7, Key.NumPad8, Key.NumPad9,
)

/**
 * Dataset mode's category strip. A fixed bar under the panes rather than part of the
 * auto-hiding overlay: sorting means tapping it constantly, and it must never cover the
 * picture being judged.
 */
@Composable
private fun DatasetBar(
    root: File?,
    categories: List<File>,
    isMember: (File) -> Boolean,
    frameCount: (File) -> Int,
    onToggle: (File) -> Unit,
    onNew: () -> Unit,
    onChangeRoot: () -> Unit,
) {
    val floating = LocalAppHost.current.floating
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF101114))
            .horizontalScroll(rememberScrollState())
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            root?.name ?: "NO DATASET FOLDER",
            Modifier.padding(horizontal = 4.dp),
            color = Color(0xFF9BA3AE),
            fontSize = 12.sp,
            maxLines = 1,
        )
        if (root != null && categories.isEmpty()) {
            Text("no categories yet", color = Color(0xFF6B6F76), fontSize = 12.sp)
        }
        categories.forEachIndexed { i, c ->
            val n = frameCount(c)
            val label = (if (i < 9) "${i + 1} ${c.name}" else c.name) + if (n > 0) " · $n" else ""
            PaneButton(label, active = isMember(c)) { onToggle(c) }
        }
        // Naming a category needs the keyboard, which the floating window leaves to the
        // app underneath — make categories from the fullscreen app (or a file manager).
        if (!floating && root != null) PaneButton("+ NEW", onClick = onNew)
        PaneButton("ROOT", onClick = onChangeRoot)
    }
}

@Composable
private fun NewCategoryDialog(root: File, onDismiss: () -> Unit, onCreated: (File) -> Unit) {
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New category in ${root.name}") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        error = null
                    },
                    singleLine = true,
                )
                error?.let { Text(it, color = Color(0xFFE57373), fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val n = name.trim()
                when {
                    n.isEmpty() || n.contains('/') || n.startsWith(".") -> error = "Invalid name"
                    File(root, n).exists() -> error = "Already exists"
                    else -> Dataset.create(root, n)?.let(onCreated) ?: run { error = "Couldn't create folder" }
                }
            }) { Text("CREATE") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } },
    )
}

/** Compact folder browser used to choose the second media item in split mode. */
@Composable
private fun PickerPane(
    dir: File,
    onDirChange: (File) -> Unit,
    onPick: (List<File>, Int) -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(
            Modifier.fillMaxWidth().background(Color(0xFF101114)).padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PaneButton("UP") { FileRepo.parentOf(dir)?.let(onDirChange) }
            Text(
                dir.absolutePath,
                Modifier.weight(1f).padding(horizontal = 4.dp),
                color = Color(0xFFB8BCC2),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        BrowserGrid(
            dir = dir,
            onOpenDir = onDirChange,
            onOpenMedia = onPick,
        )
    }
}

fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
