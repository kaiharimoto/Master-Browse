package com.kai.masterbrowse.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Zoom/pan state for one media pane.
 * scale is relative to fit-to-screen: 1f == fitted, pixelPerfectScale == 1 media pixel : 1 screen pixel.
 */
class ZoomState {
    var scale by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)
    var containerSize by mutableStateOf(Size.Zero)

    /** Intrinsic pixel size of the current media (image bitmap or video frame size). */
    var contentPixels by mutableStateOf(Size.Zero)

    /** Size of the media when fitted inside the container, in screen px. */
    val fittedSize: Size
        get() {
            val cw = containerSize.width
            val ch = containerSize.height
            val iw = contentPixels.width
            val ih = contentPixels.height
            if (cw <= 0f || ch <= 0f || iw <= 0f || ih <= 0f) return Size.Zero
            val s = min(cw / iw, ch / ih)
            return Size(iw * s, ih * s)
        }

    val pixelPerfectScale: Float
        get() {
            val fw = fittedSize.width
            return if (fw > 0f) contentPixels.width / fw else 1f
        }

    val isPixelPerfect: Boolean
        get() = contentPixels.width > 0f && abs(scale - pixelPerfectScale) < 0.01f

    /** True when the displayed media is larger than the container in either axis (drag pans instead of paging). */
    val overflows: Boolean
        get() = fittedSize.width * scale > containerSize.width + 1f ||
            fittedSize.height * scale > containerSize.height + 1f

    fun applyGesture(centroid: Offset, pan: Offset, zoomChange: Float) {
        if (fittedSize == Size.Zero) return
        val minScale = min(1f, pixelPerfectScale)
        val maxScale = max(8f, pixelPerfectScale * 4f)
        val newScale = (scale * zoomChange).coerceIn(minScale, maxScale)
        val center = Offset(containerSize.width / 2f, containerSize.height / 2f)
        val c = centroid - center
        val newOffset = c - (c - offset) * (newScale / scale) + pan
        scale = newScale
        offset = clamp(newOffset)
    }

    fun togglePixelPerfect() {
        val pp = pixelPerfectScale
        if (pp <= 0f) return
        if (isPixelPerfect) {
            reset()
        } else {
            scale = pp
            offset = clamp(offset)
        }
    }

    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }

    private fun clamp(o: Offset): Offset {
        val fw = fittedSize.width * scale
        val fh = fittedSize.height * scale
        val mx = max(0f, (fw - containerSize.width) / 2f)
        val my = max(0f, (fh - containerSize.height) / 2f)
        return Offset(o.x.coerceIn(-mx, mx), o.y.coerceIn(-my, my))
    }
}

/**
 * Gestures for a media pane:
 * - tap: toggle controls
 * - double tap: previous (left half) / next (right half), instant
 * - pinch: zoom (about the pinch centroid)
 * - single-finger drag when zoomed in: pan
 * - single-finger horizontal drag when at fit: TikTok-style paging — [pageOffset] follows the
 *   finger, the neighbor peeks in, and on release it snaps to next/previous (past 25% width or a
 *   flick) or springs back. Drags already consumed by a child (the seek bar) are ignored, so
 *   scrubbing a video no longer also pages.
 * - press-and-hold (when [canHoldScrub]): [onHoldStart] with the pressed half (right = forward)
 *   until release → [onHoldEnd]. Movement, a second finger, or a child consuming the pointer
 *   cancels the hold and falls through to the gestures above.
 */
@Composable
fun Modifier.mediaGestures(
    zoom: ZoomState,
    pageOffset: Animatable<Float, AnimationVector1D>,
    scope: CoroutineScope,
    canPrev: () -> Boolean,
    canNext: () -> Boolean,
    onCommit: (forward: Boolean) -> Unit,
    onTap: () -> Unit,
    onDoubleTapNav: (forward: Boolean) -> Unit,
    canHoldScrub: () -> Boolean = { false },
    onHoldStart: (forward: Boolean) -> Unit = {},
    onHoldEnd: () -> Unit = {},
): Modifier {
    val tapCb = rememberUpdatedState(onTap)
    val doubleTapCb = rememberUpdatedState(onDoubleTapNav)
    val commitCb = rememberUpdatedState(onCommit)
    val canPrevCb = rememberUpdatedState(canPrev)
    val canNextCb = rememberUpdatedState(canNext)
    val canHoldCb = rememberUpdatedState(canHoldScrub)
    val holdStartCb = rememberUpdatedState(onHoldStart)
    val holdEndCb = rememberUpdatedState(onHoldEnd)
    return this
        .pointerInput(zoom) {
            detectTapGestures(
                onTap = { tapCb.value() },
                onDoubleTap = { pos ->
                    val w = zoom.containerSize.width
                    if (w > 0f) doubleTapCb.value(pos.x > w / 2f)
                },
            )
        }
        .pointerInput(zoom) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                // Hold-to-scrub arming window: the hold fires only if one finger stays within
                // touch slop, unconsumed, for the long-press timeout. Movement, a second finger,
                // release, or child consumption falls through to normal handling, seeding the
                // paging total with any sub-slop movement seen meanwhile. (This is hand-rolled
                // because awaitLongPressOrCancellation ignores movement, which turned every
                // slower-than-500ms swipe on a video into a hold.)
                var preTotal = Offset.Zero
                if (canHoldCb.value()) {
                    val timedOut = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.count { it.pressed } != 1) return@withTimeoutOrNull
                            if (event.changes.any { it.isConsumed }) return@withTimeoutOrNull
                            preTotal += event.calculatePan()
                            if (preTotal.getDistance() > viewConfiguration.touchSlop) return@withTimeoutOrNull
                        }
                    } == null
                    if (timedOut) {
                        holdStartCb.value(down.position.x > size.width / 2f)
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() } // suppress tap/paging
                                if (event.changes.none { it.pressed }) break
                            }
                        } finally {
                            holdEndCb.value()
                        }
                        return@awaitEachGesture
                    }
                    // Armed window ended with the pointer up: it was a tap — the tap detector
                    // above owns taps/double-taps.
                    if (currentEvent.changes.none { it.pressed }) return@awaitEachGesture
                }
                val startOffset = pageOffset.value
                var total = preTotal
                var pastSlop = false
                var pinched = false
                var paging = false
                var childConsumed = false
                val velocity = VelocityTracker()
                while (true) {
                    val event = awaitPointerEvent()
                    val pressed = event.changes.count { it.pressed }
                    if (pressed == 0) break
                    if (event.changes.any { it.isConsumed }) childConsumed = true
                    val zoomChange = event.calculateZoom()
                    val pan = event.calculatePan()
                    val centroid = event.calculateCentroid()
                    if (pressed > 1 || zoom.overflows) {
                        if (pressed > 1) pinched = true
                        if (centroid.isSpecified) zoom.applyGesture(centroid, pan, zoomChange)
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                        pastSlop = true
                        total = Offset.Zero
                    } else if (!childConsumed) {
                        total += pan
                        if (!pastSlop && total.getDistance() > viewConfiguration.touchSlop) {
                            pastSlop = true
                            paging = abs(total.x) >= abs(total.y) // horizontal-only paging
                        }
                        if (paging) {
                            val pt = event.changes.firstOrNull { it.pressed }
                            if (pt != null) velocity.addPosition(pt.uptimeMillis, pt.position)
                            val w = size.width.toFloat()
                            val minX = if (canNextCb.value()) -w else 0f
                            val maxX = if (canPrevCb.value()) w else 0f
                            // snapTo is suspend and this is a restricted pointer scope, so hop to the
                            // regular scope; the target is accumulated synchronously so order is safe.
                            val target = (startOffset + total.x).coerceIn(minX, maxX)
                            scope.launch { pageOffset.snapTo(target) }
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    }
                }
                if (!pinched && !zoom.overflows && paging && !childConsumed) {
                    val w = size.width.toFloat()
                    val vx = velocity.calculateVelocity().x
                    val off = pageOffset.value
                    val threshold = w * 0.25f
                    val fling = 1000f
                    val goNext = (off <= -threshold || vx <= -fling) && canNextCb.value()
                    val goPrev = (off >= threshold || vx >= fling) && canPrevCb.value()
                    scope.launch {
                        when {
                            goNext -> {
                                pageOffset.animateTo(-w, tween(180))
                                commitCb.value(true)
                                pageOffset.snapTo(0f)
                            }
                            goPrev -> {
                                pageOffset.animateTo(w, tween(180))
                                commitCb.value(false)
                                pageOffset.snapTo(0f)
                            }
                            else -> pageOffset.animateTo(0f, tween(180))
                        }
                    }
                }
            }
        }
}
