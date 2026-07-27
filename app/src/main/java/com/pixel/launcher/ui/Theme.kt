package com.pixel.launcher.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.pixel.launcher.R
import com.pixel.launcher.audio.Sfx
import com.pixel.launcher.core.IconLoader
import com.pixel.launcher.core.Palette
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
)

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

/** Press Start 2P for headings, VT323 for anything that has to stay readable. */
object PixelFonts {
    val Display = FontFamily(Font(R.font.pressstart2p))
    val Body = FontFamily(Font(R.font.vt323))
}

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
