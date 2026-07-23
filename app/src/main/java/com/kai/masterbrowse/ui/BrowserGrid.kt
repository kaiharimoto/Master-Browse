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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
import com.kai.masterbrowse.isVideoFile
import java.io.File
import kotlinx.coroutines.Dispatchers
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
        val (dirs, media) = e
        if (dirs.isEmpty() && media.isEmpty()) {
            Text("Empty", Modifier.align(Alignment.Center), color = Color.DarkGray, fontSize = 13.sp)
            return@Box
        }
        if (aspectMode) {
            JustifiedGrid(
                dirs = dirs,
                media = media,
                targetRowHeightDp = tileDp,
                thumbVersion = thumbVersion,
                onOpenDir = onOpenDir,
                onOpenMedia = onOpenMedia,
                dirMenu = dirMenu,
                fileMenu = fileMenu,
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(tileDp.dp),
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
