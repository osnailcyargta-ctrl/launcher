package com.pixel.launcher.ui

import android.app.ActivityOptions
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.IntState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixel.launcher.PixelLauncherApp
import com.pixel.launcher.core.AppEntry
import com.pixel.launcher.core.ClockMode
import com.pixel.launcher.core.IconLoader
import com.pixel.launcher.core.Prefs
import com.pixel.launcher.core.SfxEvent
import com.pixel.launcher.core.Tile
import kotlinx.coroutines.flow.drop
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

/** Rows per snap. The drawer always advances a whole block of four. */
private const val ROWS_PER_PAGE = 4

private enum class Screen { HOME, SETTINGS }

@Composable
fun LauncherRoot(homePresses: IntState) {
    val context = LocalContext.current
    val app = context.applicationContext as PixelLauncherApp

    val settings by app.prefs.settings.collectAsStateWithLifecycle()
    val retro = paletteOf(settings.palette)

    LaunchedEffect(settings.iconSignature) {
        app.icons.applySignature(settings.iconSignature)
    }

    CompositionLocalProvider(
        LocalRetro provides retro,
        LocalSettings provides settings,
        LocalSfx provides app.sfx,
        LocalIcons provides app.icons,
    ) {
        var screen by remember { mutableStateOf(Screen.HOME) }
        var resetSignal by remember { mutableIntStateOf(0) }

        // Pressing HOME while already home returns to the top of the drawer.
        LaunchedEffect(homePresses.intValue) {
            if (homePresses.intValue > 0) {
                screen = Screen.HOME
                resetSignal++
            }
        }

        val background = if (settings.showWallpaper) {
            retro.background.copy(alpha = (settings.wallpaperDim / 100f).coerceIn(0f, 1f))
        } else {
            retro.background
        }

        Box(Modifier.fillMaxSize().background(background)) {
            when (screen) {
                Screen.HOME -> HomeScreen(
                    app = app,
                    resetSignal = resetSignal,
                    onOpenSettings = { screen = Screen.SETTINGS },
                )

                Screen.SETTINGS -> SettingsScreen(
                    app = app,
                    onClose = {
                        app.sfx.play(SfxEvent.BACK)
                        screen = Screen.HOME
                    },
                )
            }

            if (settings.effectiveScanlines) {
                ScanlineOverlay(strength = 0.12f)
            }
        }
    }
}

@Composable
private fun HomeScreen(
    app: PixelLauncherApp,
    resetSignal: Int,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val settings = LocalSettings.current
    val sfx = LocalSfx.current

    val apps by app.apps.apps.collectAsStateWithLifecycle()
    val loaded by app.apps.loaded.collectAsStateWithLifecycle()
    val hidden by app.prefs.hidden.collectAsStateWithLifecycle()
    val pinnedKeys by app.prefs.pinned.collectAsStateWithLifecycle()
    val ranking by app.usage.ranking.collectAsStateWithLifecycle()

    var menuTarget by remember { mutableStateOf<AppEntry?>(null) }

    val tiles = remember(apps, hidden, settings.showSettingsTile) {
        buildList(apps.size + 1) {
            if (settings.showSettingsTile) add(Tile.Settings)
            for (entry in apps) if (entry.key !in hidden) add(Tile.App(entry))
        }
    }

    val byKey = remember(apps) { apps.associateBy { it.key } }
    val pinned = remember(pinnedKeys, byKey) { pinnedKeys.mapNotNull { byKey[it] } }
    val frequent = remember(ranking, byKey, pinnedKeys, hidden, apps) {
        val chosen = LinkedHashMap<String, AppEntry>()
        for (key in ranking) {
            if (chosen.size >= DOCK_FREQUENT) break
            if (key in pinnedKeys || key in hidden) continue
            byKey[key]?.let { chosen[key] = it }
        }
        // Fresh install: nothing has been opened yet, so show the first apps
        // instead of leaving a row of empty holes.
        for (entry in apps) {
            if (chosen.size >= DOCK_FREQUENT) break
            if (entry.key in pinnedKeys || entry.key in hidden) continue
            chosen.putIfAbsent(entry.key, entry)
        }
        chosen.values.toList()
    }

    val spec = remember(settings.iconSignature) { IconLoader.specOf(settings) }

    fun open(entry: AppEntry, bounds: android.graphics.Rect?) {
        sfx.play(SfxEvent.OPEN)
        app.usage.record(entry.key)
        val options = if (settings.effectiveAnimations && bounds != null) {
            runCatching {
                ActivityOptions.makeClipRevealAnimation(
                    view,
                    bounds.left,
                    bounds.top,
                    bounds.width(),
                    bounds.height(),
                ).toBundle()
            }.getOrNull()
        } else {
            null
        }
        if (!app.apps.launch(entry, bounds, options)) {
            sfx.play(SfxEvent.ERROR)
            Toast.makeText(context, "Can't open ${entry.label}", Toast.LENGTH_SHORT).show()
        }
    }

    BackHandler(enabled = menuTarget != null) { menuTarget = null }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxHeight < 480.dp
        val columns = settings.columns.takeIf { it > 0 }
            ?: (maxWidth / MIN_TILE_WIDTH).toInt().coerceIn(3, 8)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 8.dp),
        ) {
            StatusHeader(
                compact = compact,
                onOpenSettings = {
                    sfx.play(SfxEvent.CLICK)
                    onOpenSettings()
                },
            )

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    !loaded -> CenterNotice("SCANNING APPS...")
                    tiles.isEmpty() -> CenterNotice("NO APPS FOUND")
                    else -> AppDrawer(
                        tiles = tiles,
                        columns = columns,
                        spec = spec,
                        compact = compact,
                        resetSignal = resetSignal,
                        onOpenSettings = {
                            sfx.play(SfxEvent.CLICK)
                            onOpenSettings()
                        },
                        onOpenApp = { entry, bounds -> open(entry, bounds) },
                        onLongPress = { entry ->
                            sfx.play(SfxEvent.MENU)
                            menuTarget = entry
                        },
                    )
                }
            }

            Dock(
                frequent = frequent,
                pinned = pinned,
                spec = spec,
                compact = compact,
                onOpenApp = { entry, bounds -> open(entry, bounds) },
                onLongPress = { entry ->
                    sfx.play(SfxEvent.MENU)
                    menuTarget = entry
                },
            )
        }

        menuTarget?.let { entry ->
            AppMenu(
                entry = entry,
                pinned = entry.key in pinnedKeys,
                onDismiss = { menuTarget = null },
                onPin = {
                    app.prefs.togglePin(entry.key)
                    sfx.play(SfxEvent.PIN)
                    menuTarget = null
                },
                onHide = {
                    app.prefs.toggleHidden(entry.key)
                    sfx.play(SfxEvent.TOGGLE)
                    menuTarget = null
                },
                onInfo = {
                    sfx.play(SfxEvent.CLICK)
                    app.apps.openAppInfo(entry, null)
                    menuTarget = null
                },
                onUninstall = {
                    sfx.play(SfxEvent.CLICK)
                    app.apps.requestUninstall(entry)
                    menuTarget = null
                },
            )
        }
    }
}

// ------------------------------------------------------------------- drawer

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppDrawer(
    tiles: List<Tile>,
    columns: Int,
    spec: IconLoader.Spec,
    compact: Boolean,
    resetSignal: Int,
    onOpenSettings: () -> Unit,
    onOpenApp: (AppEntry, android.graphics.Rect?) -> Unit,
    onLongPress: (AppEntry) -> Unit,
) {
    val settings = LocalSettings.current
    val sfx = LocalSfx.current

    val perPage = columns * ROWS_PER_PAGE
    val pageCount = max(1, ceil(tiles.size / perPage.toFloat()).toInt())
    val pagerState = rememberPagerState(pageCount = { pageCount })

    LaunchedEffect(resetSignal) {
        if (resetSignal > 0 && pagerState.currentPage != 0) {
            pagerState.animateScrollToPage(0)
        }
    }

    // A tick every time the drawer settles on a new block of four rows.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .drop(1)
            .collect { sfx.play(SfxEvent.PAGE) }
    }

    Row(Modifier.fillMaxSize()) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) { page ->
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val cellWidth = maxWidth / columns
                val cellHeight = maxHeight / ROWS_PER_PAGE
                val showLabels = settings.showLabels && cellHeight > 56.dp
                val iconSize = minOf(
                    cellWidth * 0.72f,
                    if (showLabels) cellHeight * 0.58f else cellHeight * 0.82f,
                ).coerceAtMost(if (compact) 48.dp else 72.dp)

                Column(Modifier.fillMaxSize()) {
                    repeat(ROWS_PER_PAGE) { row ->
                        Row(Modifier.fillMaxWidth().weight(1f)) {
                            repeat(columns) { column ->
                                val index = page * perPage + row * columns + column
                                Box(
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    tiles.getOrNull(index)?.let { tile ->
                                        DrawerTile(
                                            tile = tile,
                                            spec = spec,
                                            iconSize = iconSize,
                                            showLabel = showLabels,
                                            onOpenSettings = onOpenSettings,
                                            onOpenApp = onOpenApp,
                                            onLongPress = onLongPress,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (pageCount > 1) {
            PageIndicator(
                pageCount = pageCount,
                current = pagerState.currentPage,
                modifier = Modifier.fillMaxHeight().width(10.dp),
            )
        }
    }
}

@Composable
private fun DrawerTile(
    tile: Tile,
    spec: IconLoader.Spec,
    iconSize: Dp,
    showLabel: Boolean,
    onOpenSettings: () -> Unit,
    onOpenApp: (AppEntry, android.graphics.Rect?) -> Unit,
    onLongPress: (AppEntry) -> Unit,
) {
    when (tile) {
        is Tile.Settings -> AppTile(
            tile = tile,
            spec = spec,
            iconSize = iconSize,
            label = if (showLabel) "SETTINGS" else null,
            labelSize = 15.sp,
            highlight = true,
            onClick = { onOpenSettings() },
        )

        is Tile.App -> AppTile(
            tile = tile,
            spec = spec,
            iconSize = iconSize,
            label = if (showLabel) tile.entry.label else null,
            labelSize = 15.sp,
            onClick = { bounds -> onOpenApp(tile.entry, bounds) },
            onLongClick = { onLongPress(tile.entry) },
        )
    }
}

@Composable
private fun PageIndicator(pageCount: Int, current: Int, modifier: Modifier = Modifier) {
    val retro = LocalRetro.current
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        repeat(pageCount.coerceAtMost(24)) { index ->
            Box(
                Modifier
                    .size(if (index == current) 8.dp else 5.dp)
                    .background(if (index == current) retro.accent else retro.edge),
            )
        }
    }
}

// --------------------------------------------------------------------- dock

private const val DOCK_FREQUENT = 3

@Composable
private fun Dock(
    frequent: List<AppEntry>,
    pinned: List<AppEntry>,
    spec: IconLoader.Spec,
    compact: Boolean,
    onOpenApp: (AppEntry, android.graphics.Rect?) -> Unit,
    onLongPress: (AppEntry) -> Unit,
) {
    val context = LocalContext.current
    val sfx = LocalSfx.current
    val iconSize = if (compact) 30.dp else 40.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DockGroup(
            title = "FREQUENT",
            compact = compact,
            modifier = Modifier.weight(DOCK_FREQUENT.toFloat()),
        ) {
            repeat(DOCK_FREQUENT) { index ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    val entry = frequent.getOrNull(index)
                    if (entry != null) {
                        AppTile(
                            tile = Tile.App(entry),
                            spec = spec,
                            iconSize = iconSize,
                            onClick = { bounds -> onOpenApp(entry, bounds) },
                            onLongClick = { onLongPress(entry) },
                        )
                    } else {
                        EmptySlot(iconSize) { }
                    }
                }
            }
        }

        DockGroup(
            title = "PINNED",
            compact = compact,
            modifier = Modifier.weight(Prefs.MAX_PINNED.toFloat()),
        ) {
            repeat(Prefs.MAX_PINNED) { index ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    val entry = pinned.getOrNull(index)
                    if (entry != null) {
                        AppTile(
                            tile = Tile.App(entry),
                            spec = spec,
                            iconSize = iconSize,
                            highlight = true,
                            onClick = { bounds -> onOpenApp(entry, bounds) },
                            onLongClick = { onLongPress(entry) },
                        )
                    } else {
                        EmptySlot(iconSize) {
                            sfx.play(SfxEvent.ERROR)
                            Toast.makeText(
                                context,
                                "Hold an app, then choose PIN",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                }
            }
        }
    }

}

@Composable
private fun DockGroup(
    title: String,
    compact: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val retro = LocalRetro.current
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (!compact) {
            DisplayText(
                text = title,
                size = 7.sp,
                color = retro.textDim,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }
        PixelPanel(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = 4.dp,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
}

@Composable
private fun EmptySlot(size: Dp, onClick: () -> Unit) {
    val retro = LocalRetro.current
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(size)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .background(retro.edge)
            .padding(2.dp)
            .background(retro.panel),
        contentAlignment = Alignment.Center,
    ) {
        PixelGlyph(
            rows = PinGlyph,
            color = retro.textDim.copy(alpha = 0.55f),
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

// ------------------------------------------------------------------- header

@Composable
private fun StatusHeader(compact: Boolean, onOpenSettings: () -> Unit) {
    val retro = LocalRetro.current
    val settings = LocalSettings.current
    val clock = rememberClockSnapshot(settings.clockMode, settings.showBattery)
    val openSettings by rememberUpdatedState(onOpenSettings)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) { detectTapGestures(onLongPress = { openSettings() }) }
            .padding(top = if (compact) 2.dp else 10.dp, bottom = if (compact) 2.dp else 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DisplayText(
            text = clock.time,
            size = if (compact) 24.sp else 40.sp,
            color = retro.accent,
            align = TextAlign.Center,
        )
        Spacer(Modifier.height(if (compact) 2.dp else 6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BodyText(
                text = clock.date,
                size = if (compact) 15.sp else 19.sp,
                color = retro.text,
                letterSpacing = 2.sp,
            )
            if (clock.battery in 0..100) {
                Spacer(Modifier.width(8.dp))
                Box(Modifier.width(2.dp).height(12.dp).background(retro.edge))
                Spacer(Modifier.width(8.dp))
                BodyText(
                    text = "BAT ${clock.battery}%",
                    size = if (compact) 15.sp else 19.sp,
                    color = if (clock.battery <= 15) retro.accent else retro.textDim,
                    letterSpacing = 1.sp,
                )
            }
        }
    }
}

private data class ClockSnapshot(val time: String, val date: String, val battery: Int)

/**
 * Clock state driven by [Intent.ACTION_TIME_TICK].
 *
 * The launcher never runs a timer: the system already broadcasts once a minute,
 * and the receiver is only registered while the launcher is actually on screen,
 * so a launcher sitting behind another app costs nothing at all.
 */
@Composable
private fun rememberClockSnapshot(mode: ClockMode, showBattery: Boolean): ClockSnapshot {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var snapshot by remember { mutableStateOf(readClock(context, mode, showBattery)) }

    DisposableEffect(lifecycleOwner, mode, showBattery) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                snapshot = readClock(context, mode, showBattery)
            }
        }
        var registered = false

        fun register() {
            if (registered) return
            val filter = IntentFilter(Intent.ACTION_TIME_TICK).apply {
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
                addAction(Intent.ACTION_LOCALE_CHANGED)
            }
            ContextCompat.registerReceiver(
                context,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            registered = true
            snapshot = readClock(context, mode, showBattery)
        }

        fun unregister() {
            if (!registered) return
            runCatching { context.unregisterReceiver(receiver) }
            registered = false
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> register()
                Lifecycle.Event.ON_STOP -> unregister()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            unregister()
        }
    }

    return snapshot
}

private fun readClock(context: Context, mode: ClockMode, showBattery: Boolean): ClockSnapshot {
    val now = Date()
    val locale = Locale.getDefault()
    val is24 = when (mode) {
        ClockMode.SYSTEM -> DateFormat.is24HourFormat(context)
        ClockMode.H24 -> true
        ClockMode.H12 -> false
    }
    val time = SimpleDateFormat(if (is24) "HH:mm" else "hh:mm a", locale).format(now)
    val date = SimpleDateFormat("EEE dd MMM yyyy", locale).format(now).uppercase(locale)
    val battery = if (showBattery) readBattery(context) else -1
    return ClockSnapshot(time.uppercase(locale), date, battery)
}

private fun readBattery(context: Context): Int = runCatching {
    val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
}.getOrDefault(-1)

// -------------------------------------------------------------------- menus

@Composable
private fun CenterNotice(text: String) {
    val retro = LocalRetro.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        DisplayText(text = text, size = 12.sp, color = retro.textDim, align = TextAlign.Center)
    }
}

@Composable
private fun AppMenu(
    entry: AppEntry,
    pinned: Boolean,
    onDismiss: () -> Unit,
    onPin: () -> Unit,
    onHide: () -> Unit,
    onInfo: () -> Unit,
    onUninstall: () -> Unit,
) {
    val retro = LocalRetro.current
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(retro.background.copy(alpha = 0.88f))
            .clickable(interactionSource = interaction, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        PixelPanel(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .padding(16.dp),
            contentPadding = 14.dp,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                DisplayText(
                    text = entry.label.uppercase(Locale.getDefault()),
                    size = 11.sp,
                    color = retro.accent,
                    align = TextAlign.Center,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                BodyText(
                    text = entry.packageName,
                    size = 14.sp,
                    color = retro.textDim,
                    align = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                PixelDivider()
                Spacer(Modifier.height(12.dp))

                PixelButton(
                    text = if (pinned) "UNPIN FROM DOCK" else "PIN TO DOCK (MAX 2)",
                    onClick = onPin,
                    selected = pinned,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                PixelButton(
                    text = "HIDE FROM DRAWER",
                    onClick = onHide,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                PixelButton(
                    text = "APP INFO",
                    onClick = onInfo,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!entry.isSystem) {
                    Spacer(Modifier.height(6.dp))
                    PixelButton(
                        text = "UNINSTALL",
                        onClick = onUninstall,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(12.dp))
                PixelButton(text = "CLOSE", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

private val MIN_TILE_WIDTH = 88.dp
