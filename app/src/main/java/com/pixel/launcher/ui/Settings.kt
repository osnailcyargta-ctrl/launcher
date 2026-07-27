package com.pixel.launcher.ui

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixel.launcher.BuildConfig
import com.pixel.launcher.PixelLauncherApp
import com.pixel.launcher.core.ClockMode
import com.pixel.launcher.core.IconShape
import com.pixel.launcher.core.SfxEvent
import kotlinx.coroutines.launch

private val PIXEL_LEVELS = listOf(24, 32, 48, 64)
private val COLUMN_CHOICES = listOf(0, 3, 4, 5, 6)

@Composable
fun SettingsScreen(app: PixelLauncherApp, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val retro = LocalRetro.current
    val sfx = LocalSfx.current
    val settings = LocalSettings.current

    val hidden by app.prefs.hidden.collectAsStateWithLifecycle()
    val customSounds by app.prefs.customSounds.collectAsStateWithLifecycle()

    BackHandler(enabled = true) { onClose() }

    var pendingSound by remember { mutableStateOf<SfxEvent?>(null) }
    val soundPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        val event = pendingSound
        pendingSound = null
        if (uri == null || event == null) return@rememberLauncherForActivityResult
        scope.launch {
            val name = queryDisplayName(context, uri)
            val ok = app.sfx.installCustom(event, uri, name)
            Toast.makeText(
                context,
                if (ok) "${event.name} sound updated" else "Could not read that file",
                Toast.LENGTH_SHORT,
            ).show()
            app.sfx.play(if (ok) SfxEvent.TOGGLE else SfxEvent.ERROR)
        }
    }

    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { }

    fun tick() = sfx.play(SfxEvent.TOGGLE)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PixelButton(text = "< BACK", onClick = onClose)
                Spacer(Modifier.width(12.dp))
                DisplayText(
                    text = "SETTINGS",
                    size = 14.sp,
                    color = retro.accent,
                    letterSpacing = 1.sp,
                )
            }
        }

        // ------------------------------------------------------------- icons
        item { SectionHeader("ICONS") }
        item {
            SettingBlock("ICON SHAPE") {
                PixelChoiceWrap(
                    options = listOf("ROUNDED", "R.SQUARE", "SQUARE", "ORIGINAL"),
                    // Chips are ordered the way a person reads them, not the way
                    // the enum happens to be declared.
                    selectedIndex = when (settings.iconShape) {
                        IconShape.CIRCLE -> 0
                        IconShape.ROUNDED_SQUARE -> 1
                        IconShape.SQUARE -> 2
                        IconShape.ORIGINAL -> 3
                    },
                    onSelect = { index ->
                        tick()
                        app.prefs.update {
                            it.copy(
                                iconShape = when (index) {
                                    0 -> IconShape.CIRCLE
                                    1 -> IconShape.ROUNDED_SQUARE
                                    2 -> IconShape.SQUARE
                                    else -> IconShape.ORIGINAL
                                },
                            )
                        }
                    },
                )
            }
        }
        item {
            ToggleRow("PIXEL ART ICONS", settings.pixelIcons) {
                tick()
                app.prefs.update { it.copy(pixelIcons = !it.pixelIcons) }
            }
        }
        if (settings.pixelIcons) {
            item {
                SettingBlock("PIXEL DETAIL") {
                    PixelChoiceWrap(
                        options = PIXEL_LEVELS.map { "${it}PX" },
                        selectedIndex = PIXEL_LEVELS.indexOf(settings.pixelLevel).coerceAtLeast(0),
                        onSelect = { index ->
                            tick()
                            app.prefs.update { it.copy(pixelLevel = PIXEL_LEVELS[index]) }
                        },
                    )
                }
            }
        }
        item {
            ToggleRow("APP LABELS", settings.showLabels) {
                tick()
                app.prefs.update { it.copy(showLabels = !it.showLabels) }
            }
        }
        item {
            SettingBlock("COLUMNS") {
                PixelChoiceWrap(
                    options = COLUMN_CHOICES.map { if (it == 0) "AUTO" else "$it" },
                    selectedIndex = COLUMN_CHOICES.indexOf(settings.columns).coerceAtLeast(0),
                    onSelect = { index ->
                        tick()
                        app.prefs.update { it.copy(columns = COLUMN_CHOICES[index]) }
                    },
                )
            }
        }

        // ------------------------------------------------------------ screen
        item { SectionHeader("SCREEN") }
        item {
            SettingBlock("PALETTE") {
                PixelChoiceWrap(
                    options = AllPalettes.map { it.label },
                    selectedIndex = AllPalettes.indexOfFirst { it.id == settings.palette },
                    onSelect = { index ->
                        tick()
                        app.prefs.update { it.copy(palette = AllPalettes[index].id) }
                    },
                )
            }
        }
        item {
            ToggleRow("CRT SCANLINES", settings.scanlines) {
                tick()
                app.prefs.update { it.copy(scanlines = !it.scanlines) }
            }
        }
        item {
            ToggleRow("SHOW WALLPAPER", settings.showWallpaper) {
                tick()
                app.prefs.update { it.copy(showWallpaper = !it.showWallpaper) }
            }
        }
        if (settings.showWallpaper) {
            item {
                SettingBlock("BACKGROUND DIM") {
                    PixelStepper(
                        value = settings.wallpaperDim,
                        onChange = { value ->
                            tick()
                            app.prefs.update { it.copy(wallpaperDim = value) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        item {
            ToggleRow("OPEN ANIMATION", settings.animations) {
                tick()
                app.prefs.update { it.copy(animations = !it.animations) }
            }
        }
        item {
            SettingBlock("CLOCK") {
                PixelChoiceWrap(
                    options = listOf("SYSTEM", "24H", "12H"),
                    selectedIndex = ClockMode.entries.indexOf(settings.clockMode),
                    onSelect = { index ->
                        tick()
                        app.prefs.update { it.copy(clockMode = ClockMode.entries[index]) }
                    },
                )
            }
        }
        item {
            ToggleRow("BATTERY READOUT", settings.showBattery) {
                tick()
                app.prefs.update { it.copy(showBattery = !it.showBattery) }
            }
        }
        item {
            ToggleRow("SETTINGS TILE IN DRAWER", settings.showSettingsTile) {
                tick()
                app.prefs.update { it.copy(showSettingsTile = !it.showSettingsTile) }
            }
        }

        // ------------------------------------------------------------- sound
        item { SectionHeader("SOUND") }
        item {
            ToggleRow("SOUND EFFECTS", settings.soundEnabled) {
                app.prefs.update { it.copy(soundEnabled = !it.soundEnabled) }
                sfx.play(SfxEvent.TOGGLE)
            }
        }
        item {
            SettingBlock("VOLUME") {
                PixelStepper(
                    value = settings.volume,
                    onChange = { value ->
                        app.prefs.update { it.copy(volume = value) }
                        sfx.play(SfxEvent.CLICK)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item {
            Hint("Muted automatically while the phone is on silent or vibrate.")
        }
        items(SfxEvent.entries.toList(), key = { it.name }) { event ->
            SoundRow(
                event = event,
                custom = customSounds[event],
                onTest = { sfx.play(event) },
                onPick = {
                    pendingSound = event
                    soundPicker.launch(arrayOf("audio/*"))
                },
                onReset = {
                    app.sfx.clearCustom(event)
                    sfx.play(SfxEvent.BACK)
                },
            )
        }
        item {
            PixelButton(
                text = "RESET ALL SOUNDS",
                onClick = {
                    app.sfx.clearAllCustom()
                    sfx.play(SfxEvent.BACK)
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // ------------------------------------------------------------- power
        item { SectionHeader("POWER") }
        item {
            ToggleRow("LOW POWER MODE", settings.lowPower) {
                tick()
                app.prefs.update { it.copy(lowPower = !it.lowPower) }
            }
        }
        item {
            Hint("Drops scanlines, open animations and icon detail. Best for long days off the charger.")
        }
        item {
            ToggleRow("HAPTICS", settings.haptics) {
                tick()
                app.prefs.update { it.copy(haptics = !it.haptics) }
            }
        }

        // ------------------------------------------------------------ system
        item { SectionHeader("SYSTEM") }
        item {
            PixelButton(
                text = "SET AS DEFAULT LAUNCHER",
                onClick = {
                    sfx.play(SfxEvent.CLICK)
                    requestHomeRole(context) { intent -> roleLauncher.launch(intent) }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            PixelButton(
                text = "CHANGE WALLPAPER",
                onClick = {
                    sfx.play(SfxEvent.CLICK)
                    runCatching {
                        context.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SET_WALLPAPER),
                                "Wallpaper",
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            PixelButton(
                text = "UNHIDE ALL APPS (${hidden.size})",
                onClick = {
                    sfx.play(SfxEvent.TOGGLE)
                    app.prefs.unhideAll()
                },
                enabled = hidden.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            PixelButton(
                text = "RESET USAGE RANKING",
                onClick = {
                    sfx.play(SfxEvent.BACK)
                    app.usage.reset()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            PixelButton(
                text = "RESET ALL SETTINGS",
                onClick = {
                    sfx.play(SfxEvent.ERROR)
                    app.prefs.resetSettings()
                    app.sfx.clearAllCustom()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DisplayText("PIXEL LAUNCHER", 9.sp, retro.textDim)
                Spacer(Modifier.height(6.dp))
                BodyText("v${BuildConfig.VERSION_NAME}", 15.sp, retro.textDim)
            }
        }
    }
}

// ----------------------------------------------------------------- widgets

@Composable
private fun SectionHeader(title: String) {
    val retro = LocalRetro.current
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        DisplayText(title, 10.sp, retro.accent, letterSpacing = 2.sp)
        Spacer(Modifier.height(6.dp))
        PixelDivider()
    }
}

@Composable
private fun SettingBlock(label: String, content: @Composable () -> Unit) {
    val retro = LocalRetro.current
    Column(Modifier.fillMaxWidth()) {
        BodyText(label, 18.sp, retro.text, letterSpacing = 1.sp)
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    val retro = LocalRetro.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BodyText(
            text = label,
            size = 18.sp,
            color = retro.text,
            letterSpacing = 1.sp,
            modifier = Modifier.weight(1f),
        )
        PixelToggle(checked = checked, onToggle = onToggle)
    }
}

@Composable
private fun Hint(text: String) {
    val retro = LocalRetro.current
    BodyText(
        text = text,
        size = 15.sp,
        color = retro.textDim,
        maxLines = 3,
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PixelChoiceWrap(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEachIndexed { index, label ->
            PixelButton(
                text = label,
                onClick = { onSelect(index) },
                selected = index == selectedIndex,
                fontSize = 15.sp,
            )
        }
    }
}

@Composable
private fun SoundRow(
    event: SfxEvent,
    custom: String?,
    onTest: () -> Unit,
    onPick: () -> Unit,
    onReset: () -> Unit,
) {
    val retro = LocalRetro.current
    PixelPanel(modifier = Modifier.fillMaxWidth(), contentPadding = 8.dp) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BodyText(
                    text = event.name,
                    size = 18.sp,
                    color = retro.accent,
                    letterSpacing = 1.sp,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier
                        .background(if (custom != null) retro.accentDim else retro.edge)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    BodyText(
                        text = if (custom != null) "CUSTOM" else "DEFAULT",
                        size = 13.sp,
                        color = retro.text,
                    )
                }
            }
            if (custom != null) {
                Spacer(Modifier.height(2.dp))
                BodyText(custom, 14.sp, retro.textDim, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PixelButton("TEST", onTest, Modifier.weight(1f), fontSize = 15.sp)
                PixelButton("PICK", onPick, Modifier.weight(1f), fontSize = 15.sp)
                PixelButton(
                    text = "RESET",
                    onClick = onReset,
                    modifier = Modifier.weight(1f),
                    enabled = custom != null,
                    fontSize = 15.sp,
                )
            }
        }
    }
}

// ------------------------------------------------------------------ helpers

private fun queryDisplayName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver
        .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
}.getOrNull()

/**
 * Asks to become the home app. Android 10+ has a proper role request dialog;
 * older releases only expose the home-screen page in system settings.
 */
private fun requestHomeRole(context: Context, launch: (Intent) -> Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = context.getSystemService(RoleManager::class.java)
        if (roleManager != null &&
            roleManager.isRoleAvailable(RoleManager.ROLE_HOME) &&
            !roleManager.isRoleHeld(RoleManager.ROLE_HOME)
        ) {
            runCatching { launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)) }
                .onSuccess { return }
        }
    }
    val fallback = Intent(android.provider.Settings.ACTION_HOME_SETTINGS)
    if (context !is Activity) fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(fallback) }.onFailure {
        runCatching {
            context.startActivity(
                Intent(android.provider.Settings.ACTION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
