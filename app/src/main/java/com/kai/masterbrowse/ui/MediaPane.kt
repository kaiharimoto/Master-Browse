package com.kai.masterbrowse.ui

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.view.TextureView
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.request.videoFrameOption
import coil.request.videoFramePercent
import com.kai.masterbrowse.isVideoFile
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * One fullscreen (or half-screen in split mode) media view: an image or a looping video,
 * with zoom/pan, TikTok-style paging within [items], keyboard navigation, and a tap-toggled
 * control overlay.
 *
 * Navigation: horizontal drag (follows finger, snaps), double-tap left/right (instant),
 * ←/→ keys (instant). For videos, hold J to rewind and K to fast-forward (accelerating).
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
    // Auto-hide: any interaction bumps the tick, restarting a 3s countdown; a
    // finger resting on the seek bar (seekDragging) blocks the hide until release.
    var interactionTick by remember { mutableIntStateOf(0) }
    var seekDragging by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val pageOffset = remember { Animatable(0f) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(file) {
        zoom.reset()
        zoom.contentPixels = Size.Zero
    }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    // Pre-decoded fit-to-screen previews of the current item and its neighbors, keyed by
    // path. Drawing these as plain Images (no async request) is what lets swipe previews
    // and the post-commit placeholder appear on the very first frame with no flash: when
    // the index changes, the new current item's bitmap is already in this map because it
    // was just a neighbor.
    val previewContext = LocalContext.current
    val previewBitmaps = remember { mutableStateMapOf<String, ImageBitmap>() }
    LaunchedEffect(index, items) {
        val wanted = listOfNotNull(
            items.getOrNull(index), items.getOrNull(index + 1), items.getOrNull(index - 1),
        )
        previewBitmaps.keys.retainAll(wanted.map { it.absolutePath }.toSet())
        for (f in wanted) {
            if (previewBitmaps.containsKey(f.absolutePath)) continue
            loadPreviewBitmap(previewContext, f)?.let { previewBitmaps[f.absolutePath] = it }
        }
    }
    LaunchedEffect(controlsVisible, interactionTick) {
        if (!controlsVisible) return@LaunchedEffect
        delay(3000)
        if (!seekDragging) controlsVisible = false
    }

    val isVideo = file?.isVideoFile() == true
    // Keeps the thumbnail placeholder up until the video has actually drawn a frame.
    var videoFrameReady by remember(file) { mutableStateOf(false) }
    val player = if (file != null && isVideo) {
        rememberVideoPlayer(file, zoom, onFirstFrame = { videoFrameReady = true })
    } else null

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

    // Videos: seed a provisional size so the media Box (and thus the TextureView surface) is laid
    // out before ExoPlayer reports its dimensions. Without a real surface the video renderer never
    // decodes on some devices, so onVideoSizeChanged would never fire and contentPixels would stay
    // 0 → "Loading…" forever while audio plays. The real size then corrects the aspect ratio.
    if (player != null) {
        LaunchedEffect(player, zoom.containerSize) {
            if (zoom.contentPixels == Size.Zero && zoom.containerSize != Size.Zero) {
                val vs = player.videoSize
                zoom.contentPixels = if (vs.width > 0 && vs.height > 0) {
                    Size(vs.width.toFloat(), vs.height.toFloat())
                } else {
                    zoom.containerSize
                }
            }
        }
    }

    fun goInstant(forward: Boolean) {
        if (items.isEmpty()) return
        val next = index + if (forward) 1 else -1
        if (next in items.indices) index = next
    }

    // Hold-to-scrub (J/K on videos): a coroutine that seeks by a step that grows the longer the
    // key is held. Uses exact seeks and pauses playback for a clean scrub, restoring state
    // when released.
    var jogJob by remember { mutableStateOf<Job?>(null) }
    var jogWasPlaying by remember { mutableStateOf(true) }
    fun startJog(backward: Boolean) {
        val p = player ?: return
        if (jogJob != null) return
        jogWasPlaying = p.playWhenReady
        p.setSeekParameters(SeekParameters.EXACT)
        p.playWhenReady = false
        jogJob = scope.launch {
            var held = 0L
            // Advance our own target instead of stepping from currentPosition: keyframe seeks
            // snap currentPosition back to the previous sync frame, so a step smaller than the
            // keyframe interval never escaped it and the jog looped the same snippet forever.
            var target = p.currentPosition
            while (isActive) {
                val duration = p.duration.coerceAtLeast(1L)
                val step = (250L + held / 2L).coerceAtMost(5000L) // accelerates while held
                target = (target + if (backward) -step else step).coerceIn(0L, duration)
                // Skip a tick if the previous seek is still decoding; target keeps advancing
                // so the jog rate stays tied to hold time, not decode speed.
                if (p.playbackState == Player.STATE_READY) p.seekTo(target)
                delay(60)
                held += 60
            }
        }
    }
    fun stopJog() {
        jogJob?.cancel()
        jogJob = null
        player?.let {
            it.setSeekParameters(SeekParameters.DEFAULT)
            it.playWhenReady = jogWasPlaying
        }
    }

    // Press-and-hold on the right/left half: 3x fast-forward (real playback, so audio
    // stays, time-stretched) / 3x rewind (exact-seek pump — decoders can't run backward,
    // so no audio there).
    var holdRewindJob by remember { mutableStateOf<Job?>(null) }
    var holdWasPlaying by remember { mutableStateOf(true) }
    var holdForward by remember { mutableStateOf(true) }
    fun startHoldScrub(forward: Boolean) {
        val p = player ?: return
        interactionTick++
        holdForward = forward
        holdWasPlaying = p.playWhenReady
        if (forward) {
            p.playbackParameters = PlaybackParameters(3f)
            p.playWhenReady = true
        } else {
            p.setSeekParameters(SeekParameters.EXACT)
            p.playWhenReady = false
            holdRewindJob = scope.launch {
                var target = p.currentPosition
                while (isActive) {
                    target = (target - 180L).coerceAtLeast(0L) // 3x realtime, 60ms ticks
                    if (p.playbackState == Player.STATE_READY) p.seekTo(target)
                    delay(60)
                }
            }
        }
    }
    fun stopHoldScrub() {
        val p = player ?: return
        interactionTick++
        if (holdForward) {
            p.playbackParameters = PlaybackParameters.DEFAULT
        } else {
            holdRewindJob?.cancel()
            holdRewindJob = null
            p.setSeekParameters(SeekParameters.DEFAULT)
        }
        p.playWhenReady = holdWasPlaying
    }
    DisposableEffect(player) {
        onDispose {
            jogJob?.cancel()
            jogJob = null
            holdRewindJob?.cancel()
            holdRewindJob = null
        }
    }

    Box(
        modifier
            .background(Color.Black)
            .clipToBounds()
            .onSizeChanged { zoom.containerSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { ev ->
                when (ev.key) {
                    Key.DirectionLeft -> {
                        if (ev.type == KeyEventType.KeyDown && ev.nativeKeyEvent.repeatCount == 0) goInstant(false)
                        interactionTick++
                        true
                    }
                    Key.DirectionRight -> {
                        if (ev.type == KeyEventType.KeyDown && ev.nativeKeyEvent.repeatCount == 0) goInstant(true)
                        interactionTick++
                        true
                    }
                    Key.J -> {
                        if (ev.type == KeyEventType.KeyDown) {
                            if (ev.nativeKeyEvent.repeatCount == 0) startJog(backward = true)
                        } else if (ev.type == KeyEventType.KeyUp) stopJog()
                        interactionTick++
                        true
                    }
                    Key.K -> {
                        if (ev.type == KeyEventType.KeyDown) {
                            if (ev.nativeKeyEvent.repeatCount == 0) startJog(backward = false)
                        } else if (ev.type == KeyEventType.KeyUp) stopJog()
                        interactionTick++
                        true
                    }
                    else -> false
                }
            }
            .focusable()
            .mediaGestures(
                zoom = zoom,
                pageOffset = pageOffset,
                scope = scope,
                canPrev = { index > 0 },
                canNext = { index < items.size - 1 },
                onCommit = { forward -> goInstant(forward) },
                onTap = {
                    controlsVisible = !controlsVisible
                    interactionTick++
                    runCatching { focusRequester.requestFocus() }
                },
                onDoubleTapNav = { forward -> goInstant(forward) },
                canHoldScrub = { player != null },
                onHoldStart = { forward -> startHoldScrub(forward) },
                onHoldEnd = { stopHoldScrub() },
            )
    ) {
        if (file == null) {
            Text("No media", Modifier.align(Alignment.Center), color = Color.Gray, fontSize = 14.sp)
        } else {
            // Neighbors, parked just off-screen (±container width) and slid in via the page offset.
            items.getOrNull(index - 1)?.let { prev ->
                NeighborPreview(prev, previewBitmaps[prev.absolutePath]) {
                    translationX = pageOffset.value - zoom.containerSize.width
                }
            }
            items.getOrNull(index + 1)?.let { next ->
                NeighborPreview(next, previewBitmaps[next.absolutePath]) {
                    translationX = pageOffset.value + zoom.containerSize.width
                }
            }

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
                            translationX = zoom.offset.x + pageOffset.value
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
            // Keep the (memory-cached) thumbnail on screen until the real content can
            // actually draw, so committing a swipe never flashes black/"Loading…" —
            // the preview simply persists and then dissolves into the live media.
            val contentReady = if (isVideo) {
                videoFrameReady
            } else {
                painter?.state is AsyncImagePainter.State.Success && zoom.fittedSize != Size.Zero
            }
            if (!contentReady) {
                NeighborPreview(file, previewBitmaps[file.absolutePath]) {
                    translationX = pageOffset.value
                }
            }
            if (controlsVisible) {
                PaneOverlay(
                    file, index, items.size, zoom, player, extraButtons,
                    onInteraction = { interactionTick++ },
                    onSeekDragChange = { seekDragging = it },
                )
            }
        }
    }
}

/**
 * A fit-to-screen thumbnail of an adjacent (or still-loading current) item, positioned via
 * [layer]. Draws the pre-decoded [bitmap] when available — a synchronous draw with no async
 * gap — and falls back to an async request only the first time an item is ever seen.
 */
@Composable
private fun NeighborPreview(file: File, bitmap: ImageBitmap?, layer: GraphicsLayerScope.() -> Unit) {
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(layer),
        )
        return
    }
    val context = LocalContext.current
    AsyncImage(
        model = remember(file) {
            ImageRequest.Builder(context)
                .data(file)
                .apply {
                    if (file.isVideoFile()) {
                        // 25% in (not a fixed 1s, which overshoots short clips), decoding the
                        // exact frame: keyframe-only retrieval snaps fade-ins to the black
                        // first keyframe.
                        videoFramePercent(0.25)
                        videoFrameOption(MediaMetadataRetriever.OPTION_CLOSEST)
                    }
                }
                .build()
        },
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer(layer),
    )
}

/**
 * Decodes a preview of [file] through the Coil pipeline (EXIF rotation, the video-thumb
 * disk cache, and the memory cache all apply), returning a software bitmap the previews
 * can draw synchronously.
 */
private suspend fun loadPreviewBitmap(context: Context, file: File): ImageBitmap? {
    val request = ImageRequest.Builder(context)
        .data(file)
        .apply {
            if (file.isVideoFile()) {
                videoFramePercent(0.25)
                videoFrameOption(MediaMetadataRetriever.OPTION_CLOSEST)
            }
        }
        .size(1600)
        .allowHardware(false)
        .build()
    val result = context.imageLoader.execute(request)
    return ((result as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap?.asImageBitmap()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoxScope.PaneOverlay(
    file: File,
    index: Int,
    count: Int,
    zoom: ZoomState,
    player: ExoPlayer?,
    extraButtons: @Composable RowScope.() -> Unit,
    onInteraction: () -> Unit = {},
    onSeekDragChange: (Boolean) -> Unit = {},
) {
    // Observe (without consuming) any press on the control bars, so buttons the
    // caller injects via extraButtons also reset the auto-hide countdown.
    val notifyPress = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            onInteraction()
        }
    }
    Row(
        Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .background(Color(0xCC000000))
            .then(notifyPress)
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
        var scrubWasPlaying by remember(player) { mutableStateOf(false) }
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
        // Scrub pump: while dragging, seek to the latest finger position, but only once the
        // previous seek has finished decoding (state back to READY). Exact seeks give a frame
        // for every position instead of sparse keyframes, and pacing them at decode speed —
        // always jumping to the newest target — keeps the preview from stuttering behind a
        // backlog of stale seeks.
        LaunchedEffect(player, dragging) {
            if (!dragging) return@LaunchedEffect
            var lastSeeked = -1L
            while (true) {
                val target = position
                if (target != lastSeeked && player.playbackState == Player.STATE_READY) {
                    player.seekTo(target)
                    lastSeeked = target
                }
                delay(33)
            }
        }
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0xCC000000))
                .then(notifyPress)
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .background(Color(0xFF222428))
                    .clickable { player.playWhenReady = !player.playWhenReady }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(if (playing) "❚❚" else "▶", color = Color.White, fontSize = 16.sp)
            }
            Text(formatTime(position), color = Color.White, fontSize = 14.sp)
            Slider(
                value = if (duration > 0) position.toFloat().coerceIn(0f, duration.toFloat()) else 0f,
                onValueChange = {
                    if (!dragging) {
                        // Pause for the scrub so playback doesn't fight the preview seeks;
                        // restored on release.
                        dragging = true
                        onSeekDragChange(true)
                        scrubWasPlaying = player.playWhenReady
                        player.playWhenReady = false
                        player.setSeekParameters(SeekParameters.EXACT)
                    }
                    position = it.toLong() // the scrub pump above issues the actual seeks
                },
                onValueChangeFinished = {
                    // Exact seek on release so you land precisely where you let go.
                    player.setSeekParameters(SeekParameters.DEFAULT)
                    player.seekTo(position)
                    player.playWhenReady = scrubWasPlaying
                    dragging = false
                    onSeekDragChange(false)
                    onInteraction()
                },
                valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                thumb = {
                    Box(
                        Modifier
                            .size(24.dp)
                            .background(Color.White, CircleShape),
                    )
                },
            )
            Text(formatTime(duration), color = Color.White, fontSize = 14.sp)
        }
    }
}

@Composable
private fun rememberVideoPlayer(
    file: File,
    zoom: ZoomState,
    onFirstFrame: () -> Unit = {},
): ExoPlayer {
    val context = LocalContext.current
    val player = remember(file) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = true
            prepare()
        }
    }
    val firstFrameCb = rememberUpdatedState(onFirstFrame)
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    zoom.contentPixels = Size(videoSize.width.toFloat(), videoSize.height.toFloat())
                }
            }

            override fun onRenderedFirstFrame() {
                firstFrameCb.value()
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    // Pause (video + audio) whenever the app leaves the screen or loses focus, and pick
    // playback back up on return only if it was playing when we left.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(player, lifecycleOwner) {
        var wasPlaying = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    wasPlaying = player.playWhenReady
                    player.playWhenReady = false
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (wasPlaying) player.playWhenReady = true
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
