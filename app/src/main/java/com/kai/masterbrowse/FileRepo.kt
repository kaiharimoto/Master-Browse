package com.kai.masterbrowse

import android.os.Environment
import java.io.File

object FileRepo {

    private val nameOrder = compareBy<File> { it.name.lowercase() }

    fun defaultHome(): File = Environment.getExternalStorageDirectory()

    /** All subdirectories and media files of [dir], sorted by name. Hidden entries included. */
    fun listEntries(dir: File): Pair<List<File>, List<File>> {
        val kids = dir.listFiles() ?: return emptyList<File>() to emptyList()
        val dirs = kids.filter { it.isDirectory }.sortedWith(nameOrder)
        val media = kids.filter { it.isMediaFile() }.sortedWith(nameOrder)
        return dirs to media
    }

    fun mediaIn(dir: File): List<File> =
        (dir.listFiles() ?: emptyArray()).filter { it.isMediaFile() }.sortedWith(nameOrder)

    /**
     * Thumbnail source for a folder tile: the user-chosen image if set,
     * otherwise the first media file inside (checking one level of subfolders as fallback).
     */
    fun thumbFor(dir: File): File? {
        Prefs.folderThumb(dir.absolutePath)?.let {
            val f = File(it)
            if (f.exists()) return f
        }
        val kids = dir.listFiles()?.sortedWith(nameOrder) ?: return null
        kids.firstOrNull { it.isMediaFile() }?.let { return it }
        for (sub in kids.filter { it.isDirectory }.take(12)) {
            val subKids = sub.listFiles() ?: continue
            subKids.sortedWith(nameOrder).firstOrNull { it.isMediaFile() }?.let { return it }
        }
        return null
    }

    /** Parent for "UP" navigation. Skips over the unreadable /storage/emulated directory. */
    fun parentOf(dir: File): File? {
        val parent = dir.parentFile ?: return null
        if (parent.absolutePath == "/storage/emulated") return File("/storage")
        return parent
    }
}
