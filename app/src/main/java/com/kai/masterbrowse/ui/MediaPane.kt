package com.kai.masterbrowse.ui

import android.net.Uri
import android.view.TextureView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.kai.masterbrowse.isVideoFile
import java.io.File
import kotlinx.coroutines.delay

/**
 * One fullscreen (or half-screen in split mode) media view: an image or a looping video,
 * with zoom/pan, swipe navigation within [items], and a tap-toggled control overlay.
 */
@Composable
fun MediaPane(
    items: List<File>,
    startIndex: Int,
    modifier: Modifier = Modifier,
    extraButtons: @Composable RowScope.() -> Unit = {},
) {
    var index by remember(items, startIndex) {
        mutableIntStateOf(startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)))
    }
    val file = items.getOrNull(index)
    val zoom = remember { ZoomState() }
    var controlsVisible by remember { mutableStateOf(true) }

    LaunchedEffect(file) {
        zoom.reset()
        zoom.contentPixels = Size.Zero
    }

    val isVideo = file?.isVideoFile() == true
    val player = if (file != null && isVideo) rememberVideoPlayer(file, zoom) else null

    val painter = if (file != null && !isVideo) {
        rememberAsyncImagePainter(
            ImageRequest.Builder(LocalContext.current)
                .data(file)
                .size(coil.size.Size.ORIGINAL)
                .build()
        )
    } else null

    if (painter != null) {
        val ps = painter.state
        LaunchedEffect(ps) {
            if (ps is AsyncImagePainter.State.Success) {
                val s = ps.painter.intrinsicSize
                if (s.width > 0f && s.height > 0f) zoom.contentPixels = s
            }
        }
    }

    Box(
        modifier
            .background(Color.Black)
            .clipToBounds()
            .onSizeChanged { zoom.containerSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .mediaGestures(
                zoom = zoom,
                onSwipe = { forward ->
                    if (items.isNotEmpty()) {
                        index = (index + if (forward) 1 else -1).coerceIn(0, items.size - 1)
                    }
                },
                onTap = { controlsVisible = !controlsVisible },
            )
    ) {
        if (file == null) {
            Text("No media", Modifier.align(Alignment.Center), color = Color.Gray, fontSize = 14.sp)
        } else {
            val density = LocalDensity.current
            val fitted = zoom.fittedSize
            if (fitted.width > 0f && fitted.height > 0f) {
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(
                            with(density) { fitted.width.toDp() },
                            with(density) { fitted.height.toDp() },
                        )
                        .graphicsLayer {
                            scaleX = zoom.scale
                            scaleY = zoom.scale
                            translationX = zoom.offset.x
                            translationY = zoom.offset.y
                        }
                ) {
                    key(file) {
                        if (player != null) {
                            AndroidView(
                                factory = { ctx ->
                                    TextureView(ctx).also { player.setVideoTextureView(it) }
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else if (painter != null) {
                            Image(
                                painter = painter,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.FillBounds,
                            )
                        }
                    }
                }
            } else {
                Text("Loading…", Modifier.align(Alignment.Center), color = Color.DarkGray, fontSize = 13.sp)
            }
            if (controlsVisible) {
                PaneOverlay(file, index, items.size, zoom, player, extraButtons)
            }
        }
    }
}

@Composable
private fun BoxScope.PaneOverlay(
    file: File,
    index: Int,
    count: Int,
    zoom: ZoomState,
    player: ExoPlayer?,
    extraButtons: @Composable RowScope.() -> Unit,
) {
    Row(
        Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .background(Color(0xCC000000))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("${index + 1}/$count", color = Color.Gray, fontSize = 12.sp)
        Text(
            file.name,
            Modifier.weight(1f),
            color = Color.White,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        PaneButton(if (zoom.isPixelPerfect) "FIT" else "1:1") { zoom.togglePixelPerfect() }
        extraButtons()
    }

    if (player != null) {
        var position by remember(player) { mutableLongStateOf(0L) }
        var duration by remember(player) { mutableLongStateOf(0L) }
        var dragging by remember(player) { mutableStateOf(false) }
        var playing by remember(player) { mutableStateOf(true) }
        LaunchedEffect(player) {
            while (true) {
                if (!dragging) {
                    position = player.currentPosition.coerceAtLeast(0L)
                    duration = player.duration.coerceAtLeast(0L)
                }
                playing = player.isPlaying
                delay(200)
            }
        }
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0xCC000000))
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PaneButton(if (playing) "❚❚" else "▶") { player.playWhenReady = !player.playWhenReady }
            Text(formatTime(position), color = Color.White, fontSize = 12.sp)
            Slider(
                value = if (duration > 0) position.toFloat().coerceIn(0f, duration.toFloat()) else 0f,
                onValueChange = {
                    dragging = true
                    position = it.toLong()
                    player.seekTo(it.toLong())
                },
                onValueChangeFinished = { dragging = false },
                valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
                modifier = Modifier.weight(1f),
            )
            Text(formatTime(duration), color = Color.White, fontSize = 12.sp)
        }
    }
}

@Composable
private fun rememberVideoPlayer(file: File, zoom: ZoomState): ExoPlayer {
    val context = LocalContext.current
    val player = remember(file) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    zoom.contentPixels = Size(videoSize.width.toFloat(), videoSize.height.toFloat())
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    return player
}

@Composable
fun PaneButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .background(Color(0xFF222428))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = 13.sp)
    }
}

private fun formatTime(ms: Long): String {
    val totalS = ms / 1000
    val s = totalS % 60
    val m = (totalS / 60) % 60
    val h = totalS / 3600
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
