package com.kai.masterbrowse.overlay

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/**
 * A browse session being moved between the fullscreen activity and the floating window:
 * the folder being browsed, the media list currently open (empty when only the tile grid
 * is showing), and which item of it is on screen.
 */
@Immutable
data class Handoff(
    val browseDir: File,
    val items: List<File> = emptyList(),
    val index: Int = 0,
)

/**
 * Hand-off between MainActivity and OverlayService. They run in the same process (one
 * <application>, no android:process), so this passes the live [List]<[File]> by reference
 * — no serialization, and no risk of blowing the Binder transaction limit on a folder
 * with thousands of files.
 */
object PopOutBus {
    /** Session the service should open with. Consumed by OverlayService.onStartCommand. */
    var toOverlay: Handoff? = null

    /** Session MainActivity should restore. Snapshot state so AppRoot recomposes on it. */
    var toActivity by mutableStateOf<Handoff?>(null)
}
