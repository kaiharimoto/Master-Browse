package com.kai.masterbrowse

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Persistent on-disk cache of video thumbnails, so each video's frame is
 * extracted exactly once and every later load (including after app restarts)
 * is a cheap JPEG decode.
 *
 * Entries are keyed by source path + mtime + size, so an edited/replaced video
 * gets a fresh thumbnail and the stale one ages out via the LRU prune. Stored
 * under filesDir (not cacheDir) so the system doesn't silently clear them.
 */
object ThumbCache {

    /** Longest edge of a cached thumbnail, sized for large tiles and swipe previews. */
    private const val MAX_DIM = 1024
    private const val JPEG_QUALITY = 85
    private const val MAX_CACHE_BYTES = 2L * 1024 * 1024 * 1024

    private lateinit var dir: File
    private val locks = Array(32) { Any() }

    fun init(context: Context) {
        dir = File(context.filesDir, "video-thumbs").apply { mkdirs() }
    }

    private fun keyFor(file: File): String {
        val raw = "${file.absolutePath}|${file.lastModified()}|${file.length()}"
        val digest = MessageDigest.getInstance("MD5").digest(raw.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** The cached thumbnail for [file] if it already exists, without generating one. */
    fun cachedThumb(file: File): File? {
        val f = File(dir, keyFor(file) + ".jpg")
        return if (f.exists()) f else null
    }

    /**
     * Returns the cached thumbnail for [file], extracting and saving it first if
     * missing. Blocking (frame extraction + JPEG encode) — call on an IO thread.
     * Returns null if the frame can't be extracted (corrupt/unsupported video).
     */
    fun getOrCreate(file: File): File? {
        val key = keyFor(file)
        val out = File(dir, "$key.jpg")
        if (out.exists()) {
            out.setLastModified(System.currentTimeMillis()) // keep LRU order honest
            return out
        }
        synchronized(locks[(key.hashCode() and Int.MAX_VALUE) % locks.size]) {
            if (out.exists()) return out
            val bitmap = extractFrame(file) ?: return null
            val tmp = File(dir, "$key.tmp")
            try {
                tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
                if (!tmp.renameTo(out)) return null
            } finally {
                bitmap.recycle()
                tmp.delete()
            }
            return out
        }
    }

    /**
     * Same frame choice as the live decoder for cached thumbs: 25% in (a fixed 1s
     * overshoots short clips), decoded exactly — keyframe-only retrieval snaps
     * fade-ins to the black first keyframe.
     */
    private fun extractFrame(file: File): Bitmap? = frameAt(file, positionFraction = 0.25, maxDim = MAX_DIM)

    /**
     * Decodes one exact frame of [file] at [positionFraction] of its duration,
     * pre-scaled to [maxDim] and already rotated per the video's metadata.
     * Blocking — call on an IO thread. The viewer uses fraction 0.0 for swipe
     * posters so they match exactly where playback starts.
     */
    fun frameAt(file: File, positionFraction: Double, maxDim: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val timeUs = (durationMs * 1000 * positionFraction).toLong()
            var w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            var h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0
            if (rotation == 90 || rotation == 270) { val t = w; w = h; h = t }
            if (w > 0 && h > 0 && maxOf(w, h) > maxDim) {
                val scale = maxDim.toFloat() / maxOf(w, h)
                retriever.getScaledFrameAtTime(
                    timeUs, MediaMetadataRetriever.OPTION_CLOSEST,
                    (w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1),
                )
            } else {
                retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
            }
        } catch (_: Throwable) {
            null
        } finally {
            retriever.release()
        }
    }

    /**
     * Generates thumbnails for every video in [files] that doesn't have one yet,
     * a couple at a time so visible tiles keep decoding priority. Cancellable.
     */
    suspend fun prefetch(files: List<File>) = withContext(Dispatchers.IO) {
        val videos = files.filter { it.isVideoFile() && cachedThumb(it) == null }
        if (videos.isEmpty()) return@withContext
        val gate = Semaphore(2)
        coroutineScope {
            for (f in videos) launch {
                gate.withPermit { if (isActive) getOrCreate(f) }
            }
        }
    }

    /** Deletes the oldest thumbnails until the cache fits [maxBytes]. Blocking. */
    fun prune(maxBytes: Long = MAX_CACHE_BYTES) {
        val entries = dir.listFiles()?.sortedBy { it.lastModified() } ?: return
        var total = entries.sumOf { it.length() }
        for (f in entries) {
            if (total <= maxBytes) break
            total -= f.length()
            f.delete()
        }
    }
}
