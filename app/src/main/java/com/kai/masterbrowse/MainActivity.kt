package com.kai.masterbrowse

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import com.kai.masterbrowse.overlay.Handoff
import com.kai.masterbrowse.overlay.OverlayService
import com.kai.masterbrowse.overlay.PopOutBus
import com.kai.masterbrowse.ui.AppHost
import com.kai.masterbrowse.ui.AppRoot
import com.kai.masterbrowse.ui.LocalAppHost
import com.kai.masterbrowse.ui.MasterBrowseTheme

class MainActivity : ComponentActivity() {

    companion object {
        /** Sent by OverlayService when the mini window is expanded back to fullscreen. */
        const val ACTION_RESTORE = "com.kai.masterbrowse.RESTORE"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var overlayRationale by remember { mutableStateOf(false) }
            val notificationPermission = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { /* The floating window runs either way; this only makes it visible in the shade. */ }

            val host = remember {
                AppHost(
                    floating = false,
                    onPopOut = { handoff ->
                        if (!Settings.canDrawOverlays(this)) {
                            overlayRationale = true
                        } else {
                            askForNotifications(notificationPermission::launch)
                            popOut(handoff)
                        }
                    },
                )
            }

            MasterBrowseTheme {
                Surface(Modifier.fillMaxSize(), color = Color.Black) {
                    CompositionLocalProvider(LocalAppHost provides host) {
                        AppRoot()
                    }
                }

                if (overlayRationale) {
                    AlertDialog(
                        onDismissRequest = { overlayRationale = false },
                        title = { Text("Pop out") },
                        text = {
                            Text(
                                "To float a mini Master Browse on top of other apps, Android " +
                                    "needs the \"Display over other apps\" permission for this app."
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                overlayRationale = false
                                requestOverlayPermission()
                            }) { Text("GRANT") }
                        },
                        dismissButton = {
                            TextButton(onClick = { overlayRationale = false }) { Text("CANCEL") }
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // The restored session itself travels through PopOutBus, which AppRoot observes;
        // this only keeps getIntent() honest.
        setIntent(intent)
    }

    /**
     * Hands [handoff] to the floating window and steps out of the way, so the user lands
     * back on whatever was behind the app with the mini window floating over it.
     */
    private fun popOut(handoff: Handoff) {
        PopOutBus.toOverlay = handoff
        ContextCompat.startForegroundService(
            this,
            Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_SHOW),
        )
        moveTaskToBack(true)
    }

    private fun askForNotifications(launch: (String) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
        if (granted != PackageManager.PERMISSION_GRANTED) launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun requestOverlayPermission() {
        val specific = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName"),
        )
        try {
            startActivity(specific)
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
        }
    }
}
