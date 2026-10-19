package com.kai.masterbrowse.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kai.masterbrowse.Dataset
import com.kai.masterbrowse.Frames
import com.kai.masterbrowse.Prefs
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val FRAME_COUNTS = listOf(12, 24, 48, 96)

/**
 * Pulls evenly spaced stills out of [video] as a grid; tap the good ones, then a category to
 * save them there at full resolution (PNG, via [Frames.save]). Frames already saved show
 * which categories hold them.
 */
@Composable
fun FramePicker(video: File, categories: List<File>, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val floating = LocalAppHost.current.floating
    BackHandler(onBack = onClose)

    var count by remember { mutableIntStateOf(Prefs.frameCount) }
    val duration by produceState(-1L, video) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val r = Frames.open(video)
                try {
                    Frames.durationMs(r)
                } finally {
                    r.release()
                }
            }.getOrDefault(0L)
        }
    }
    val times = remember(duration, count) { Dataset.evenTimes(duration, count) }
    val thumbs = remember(video) { mutableStateMapOf<Long, ImageBitmap>() }
    val selected = remember(video) { mutableStateListOf<Long>() }
    var savedVersion by remember { mutableIntStateOf(0) }
    var progress by remember { mutableStateOf<String?>(null) }

    // Decode the strip in order on one retriever, so the grid fills top to bottom. Keyframe
    // decoding would be quicker but would show frames up to seconds away from the ones saved.
    LaunchedEffect(video, times) {
        val keep = times.toSet()
        thumbs.keys.retainAll(keep)
        selected.retainAll(keep)
        withContext(Dispatchers.IO) {
            val r = runCatching { Frames.open(video) }.getOrNull() ?: return@withContext
            try {
                for (t in times) {
                    ensureActive()
                    if (t in thumbs) continue
                    Frames.thumb(r, t, maxDim = 360)?.let { thumbs[t] = it.asImageBitmap() }
                }
            } finally {
                r.release()
            }
        }
    }

    // time -> names of the categories already holding that frame
    val savedIn by produceState(emptyMap<Long, List<String>>(), video, categories, savedVersion) {
        value = withContext(Dispatchers.IO) {
            buildMap<Long, MutableList<String>> {
                for (c in categories) {
                    for (t in Dataset.framesIn(c, video)) getOrPut(t) { mutableListOf() }.add(c.name)
                }
            }
        }
    }

    fun saveTo(category: File) {
        val picks = selected.sorted()
        if (picks.isEmpty()) {
            Toast.makeText(context, "Tap frames to select them first", Toast.LENGTH_SHORT).show()
            return
        }
        if (progress != null) return
        scope.launch {
            var failed = 0
            withContext(Dispatchers.IO) {
                val r = runCatching { Frames.open(video) }.getOrNull()
                if (r == null) {
                    failed = picks.size
                    return@withContext
                }
                try {
                    picks.forEachIndexed { i, t ->
                        progress = "Saving ${i + 1}/${picks.size} to ${category.name}…"
                        if (!Frames.save(category, video, t, r)) failed++
                    }
                } finally {
                    r.release()
                }
            }
            progress = null
            selected.clear()
            savedVersion++
            val msg = "${picks.size - failed} frames saved to ${category.name}" +
                if (failed > 0) ", $failed failed" else ""
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Ui.Bg)
            .then(if (floating) Modifier else Modifier.windowInsetsPadding(WindowInsets.safeDrawing))
    ) {
        ToolBar {
            IconPill(MbIcons.Back, "Back to the video", onClick = onClose)
            Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                Text(
                    video.name,
                    color = Ui.Text,
                    fontSize = if (floating) 13.sp else 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!floating) {
                    Text(
                        "${times.size} stills · tap to select",
                        color = Ui.TextFaint,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                    )
                }
            }
            // How many stills to pull: a segmented pill, the active count lit.
            Row(
                Modifier.clip(Ui.Pill).background(Ui.Raised).padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                FRAME_COUNTS.forEach { n ->
                    Text(
                        "$n",
                        Modifier
                            .clip(Ui.Pill)
                            .background(if (count == n) Ui.Accent else Color.Transparent)
                            .clickable {
                                count = n
                                Prefs.frameCount = n
                            }
                            .padding(horizontal = if (floating) 9.dp else 13.dp, vertical = if (floating) 5.dp else 7.dp),
                        color = if (count == n) Ui.OnAccent else Ui.TextDim,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            PaneButton("All") {
                selected.clear()
                selected.addAll(times)
            }
            PaneButton("None") { selected.clear() }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                duration < 0 -> Text("Loading…", Modifier.align(Alignment.Center), color = Ui.TextFaint, fontSize = 14.sp)
                times.isEmpty() -> Text("Can't read this video", Modifier.align(Alignment.Center), color = Ui.TextFaint, fontSize = 14.sp)
                else -> {
                    val aspect = thumbs.values.firstOrNull()?.let { it.width.toFloat() / it.height } ?: (16f / 9f)
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(if (floating) 140.dp else 220.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(TILE_GAP_DP.dp),
                        verticalArrangement = Arrangement.spacedBy(TILE_GAP_DP.dp),
                        horizontalArrangement = Arrangement.spacedBy(TILE_GAP_DP.dp),
                    ) {
                        items(times, key = { it }) { t ->
                            FrameTile(
                                bitmap = thumbs[t],
                                aspect = aspect,
                                time = t,
                                selected = t in selected,
                                savedIn = savedIn[t].orEmpty(),
                            ) { if (!selected.remove(t)) selected.add(t) }
                        }
                    }
                }
            }
        }

        ToolBar(bottomEdge = false) {
            Text(
                progress ?: if (selected.isEmpty()) "Select stills, then save them to" else "Save ${selected.size} to",
                Modifier.padding(horizontal = 6.dp),
                color = if (progress != null) Ui.Accent else Ui.TextDim,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (categories.isEmpty()) {
                    Text("No categories yet", color = Ui.TextFaint, fontSize = 13.sp)
                }
                categories.forEach { c -> PaneButton(c.name, icon = MbIcons.Folder) { saveTo(c) } }
            }
        }
    }
}

@Composable
private fun FrameTile(
    bitmap: ImageBitmap?,
    aspect: Float,
    time: Long,
    selected: Boolean,
    savedIn: List<String>,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .aspectRatio(aspect)
            .clip(Ui.Tile)
            .background(Ui.Raised)
            .clickable(onClick = onClick)
    ) {
        if (bitmap != null) {
            Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Text(
            formatFrameTime(time),
            Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
                .clip(Ui.Pill)
                .background(Color(0x99000000))
                .padding(horizontal = 8.dp, vertical = 3.dp),
            color = Color.White,
            fontSize = 11.sp,
        )
        if (savedIn.isNotEmpty()) {
            Text(
                "Saved · " + savedIn.joinToString(", "),
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(Ui.Pill)
                    .background(Color(0xCC0A0B0D))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                color = Ui.Accent,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            Box(Modifier.fillMaxSize().border(3.dp, Ui.Accent, Ui.Tile).background(Color(0x268EA6FF)))
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(24.dp)
                    .clip(Ui.Pill)
                    .background(Ui.Accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(MbIcons.Check, contentDescription = "Selected", Modifier.size(16.dp), tint = Ui.OnAccent)
            }
        }
    }
}

/** 0:12.345 / 1:02:03.400 */
fun formatFrameTime(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    return if (h > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d.%03d", h, (s / 60) % 60, s % 60, ms % 1000)
    } else {
        String.format(Locale.ROOT, "%d:%02d.%03d", s / 60, s % 60, ms % 1000)
    }
}
