package com.kai.masterbrowse.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import com.kai.masterbrowse.overlay.Handoff

/**
 * Which container the app's composables are running in. The same tree serves both the
 * fullscreen activity and the floating overlay window; this says which, and carries the
 * host-level actions the UI can offer.
 *
 * Carried as a [staticCompositionLocalOf] rather than a parameter because it has to reach
 * [TileContent] and [PaneButton], several levels down and across many call sites, and it
 * never changes within one tree.
 */
@Immutable
data class AppHost(
    val floating: Boolean = false,
    /** Non-null in the activity: pops the current session out into the floating window. */
    val onPopOut: ((Handoff) -> Unit)? = null,
    /** Non-null in the overlay: brings the fullscreen app back. */
    val onExpand: (() -> Unit)? = null,
    /** Non-null in the overlay: closes the floating window. */
    val onDismiss: (() -> Unit)? = null,
)

val LocalAppHost = staticCompositionLocalOf { AppHost() }
