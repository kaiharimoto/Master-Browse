package com.kai.masterbrowse.ui

import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kai.masterbrowse.ThumbCache
import com.kai.masterbrowse.isVideoFile
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One packed row of the justified layout: entry [indices] rendered at [height] (dp). */
data class PackedRow(val indices: List<Int>, val height: Float)

/**
 * Justified-rows packing (Google Photos / Flickr style). Pure and unit-tested.
 *
 * Greedily fills a row with items shown at [targetHeight]; once a row's natural
 * width reaches [containerWidth], the row's height is scaled down so it fills the
 * width exactly (edges flush). The final, partial row keeps [targetHeight].
 *
 * @param aspects width/height ratio of each item, in order (folders pass 1f).
 * @param gap horizontal gap between items, in the same units as the sizes (dp).
 */
fun packRows(
    aspects: List<Float>,
    containerWidth: Float,
    targetHeight: Float,
    gap: Float,
): List<PackedRow> {
    if (aspects.isEmpty()) return emptyList()
    if (containerWidth <= 0f || targetHeight <= 0f) {
        return listOf(PackedRow(aspects.indices.toList(), targetHeight.coerceAtLeast(1f)))
    }

    val rows = ArrayList<PackedRow>()
    var current = ArrayList<Int>()
    var sumAspect = 0f
    for (i in aspects.indices) {
        val a = aspects[i].takeIf { it > 0f } ?: 1f
        current.add(i)
        sumAspect += a
        val gaps = gap * (current.size - 1)
        val naturalWidth = sumAspect * targetHeight + gaps
        if (naturalWidth >= containerWidth) {
            val avail = containerWidth - gaps
            val h = if (sumAspect > 0f) avail / sumAspect else targetHeight
            rows.add(PackedRow(current, h.coerceAtLeast(1f)))
            current = ArrayList()
            sumAspect = 0f
        }
    }
    if (current.isNotEmpty()) rows.add(PackedRow(current, targetHeight))
    return rows
}

/** In-memory cache of decoded aspect ratios, keyed by path+mtime+size. */
private object AspectCache {
    private val map = ConcurrentHashMap<String, Float>()
    fun get(key: String): Float? = map[key]
    fun put(key: String, value: Float) { map[key] = value }
}

private data class GridEntry(val file: File, val isDir: Boolean, val mediaIndex: Int)

/**
 * Renders subfolders (kept square) and media (at their true aspect ratio) as
 * justified rows. Aspect ratios are decoded off the main thread and the layout
 * reflows as they arrive; unknown items are shown as squares in the meantime.
 * [targetRowHeightDp] is the pinch-controlled target row height.
 */
@Composable
fun JustifiedGrid(
    dirs: List<File>,
    media: List<File>,
    targetRowHeightDp: Float,
    thumbVersion: Int,
    onOpenDir: (File) -> Unit,
    onOpenMedia: (List<File>, Int) -> Unit,
    dirMenu: ((File) -> List<Pair<String, () -> Unit>>)? = null,
    fileMenu: ((File) -> List<Pair<String, () -> Unit>>)? = null,
) {
    val entries = remember(dirs, media) {
        dirs.map { GridEntry(it, isDir = true, mediaIndex = -1) } +
            media.mapIndexed { i, f -> GridEntry(f, isDir = false, mediaIndex = i) }
    }

    // path -> aspect ratio; populated asynchronously, drives reflow.
    val aspects = remember { mutableStateMapOf<String, Float>() }
    LaunchedEffect(media) {
        for (f in media) {
            val path = f.absolutePath
            if (aspects.containsKey(path)) continue
            val key = "$path|${f.lastModified()}|${f.length()}"
            val a = AspectCache.get(key)
                ?: withContext(Dispatchers.IO) { decodeAspect(f) }.also { AspectCache.put(key, it) }
            aspects[path] = a
        }
    }

    val gap = 2f
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val containerW = maxWidth.value
        val aspectList = entries.map {
            if (it.isDir) 1f else (aspects[it.file.absolutePath] ?: 1f)
        }
        val rows = remember(aspectList, containerW, targetRowHeightDp) {
            packRows(aspectList, containerW, targetRowHeightDp, gap)
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(gap.dp),
            contentPadding = PaddingValues(gap.dp),
        ) {
            itemsIndexed(rows) { _, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap.dp)) {
                    row.indices.forEach { idx ->
                        val entry = entries[idx]
                        val a = aspectList[idx]
                        TileContent(
                            file = entry.file,
                            isDir = entry.isDir,
                            thumbVersion = thumbVersion,
                            onClick = {
                                if (entry.isDir) onOpenDir(entry.file)
                                else onOpenMedia(media, entry.mediaIndex)
                            },
                            menu = if (entry.isDir) dirMenu?.invoke(entry.file)
                            else fileMenu?.invoke(entry.file),
                            modifier = Modifier
                                .width((a * row.height).dp)
                                .height(row.height.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Decode a media file's display aspect ratio (width/height), honoring rotation. */
private fun decodeAspect(file: File): Float {
    val a = try {
        if (file.isVideoFile()) videoAspect(file) else imageAspect(file)
    } catch (_: Throwable) {
        1f
    }
    return a.coerceIn(0.25f, 5f)
}

private fun imageAspect(file: File): Float {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, opts)
    var w = opts.outWidth.toFloat()
    var h = opts.outHeight.toFloat()
    if (w <= 0f || h <= 0f) return 1f
    val orientation = try {
        ExifInterface(file.absolutePath)
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } catch (_: Throwable) {
        ExifInterface.ORIENTATION_NORMAL
    }
    if (orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
        orientation == ExifInterface.ORIENTATION_ROTATE_270 ||
        orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
        orientation == ExifInterface.ORIENTATION_TRANSVERSE
    ) {
        val t = w; w = h; h = t
    }
    return w / h
}

private fun videoAspect(file: File): Float {
    // Cached thumbnails are stored already rotated, so their bounds give the
    // display aspect without opening the (much slower) video file.
    ThumbCache.cachedThumb(file)?.let { thumb ->
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(thumb.absolutePath, opts)
        if (opts.outWidth > 0 && opts.outHeight > 0) {
            return opts.outWidth.toFloat() / opts.outHeight
        }
    }
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(file.absolutePath)
        val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            ?.toFloatOrNull() ?: return 1f
        val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            ?.toFloatOrNull() ?: return 1f
        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            ?.toIntOrNull() ?: 0
        if (w <= 0f || h <= 0f) return 1f
        return if (rotation == 90 || rotation == 270) h / w else w / h
    } finally {
        retriever.release()
    }
}
