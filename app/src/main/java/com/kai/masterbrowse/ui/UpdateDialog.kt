package com.kai.masterbrowse.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.kai.masterbrowse.BuildConfig
import com.kai.masterbrowse.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface UpdateState {
    data object Checking : UpdateState
    data class Found(val release: Updater.Release) : UpdateState
    data class Downloading(val percent: Int) : UpdateState
    data class Error(val message: String) : UpdateState
}

@Composable
fun UpdateDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var state by remember { mutableStateOf<UpdateState>(UpdateState.Checking) }
    var downloadUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        state = try {
            UpdateState.Found(withContext(Dispatchers.IO) { Updater.fetchLatest() })
        } catch (e: Exception) {
            UpdateState.Error(e.message ?: "Unknown error")
        }
    }

    LaunchedEffect(downloadUrl) {
        val url = downloadUrl ?: return@LaunchedEffect
        state = UpdateState.Downloading(0)
        try {
            val file = withContext(Dispatchers.IO) {
                Updater.downloadApk(context, url) { p -> state = UpdateState.Downloading(p) }
            }
            Updater.installApk(context, file)
            onDismiss()
        } catch (e: Exception) {
            state = UpdateState.Error(e.message ?: "Download failed")
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update from GitHub") },
        text = {
            when (val s = state) {
                is UpdateState.Checking -> Text("Checking latest release…")
                is UpdateState.Found -> Text(
                    "Installed: v${BuildConfig.VERSION_NAME}\nLatest: ${s.release.tag}" +
                        if (s.release.apkUrl == null) "\n\nNo APK asset found on the latest release." else ""
                )
                is UpdateState.Downloading -> Text("Downloading… ${s.percent}%")
                is UpdateState.Error -> Text("Error: ${s.message}")
            }
        },
        confirmButton = {
            val s = state
            if (s is UpdateState.Found && s.release.apkUrl != null) {
                TextButton(onClick = { downloadUrl = s.release.apkUrl }) { Text("DOWNLOAD & INSTALL") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CLOSE") } },
    )
}
