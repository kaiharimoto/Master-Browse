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
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
            .background(Color.Black)
            .then(if (floating) Modifier else Modifier.windowInsetsPadding(WindowInsets.safeDrawing))
    ) {
        Row(
            Modifier.fillMaxWidth().background(Color(0xFF101114)).padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PaneButton("BACK", onClick = onClose)
            Text(
                video.name,
                Modifier.weight(1f).padding(horizontal = 4.dp),
                color = Color(0xFFB8BCC2),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            FRAME_COUNTS.forEach { n ->
                PaneButton("$n", active = count == n) {
                    count = n
                    Prefs.frameCount = n
                }
            }
            PaneButton("ALL") {
                selected.clear()
                selected.addAll(times)
            }
            PaneButton("NONE") { selected.clear() }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                duration < 0 -> Text("Loading…", Modifier.align(Alignment.Center), color = Color.DarkGray, fontSize = 13.sp)
                times.isEmpty() -> Text("Can't read this video", Modifier.align(Alignment.Center), color = Color.DarkGray, fontSize = 13.sp)
                else -> {
                    val aspect = thumbs.values.firstOrNull()?.let { it.width.toFloat() / it.height } ?: (16f / 9f)
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(if (floating) 140.dp else 220.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
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

        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF101114))
                .horizontalScroll(rememberScrollState())
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                progress ?: "${selected.size} selected — save to:",
                Modifier.padding(horizontal = 4.dp),
                color = Color(0xFF9BA3AE),
                fontSize = 12.sp,
            )
            if (categories.isEmpty()) {
                Text("no categories yet", color = Color(0xFF6B6F76), fontSize = 12.sp)
            }
            categories.forEach { c -> PaneButton(c.name) { saveTo(c) } }
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
            .background(Color(0xFF17181B))
            .clickable(onClick = onClick)
    ) {
        if (bitmap != null) {
            Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Text(
            formatFrameTime(time),
            Modifier
                .align(Alignment.BottomStart)
                .background(Color(0xB3000000))
                .padding(horizontal = 6.dp, vertical = 2.dp),
            color = Color.White,
            fontSize = 11.sp,
        )
        if (savedIn.isNotEmpty()) {
            Text(
                "✓ " + savedIn.joinToString(", "),
                Modifier
                    .align(Alignment.TopEnd)
                    .background(Color(0xCC2F5D3A))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                color = Color.White,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            Box(Modifier.fillMaxSize().border(3.dp, Color(0xFF4C8DFF)))
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
