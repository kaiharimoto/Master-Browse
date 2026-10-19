package com.kai.masterbrowse

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import java.io.File
import java.io.FileOutputStream

/**
 * Still frames from videos, for dataset mode. Frames are decoded with OPTION_CLOSEST — the
 * exact frame at the requested time, not the nearest keyframe, which can be seconds away —
 * and come back already rotated the way the video plays.
 */
object Frames {

    /** Opens [video] for frame grabbing. The caller must release() it. */
    fun open(video: File): MediaMetadataRetriever =
        MediaMetadataRetriever().apply { setDataSource(video.absolutePath) }

    fun durationMs(retriever: MediaMetadataRetriever): Long =
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L

    /**
     * A small preview of the frame at [timeMs] for the frame picker grid, longest side
     * [maxDim]. RGB_565 halves the memory of a strip that can hold ~100 of these.
     */
    fun thumb(retriever: MediaMetadataRetriever, timeMs: Long, maxDim: Int): Bitmap? {
        val (w, h) = displaySize(retriever)
        if (w <= 0 || h <= 0) return null
        val scale = minOf(1f, maxDim.toFloat() / maxOf(w, h))
        val params = MediaMetadataRetriever.BitmapParams().apply { preferredConfig = Bitmap.Config.RGB_565 }
        return runCatching {
            retriever.getScaledFrameAtTime(
                timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST,
                (w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1), params,
            )
        }.getOrNull()
    }

    /**
     * Saves the full-resolution frame of [video] at [timeMs] into [category] as a PNG named
     * by [Dataset.frameName]. True if it was saved or was already there. Written to a hidden
     * temp name first, so a failed encode never leaves a truncated PNG that looks valid.
     */
    fun save(category: File, video: File, timeMs: Long, retriever: MediaMetadataRetriever): Boolean {
        val dest = File(category, Dataset.frameName(video, timeMs))
        if (dest.exists()) return true
        val bmp = runCatching { retriever.getFrameAtTime(timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST) }
            .getOrNull() ?: return false
        val tmp = File(category, ".${dest.name}.part")
        return try {
            val ok = FileOutputStream(tmp).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            ok && tmp.renameTo(dest)
        } catch (e: Exception) {
            false
        } finally {
            bmp.recycle()
            tmp.delete()
        }
    }

    /** Convenience for a single grab: opens and releases its own retriever. */
    fun save(category: File, video: File, timeMs: Long): Boolean {
        val r = runCatching { open(video) }.getOrNull() ?: return false
        return try {
            save(category, video, timeMs, r)
        } finally {
            r.release()
        }
    }

    /** Width/height as played, i.e. swapped for 90°/270° rotated recordings. */
    private fun displaySize(retriever: MediaMetadataRetriever): Pair<Int, Int> {
        val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        val rot = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        return if (rot == 90 || rot == 270) h to w else w to h
    }
}
