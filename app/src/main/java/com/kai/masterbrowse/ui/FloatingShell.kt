package com.kai.masterbrowse.ui

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/** Height of the floating window's title bar, in the window's own (scaled) dp. */
const val FLOATING_TITLE_BAR_DP = 34f

/**
 * Chrome around the mini app inside the floating window: a title bar you drag the window
 * by, EXPAND / CLOSE buttons, and a corner grip you resize by.
 *
 * The handles are deliberately separate subtrees from [content]. MediaPane claims
 * horizontal drags for paging and pinches for zoom, and BrowserGrid claims pinches for
 * tile size — none of those pointerInput modifiers ever see an event that lands on the
 * chrome, so the two gesture sets cannot fight.
 */
@Composable
fun FloatingShell(
    title: String,
    onMoveStart: () -> Unit,
    onMoveBy: (dx: Float, dy: Float) -> Unit,
    onMoveEnd: () -> Unit,
    onResizeStart: () -> Unit,
    onResizeBy: (dx: Float, dy: Float) -> Unit,
    onResizeEnd: () -> Unit,
    onExpand: () -> Unit,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(FLOATING_TITLE_BAR_DP.dp)
                    .background(Color(0xFF101114)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    RawTouchHandle(
                        Modifier.fillMaxSize(),
                        onStart = onMoveStart,
                        onDelta = onMoveBy,
                        onEnd = onMoveEnd,
                    )
                    // Text has no pointer-input modifier, so taps fall through to the
                    // handle underneath it.
                    Text(
                        title,
                        Modifier.align(Alignment.CenterStart).padding(horizontal = 8.dp),
                        color = Color(0xFFB8BCC2),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                PaneButton("EXPAND", onExpand)
                PaneButton("✕", onClose)
            }
            Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        }

        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .size(30.dp)
                .background(Color(0xCC222428)),
            contentAlignment = Alignment.Center,
        ) {
            RawTouchHandle(
                Modifier.fillMaxSize(),
                onStart = onResizeStart,
                onDelta = onResizeBy,
                onEnd = onResizeEnd,
            )
            Text("◢", color = Color(0xFFB8BCC2), fontSize = 13.sp)
        }
    }
}

/**
 * A drag handle backed by a plain Android [View] so it can read *screen-absolute*
 * coordinates.
 *
 * Compose reports pointer positions relative to the window. Move the window by the
 * reported delta and the finger's local position snaps back to where it started, the next
 * delta is ~0, and the window stalls and jitters instead of following the finger. Raw
 * coordinates do not move with the window, so the deltas stay correct.
 *
 * [onDelta] receives the total movement since the gesture started, not a per-event step.
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
private fun RawTouchHandle(
    modifier: Modifier,
    onStart: () -> Unit,
    onDelta: (dx: Float, dy: Float) -> Unit,
    onEnd: () -> Unit,
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            View(ctx).apply {
                var downX = 0f
                var downY = 0f
                setOnTouchListener { _, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            downX = event.rawX
                            downY = event.rawY
                            onStart()
                            true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            onDelta(event.rawX - downX, event.rawY - downY)
                            true
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            onEnd()
                            true
                        }
                        else -> false
                    }
                }
            }
        },
    )
}
