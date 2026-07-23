package com.kai.masterbrowse

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileRepoTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `no collision keeps the name`() {
        assertEquals("a.jpg", FileRepo.uniqueDest(tmp.root, "a.jpg", isDir = false).name)
    }

    @Test
    fun `file collision inserts counter before extension`() {
        tmp.newFile("a.jpg")
        assertEquals("a (1).jpg", FileRepo.uniqueDest(tmp.root, "a.jpg", isDir = false).name)
        tmp.newFile("a (1).jpg")
        assertEquals("a (2).jpg", FileRepo.uniqueDest(tmp.root, "a.jpg", isDir = false).name)
    }

    @Test
    fun `extensionless file collision appends counter`() {
        tmp.newFile("notes")
        assertEquals("notes (1)", FileRepo.uniqueDest(tmp.root, "notes", isDir = false).name)
    }

    @Test
    fun `dotfile collision appends counter, dot is not an extension`() {
        tmp.newFile(".nomedia")
        assertEquals(".nomedia (1)", FileRepo.uniqueDest(tmp.root, ".nomedia", isDir = false).name)
    }

    @Test
    fun `directory collision appends counter even with a dot in the name`() {
        tmp.newFolder("my.photos")
        assertEquals("my.photos (1)", FileRepo.uniqueDest(tmp.root, "my.photos", isDir = true).name)
    }

    @Test
    fun `rename rejects blank, separators, and existing siblings`() {
        val f = File(tmp.root, "a.jpg").apply { createNewFile() }
        tmp.newFile("b.jpg")
        assertEquals(false, FileRepo.rename(f, ""))
        assertEquals(false, FileRepo.rename(f, "x/y"))
        assertEquals(false, FileRepo.rename(f, "b.jpg"))
        assertEquals(true, FileRepo.rename(f, "c.jpg"))
        assertEquals(true, File(tmp.root, "c.jpg").exists())
    }

    @Test
    fun `moveEntry refuses folder into itself or descendant, no-ops same parent`() {
        val folder = tmp.newFolder("f")
        val child = File(folder, "sub").apply { mkdirs() }
        assertEquals(false, FileRepo.moveEntry(folder, folder))
        assertEquals(false, FileRepo.moveEntry(folder, child))
        assertEquals(true, FileRepo.moveEntry(folder, tmp.root)) // already there → no-op success
        assertEquals(true, folder.exists())
    }
}
