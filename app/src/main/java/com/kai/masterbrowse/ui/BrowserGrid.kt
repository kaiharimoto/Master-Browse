package com.kai.masterbrowse.ui

import android.media.MediaMetadataRetriever
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameOption
import coil.request.videoFramePercent
import com.kai.masterbrowse.FileRepo
import com.kai.masterbrowse.Prefs
import com.kai.masterbrowse.SortMode
import com.kai.masterbrowse.ThumbCache
import com.kai.masterbrowse.isVideoFile
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Tile grid for one directory: subfolders first, then media files.
 * Pinch anywhere on the grid to change the tile size (persisted).
 *
 * Two layouts, chosen by [aspectMode]:
 *  - square grid ([LazyVerticalGrid], cropped 1:1 thumbnails)
 *  - justified rows ([JustifiedGrid], each tile at its true aspect ratio)
 */
@Composable
fun BrowserGrid(
    dir: File,
    thumbVersion: Int = 0,
    sortMode: SortMode = Prefs.sortMode,
    sortDesc: Boolean = Prefs.sortDesc,
    aspectMode: Boolean = Prefs.aspectMode,
    filter: String = "",
    dirsOnly: Boolean = false,
    onOpenDir: (File) -> Unit,
    onOpenMedia: (List<File>, Int) -> Unit,
    dirMenu: ((File) -> List<Pair<String, () -> Unit>>)? = null,
    fileMenu: ((File) -> List<Pair<String, () -> Unit>>)? = null,
) {
    var tileDp by remember { mutableFloatStateOf(Prefs.tileSizeDp) }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var pinching = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val pressed = event.changes.count { it.pressed }
                        if (pressed == 0) break
                        if (pressed >= 2) {
                            pinching = true
                            val zoomChange = event.calculateZoom()
                            if (zoomChange != 1f) {
                                tileDp = (tileDp * zoomChange).coerceIn(70f, 400f)
                            }
                            event.changes.forEach { it.consume() }
                        }
                    }
                    if (pinching) Prefs.tileSizeDp = tileDp
                }
            }
    ) {
        val entries by produceState<Pair<List<File>, List<File>>?>(
            null, dir, thumbVersion, sortMode, sortDesc,
        ) {
            value = withContext(Dispatchers.IO) { FileRepo.listEntries(dir, sortMode, sortDesc) }
        }
        val e = entries
        if (e == null) {
            Text("Loading…", Modifier.align(Alignment.Center), color = Color.DarkGray, fontSize = 13.sp)
            return@Box
        }
        val (dirs, media) = remember(e, filter, dirsOnly) {
            val d = if (filter.isBlank()) e.first
            else e.first.filter { it.name.contains(filter, ignoreCase = true) }
            val m = when {
                dirsOnly -> emptyList()
                filter.isBlank() -> e.second
                else -> e.second.filter { it.name.contains(filter, ignoreCase = true) }
            }
            d to m
        }
        // Generate every video thumbnail in this folder up front (cached on disk),
        // so scrolling never waits on frame extraction — only on-screen JPEG decodes.
        LaunchedEffect(media) { ThumbCache.prefetch(media) }
        if (dirs.isEmpty() && media.isEmpty()) {
            Text(
                if (filter.isBlank()) "Empty" else "No matches",
                Modifier.align(Alignment.Center),
                color = Color.DarkGray,
                fontSize = 13.sp,
            )
            return@Box
        }
        val scope = rememberCoroutineScope()
        val scrollKey = dir.absolutePath + if (aspectMode) "|A" else "|S"
        if (aspectMode) {
            val listState = remember(scrollKey) {
                val saved = ScrollMemory.get(scrollKey)
                LazyListState(saved?.first ?: 0, saved?.second ?: 0)
            }
            DisposableEffect(scrollKey) {
                onDispose {
                    ScrollMemory.put(
                        scrollKey,
                        listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset,
                    )
                }
            }
            JustifiedGrid(
                dirs = dirs,
                media = media,
                targetRowHeightDp = tileDp,
                thumbVersion = thumbVersion,
                listState = listState,
                onOpenDir = onOpenDir,
                onOpenMedia = onOpenMedia,
                dirMenu = dirMenu,
                fileMenu = fileMenu,
            )
            FastScrollThumb(
                progress = {
                    val info = listState.layoutInfo
                    val range = info.totalItemsCount - info.visibleItemsInfo.size
                    if (range <= 0) 0f else listState.firstVisibleItemIndex.toFloat() / range
                },
                thumbFrac = {
                    val info = listState.layoutInfo
                    if (info.totalItemsCount == 0) 1f
                    else info.visibleItemsInfo.size.toFloat() / info.totalItemsCount
                },
                visible = {
                    val info = listState.layoutInfo
                    info.totalItemsCount > info.visibleItemsInfo.size * 3
                },
                onDragTo = { frac ->
                    val info = listState.layoutInfo
                    val range = (info.totalItemsCount - info.visibleItemsInfo.size).coerceAtLeast(0)
                    scope.launch { listState.scrollToItem((frac * range).roundToInt()) }
                },
            )
        } else {
            val gridState = remember(scrollKey) {
                val saved = ScrollMemory.get(scrollKey)
                LazyGridState(saved?.first ?: 0, saved?.second ?: 0)
            }
            DisposableEffect(scrollKey) {
                onDispose {
                    ScrollMemory.put(
                        scrollKey,
                        gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset,
                    )
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(tileDp.dp),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(2.dp),
            ) {
                items(dirs, key = { "d:" + it.absolutePath }) { d ->
                    TileContent(
                        file = d,
                        isDir = true,
                        thumbVersion = thumbVersion,
                        onClick = { onOpenDir(d) },
                        menu = dirMenu?.invoke(d),
                        modifier = Modifier.aspectRatio(1f),
                    )
                }
                itemsIndexed(media, key = { _, f -> "f:" + f.absolutePath }) { i, f ->
                    TileContent(
                        file = f,
                        isDir = false,
                        thumbVersion = thumbVersion,
                        onClick = { onOpenMedia(media, i) },
                        menu = fileMenu?.invoke(f),
                        modifier = Modifier.aspectRatio(1f),
                    )
                }
            }
            FastScrollThumb(
                progress = {
                    val info = gridState.layoutInfo
                    val range = info.totalItemsCount - info.visibleItemsInfo.size
                    if (range <= 0) 0f else gridState.firstVisibleItemIndex.toFloat() / range
                },
                thumbFrac = {
                    val info = gridState.layoutInfo
                    if (info.totalItemsCount == 0) 1f
                    else info.visibleItemsInfo.size.toFloat() / info.totalItemsCount
                },
                visible = {
                    val info = gridState.layoutInfo
                    info.totalItemsCount > info.visibleItemsInfo.size * 3
                },
                onDragTo = { frac ->
                    val info = gridState.layoutInfo
                    val range = (info.totalItemsCount - info.visibleItemsInfo.size).coerceAtLeast(0)
                    scope.launch { gridState.scrollToItem((frac * range).roundToInt()) }
                },
            )
        }
    }
}

/**
 * The visual content of a single tile: thumbnail, video badge, name label, and
 * long-press menu. The caller sizes it via [modifier] (a 1:1 square in the grid,
 * or an aspect-ratio'd box in the justified layout).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TileContent(
    file: File,
    isDir: Boolean,
    thumbVersion: Int,
    onClick: () -> Unit,
    menu: List<Pair<String, () -> Unit>>?,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(
        modifier
            .background(Color(0xFF17181B))
            .combinedClickable(
                onClick = onClick,
                onLongClick = if (menu.isNullOrEmpty()) null else ({ menuOpen = true }),
            )
    ) {
        val context = LocalContext.current
        val thumbFile: File? = if (isDir) {
            produceState<File?>(null, file, thumbVersion) {
                value = withContext(Dispatchers.IO) { FileRepo.thumbFor(file) }
            }.value
        } else file
        if (thumbFile != null) {
            AsyncImage(
                model = remember(thumbFile) {
                    ImageRequest.Builder(context)
                        .data(thumbFile)
                        .apply {
                            if (thumbFile.isVideoFile()) {
                                // 25% in (not a fixed 1s, which overshoots short clips), decoding
                                // the exact frame: keyframe-only retrieval snaps fade-ins to the
                                // black first keyframe.
                                videoFramePercent(0.25)
                                videoFrameOption(MediaMetadataRetriever.OPTION_CLOSEST)
                            }
                        }
                        .crossfade(false)
                        .build()
                },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (!isDir && file.isVideoFile()) {
            Text(
                "▶",
                Modifier
                    .align(Alignment.TopEnd)
                    .background(Color(0x99000000))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                color = Color.White,
                fontSize = 12.sp,
            )
        }
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0xB3000000))
                .padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isDir) Text("▸ ", color = Color(0xFF9BA3AE), fontSize = 12.sp)
            Text(
                file.name,
                color = Color.White,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!menu.isNullOrEmpty()) {
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                menu.forEach { (label, action) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            menuOpen = false
                            action()
                        },
                    )
                }
            }
        }
    }
}
