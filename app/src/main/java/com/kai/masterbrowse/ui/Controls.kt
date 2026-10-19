package com.kai.masterbrowse.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * True for controls drawn over media (the viewer's overlay bars), which use translucent
 * glass instead of a solid fill so the picture shows through. A local rather than a
 * parameter so the buttons callers inject through MediaPane's extraButtons pick it up.
 */
val LocalOnMedia = compositionLocalOf { false }

/**
 * The app's button: a pill with an optional leading icon. [active] fills it with the
 * accent (a toggle that is on, a category that holds the item). In the small floating
 * window a button with an icon drops its label to save room; the label stays as its
 * content description.
 */
@Composable
fun PaneButton(
    label: String,
    active: Boolean = false,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val compact = LocalAppHost.current.floating
    val onMedia = LocalOnMedia.current
    val iconOnly = compact && icon != null
    val fg = if (active) Ui.OnAccent else Ui.Text
    Row(
        Modifier
            .height(if (compact) 32.dp else 38.dp)
            .clip(Ui.Pill)
            .background(
                when {
                    active -> Ui.Accent
                    onMedia -> Ui.Glass
                    else -> Ui.Raised
                }
            )
            .then(if (onMedia && !active) Modifier.border(1.dp, Ui.GlassEdge, Ui.Pill) else Modifier)
            .clickable(onClick = onClick)
            .then(if (iconOnly) Modifier.semantics { contentDescription = label } else Modifier)
            .padding(horizontal = if (iconOnly) 8.dp else if (compact) 11.dp else 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, Modifier.size(if (compact) 16.dp else 18.dp), tint = fg)
        }
        if (!iconOnly) {
            Text(label, color = fg, fontSize = if (compact) 12.sp else 13.5.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

/** A round icon-only button for toolbars. [description] is what accessibility reads. */
@Composable
fun IconPill(
    icon: ImageVector,
    description: String,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val compact = LocalAppHost.current.floating
    val onMedia = LocalOnMedia.current
    Box(
        Modifier
            .size(if (compact) 32.dp else 40.dp)
            .clip(Ui.Pill)
            .background(
                when {
                    active -> Ui.Accent
                    onMedia -> Ui.Glass
                    else -> Color.Transparent
                }
            )
            .then(if (onMedia && !active) Modifier.border(1.dp, Ui.GlassEdge, Ui.Pill) else Modifier)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            Modifier.size(if (compact) 18.dp else 22.dp),
            tint = if (active) Ui.OnAccent else Ui.Text,
        )
    }
}

/**
 * A dataset category toggle: the 1–9 key it answers to as a small badge, its name, and
 * (on a video) how many stills it already holds from it.
 */
@Composable
fun CategoryChip(keyNumber: Int?, name: String, count: Int, active: Boolean, onClick: () -> Unit) {
    val compact = LocalAppHost.current.floating
    val fg = if (active) Ui.OnAccent else Ui.Text
    Row(
        Modifier
            .height(if (compact) 32.dp else 38.dp)
            .clip(Ui.Pill)
            .background(if (active) Ui.Accent else Ui.Raised)
            .clickable(onClick = onClick)
            .padding(start = if (keyNumber != null) 5.dp else 14.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (keyNumber != null) {
            Box(
                Modifier
                    .size(if (compact) 22.dp else 28.dp)
                    .clip(Ui.Pill)
                    .background(if (active) Color(0x260D1433) else Ui.RaisedHi),
                contentAlignment = Alignment.Center,
            ) {
                Text("$keyNumber", color = if (active) Ui.OnAccent else Ui.TextDim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Text(name, color = fg, fontSize = if (compact) 12.sp else 13.5.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        if (count > 0) {
            Text("$count", color = if (active) Ui.OnAccent else Ui.Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** A toolbar strip: the bar colour, a hairline on the edge facing the content. */
@Composable
fun ToolBar(
    modifier: Modifier = Modifier,
    bottomEdge: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val compact = LocalAppHost.current.floating
    Box(modifier.fillMaxWidth().background(Ui.Bar)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = if (compact) 4.dp else 10.dp, vertical = if (compact) 4.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 6.dp),
            content = content,
        )
        Box(
            Modifier
                .align(if (bottomEdge) Alignment.BottomCenter else Alignment.TopCenter)
                .fillMaxWidth()
                .height(1.dp)
                .background(Ui.Hairline)
        )
    }
}

/** A tinted notice strip (picking a thumbnail, multi-select) shown under the toolbar. */
@Composable
fun Banner(text: String, content: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Ui.AccentSoft)
            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text, Modifier.weight(1f), color = Ui.Accent, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
        content()
    }
}
