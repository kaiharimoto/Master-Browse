package com.kai.masterbrowse

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.ImageDecoderDecoder
import coil.decode.VideoFrameDecoder
import coil.intercept.Interceptor
import coil.request.ImageResult
import com.kai.masterbrowse.overlay.OverlayService
import java.io.File
import kotlin.concurrent.thread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class App : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        ThumbCache.init(this)
        thread(name = "thumb-prune") { ThumbCache.prune() }
        // The floating window's foreground service needs somewhere to post its ongoing
        // notification. Low importance: no sound, no heads-up, no badge.
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                OverlayService.CHANNEL_ID,
                "Floating window",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { setShowBadge(false) }
        )
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                add(VideoThumbInterceptor)
                add(VideoFrameDecoder.Factory())
                add(ImageDecoderDecoder.Factory())
            }
            .crossfade(false)
            .build()
}

/**
 * Serves every video-file image request from [ThumbCache] (generating the
 * thumbnail on first sight) instead of re-extracting a frame from the video on
 * each load. Falls through to the live [VideoFrameDecoder] path only when
 * extraction fails.
 */
private object VideoThumbInterceptor : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val data = chain.request.data
        if (data is File && data.isVideoFile()) {
            // Fast path: an existing cache entry is just a stat check, so skip the dispatcher
            // hop — that hop cost already-cached thumbnails their synchronous first-frame draw.
            val thumb = ThumbCache.cachedThumb(data)
                ?: withContext(Dispatchers.IO) { ThumbCache.getOrCreate(data) }
            if (thumb != null) {
                return chain.proceed(chain.request.newBuilder().data(thumb).build())
            }
        }
        return chain.proceed(chain.request)
    }
}
