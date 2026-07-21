package com.kai.masterbrowse

import android.content.Context
import android.os.Environment
import java.io.File

object FileRepo {

    private const val TRASH_DIR = ".MasterBrowseTrash"

    private val nameOrder = compareBy<File> { it.name.lowercase() }

    fun defaultHome(): File = Environment.getExternalStorageDirectory()

    fun internalRoot(): File = Environment.getExternalStorageDirectory()

    /** Root of the first removable volume (SD card / USB), or null if none is mounted. */
    fun sdCardRoot(context: Context): File? {
        for (d in context.getExternalFilesDirs(null)) {
            d ?: continue
            val root = File(d.absolutePath.substringBefore("/Android/"))
            if (!root.absolutePath.startsWith("/storage/emulated")) return root
        }
        return File("/storage").listFiles()?.firstOrNull {
            it.isDirectory && it.name != "emulated" && it.name != "self" && it.canRead()
        }
    }

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
        for (sub in kids.filter { it.isDirectory && it.name != TRASH_DIR }.take(12)) {
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

    /** Root of the volume a file lives on, for keeping trash moves on the same volume. */
    private fun volumeRootOf(file: File): File {
        val p = file.absolutePath
        if (p.startsWith("/storage/emulated")) return internalRoot()
        val vol = p.removePrefix("/storage/").substringBefore('/')
        return if (vol.isNotEmpty()) File("/storage/$vol") else internalRoot()
    }

    /**
     * Moves a file or folder into the volume's trash folder as `<epochMillis>_<name>`.
     * Falls back to copy+delete if a direct rename fails.
     */
    fun moveToTrash(file: File): Boolean {
        val trash = File(volumeRootOf(file), TRASH_DIR)
        trash.mkdirs()
        val dest = File(trash, "${System.currentTimeMillis()}_${file.name}")
        if (file.renameTo(dest)) return true
        return try {
            file.copyRecursively(dest, overwrite = true) && file.deleteRecursively()
        } catch (e: Exception) {
            false
        }
    }

    /** Permanently deletes trash entries older than 30 days, on every mounted volume. */
    fun purgeTrash(context: Context, maxAgeMillis: Long = 30L * 24 * 60 * 60 * 1000) {
        val now = System.currentTimeMillis()
        val roots = listOfNotNull(internalRoot(), sdCardRoot(context))
        for (root in roots) {
            val kids = File(root, TRASH_DIR).listFiles() ?: continue
            for (k in kids) {
                val ts = k.name.substringBefore('_').toLongOrNull() ?: k.lastModified()
                if (now - ts > maxAgeMillis) k.deleteRecursively()
            }
        }
    }
}
