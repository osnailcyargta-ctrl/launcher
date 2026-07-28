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

/** Bundled pixel typefaces. */
enum class PixelFont { PRESS_START, SILKSCREEN, PIXELIFY, VT323, MONO }

/** Widgets that can be stacked above the app drawer. */
enum class TopWidget { CLOCK, DINO, TEXT }

/** Where icon artwork comes from. */
enum class IconSource {
    /** The app's own icon, optionally pixelated. */
    SYSTEM,

    /** The launcher's hand-drawn pixel icons, falling back to SYSTEM. */
    BUILT_IN,

    /** An installed third-party icon pack, falling back to SYSTEM. */
    EXTERNAL,
}

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

/** A user-made group of apps, shown in the drawer as a single tile. */
data class Folder(
    val id: String,
    val name: String,
    val appKeys: List<String>,
)

/** A cell in the drawer. */
sealed interface Tile {
    val id: String

    data object Settings : Tile {
        override val id: String = "::launcher-settings"
    }

    data class App(val entry: AppEntry) : Tile {
        override val id: String get() = entry.key
    }

    data class Group(val folder: Folder, val entries: List<AppEntry>) : Tile {
        override val id: String get() = "::folder/${folder.id}"
    }
}

/** All user preferences, read once and kept in memory. */
data class Settings(
    // --- icons ---
    val iconShape: IconShape = IconShape.ROUNDED_SQUARE,
    val pixelIcons: Boolean = true,
    val pixelLevel: Int = 48,
    /** Icon size as a percentage of the automatic size. */
    val iconScale: Int = 100,
    val iconSource: IconSource = IconSource.SYSTEM,
    /** Package name of the external icon pack, when [iconSource] is EXTERNAL. */
    val iconPackPackage: String = "",

    // --- layout ---
    val showLabels: Boolean = true,
    /** 0 = auto (derived from screen width). */
    val columns: Int = 0,
    val showSettingsTile: Boolean = true,
    val showAlphabetBar: Boolean = true,
    val showSearch: Boolean = true,

    // --- type ---
    val displayFont: PixelFont = PixelFont.PRESS_START,
    val bodyFont: PixelFont = PixelFont.VT323,
    /** Text size as a percentage. */
    val textScale: Int = 100,

    // --- widgets ---
    val widgets: Set<TopWidget> = setOf(TopWidget.CLOCK),
    val customText: String = "STAY RETRO",
    val clockMode: ClockMode = ClockMode.SYSTEM,
    val showBattery: Boolean = true,

    // --- screen ---
    val palette: Palette = Palette.GREEN,
    val scanlines: Boolean = true,
    val showWallpaper: Boolean = false,
    val wallpaperDim: Int = 55,
    val animations: Boolean = true,

    // --- sound ---
    val soundEnabled: Boolean = true,
    val volume: Int = 70,
    /** When on, the launcher stays quiet in silent/vibrate mode. */
    val respectSilentMode: Boolean = false,
    val haptics: Boolean = true,

    // --- power ---
    val lowPower: Boolean = false,
) {
    /**
     * Icons are re-rendered whenever any of these change. The palette counts
     * because the built-in pack re-colours artwork to match it.
     */
    val iconSignature: String
        get() = "$iconShape/$pixelIcons/$pixelLevel/$lowPower/$iconSource/$iconPackPackage/$palette"

    val effectiveScanlines: Boolean get() = scanlines && !lowPower
    val effectiveAnimations: Boolean get() = animations && !lowPower

    /** The dino only runs when animations are on. */
    val dinoEnabled: Boolean get() = TopWidget.DINO in widgets && effectiveAnimations
}
