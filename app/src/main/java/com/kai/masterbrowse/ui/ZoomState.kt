package com.kai.masterbrowse.ui

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
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

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

    /** True when the displayed media is larger than the container in either axis (drag pans instead of swiping). */
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
 * - double tap: toggle fit / pixel-perfect
 * - pinch: zoom (about the pinch centroid)
 * - single-finger drag: pan when zoomed in, otherwise swipe (any direction) to next/previous media
 */
@Composable
fun Modifier.mediaGestures(
    zoom: ZoomState,
    onSwipe: (forward: Boolean) -> Unit,
    onTap: () -> Unit,
): Modifier {
    val swipeCb = rememberUpdatedState(onSwipe)
    val tapCb = rememberUpdatedState(onTap)
    return this
        .pointerInput(zoom) {
            detectTapGestures(
                onTap = { tapCb.value() },
                onDoubleTap = { zoom.togglePixelPerfect() },
            )
        }
        .pointerInput(zoom) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                var total = Offset.Zero
                var pastSlop = false
                var pinched = false
                while (true) {
                    val event = awaitPointerEvent()
                    val pressed = event.changes.count { it.pressed }
                    if (pressed == 0) break
                    val zoomChange = event.calculateZoom()
                    val pan = event.calculatePan()
                    val centroid = event.calculateCentroid()
                    if (pressed > 1 || zoom.overflows) {
                        if (pressed > 1) pinched = true
                        if (centroid.isSpecified) zoom.applyGesture(centroid, pan, zoomChange)
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                        pastSlop = true
                        total = Offset.Zero
                    } else {
                        total += pan
                        if (!pastSlop && total.getDistance() > viewConfiguration.touchSlop) pastSlop = true
                        if (pastSlop) event.changes.forEach { if (it.positionChanged()) it.consume() }
                    }
                }
                if (!pinched && !zoom.overflows && pastSlop) {
                    val threshold = 80.dp.toPx()
                    val d = if (abs(total.x) >= abs(total.y)) total.x else total.y
                    if (d <= -threshold) swipeCb.value(true)
                    else if (d >= threshold) swipeCb.value(false)
                }
            }
        }
}
