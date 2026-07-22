package com.kai.masterbrowse.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PackRowsTest {

    @Test
    fun emptyInputProducesNoRows() {
        assertTrue(packRows(emptyList(), 300f, 100f, 0f).isEmpty())
    }

    @Test
    fun exactlyFillingItemsFormOneRowAtTargetHeight() {
        // Three 1:1 items at H=100 are 300 wide, exactly the container.
        val rows = packRows(listOf(1f, 1f, 1f), 300f, 100f, 0f)
        assertEquals(1, rows.size)
        assertEquals(listOf(0, 1, 2), rows[0].indices)
        assertEquals(100f, rows[0].height, 0.01f)
    }

    @Test
    fun overflowingRowIsScaledDownToFit() {
        // 1:1 items, W=250, H=100: the 3rd item pushes natural width to 300 >= 250,
        // so row [0,1,2] is scaled to height 250/3; item 3 is the partial last row.
        val rows = packRows(listOf(1f, 1f, 1f, 1f), 250f, 100f, 0f)
        assertEquals(2, rows.size)
        assertEquals(listOf(0, 1, 2), rows[0].indices)
        assertEquals(250f / 3f, rows[0].height, 0.01f)
        assertEquals(listOf(3), rows[1].indices)
        assertEquals(100f, rows[1].height, 0.01f) // last row keeps the target height
    }

    @Test
    fun gapsAreSubtractedBeforeScaling() {
        // Two 1:1 items, gap=10, W=210: natural width 100 + 100 + 10 = 210 >= 210.
        // Available width for images = 210 - 10 = 200, so height = 200/2 = 100.
        val rows = packRows(listOf(1f, 1f), 210f, 100f, 10f)
        assertEquals(1, rows.size)
        assertEquals(100f, rows[0].height, 0.01f)
    }

    @Test
    fun singleWideImageScalesDownToFitWidth() {
        // A 4:1 panorama at H=100 is 400 wide > W=200, so it shrinks to height 200/4 = 50.
        val rows = packRows(listOf(4f), 200f, 100f, 0f)
        assertEquals(1, rows.size)
        assertEquals(listOf(0), rows[0].indices)
        assertEquals(50f, rows[0].height, 0.01f)
    }

    @Test
    fun everyItemIsPlacedExactlyOnce() {
        val rows = packRows(List(17) { 1.3f }, 500f, 120f, 2f)
        val placed = rows.flatMap { it.indices }
        assertEquals((0 until 17).toList(), placed.sorted())
        assertEquals(17, placed.size) // no duplicates, no drops
    }
}
