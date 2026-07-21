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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.kai.masterbrowse.FileRepo
import java.io.File

/**
 * Fullscreen viewer. Shows one media pane, or two side by side in split mode.
 * In split mode the right half starts as a tile picker to choose the second item.
 */
@Composable
fun ViewerScreen(
    items: List<File>,
    startIndex: Int,
    browseStart: File,
    onClose: () -> Unit,
) {
    var split by remember { mutableStateOf(false) }
    var secondary by remember { mutableStateOf<Pair<List<File>, Int>?>(null) }
    var pickerDir by remember { mutableStateOf(browseStart) }

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
        ) {
            PaneButton(if (split) "SINGLE" else "SPLIT") {
                split = !split
                if (!split) secondary = null
            }
            PaneButton("CLOSE") { onClose() }
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
            tileMinSize = 110.dp,
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
