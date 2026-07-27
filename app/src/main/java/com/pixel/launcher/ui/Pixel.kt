package com.pixel.launcher.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------------- text

@Composable
fun DisplayText(
    text: String,
    size: TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    align: TextAlign? = null,
    letterSpacing: TextUnit = 0.sp,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = size,
            fontFamily = PixelFonts.Display,
            letterSpacing = letterSpacing,
            textAlign = align,
        ),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun BodyText(
    text: String,
    size: TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    align: TextAlign? = null,
    letterSpacing: TextUnit = 0.sp,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = size,
            fontFamily = PixelFonts.Body,
            letterSpacing = letterSpacing,
            textAlign = align,
        ),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

// --------------------------------------------------------------------- frame

/**
 * A hard-edged panel: two nested solid boxes rather than a stroked border, so
 * the frame stays exactly one pixel-block wide at any density and never gets
 * anti-aliased into a blur.
 */
@Composable
fun PixelPanel(
    modifier: Modifier = Modifier,
    fill: Color = LocalRetro.current.panel,
    edge: Color = LocalRetro.current.edge,
    borderWidth: Dp = 2.dp,
    contentPadding: Dp = 8.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .background(edge)
            .padding(borderWidth)
            .background(fill)
            .padding(contentPadding),
    ) {
        content()
    }
}

@Composable
fun PixelDivider(modifier: Modifier = Modifier, color: Color = LocalRetro.current.edge) {
    Box(modifier.fillMaxWidth().height(2.dp).background(color))
}

@Composable
fun PixelVerticalDivider(modifier: Modifier = Modifier, color: Color = LocalRetro.current.edge) {
    Box(modifier.width(2.dp).background(color))
}

// -------------------------------------------------------------------- glyphs

/**
 * Draws pixel art from a character grid: '#' is [color], '.' is [dim].
 * Cheaper and sharper than shipping a vector for a handful of shapes.
 */
@Composable
fun PixelGlyph(
    rows: List<String>,
    color: Color,
    modifier: Modifier = Modifier,
    dim: Color = color.copy(alpha = 0.45f),
) {
    Canvas(modifier) {
        if (rows.isEmpty()) return@Canvas
        val columns = rows.maxOf { it.length }
        if (columns == 0) return@Canvas
        val cell = minOf(size.width / columns, size.height / rows.size)
        val originX = (size.width - cell * columns) / 2f
        val originY = (size.height - cell * rows.size) / 2f
        // Half a pixel of overdraw stops seams appearing between blocks.
        val blockSize = Size(cell + 0.5f, cell + 0.5f)
        for (y in rows.indices) {
            val row = rows[y]
            for (x in row.indices) {
                val paint = when (row[x]) {
                    '#' -> color
                    '.' -> dim
                    else -> null
                } ?: continue
                drawRect(
                    color = paint,
                    topLeft = Offset(originX + x * cell, originY + y * cell),
                    size = blockSize,
                )
            }
        }
    }
}

/** Sliders glyph used by the built-in settings tile. */
val SettingsGlyph = listOf(
    "         ",
    "  #      ",
    "..#......",
    "  #      ",
    "         ",
    "      #  ",
    "......#..",
    "      #  ",
    "         ",
)

/** Push-pin glyph used by empty dock slots. */
val PinGlyph = listOf(
    "  ###  ",
    "  #.#  ",
    " ##### ",
    "  ###  ",
    "   #   ",
    "   #   ",
    "       ",
)

// ------------------------------------------------------------------ controls

@Composable
fun PixelButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    fontSize: TextUnit = 16.sp,
) {
    val retro = LocalRetro.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val fill = when {
        !enabled -> retro.panel
        selected -> retro.accentDim
        pressed -> retro.edge
        else -> retro.panel
    }
    val border = when {
        !enabled -> retro.edge
        selected -> retro.accent
        else -> retro.edge
    }
    val label = when {
        !enabled -> retro.textDim
        selected -> retro.background
        else -> retro.text
    }

    Box(
        modifier = modifier
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .background(border)
            .padding(2.dp)
            .background(fill)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        BodyText(text = text, size = fontSize, color = label)
    }
}

/** Segmented control. Each option is a hard-edged button. */
@Composable
fun PixelChoice(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEachIndexed { index, label ->
            PixelButton(
                text = label,
                onClick = { onSelect(index) },
                selected = index == selectedIndex,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** ON/OFF switch drawn as two blocks. */
@Composable
fun PixelToggle(checked: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val retro = LocalRetro.current
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier.clickable(
            interactionSource = interaction,
            indication = null,
            onClick = onToggle,
        ),
    ) {
        Box(
            modifier = Modifier
                .background(if (checked) retro.accent else retro.panel)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            BodyText("ON", 15.sp, if (checked) retro.background else retro.textDim)
        }
        Box(
            modifier = Modifier
                .background(if (checked) retro.panel else retro.accentDim)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            BodyText("OFF", 15.sp, if (checked) retro.textDim else retro.text)
        }
    }
}

/** A 0-100 value drawn as ten blocks with -/+ steppers. */
@Composable
fun PixelStepper(
    value: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    step: Int = 10,
    min: Int = 0,
    max: Int = 100,
) {
    val retro = LocalRetro.current
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PixelButton(
            text = "-",
            onClick = { onChange((value - step).coerceIn(min, max)) },
            enabled = value > min,
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val filled = ((value - min).toFloat() / (max - min) * BAR_BLOCKS).toInt()
            repeat(BAR_BLOCKS) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(14.dp)
                        .background(if (index < filled) retro.accent else retro.edge),
                )
            }
        }
        PixelButton(
            text = "+",
            onClick = { onChange((value + step).coerceIn(min, max)) },
            enabled = value < max,
        )
    }
}

private const val BAR_BLOCKS = 10

// ------------------------------------------------------------------- overlay

/**
 * CRT scanlines. Drawn as a single tiled 1x3 shader rather than hundreds of
 * rectangles, so the overlay costs one draw call per frame no matter how tall
 * the screen is.
 */
@Composable
fun ScanlineOverlay(modifier: Modifier = Modifier, strength: Float = 0.10f) {
    val brush = remember(strength) {
        val alpha = (strength.coerceIn(0f, 1f) * 255f).toInt()
        val bitmap = Bitmap.createBitmap(1, 3, Bitmap.Config.ARGB_8888)
        bitmap.setPixel(0, 0, android.graphics.Color.argb(alpha, 0, 0, 0))
        bitmap.setPixel(0, 1, android.graphics.Color.TRANSPARENT)
        bitmap.setPixel(0, 2, android.graphics.Color.argb(alpha / 3, 0, 0, 0))
        ShaderBrush(
            ImageShader(bitmap.asImageBitmap(), TileMode.Repeated, TileMode.Repeated),
        )
    }
    Box(modifier.fillMaxSize().drawBehind { drawRect(brush) })
}
