package com.kai.masterbrowse

import java.io.File

/**
 * Dataset mode: sorting media into category folders by *copying* it, for building
 * training sets without disturbing the folders the media came from. A dataset is one
 * root folder; each non-hidden subfolder of it is a category.
 *
 * Kept free of Android types so it can be unit tested on the JVM. Sources are only ever
 * read; every write lands inside a category folder.
 */
object Dataset {

    /**
     * The categories under [root], name-ordered. Read from disk every time rather than
     * kept in Prefs, so folders made or renamed in another file manager just show up.
     */
    fun categories(root: File): List<File> =
        (root.listFiles() ?: emptyArray())
            .filter { it.isDirectory && !it.name.startsWith(".") }
            .sortedBy { it.name.lowercase() }

    /**
     * The copy of [src] already in [category], or null. A name alone can't identify it:
     * two source folders can each hold an IMG_0001.jpg, and the second one is copied in as
     * "IMG_0001 (1).jpg" by [FileRepo.uniqueDest]. So this walks the same name sequence
     * uniqueDest does — up to the first gap — and accepts a file of the same byte length.
     * Needs no index file, so it survives reinstalls and edits made outside the app.
     */
    fun findCopy(category: File, src: File): File? {
        val len = src.length()
        val name = src.name
        val dot = name.lastIndexOf('.')
        val (base, ext) = if (dot > 0) name.substring(0, dot) to name.substring(dot) else name to ""
        var candidate = File(category, name)
        var n = 0
        while (candidate.exists()) {
            if (candidate.isFile && candidate.length() == len) return candidate
            n++
            candidate = File(category, "$base ($n)$ext")
        }
        return null
    }

    fun contains(category: File, src: File): Boolean = findCopy(category, src) != null

    /**
     * Copies [src] into [category] unless it is already there. The copy keeps the
     * source's modified time so sorting the dataset by date still follows the originals.
     */
    fun add(category: File, src: File): Boolean {
        if (findCopy(category, src) != null) return true
        val dest = FileRepo.uniqueDest(category, src.name, isDir = false)
        return try {
            src.copyTo(dest, overwrite = false)
            dest.setLastModified(src.lastModified())
            true
        } catch (e: Exception) {
            dest.delete()
            false
        }
    }

    /**
     * Moves [src]'s copy out of [category] into the trash. The source is not touched.
     * [trash] is swappable because the real one resolves the volume root through
     * android.os.Environment, which plain JVM tests can't call.
     */
    fun remove(category: File, src: File, trash: (File) -> Boolean = FileRepo::moveToTrash): Boolean {
        val copy = findCopy(category, src) ?: return true
        return trash(copy)
    }

    /**
     * Creates a category folder named [name] under [root]. Refuses blank names, path
     * separators, leading dots (a hidden folder would never show as a category) and
     * names already taken. Returns the new folder, or null.
     */
    fun create(root: File, name: String): File? {
        val n = name.trim()
        if (n.isEmpty() || n.contains('/') || n.startsWith(".")) return null
        val dir = File(root, n)
        if (dir.exists()) return null
        return dir.takeIf { it.mkdirs() }
    }
}
