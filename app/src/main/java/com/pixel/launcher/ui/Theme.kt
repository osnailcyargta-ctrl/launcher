package com.pixel.launcher.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.pixel.launcher.R
import com.pixel.launcher.audio.Sfx
import com.pixel.launcher.core.IconLoader
import com.pixel.launcher.core.Palette
import com.pixel.launcher.core.PixelFont
import com.pixel.launcher.core.Settings

/** A CRT colour scheme. Five of them ship with the launcher. */
@Immutable
data class RetroPalette(
    val id: Palette,
    val label: String,
    val background: Color,
    val panel: Color,
    val edge: Color,
    val accent: Color,
    val accentDim: Color,
    val text: Color,
    val textDim: Color,
) {
    /**
     * Four shades, darkest first. Used to re-colour app icons into the palette
     * the same way a two-bit handheld would.
     */
    val shades: List<Int>
        get() = listOf(
            background.toArgb(),
            accentDim.toArgb(),
            accent.toArgb(),
            text.toArgb(),
        )
}

private val Green = RetroPalette(
    id = Palette.GREEN,
    label = "GREEN",
    background = Color(0xFF050A06),
    panel = Color(0xFF0C1710),
    edge = Color(0xFF1B3222),
    accent = Color(0xFF7CFF6B),
    accentDim = Color(0xFF2F8C3C),
    text = Color(0xFFCFF7C4),
    textDim = Color(0xFF6E9A73),
)

private val Amber = RetroPalette(
    id = Palette.AMBER,
    label = "AMBER",
    background = Color(0xFF0A0703),
    panel = Color(0xFF17100A),
    edge = Color(0xFF382613),
    accent = Color(0xFFFFB43C),
    accentDim = Color(0xFF8C5C1E),
    text = Color(0xFFF7E2C4),
    textDim = Color(0xFF9A8266),
)

private val Cyan = RetroPalette(
    id = Palette.CYAN,
    label = "CYAN",
    background = Color(0xFF03080A),
    panel = Color(0xFF0A1619),
    edge = Color(0xFF15343D),
    accent = Color(0xFF5CE1FF),
    accentDim = Color(0xFF1E6E8C),
    text = Color(0xFFC4EDF7),
    textDim = Color(0xFF669099),
)

private val Magenta = RetroPalette(
    id = Palette.MAGENTA,
    label = "MAGENTA",
    background = Color(0xFF0A0409),
    panel = Color(0xFF190A16),
    edge = Color(0xFF3B1533),
    accent = Color(0xFFFF6BD6),
    accentDim = Color(0xFF8C1E6E),
    text = Color(0xFFF7C4EB),
    textDim = Color(0xFF99668D),
)

private val Ice = RetroPalette(
    id = Palette.ICE,
    label = "ICE",
    background = Color(0xFF06070A),
    panel = Color(0xFF11141A),
    edge = Color(0xFF262D38),
    accent = Color(0xFFE6ECF5),
    accentDim = Color(0xFF5A6472),
    text = Color(0xFFE6ECF5),
    textDim = Color(0xFF7E8794),
)

fun paletteOf(palette: Palette): RetroPalette = when (palette) {
    Palette.GREEN -> Green
    Palette.AMBER -> Amber
    Palette.CYAN -> Cyan
    Palette.MAGENTA -> Magenta
    Palette.ICE -> Ice
}

val AllPalettes: List<RetroPalette> = listOf(Green, Amber, Cyan, Magenta, Ice)

/** The bundled typefaces, resolved lazily so unused ones are never loaded. */
object PixelFonts {
    val PressStart: FontFamily by lazy { FontFamily(Font(R.font.pressstart2p)) }
    val Silkscreen: FontFamily by lazy { FontFamily(Font(R.font.silkscreen)) }
    val Pixelify: FontFamily by lazy { FontFamily(Font(R.font.pixelifysans)) }
    val Vt323: FontFamily by lazy { FontFamily(Font(R.font.vt323)) }

    fun of(font: PixelFont): FontFamily = when (font) {
        PixelFont.PRESS_START -> PressStart
        PixelFont.SILKSCREEN -> Silkscreen
        PixelFont.PIXELIFY -> Pixelify
        PixelFont.VT323 -> Vt323
        PixelFont.MONO -> FontFamily.Monospace
    }

    /**
     * Press Start 2P is drawn on a much larger em square than the others, so a
     * shared point size would make it tower over them. This evens them out.
     */
    fun sizeFactor(font: PixelFont): Float = when (font) {
        PixelFont.PRESS_START -> 1.0f
        PixelFont.SILKSCREEN -> 1.15f
        PixelFont.PIXELIFY -> 1.35f
        PixelFont.VT323 -> 1.0f
        PixelFont.MONO -> 1.0f
    }
}

/** The resolved type for the current settings. */
@Immutable
data class LauncherTypography(
    val display: FontFamily,
    val displayFactor: Float,
    val body: FontFamily,
    val bodyFactor: Float,
    val scale: Float,
) {
    fun displayScale(): Float = displayFactor * scale
    fun bodyScale(): Float = bodyFactor * scale
}

fun typographyOf(settings: Settings): LauncherTypography = LauncherTypography(
    display = PixelFonts.of(settings.displayFont),
    displayFactor = PixelFonts.sizeFactor(settings.displayFont),
    body = PixelFonts.of(settings.bodyFont),
    bodyFactor = PixelFonts.sizeFactor(settings.bodyFont),
    scale = (settings.textScale / 100f).coerceIn(0.6f, 1.8f),
)

val LocalRetro = staticCompositionLocalOf<RetroPalette> {
    error("RetroPalette not provided")
}
val LocalSettings = staticCompositionLocalOf<Settings> {
    error("Settings not provided")
}
val LocalSfx = staticCompositionLocalOf<Sfx> {
    error("Sfx not provided")
}
val LocalIcons = staticCompositionLocalOf<IconLoader> {
    error("IconLoader not provided")
}
val LocalTypography = staticCompositionLocalOf<LauncherTypography> {
    error("Typography not provided")
}
