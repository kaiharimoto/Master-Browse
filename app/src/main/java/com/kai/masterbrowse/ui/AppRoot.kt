package com.kai.masterbrowse.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import java.io.File

@Composable
fun AppRoot() {
    var hasAccess by remember { mutableStateOf(Environment.isExternalStorageManager()) }
    LifecycleResumeEffect(Unit) {
        hasAccess = Environment.isExternalStorageManager()
        onPauseOrDispose { }
    }
    if (!hasAccess) {
        PermissionScreen()
        return
    }

    var homePath by remember { mutableStateOf(Prefs.homeFolder) }
    val home = remember(homePath) {
        homePath?.let(::File)?.takeIf { it.isDirectory } ?: FileRepo.defaultHome()
    }
    var dir by remember { mutableStateOf(home) }
    var viewer by remember { mutableStateOf<Triple<List<File>, Int, File>?>(null) }

    val v = viewer
    if (v == null) {
        BrowserScreen(
            dir = dir,
            home = home,
            onDirChange = { dir = it },
            onOpenMedia = { list, i -> viewer = Triple(list, i, dir) },
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
        )
    }
}

@Composable
private fun PermissionScreen() {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().padding(32.dp),
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
