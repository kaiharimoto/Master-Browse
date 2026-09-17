package com.kai.masterbrowse.overlay

/** Position and size of the floating window, in raw screen pixels. */
data class Geom(val x: Int, val y: Int, val w: Int, val h: Int)

/**
 * Pure geometry for the floating window: where it starts, how dragging and resizing move
 * it, and how it is kept on screen after a rotation or a change of display size.
 *
 * Deliberately free of Android imports so it can be unit-tested on the JVM.
 */
object OverlayGeometry {
    const val MIN_W_DP = 260
    const val MIN_H_DP = 180

    /** Keeps [g] at least [minW] x [minH], no bigger than the screen, and fully on it. */
    fun clamp(g: Geom, screenW: Int, screenH: Int, minW: Int, minH: Int): Geom {
        val w = g.w.coerceIn(minW.coerceAtMost(screenW), screenW.coerceAtLeast(1))
        val h = g.h.coerceIn(minH.coerceAtMost(screenH), screenH.coerceAtLeast(1))
        return Geom(
            x = g.x.coerceIn(0, (screenW - w).coerceAtLeast(0)),
            y = g.y.coerceIn(0, (screenH - h).coerceAtLeast(0)),
            w = w,
            h = h,
        )
    }

    /** First-run placement: a readable slab in the top-right corner. */
    fun default(screenW: Int, screenH: Int, minW: Int, minH: Int): Geom {
        val w = (screenW * 0.42f).toInt()
        val h = (screenH * 0.42f).toInt()
        val margin = (screenW * 0.03f).toInt()
        return clamp(Geom(screenW - w - margin, margin, w, h), screenW, screenH, minW, minH)
    }

    /** [start] dragged by a screen-space delta. */
    fun moved(start: Geom, dx: Int, dy: Int, screenW: Int, screenH: Int, minW: Int, minH: Int): Geom =
        clamp(start.copy(x = start.x + dx, y = start.y + dy), screenW, screenH, minW, minH)

    /**
     * [start] resized to [w] x [h] about its own centre, so the window grows and shrinks
     * in place instead of walking across the screen as the media changes shape.
     */
    fun centeredResize(
        start: Geom,
        w: Int,
        h: Int,
        screenW: Int,
        screenH: Int,
        minW: Int,
        minH: Int,
    ): Geom {
        val centreX = start.x + start.w / 2
        val centreY = start.y + start.h / 2
        return clamp(Geom(centreX - w / 2, centreY - h / 2, w, h), screenW, screenH, minW, minH)
    }

    /** [start] resized from its bottom-right corner by a screen-space delta. */
    fun resized(start: Geom, dx: Int, dy: Int, screenW: Int, screenH: Int, minW: Int, minH: Int): Geom =
        clamp(start.copy(w = start.w + dx, h = start.h + dy), screenW, screenH, minW, minH)
}
