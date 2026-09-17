package com.kai.masterbrowse.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.kai.masterbrowse.FileRepo
import com.kai.masterbrowse.Prefs
import com.kai.masterbrowse.overlay.Handoff
import com.kai.masterbrowse.overlay.PopOutBus
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The whole app: browse a folder, or view media from it. Hosted twice — fullscreen by
 * MainActivity and, at a smaller scale, by the floating overlay window — with
 * [LocalAppHost] saying which.
 *
 * @param initial a session handed over from the other host, opened immediately.
 * @param onSessionChange reports what is currently on screen, so the host can hand it
 *   back the other way (POP OUT from the activity, EXPAND from the floating window).
 * @param onMediaAspect width/height of the item being viewed, which the floating window
 *   uses to reshape itself around the media.
 */
@Composable
fun AppRoot(
    initial: Handoff? = null,
    onSessionChange: (Handoff) -> Unit = {},
    onMediaAspect: (Float) -> Unit = {},
) {
    val host = LocalAppHost.current
    var hasAccess by remember { mutableStateOf(Environment.isExternalStorageManager()) }
    LifecycleResumeEffect(Unit) {
        hasAccess = Environment.isExternalStorageManager()
        onPauseOrDispose { }
    }
    if (!hasAccess) {
        // There is no sane way to run a system settings flow from the floating window,
        // so bow out and let the user grant access in the fullscreen app.
        if (host.floating) {
            LaunchedEffect(Unit) { host.onDismiss?.invoke() }
        } else {
            PermissionScreen()
        }
        return
    }

    val purgeContext = LocalContext.current.applicationContext
    if (!host.floating) {
        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) { FileRepo.purgeTrash(purgeContext) }
        }
    }

    var homePath by remember { mutableStateOf(Prefs.homeFolder) }
    val home = remember(homePath) {
        homePath?.let(::File)?.takeIf { it.isDirectory } ?: FileRepo.defaultHome()
    }
    var dir by remember { mutableStateOf(initial?.browseDir?.takeIf { it.isDirectory } ?: home) }
    var viewer by remember {
        mutableStateOf(
            initial?.takeIf { it.items.isNotEmpty() }
                ?.let { Triple(it.items, it.index, it.browseDir) }
        )
    }
    var liveIndex by remember { mutableIntStateOf(initial?.index ?: 0) }

    // Restore a session handed back by the floating window. Snapshot state, so this fires
    // whether the activity's composition survived in the background or was recreated.
    if (!host.floating) {
        val restore = PopOutBus.toActivity
        LaunchedEffect(restore) {
            if (restore != null) {
                dir = restore.browseDir
                viewer = restore.items
                    .takeIf { it.isNotEmpty() }
                    ?.let { Triple(it, restore.index, restore.browseDir) }
                liveIndex = restore.index
                PopOutBus.toActivity = null
            }
        }
    }

    val v = viewer
    LaunchedEffect(dir, v, liveIndex) {
        onSessionChange(
            if (v == null) Handoff(dir) else Handoff(v.third, v.first, liveIndex)
        )
    }

    if (v == null) {
        BrowserScreen(
            dir = dir,
            home = home,
            onDirChange = { dir = it },
            onOpenMedia = { list, i ->
                liveIndex = i
                viewer = Triple(list, i, dir)
            },
            onSetHome = {
                Prefs.homeFolder = it.absolutePath
                homePath = it.absolutePath
            },
        )
    } else {
        ViewerScreen(
            items = v.first,
            startIndex = v.second,
            browseStart = v.third,
            onClose = { viewer = null },
            onIndexChange = { liveIndex = it },
            onMediaSizeChange = { size -> onMediaAspect(size.width / size.height) },
        )
    }
}

@Composable
private fun PermissionScreen() {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Master Browse needs \"All files access\" to browse every folder, " +
                "including hidden folders and ones containing .nomedia files.",
            color = Color.White,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        PaneButton("GRANT ALL FILES ACCESS") {
            val specific = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            )
            try {
                context.startActivity(specific)
            } catch (e: ActivityNotFoundException) {
                context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        }
    }
}
