package com.kai.masterbrowse.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One row of a menu (or one button of a confirmation). */
data class MenuEntry(
    val label: String,
    val highlighted: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * Drives the inline menu/confirmation card used inside the floating window.
 *
 * The overlay cannot use Compose [DropdownMenu] or AlertDialog: both add a *new* window
 * (TYPE_APPLICATION_PANEL / a Dialog window) using the ComposeView's application window
 * token, and inside a Service-owned TYPE_APPLICATION_OVERLAY window that token is not an
 * activity token, so the add throws BadTokenException. Everything the overlay pops up has
 * to be drawn inside the overlay's own window instead — that is what this does.
 */
class MenuController {
    var title by mutableStateOf<String?>(null)
        private set
    var entries by mutableStateOf<List<MenuEntry>?>(null)
        private set

    fun show(title: String? = null, entries: List<MenuEntry>) {
        this.title = title
        this.entries = entries
    }

    fun dismiss() {
        entries = null
        title = null
    }
}

/**
 * Null in the fullscreen activity (which keeps its real anchored dropdowns and dialogs),
 * non-null inside the floating window.
 */
val LocalMenuController = staticCompositionLocalOf<MenuController?> { null }

/** Wraps the overlay's content and draws the menu card on top of it, in the same window. */
@Composable
fun MenuHostBox(controller: MenuController, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalMenuController provides controller) {
        Box(Modifier.fillMaxSize()) {
            content()
            val entries = controller.entries
            if (entries != null) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color(0xB3000000))
                        // detectTapGestures, not clickable: no ripple and no focus side effects.
                        .pointerInput(Unit) { detectTapGestures { controller.dismiss() } },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth(0.88f)
                            .clip(Ui.Card)
                            .background(Ui.Raised)
                            .border(1.dp, Ui.Hairline, Ui.Card)
                            // Swallow taps on the card so they don't dismiss it.
                            .pointerInput(Unit) { detectTapGestures { } }
                            .verticalScroll(rememberScrollState()),
                    ) {
                        controller.title?.let {
                            Text(
                                it,
                                Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 6.dp),
                                color = Ui.TextDim,
                                fontSize = 13.sp,
                            )
                        }
                        entries.forEach { entry ->
                            Text(
                                entry.label,
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        controller.dismiss()
                                        entry.onClick()
                                    }
                                    .padding(horizontal = 18.dp, vertical = 13.dp),
                                color = if (entry.highlighted) Ui.Text else Ui.TextDim,
                                fontSize = 14.5.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A [PaneButton] (or, with [iconOnly], an [IconPill]) that opens a menu: a real anchored
 * [DropdownMenu] in the activity, the inline card of [MenuHostBox] in the floating window.
 */
@Composable
fun MenuButton(
    label: String,
    icon: ImageVector? = null,
    iconOnly: Boolean = false,
    entries: () -> List<MenuEntry>,
) {
    val controller = LocalMenuController.current
    @Composable
    fun Trigger(onClick: () -> Unit) {
        if (iconOnly && icon != null) IconPill(icon, label, onClick = onClick)
        else PaneButton(label, icon = icon, onClick = onClick)
    }
    if (controller != null) {
        Trigger { controller.show(entries = entries()) }
        return
    }
    var open by remember { mutableStateOf(false) }
    Box {
        Trigger { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            entries().forEach { entry ->
                DropdownMenuItem(
                    text = {
                        Text(
                            entry.label,
                            color = if (entry.highlighted) Ui.Text else Ui.TextDim,
                        )
                    },
                    onClick = {
                        open = false
                        entry.onClick()
                    },
                )
            }
        }
    }
}
