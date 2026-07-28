package com.pixel.launcher.ui

import android.graphics.Rect
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixel.launcher.core.IconLoader
import com.pixel.launcher.core.IconShape
import com.pixel.launcher.core.Tile

fun shapeOf(shape: IconShape): Shape = when (shape) {
    IconShape.CIRCLE -> CircleShape
    IconShape.ROUNDED_SQUARE -> RoundedCornerShape(percent = 22)
    IconShape.SQUARE -> RectangleShape
    IconShape.ORIGINAL -> RectangleShape
}

/**
 * The icon for one tile.
 *
 * Bitmaps are requested only once the tile is composed, and a cache hit is read
 * synchronously so that scrolling back to a page never flashes a placeholder.
 */
@Composable
fun TileIcon(tile: Tile, spec: IconLoader.Spec, size: Dp, modifier: Modifier = Modifier) {
    val retro = LocalRetro.current

    if (tile is Tile.Settings) {
        Box(
            modifier = modifier
                .size(size)
                .clip(shapeOf(spec.shape))
                .background(retro.accentDim),
            contentAlignment = Alignment.Center,
        ) {
            PixelGlyph(
                rows = SettingsGlyph,
                color = retro.background,
                dim = retro.background.copy(alpha = 0.55f),
                modifier = Modifier.size(size * 0.62f),
            )
        }
        return
    }

    if (tile is Tile.Group) {
        // A folder shows the first four members as a mini grid, the way a
        // physical set of app cards would stack in a slot.
        Box(
            modifier = modifier
                .size(size)
                .clip(shapeOf(spec.shape))
                .background(retro.panel)
                .padding(size * 0.09f),
            contentAlignment = Alignment.Center,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(size * 0.04f)) {
                repeat(2) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(size * 0.04f)) {
                        repeat(2) { column ->
                            val member = tile.entries.getOrNull(row * 2 + column)
                            if (member == null) {
                                Box(Modifier.size(size * 0.37f))
                            } else {
                                TileIcon(Tile.App(member), spec, size * 0.37f)
                            }
                        }
                    }
                }
            }
        }
        return
    }

    val entry = (tile as Tile.App).entry
    val loader = LocalIcons.current
    var image by remember(entry.key, spec) { mutableStateOf(loader.cached(entry.key)) }

    LaunchedEffect(entry.key, spec) {
        if (image == null) image = loader.load(entry, spec)
    }

    val bitmap = image
    if (bitmap == null) {
        // Placeholder keeps the grid from reflowing while icons stream in.
        Box(
            modifier = modifier
                .size(size)
                .clip(shapeOf(spec.shape))
                .background(retro.panel),
        )
    } else {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = modifier.size(size),
            // Nearest-neighbour is what turns a small bitmap into crisp pixel art;
            // anything else would blur it back into mush.
            filterQuality = if (spec.pixelate) FilterQuality.None else FilterQuality.Medium,
        )
    }
}

/** One cell of the drawer or the dock. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppTile(
    tile: Tile,
    spec: IconLoader.Spec,
    iconSize: Dp,
    modifier: Modifier = Modifier,
    label: String? = null,
    labelSize: TextUnit = 14.sp,
    highlight: Boolean = false,
    onClick: (Rect?) -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val retro = LocalRetro.current
    val haptics = LocalHapticFeedback.current
    val settings = LocalSettings.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    var bounds by remember { mutableStateOf<Rect?>(null) }

    val longPress = onLongClick
    val longClickHandler: (() -> Unit)? = if (longPress == null) {
        null
    } else {
        {
            if (settings.haptics) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            longPress()
        }
    }

    Column(
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                val box = coordinates.boundsInWindow()
                bounds = Rect(
                    box.left.toInt(),
                    box.top.toInt(),
                    box.right.toInt(),
                    box.bottom.toInt(),
                )
            }
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onLongClick = longClickHandler,
                onClick = { onClick(bounds) },
            )
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.padding(bottom = if (label != null) 4.dp else 0.dp),
            contentAlignment = Alignment.Center,
        ) {
            // The press state nudges the icon instead of drawing a ripple: a
            // material ripple would be the one non-pixel thing on screen.
            TileIcon(
                tile = tile,
                spec = spec,
                size = if (pressed) iconSize * 0.88f else iconSize,
            )
        }
        if (label != null) {
            BodyText(
                text = label,
                size = labelSize,
                color = when {
                    highlight -> retro.accent
                    pressed -> retro.accent
                    else -> retro.text
                },
                align = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
