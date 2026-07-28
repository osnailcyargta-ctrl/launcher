package com.pixel.launcher.core

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Preference storage.
 *
 * Backed by [SharedPreferences] rather than DataStore on purpose: the values are
 * read exactly once at process start (a handful of primitives), which keeps the
 * launcher's cold start free of coroutine plumbing and disk churn.
 */
class Prefs(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _pinned = MutableStateFlow(readList(KEY_PINNED))
    val pinned: StateFlow<List<String>> = _pinned.asStateFlow()

    private val _hidden = MutableStateFlow(readList(KEY_HIDDEN).toSet())
    val hidden: StateFlow<Set<String>> = _hidden.asStateFlow()

    private val _sounds = MutableStateFlow(readSounds())
    val customSounds: StateFlow<Map<SfxEvent, String>> = _sounds.asStateFlow()

    private val _folders = MutableStateFlow(readFolders())
    val folders: StateFlow<List<Folder>> = _folders.asStateFlow()

    // ---------------------------------------------------------------- settings

    private fun read(): Settings {
        val d = Settings()
        return Settings(
            iconShape = enumOf(sp.getString(KEY_SHAPE, null), IconShape.entries, d.iconShape),
            pixelIcons = sp.getBoolean(KEY_PIXEL, d.pixelIcons),
            pixelLevel = sp.getInt(KEY_PIXEL_LEVEL, d.pixelLevel),
            iconScale = sp.getInt(KEY_ICON_SCALE, d.iconScale),
            iconSource = enumOf(sp.getString(KEY_ICON_SOURCE, null), IconSource.entries, d.iconSource),
            iconPackPackage = sp.getString(KEY_ICON_PACK, null) ?: d.iconPackPackage,
            showLabels = sp.getBoolean(KEY_LABELS, d.showLabels),
            columns = sp.getInt(KEY_COLUMNS, d.columns),
            showSettingsTile = sp.getBoolean(KEY_SETTINGS_TILE, d.showSettingsTile),
            showAlphabetBar = sp.getBoolean(KEY_ALPHABET, d.showAlphabetBar),
            showSearch = sp.getBoolean(KEY_SEARCH, d.showSearch),
            displayFont = enumOf(sp.getString(KEY_FONT_DISPLAY, null), PixelFont.entries, d.displayFont),
            bodyFont = enumOf(sp.getString(KEY_FONT_BODY, null), PixelFont.entries, d.bodyFont),
            textScale = sp.getInt(KEY_TEXT_SCALE, d.textScale),
            widgets = readWidgets(d.widgets),
            customText = sp.getString(KEY_CUSTOM_TEXT, null) ?: d.customText,
            clockMode = enumOf(sp.getString(KEY_CLOCK, null), ClockMode.entries, d.clockMode),
            showBattery = sp.getBoolean(KEY_BATTERY, d.showBattery),
            palette = enumOf(sp.getString(KEY_PALETTE, null), Palette.entries, d.palette),
            scanlines = sp.getBoolean(KEY_SCANLINES, d.scanlines),
            showWallpaper = sp.getBoolean(KEY_WALLPAPER, d.showWallpaper),
            wallpaperDim = sp.getInt(KEY_DIM, d.wallpaperDim),
            animations = sp.getBoolean(KEY_ANIM, d.animations),
            soundEnabled = sp.getBoolean(KEY_SOUND, d.soundEnabled),
            volume = sp.getInt(KEY_VOLUME, d.volume),
            respectSilentMode = sp.getBoolean(KEY_RESPECT_SILENT, d.respectSilentMode),
            haptics = sp.getBoolean(KEY_HAPTICS, d.haptics),
            lowPower = sp.getBoolean(KEY_LOW_POWER, d.lowPower),
        )
    }

    fun update(block: (Settings) -> Settings) {
        val next = block(_settings.value)
        if (next == _settings.value) return
        _settings.value = next
        sp.edit()
            .putString(KEY_SHAPE, next.iconShape.name)
            .putBoolean(KEY_PIXEL, next.pixelIcons)
            .putInt(KEY_PIXEL_LEVEL, next.pixelLevel)
            .putInt(KEY_ICON_SCALE, next.iconScale)
            .putString(KEY_ICON_SOURCE, next.iconSource.name)
            .putString(KEY_ICON_PACK, next.iconPackPackage)
            .putBoolean(KEY_LABELS, next.showLabels)
            .putInt(KEY_COLUMNS, next.columns)
            .putBoolean(KEY_SETTINGS_TILE, next.showSettingsTile)
            .putBoolean(KEY_ALPHABET, next.showAlphabetBar)
            .putBoolean(KEY_SEARCH, next.showSearch)
            .putString(KEY_FONT_DISPLAY, next.displayFont.name)
            .putString(KEY_FONT_BODY, next.bodyFont.name)
            .putInt(KEY_TEXT_SCALE, next.textScale)
            .putString(KEY_WIDGETS, next.widgets.joinToString(",") { it.name })
            .putString(KEY_CUSTOM_TEXT, next.customText)
            .putString(KEY_CLOCK, next.clockMode.name)
            .putBoolean(KEY_BATTERY, next.showBattery)
            .putString(KEY_PALETTE, next.palette.name)
            .putBoolean(KEY_SCANLINES, next.scanlines)
            .putBoolean(KEY_WALLPAPER, next.showWallpaper)
            .putInt(KEY_DIM, next.wallpaperDim)
            .putBoolean(KEY_ANIM, next.animations)
            .putBoolean(KEY_SOUND, next.soundEnabled)
            .putInt(KEY_VOLUME, next.volume)
            .putBoolean(KEY_RESPECT_SILENT, next.respectSilentMode)
            .putBoolean(KEY_HAPTICS, next.haptics)
            .putBoolean(KEY_LOW_POWER, next.lowPower)
            .apply()
    }

    fun resetSettings() {
        _settings.value = Settings()
        sp.edit().clear().apply()
        _pinned.value = emptyList()
        _hidden.value = emptySet()
        _sounds.value = emptyMap()
        _folders.value = emptyList()
    }

    private fun readWidgets(fallback: Set<TopWidget>): Set<TopWidget> {
        val raw = sp.getString(KEY_WIDGETS, null) ?: return fallback
        if (raw.isBlank()) return emptySet()
        return raw.split(',')
            .mapNotNull { name -> TopWidget.entries.firstOrNull { it.name == name } }
            .toSet()
    }

    // ------------------------------------------------------------------ pinned

    /** Pins [key], keeping at most [MAX_PINNED] entries (oldest drops out). */
    fun togglePin(key: String) {
        val current = _pinned.value
        val next = if (key in current) {
            current - key
        } else {
            (current + key).takeLast(MAX_PINNED)
        }
        _pinned.value = next
        writeList(KEY_PINNED, next)
    }

    fun isPinned(key: String): Boolean = key in _pinned.value

    // ------------------------------------------------------------------ hidden

    fun toggleHidden(key: String) {
        val next = _hidden.value.toMutableSet().apply { if (!add(key)) remove(key) }
        _hidden.value = next
        writeList(KEY_HIDDEN, next.toList())
    }

    fun unhideAll() {
        _hidden.value = emptySet()
        writeList(KEY_HIDDEN, emptyList())
    }

    // ----------------------------------------------------------------- folders

    /**
     * Folders are stored one per line as `id|name|key;key;key`. A hand-rolled
     * format keeps the launcher free of a JSON dependency for what is, at most,
     * a few dozen short lines.
     */
    private fun readFolders(): List<Folder> =
        readList(KEY_FOLDERS).mapNotNull { line ->
            val parts = line.split('|')
            if (parts.size < 2) return@mapNotNull null
            val keys = parts.getOrNull(2)?.split(';')?.filter { it.isNotBlank() } ?: emptyList()
            Folder(id = parts[0], name = parts[1], appKeys = keys)
        }

    private fun writeFolders(folders: List<Folder>) {
        _folders.value = folders
        writeList(
            KEY_FOLDERS,
            folders.map { folder ->
                // '|' and ';' are structural, and ';' can never appear in a key.
                "${folder.id}|${folder.name.replace('|', ' ')}|${folder.appKeys.joinToString(";")}"
            },
        )
    }

    fun createFolder(name: String): Folder {
        val folder = Folder(
            id = System.currentTimeMillis().toString(36),
            name = name.ifBlank { "FOLDER" },
            appKeys = emptyList(),
        )
        writeFolders(_folders.value + folder)
        return folder
    }

    fun renameFolder(id: String, name: String) {
        writeFolders(_folders.value.map { if (it.id == id) it.copy(name = name) else it })
    }

    fun deleteFolder(id: String) {
        writeFolders(_folders.value.filterNot { it.id == id })
    }

    /** Adds [appKey] to [folderId], removing it from any folder it was in. */
    fun addToFolder(folderId: String, appKey: String) {
        writeFolders(
            _folders.value.map { folder ->
                when {
                    folder.id == folderId && appKey !in folder.appKeys ->
                        folder.copy(appKeys = folder.appKeys + appKey)

                    folder.id != folderId && appKey in folder.appKeys ->
                        folder.copy(appKeys = folder.appKeys - appKey)

                    else -> folder
                }
            },
        )
    }

    fun removeFromFolder(appKey: String) {
        writeFolders(
            _folders.value.map { folder ->
                if (appKey in folder.appKeys) {
                    folder.copy(appKeys = folder.appKeys - appKey)
                } else {
                    folder
                }
            },
        )
    }

    fun folderOf(appKey: String): Folder? = _folders.value.firstOrNull { appKey in it.appKeys }

    // ------------------------------------------------------------------ sounds

    private fun readSounds(): Map<SfxEvent, String> = buildMap {
        for (e in SfxEvent.entries) {
            sp.getString(soundKey(e), null)?.takeIf { it.isNotEmpty() }?.let { put(e, it) }
        }
    }

    fun setCustomSound(event: SfxEvent, uri: String?) {
        val next = _sounds.value.toMutableMap()
        if (uri.isNullOrEmpty()) next.remove(event) else next[event] = uri
        _sounds.value = next
        sp.edit().putString(soundKey(event), uri ?: "").apply()
    }

    fun clearCustomSounds() {
        _sounds.value = emptyMap()
        sp.edit().apply { SfxEvent.entries.forEach { remove(soundKey(it)) } }.apply()
    }

    // ------------------------------------------------------------------ helper

    private fun readList(key: String): List<String> =
        sp.getString(key, null)?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()

    private fun writeList(key: String, values: List<String>) {
        sp.edit().putString(key, values.joinToString("\n")).apply()
    }

    private fun <T : Enum<T>> enumOf(name: String?, values: List<T>, fallback: T): T =
        values.firstOrNull { it.name == name } ?: fallback

    private fun soundKey(event: SfxEvent) = "sfx_${event.name}"

    companion object {
        const val MAX_PINNED = 4
        private const val FILE = "pixel_launcher_prefs"
        private const val KEY_SHAPE = "icon_shape"
        private const val KEY_PIXEL = "pixel_icons"
        private const val KEY_PIXEL_LEVEL = "pixel_level"
        private const val KEY_ICON_SCALE = "icon_scale"
        private const val KEY_ICON_SOURCE = "icon_source"
        private const val KEY_ICON_PACK = "icon_pack"
        private const val KEY_LABELS = "show_labels"
        private const val KEY_COLUMNS = "columns"
        private const val KEY_SETTINGS_TILE = "settings_tile"
        private const val KEY_ALPHABET = "alphabet_bar"
        private const val KEY_SEARCH = "search_bar"
        private const val KEY_FONT_DISPLAY = "font_display"
        private const val KEY_FONT_BODY = "font_body"
        private const val KEY_TEXT_SCALE = "text_scale"
        private const val KEY_WIDGETS = "widgets"
        private const val KEY_CUSTOM_TEXT = "custom_text"
        private const val KEY_CLOCK = "clock_mode"
        private const val KEY_BATTERY = "battery"
        private const val KEY_PALETTE = "palette"
        private const val KEY_SCANLINES = "scanlines"
        private const val KEY_WALLPAPER = "wallpaper"
        private const val KEY_DIM = "wallpaper_dim"
        private const val KEY_ANIM = "animations"
        private const val KEY_SOUND = "sound"
        private const val KEY_VOLUME = "volume"
        private const val KEY_RESPECT_SILENT = "respect_silent"
        private const val KEY_HAPTICS = "haptics"
        private const val KEY_LOW_POWER = "low_power"
        private const val KEY_PINNED = "pinned"
        private const val KEY_HIDDEN = "hidden"
        private const val KEY_FOLDERS = "folders"
    }
}
