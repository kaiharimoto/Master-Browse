package com.kai.masterbrowse.overlay

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentCallbacks
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.IBinder
import android.view.ContextThemeWrapper
import android.view.Display
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.Density
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.kai.masterbrowse.MainActivity
import com.kai.masterbrowse.Prefs
import com.kai.masterbrowse.R
import com.kai.masterbrowse.ui.AppHost
import com.kai.masterbrowse.ui.AppRoot
import com.kai.masterbrowse.ui.FLOATING_TITLE_BAR_DP
import com.kai.masterbrowse.ui.FloatingShell
import com.kai.masterbrowse.ui.LocalAppHost
import com.kai.masterbrowse.ui.MasterBrowseTheme
import com.kai.masterbrowse.ui.MenuController
import com.kai.masterbrowse.ui.MenuHostBox
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Hosts the floating mini app: a WindowManager window of type TYPE_APPLICATION_OVERLAY
 * containing the same Compose tree the activity runs, at a smaller scale.
 *
 * A foreground service rather than a bare window, because the window outlives the
 * activity's visible lifetime by design — the whole point is to keep browsing and playing
 * video while another app is in front.
 */
class OverlayService : Service() {

    companion object {
        const val ACTION_SHOW = "com.kai.masterbrowse.overlay.SHOW"
        const val ACTION_STOP = "com.kai.masterbrowse.overlay.STOP"
        const val ACTION_EXPAND = "com.kai.masterbrowse.overlay.EXPAND"
        const val CHANNEL_ID = "floating_window"
        private const val NOTIF_ID = 1837

        /** Content scale inside the mini window; everything in dp/sp shrinks with it. */
        private const val DENSITY_SCALE = 0.8f
    }

    private lateinit var windowContext: Context
    private lateinit var themed: Context
    private lateinit var wm: WindowManager

    private var root: OverlayRootView? = null
    private var host: OverlayLifecycleHost? = null
    private var params: WindowManager.LayoutParams? = null
    private var windowFocusable = false

    private var geom = Geom(0, 0, 0, 0)
    private var gestureStart = geom

    /**
     * How much room the media area should take up, in px². Set from the window size the
     * user last chose, so reshaping to a new aspect ratio keeps the footprint they picked
     * instead of drifting bigger or smaller with every item.
     */
    private var contentAreaBudget = 0L

    /** What the mini window is showing right now, handed back to the activity on EXPAND. */
    private var liveSession: Handoff? = null

    private val configCallbacks = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
            // A rotation can leave a window that fitted in landscape hanging off a
            // portrait screen.
            applyGeometry(clampToScreen(geom), persist = true)
        }

        override fun onLowMemory() {}
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val display = getSystemService(DisplayManager::class.java)
            .getDisplay(Display.DEFAULT_DISPLAY)
        // A Service is a non-visual context: its WindowManager has no valid window
        // metrics. A window context does, and it also propagates configuration changes
        // into the composition.
        windowContext = createDisplayContext(display)
            .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        wm = windowContext.getSystemService(WindowManager::class.java)
        themed = ContextThemeWrapper(windowContext, R.style.Theme_MasterBrowse)
        windowContext.registerComponentCallbacks(configCallbacks)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopOverlay()
                return START_NOT_STICKY
            }
            ACTION_EXPAND -> {
                expand()
                return START_NOT_STICKY
            }
        }

        startForegroundNotification()
        val handoff = PopOutBus.toOverlay
        PopOutBus.toOverlay = null
        // A second pop-out while the window is already up re-seeds it with the new
        // session rather than stacking another window on top.
        if (root != null && handoff != null) removeWindow()
        if (root == null) {
            if (!addWindow(handoff ?: liveSession)) return START_NOT_STICKY
        }
        // START_NOT_STICKY: the session lives in-process, so a system-restarted service
        // with a null intent would have nothing to show.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        windowContext.unregisterComponentCallbacks(configCallbacks)
        removeWindow()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- window

    private fun screenBounds() = wm.currentWindowMetrics.bounds

    private fun minW() = (OverlayGeometry.MIN_W_DP * resources.displayMetrics.density).toInt()

    private fun minH() = (OverlayGeometry.MIN_H_DP * resources.displayMetrics.density).toInt()

    private fun clampToScreen(g: Geom): Geom {
        val b = screenBounds()
        return OverlayGeometry.clamp(g, b.width(), b.height(), minW(), minH())
    }

    private fun loadGeometry(): Geom {
        val b = screenBounds()
        val saved = Geom(Prefs.floatX, Prefs.floatY, Prefs.floatW, Prefs.floatH)
        return if (saved.w <= 0 || saved.h <= 0 || saved.x < 0 || saved.y < 0) {
            OverlayGeometry.default(b.width(), b.height(), minW(), minH())
        } else {
            clampToScreen(saved)
        }
    }

    /** Height of the title bar in real screen px (the shell measures it at [DENSITY_SCALE]). */
    private fun chromeHeight(): Int =
        (FLOATING_TITLE_BAR_DP * resources.displayMetrics.density * DENSITY_SCALE).toInt()

    private fun contentHeight(g: Geom) = (g.h - chromeHeight()).coerceAtLeast(1)

    /** Called only when the user sizes the window themselves, never from an auto-fit. */
    private fun rememberSizeBudget() {
        contentAreaBudget = geom.w.toLong() * contentHeight(geom)
        Prefs.floatContentArea = contentAreaBudget
    }

    /**
     * Reshapes the window so the media fills it instead of sitting in letterbox bars:
     * the media area is given [aspect], at whatever area the user last sized the window
     * to, centred where the window already is.
     */
    private fun fitToMedia(aspect: Float) {
        if (!aspect.isFinite() || aspect <= 0f) return
        if (root == null) return
        val chrome = chromeHeight()
        val current = geom.w.toFloat() / contentHeight(geom)
        // Already the right shape — this also makes the provisional size a video reports
        // before its first frame decodes a no-op.
        if (abs(current - aspect) / aspect < 0.02f) return
        if (contentAreaBudget <= 0L) contentAreaBudget = geom.w.toLong() * contentHeight(geom)
        val h = sqrt(contentAreaBudget.toDouble() / aspect)
        val w = h * aspect
        val bounds = screenBounds()
        applyGeometry(
            OverlayGeometry.centeredResize(
                geom, w.toInt(), h.toInt() + chrome,
                bounds.width(), bounds.height(), minW(), minH(),
            ),
            persist = true,
        )
    }

    private fun persistGeometry() {
        Prefs.floatX = geom.x
        Prefs.floatY = geom.y
        Prefs.floatW = geom.w
        Prefs.floatH = geom.h
    }

    private fun applyGeometry(g: Geom, persist: Boolean) {
        geom = g
        val p = params ?: return
        p.x = g.x
        p.y = g.y
        p.width = g.w
        p.height = g.h
        root?.let { runCatching { wm.updateViewLayout(it, p) } }
        if (persist) persistGeometry()
    }

    private fun addWindow(handoff: Handoff?): Boolean {
        geom = loadGeometry()
        val lifecycleHost = OverlayLifecycleHost(onUnhandledBack = Runnable { stopOverlay() })
        lifecycleHost.create()

        val rootView = OverlayRootView(themed, lifecycleHost, ::setWindowFocusable)
        rootView.setViewTreeLifecycleOwner(lifecycleHost)
        rootView.setViewTreeViewModelStoreOwner(lifecycleHost)
        rootView.setViewTreeSavedStateRegistryOwner(lifecycleHost)
        rootView.setViewTreeOnBackPressedDispatcherOwner(lifecycleHost)

        val compose = ComposeView(themed).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent { OverlayContent(handoff) }
        }
        rootView.addView(
            compose,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        val p = WindowManager.LayoutParams(
            geom.w,
            geom.h,
            geom.x,
            geom.y,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            baseFlags() or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }

        return try {
            wm.addView(rootView, p)
            root = rootView
            host = lifecycleHost
            params = p
            windowFocusable = false
            contentAreaBudget = Prefs.floatContentArea
                .takeIf { it > 0L } ?: (geom.w.toLong() * contentHeight(geom))
            // Below RESUMED the Recomposer stays paused and the window never draws.
            lifecycleHost.resume()
            true
        } catch (e: Exception) {
            // Revoking "display over other apps" while we run lands here.
            lifecycleHost.destroy()
            stopSelf()
            false
        }
    }

    private fun removeWindow() {
        val r = root ?: return
        root = null
        persistGeometry()
        // Remove first: detaching disposes the composition, which releases the ExoPlayer.
        // Destroying the lifecycle first would cancel the Recomposer and leak a player
        // that keeps playing audio with no window.
        runCatching { wm.removeViewImmediate(r) }
        host?.destroy()
        host = null
        params = null
    }

    /** Teardown posted off the current dispatch — removing the window from inside a touch
     *  or composition callback would pull the view out from under its own event. */
    private fun requestStop() {
        val r = root
        if (r == null) stopOverlay() else r.post { stopOverlay() }
    }

    private fun requestExpand() {
        val r = root
        if (r == null) expand() else r.post { expand() }
    }

    private fun stopOverlay() {
        removeWindow()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun expand() {
        PopOutBus.toActivity = liveSession
        runCatching {
            // Allowed from the background because we hold SYSTEM_ALERT_WINDOW with a
            // visible overlay. No CLEAR_TOP: it can recreate the activity and throw away
            // the composition we are restoring into.
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    action = MainActivity.ACTION_RESTORE
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
            )
        }
        stopOverlay()
    }

    /**
     * A permanently focusable overlay swallows the keyboard from the app behind it; a
     * permanently unfocusable one never sees the arrow/J/K keys or BACK. So take focus
     * while the user is touching the window and give it back when they touch elsewhere.
     */
    private fun setWindowFocusable(focusable: Boolean) {
        if (windowFocusable == focusable) return
        val p = params ?: return
        val r = root ?: return
        windowFocusable = focusable
        p.flags = if (focusable) {
            // ALT_FOCUSABLE_IM: take key focus but leave the IME to the app behind, so
            // popping out over a chat app doesn't dismiss its keyboard.
            baseFlags() or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
        } else {
            baseFlags() or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        runCatching { wm.updateViewLayout(r, p) }
        if (focusable) r.requestFocus()
    }

    private fun baseFlags(): Int =
        // NOT_TOUCH_MODAL: the app behind stays usable once we are focusable.
        // HARDWARE_ACCELERATED: mandatory — a WindowManager window from a service is
        // software-rendered by default, and the video's TextureView draws nothing then.
        // WATCH_OUTSIDE_TOUCH: tells us when to drop focus.
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED

    // ---------------------------------------------------------------- content

    @Composable
    private fun OverlayContent(initial: Handoff?) {
        val menuController = remember { MenuController() }
        val appHost = remember {
            AppHost(
                floating = true,
                onExpand = ::requestExpand,
                onDismiss = ::requestStop,
            )
        }
        val title = remember { mutableStateOf(initial?.browseDir?.name ?: "Master Browse") }
        MasterBrowseTheme {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalAppHost provides appHost,
                LocalDensity provides Density(base.density * DENSITY_SCALE, base.fontScale),
            ) {
                MenuHostBox(menuController) {
                    FloatingShell(
                        title = title.value,
                        onMoveStart = { gestureStart = geom },
                        onMoveBy = { dx, dy ->
                            applyGeometry(
                                OverlayGeometry.moved(
                                    gestureStart, dx.toInt(), dy.toInt(),
                                    screenBounds().width(), screenBounds().height(),
                                    minW(), minH(),
                                ),
                                persist = false,
                            )
                        },
                        onMoveEnd = ::persistGeometry,
                        onResizeStart = { gestureStart = geom },
                        onResizeBy = { dx, dy ->
                            applyGeometry(
                                OverlayGeometry.resized(
                                    gestureStart, dx.toInt(), dy.toInt(),
                                    screenBounds().width(), screenBounds().height(),
                                    minW(), minH(),
                                ),
                                persist = false,
                            )
                        },
                        onResizeEnd = {
                            persistGeometry()
                            rememberSizeBudget()
                        },
                        onExpand = ::requestExpand,
                        onClose = ::requestStop,
                    ) {
                        AppRoot(
                            onMediaAspect = ::fitToMedia,
                            initial = initial,
                            onSessionChange = { session ->
                                liveSession = session
                                title.value = session.items.getOrNull(session.index)?.name
                                    ?: session.browseDir.name.ifEmpty { session.browseDir.absolutePath }
                            },
                        )
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- notification

    private fun startForegroundNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_float)
            .setContentTitle("Master Browse")
            .setContentText("Floating window")
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(servicePendingIntent(ACTION_EXPAND, 1))
            .addAction(0, "EXPAND", servicePendingIntent(ACTION_EXPAND, 1))
            .addAction(0, "CLOSE", servicePendingIntent(ACTION_STOP, 2))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
        ServiceCompat.startForeground(
            this,
            NOTIF_ID,
            notification,
            // Hosting a window is "special use"; it also genuinely plays video.
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, OverlayService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}

/**
 * Root of the overlay window. Exists to watch raw touch and key events, which is the only
 * place focus handover and the BACK key can be intercepted.
 */
@SuppressLint("ViewConstructor") // never inflated from XML
private class OverlayRootView(
    context: Context,
    private val host: OverlayLifecycleHost,
    private val onFocusWanted: (Boolean) -> Unit,
) : FrameLayout(context) {

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        // Without this the ComposeView never takes view focus and MediaPane's key handling
        // stays dead even when the window itself is focused.
        descendantFocusability = FOCUS_AFTER_DESCENDANTS
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_OUTSIDE -> {
                onFocusWanted(false)
                return true
            }
            MotionEvent.ACTION_DOWN -> onFocusWanted(true)
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
                host.onBackPressedDispatcher.onBackPressed()
            }
            return true
        }
        // Arrow keys and J/K flow on into Compose.
        return super.dispatchKeyEvent(event)
    }
}
