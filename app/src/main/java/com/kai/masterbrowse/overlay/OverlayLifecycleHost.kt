package com.kai.masterbrowse.overlay

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/**
 * Stands in for an Activity as the owner of everything a Compose tree expects to find by
 * walking up the view hierarchy, so a ComposeView can live in a window owned by a Service.
 *
 * All four are needed: AndroidComposeView refuses to attach without a LifecycleOwner and a
 * SavedStateRegistryOwner, BackHandler (used by both BrowserScreen and ViewerScreen) needs
 * an OnBackPressedDispatcherOwner, and without a ViewModelStoreOwner LocalViewModelStoreOwner
 * is null.
 *
 * @param onUnhandledBack runs when back is pressed and no BackHandler is enabled — the same
 *   point at which the fullscreen app would exit, so the floating window closes.
 */
class OverlayLifecycleHost(
    onUnhandledBack: Runnable,
) : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner, OnBackPressedDispatcherOwner {

    private val registry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = registry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    override val onBackPressedDispatcher = OnBackPressedDispatcher(onUnhandledBack)

    /** Attach and restore must both happen while the lifecycle is still INITIALIZED. */
    fun create() {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
    }

    /** The composition's Recomposer stays paused below RESUMED, which freezes the UI. */
    fun resume() {
        registry.currentState = Lifecycle.State.RESUMED
    }

    /**
     * Call *after* the view has been removed from the window. Detaching disposes the
     * composition, which is what releases the ExoPlayer; destroying the lifecycle first
     * cancels the Recomposer and the dispose effects may never run.
     */
    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
        store.clear()
    }
}
