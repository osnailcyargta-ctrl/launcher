package com.pixel.launcher.core

import android.content.pm.LauncherActivityInfo
import android.os.UserHandle

/** How app icons are masked. */
enum class IconShape { CIRCLE, ROUNDED_SQUARE, SQUARE, ORIGINAL }

/** Retro CRT colour schemes. */
enum class Palette { GREEN, AMBER, CYAN, MAGENTA, ICE }

/** Every sound the launcher can make. Each one is user-overridable. */
enum class SfxEvent { CLICK, OPEN, BACK, TOGGLE, PAGE, MENU, PIN, ERROR }

/** Clock format preference. */
enum class ClockMode { SYSTEM, H24, H12 }

/**
 * One launchable activity. [info] is kept for icon loading only and is
 * deliberately excluded from equality so that recomposition is driven by the
 * stable identity of the entry.
 */
class AppEntry(
    val packageName: String,
    val className: String,
    val user: UserHandle,
    val userSerial: Long,
    val label: String,
    val isSystem: Boolean,
    val info: LauncherActivityInfo?,
) {
    val key: String = "$packageName/$className/$userSerial"

    override fun equals(other: Any?): Boolean =
        other is AppEntry && other.key == key && other.label == label

    override fun hashCode(): Int = 31 * key.hashCode() + label.hashCode()
}

/** A cell in the drawer: either a real app or the built-in settings app. */
sealed interface Tile {
    val id: String

    data object Settings : Tile {
        override val id: String = "::launcher-settings"
    }

    data class App(val entry: AppEntry) : Tile {
        override val id: String get() = entry.key
    }
}

/** All user preferences, read once and kept in memory. */
data class Settings(
    val iconShape: IconShape = IconShape.ROUNDED_SQUARE,
    val pixelIcons: Boolean = true,
    val pixelLevel: Int = 48,
    val showLabels: Boolean = true,
    /** 0 = auto (derived from screen width). */
    val columns: Int = 0,
    val palette: Palette = Palette.GREEN,
    val scanlines: Boolean = true,
    val showWallpaper: Boolean = false,
    val wallpaperDim: Int = 55,
    val soundEnabled: Boolean = true,
    val volume: Int = 70,
    val haptics: Boolean = true,
    val animations: Boolean = true,
    val lowPower: Boolean = false,
    val clockMode: ClockMode = ClockMode.SYSTEM,
    val showBattery: Boolean = true,
    val showSettingsTile: Boolean = true,
) {
    /** Icons are re-rendered whenever any of these change. */
    val iconSignature: String
        get() = "$iconShape/$pixelIcons/$pixelLevel/$lowPower"

    val effectiveScanlines: Boolean get() = scanlines && !lowPower
    val effectiveAnimations: Boolean get() = animations && !lowPower
}
