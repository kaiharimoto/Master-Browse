package com.kai.masterbrowse.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayGeometryTest {

    private val minW = 260
    private val minH = 180

    private fun clamp(g: Geom, w: Int = 2000, h: Int = 1200) =
        OverlayGeometry.clamp(g, w, h, minW, minH)

    @Test
    fun `clamp keeps the window fully on screen`() {
        val g = clamp(Geom(x = 1900, y = 1150, w = 600, h = 400))
        assertEquals(2000, g.x + g.w)
        assertEquals(1200, g.y + g.h)
    }

    @Test
    fun `clamp pulls a too-wide window back in after a rotation`() {
        val landscape = clamp(Geom(x = 1200, y = 100, w = 700, h = 500))
        // Same window, now on a portrait screen that is narrower than it is.
        val portrait = OverlayGeometry.clamp(landscape, 1200, 2000, minW, minH)
        assertTrue(portrait.w <= 1200)
        assertTrue(portrait.x >= 0 && portrait.x + portrait.w <= 1200)
        assertTrue(portrait.y >= 0 && portrait.y + portrait.h <= 2000)
    }

    @Test
    fun `resize cannot go below the minimum size`() {
        val start = Geom(x = 100, y = 100, w = 400, h = 300)
        val g = OverlayGeometry.resized(start, -5000, -5000, 2000, 1200, minW, minH)
        assertEquals(minW, g.w)
        assertEquals(minH, g.h)
    }

    @Test
    fun `resize cannot grow past the screen`() {
        val start = Geom(x = 0, y = 0, w = 400, h = 300)
        val g = OverlayGeometry.resized(start, 5000, 5000, 2000, 1200, minW, minH)
        assertEquals(2000, g.w)
        assertEquals(1200, g.h)
    }

    @Test
    fun `drag cannot push the window off the top-left`() {
        val start = Geom(x = 40, y = 40, w = 400, h = 300)
        val g = OverlayGeometry.moved(start, -5000, -5000, 2000, 1200, minW, minH)
        assertEquals(0, g.x)
        assertEquals(0, g.y)
    }

    @Test
    fun `centered resize keeps the window centre put`() {
        val start = Geom(x = 800, y = 400, w = 400, h = 300)
        val g = OverlayGeometry.centeredResize(start, 600, 200, 2000, 1200, minW, minH)
        assertEquals(start.x + start.w / 2, g.x + g.w / 2)
        assertEquals(start.y + start.h / 2, g.y + g.h / 2)
    }

    @Test
    fun `centered resize near an edge stays on screen`() {
        val start = Geom(x = 1700, y = 20, w = 280, h = 200)
        val g = OverlayGeometry.centeredResize(start, 900, 700, 2000, 1200, minW, minH)
        assertTrue(g.x >= 0 && g.x + g.w <= 2000)
        assertTrue(g.y >= 0 && g.y + g.h <= 1200)
        assertEquals(900, g.w)
        assertEquals(700, g.h)
    }

    @Test
    fun `default placement fits even on a small screen`() {
        val g = OverlayGeometry.default(480, 320, minW, minH)
        assertTrue(g.x >= 0 && g.y >= 0)
        assertTrue(g.x + g.w <= 480)
        assertTrue(g.y + g.h <= 320)
    }

    @Test
    fun `default placement sits in the top-right`() {
        val g = OverlayGeometry.default(2000, 1200, minW, minH)
        assertTrue(g.x > 1000)
        assertTrue(g.y < 200)
    }
}
