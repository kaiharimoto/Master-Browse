package com.kai.masterbrowse

import android.content.Context
import android.os.Environment
import java.io.File

/** Ordering options offered by the toolbar SORT dropdown. */
enum class SortMode { NAME, DATE, SIZE }

object FileRepo {

    private const val TRASH_DIR = ".MasterBrowseTrash"

    private val nameOrder = compareBy<File> { it.name.lowercase() }

    /**
     * Comparator for [sort]/[desc]. Folders have no meaningful size, so callers
     * pass [SortMode.NAME] for the folder list when the user picks size.
     */
    private fun comparatorFor(sort: SortMode, desc: Boolean): Comparator<File> {
        val base: Comparator<File> = when (sort) {
            SortMode.NAME -> compareBy { it.name.lowercase() }
            SortMode.DATE -> compareBy { it.lastModified() }
            SortMode.SIZE -> compareBy { it.length() }
        }
        return if (desc) base.reversed() else base
    }

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

    /**
     * All subdirectories and media files of [dir], ordered by [sort]/[desc].
     * Folders are grouped first. Hidden entries included. Folders fall back to
     * name order when sorting by size (a folder has no byte size).
     */
    fun listEntries(
        dir: File,
        sort: SortMode = SortMode.NAME,
        desc: Boolean = false,
    ): Pair<List<File>, List<File>> {
        val kids = dir.listFiles() ?: return emptyList<File>() to emptyList()
        val dirSort = if (sort == SortMode.SIZE) SortMode.NAME else sort
        val dirs = kids.filter { it.isDirectory }.sortedWith(comparatorFor(dirSort, desc))
        val media = kids.filter { it.isMediaFile() }.sortedWith(comparatorFor(sort, desc))
        return dirs to media
    }

    fun mediaIn(dir: File): List<File> =
        (dir.listFiles() ?: emptyArray()).filter { it.isMediaFile() }.sortedWith(nameOrder)

    /**
     * Thumbnail source for a folder tile: the user-chosen image if set, otherwise the
     * first media file found inside — searching depth-first up to four folder levels
     * deep (bounded to a few hundred directories so a huge tree can't stall a tile).
     */
    fun thumbFor(dir: File): File? {
        Prefs.folderThumb(dir.absolutePath)?.let {
            val f = File(it)
            if (f.exists()) return f
        }
        return firstMediaWithin(dir, depth = 4, budget = intArrayOf(300))
    }

    private fun firstMediaWithin(dir: File, depth: Int, budget: IntArray): File? {
        if (depth < 0 || budget[0] <= 0) return null
        budget[0]--
        val kids = dir.listFiles()?.sortedWith(nameOrder) ?: return null
        kids.firstOrNull { it.isMediaFile() }?.let { return it }
        for (sub in kids) {
            if (!sub.isDirectory || sub.name == TRASH_DIR) continue
            firstMediaWithin(sub, depth - 1, budget)?.let { return it }
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

    /**
     * A path in [destDir] for something named [name] that doesn't collide with an
     * existing entry: inserts " (1)", " (2)"… — before the extension for files,
     * at the end for directories (a dot in a folder name is not an extension).
     */
    fun uniqueDest(destDir: File, name: String, isDir: Boolean): File {
        var candidate = File(destDir, name)
        if (!candidate.exists()) return candidate
        val dot = name.lastIndexOf('.')
        val (base, ext) = if (!isDir && dot > 0) name.substring(0, dot) to name.substring(dot) else name to ""
        var n = 1
        while (true) {
            candidate = File(destDir, "$base ($n)$ext")
            if (!candidate.exists()) return candidate
            n++
        }
    }

    /** Renames [file] in place. Rejects blank or path-separator names and existing siblings. */
    fun rename(file: File, newName: String): Boolean {
        if (newName.isBlank() || newName.contains('/')) return false
        val dest = File(file.parentFile ?: return false, newName)
        if (dest.exists()) return false
        return file.renameTo(dest)
    }

    /** Copies a file or folder into [destDir] under a collision-free name. */
    fun copyEntry(src: File, destDir: File): Boolean {
        val dest = uniqueDest(destDir, src.name, src.isDirectory)
        return try {
            src.copyRecursively(dest, overwrite = false)
        } catch (e: Exception) {
            dest.deleteRecursively()
            false
        }
    }

    /**
     * Moves a file or folder into [destDir]. Same-parent moves are a no-op;
     * moving a folder into itself or a descendant is refused. Tries a rename
     * first, falling back to copy+delete for cross-volume moves — the source is
     * only deleted after the copy fully succeeds.
     */
    fun moveEntry(src: File, destDir: File): Boolean {
        if (destDir.absolutePath == src.parentFile?.absolutePath) return true
        if (destDir.absolutePath == src.absolutePath ||
            destDir.absolutePath.startsWith(src.absolutePath + "/")
        ) return false
        val dest = uniqueDest(destDir, src.name, src.isDirectory)
        if (src.renameTo(dest)) return true
        return try {
            if (src.copyRecursively(dest, overwrite = false)) {
                src.deleteRecursively()
            } else {
                dest.deleteRecursively()
                false
            }
        } catch (e: Exception) {
            dest.deleteRecursively()
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
