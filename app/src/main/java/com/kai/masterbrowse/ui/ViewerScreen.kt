package com.kai.masterbrowse.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.kai.masterbrowse.FileRepo
import com.kai.masterbrowse.overlay.Handoff
import java.io.File

/**
 * Fullscreen viewer. Shows one media pane, or two side by side in split mode.
 * In split mode the right half starts as a tile picker to choose the second item.
 *
 * Split works in the floating window too; the window widens to hold both panes.
 * [onIndexChange] keeps the host told which item is on screen so POP OUT / EXPAND can
 * carry it across, and [onMediaSizeChange] reports the shape the host should give the
 * window — the primary item's, widened by the number of panes on screen.
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

    Row(Modifier.fillMaxSize().background(Color.Black)) {
        MediaPane(
            items = items,
            startIndex = startIndex,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            onIndexChange = {
                currentIndex = it
                onIndexChange(it)
            },
            // Only the primary pane drives the window shape.
            onMediaSizeChange = { primarySize = it },
        ) {
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
