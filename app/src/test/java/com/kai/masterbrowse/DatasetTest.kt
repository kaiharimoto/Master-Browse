package com.kai.masterbrowse

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DatasetTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun file(dir: File, name: String, bytes: String): File =
        File(dir, name).apply {
            parentFile!!.mkdirs()
            writeText(bytes)
        }

    @Test
    fun `adding twice leaves one copy`() {
        val src = file(tmp.newFolder("a"), "img.jpg", "aaaa")
        val cat = tmp.newFolder("cat")
        assertTrue(Dataset.add(cat, src))
        assertTrue(Dataset.add(cat, src))
        assertEquals(listOf("img.jpg"), cat.list()!!.toList())
        assertTrue(Dataset.contains(cat, src))
    }

    @Test
    fun `same name from another folder gets a suffix and both are found`() {
        val first = file(tmp.newFolder("a"), "img.jpg", "aaaa")
        val second = file(tmp.newFolder("b"), "img.jpg", "bbbbbbbb")
        val cat = tmp.newFolder("cat")
        Dataset.add(cat, first)
        assertFalse(Dataset.contains(cat, second))
        Dataset.add(cat, second)
        assertEquals(setOf("img.jpg", "img (1).jpg"), cat.list()!!.toSet())
        assertEquals("img.jpg", Dataset.findCopy(cat, first)!!.name)
        assertEquals("img (1).jpg", Dataset.findCopy(cat, second)!!.name)
        assertEquals("bbbbbbbb", File(cat, "img (1).jpg").readText())
    }

    @Test
    fun `source is never modified`() {
        val src = file(tmp.newFolder("a"), "img.jpg", "aaaa")
        src.setLastModified(1_600_000_000_000L)
        val before = src.readBytes()
        val cat = tmp.newFolder("cat")
        Dataset.add(cat, src)
        assertArrayEquals(before, src.readBytes())
        assertEquals(1_600_000_000_000L, src.lastModified())
        assertEquals(src.lastModified(), Dataset.findCopy(cat, src)!!.lastModified())
    }

    @Test
    fun `remove takes out only the copy`() {
        val src = file(tmp.newFolder("a"), "img.jpg", "aaaa")
        val cat = tmp.newFolder("cat")
        Dataset.add(cat, src)
        assertTrue(Dataset.remove(cat, src) { it.delete() })
        assertFalse(Dataset.contains(cat, src))
        assertTrue(src.exists())
        assertEquals("aaaa", src.readText())
    }

    @Test
    fun `removing something not in the category is a no-op`() {
        val src = file(tmp.newFolder("a"), "img.jpg", "aaaa")
        val cat = tmp.newFolder("cat")
        file(cat, "img.jpg", "a different picture")
        assertTrue(Dataset.remove(cat, src) { it.delete() })
        assertTrue(File(cat, "img.jpg").exists())
    }

    @Test
    fun `categories skip hidden folders and files, ordered by name`() {
        val root = tmp.newFolder("root")
        File(root, "zeta").mkdir()
        File(root, "Alpha").mkdir()
        File(root, ".hidden").mkdir()
        file(root, "notes.txt", "x")
        assertEquals(listOf("Alpha", "zeta"), Dataset.categories(root).map { it.name })
    }

    @Test
    fun `create rejects bad names`() {
        val root = tmp.newFolder("root")
        assertNull(Dataset.create(root, "  "))
        assertNull(Dataset.create(root, "a/b"))
        assertNull(Dataset.create(root, ".hidden"))
        assertNotNull(Dataset.create(root, " face "))
        assertTrue(File(root, "face").isDirectory)
        assertNull(Dataset.create(root, "face"))
    }

    @Test
    fun `frame names round-trip`() {
        val video = File("/x/clip.v2.mp4")
        assertEquals("clip.v2_01m02s345.png", Dataset.frameName(video, 62_345))
        assertEquals(62_345L, Dataset.frameTime(video, "clip.v2_01m02s345.png"))
        assertEquals("clip.v2_125m00s000.png", Dataset.frameName(video, 125 * 60_000L))
        assertEquals(125 * 60_000L, Dataset.frameTime(video, "clip.v2_125m00s000.png"))
    }

    @Test
    fun `frame time ignores other videos and other files`() {
        val video = File("/x/clip.mp4")
        assertNull(Dataset.frameTime(video, "clip2_00m01s000.png"))
        assertNull(Dataset.frameTime(video, "clip_00m01s000.jpg"))
        assertNull(Dataset.frameTime(video, "clip.png"))
        assertNull(Dataset.frameTime(video, "other_00m01s000.png"))
    }

    @Test
    fun `frames in a category are listed in time order`() {
        val cat = tmp.newFolder("cat")
        val video = File(tmp.root, "clip.mp4")
        listOf(5_000L, 1_000L, 3_000L).forEach { file(cat, Dataset.frameName(video, it), "png") }
        file(cat, "unrelated.png", "png")
        assertEquals(listOf(1_000L, 3_000L, 5_000L), Dataset.framesIn(cat, video))
    }

    @Test
    fun `even times sit mid-slice and stay inside the video`() {
        assertEquals(listOf(1_000L, 3_000L, 5_000L, 7_000L, 9_000L), Dataset.evenTimes(10_000, 5))
        assertTrue(Dataset.evenTimes(0, 5).isEmpty())
        assertTrue(Dataset.evenTimes(100, 96).all { it in 0 until 100 })
    }
}
