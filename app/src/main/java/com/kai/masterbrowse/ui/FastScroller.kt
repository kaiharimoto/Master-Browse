package com.kai.masterbrowse.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * Remembers each folder's scroll position for the lifetime of the process, so
 * going UP or returning from the viewer lands where you left off. Square-grid
 * and justified layouts index differently (grid item vs packed row), so they
 * use separate keys ("<path>|S" / "<path>|A").
 */
object ScrollMemory {
    private val map = HashMap<String, Pair<Int, Int>>()
    fun get(key: String): Pair<Int, Int>? = map[key]
    fun put(key: String, indexAndOffset: Pair<Int, Int>) { map[key] = indexAndOffset }
}

/**
 * Right-edge fast-scroll thumb for a lazy grid/list, shown only when [visible]
 * (content several viewports tall). State is read through lambdas so scrolling
 * recomposes just this composable.
 *
 * @param progress 0..1 scroll fraction of the list.
 * @param thumbFrac visible/total fraction, sizing the thumb.
 * @param onDragTo jump the list to a 0..1 fraction.
 */
@Composable
fun BoxScope.FastScrollThumb(
    progress: () -> Float,
    thumbFrac: () -> Float,
    visible: () -> Boolean,
    onDragTo: (Float) -> Unit,
) {
    if (!visible()) return
    BoxWithConstraints(
        Modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .width(24.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    val h = size.height.toFloat().coerceAtLeast(1f)
                    onDragTo((down.position.y / h).coerceIn(0f, 1f))
                    drag(down.id) { change ->
                        change.consume()
                        onDragTo((change.position.y / h).coerceIn(0f, 1f))
                    }
                }
            }
    ) {
        val frac = thumbFrac().coerceIn(0.06f, 1f)
        val thumbH = maxHeight * frac
        val offsetY = (maxHeight - thumbH) * progress().coerceIn(0f, 1f)
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(y = offsetY)
                .padding(end = 2.dp)
                .width(6.dp)
                .height(thumbH)
                .background(Color(0xFF3A3D44))
        )
    }
}
