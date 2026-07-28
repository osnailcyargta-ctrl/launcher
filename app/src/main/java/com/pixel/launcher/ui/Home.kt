package com.pixel.launcher.ui

import android.app.ActivityOptions
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.IntState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixel.launcher.PixelLauncherApp
import com.pixel.launcher.core.AppEntry
import com.pixel.launcher.core.Folder
import com.pixel.launcher.core.IconLoader
import com.pixel.launcher.core.Prefs
import com.pixel.launcher.core.SfxEvent
import com.pixel.launcher.core.Tile
import kotlinx.coroutines.flow.drop
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

/** Rows per snap. The drawer always advances a whole block of four. */
private const val ROWS_PER_PAGE = 4
private const val DOCK_FREQUENT = 3
private val MIN_TILE_WIDTH = 88.dp

private enum class Screen { HOME, SETTINGS }

@Composable
fun LauncherRoot(homePresses: IntState) {
    val context = LocalContext.current
    val app = context.applicationContext as PixelLauncherApp

    val settings by app.prefs.settings.collectAsStateWithLifecycle()
    val retro = paletteOf(settings.palette)
    val typography = typographyOf(settings)

    LaunchedEffect(settings.iconSignature) {
        app.icons.applySignature(settings.iconSignature)
    }

    LaunchedEffect(settings.iconSource, settings.iconPackPackage) {
        app.icons.useExternalPack(
            if (settings.iconSource == com.pixel.launcher.core.IconSource.EXTERNAL) {
                settings.iconPackPackage
            } else {
                ""
            },
        )
    }

    CompositionLocalProvider(
        LocalRetro provides retro,
        LocalSettings provides settings,
        LocalSfx provides app.sfx,
        LocalIcons provides app.icons,
        LocalTypography provides typography,
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

@OptIn(ExperimentalComposeUiApi::class)
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
    val keyboard = LocalSoftwareKeyboardController.current

    val apps by app.apps.apps.collectAsStateWithLifecycle()
    val loaded by app.apps.loaded.collectAsStateWithLifecycle()
    val hidden by app.prefs.hidden.collectAsStateWithLifecycle()
    val pinnedKeys by app.prefs.pinned.collectAsStateWithLifecycle()
    val ranking by app.usage.ranking.collectAsStateWithLifecycle()
    val folders by app.prefs.folders.collectAsStateWithLifecycle()

    var menuTarget by remember { mutableStateOf<AppEntry?>(null) }
    var openFolder by remember { mutableStateOf<Folder?>(null) }
    var folderPickerFor by remember { mutableStateOf<AppEntry?>(null) }
    var query by remember { mutableStateOf("") }

    val byKey = remember(apps) { apps.associateBy { it.key } }

    // Apps that live in a folder are represented by the folder tile instead.
    val visibleApps = remember(apps, hidden) {
        apps.filter { it.key !in hidden }
    }
    val groupedKeys = remember(folders) {
        folders.flatMapTo(HashSet()) { it.appKeys }
    }

    val folderTiles = remember(folders, byKey) {
        folders.map { folder ->
            Tile.Group(folder, folder.appKeys.mapNotNull { byKey[it] })
        }
    }

    val tiles: List<Tile> = remember(
        visibleApps,
        folderTiles,
        groupedKeys,
        settings.showSettingsTile,
        query,
    ) {
        val trimmed = query.trim()
        if (trimmed.isNotEmpty()) {
            // Search looks at every app, folders and hidden grouping aside.
            val needle = trimmed.lowercase(Locale.getDefault())
            visibleApps
                .filter { it.label.lowercase(Locale.getDefault()).contains(needle) }
                .map { Tile.App(it) }
        } else {
            buildList<Tile>(visibleApps.size + folderTiles.size + 1) {
                if (settings.showSettingsTile) add(Tile.Settings)
                addAll(folderTiles)
                for (entry in visibleApps) {
                    if (entry.key !in groupedKeys) add(Tile.App(entry))
                }
            }
        }
    }

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

    val retroPalette = LocalRetro.current
    val spec = remember(settings.iconSignature, retroPalette) {
        IconLoader.specOf(settings, retroPalette.shades)
    }

    fun open(entry: AppEntry, bounds: android.graphics.Rect?) {
        sfx.play(SfxEvent.OPEN)
        app.usage.record(entry.key)
        keyboard?.hide()
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

    val overlayOpen = menuTarget != null || openFolder != null || folderPickerFor != null
    BackHandler(enabled = overlayOpen || query.isNotEmpty()) {
        when {
            folderPickerFor != null -> folderPickerFor = null
            openFolder != null -> openFolder = null
            menuTarget != null -> menuTarget = null
            else -> query = ""
        }
        sfx.play(SfxEvent.BACK)
    }

    LaunchedEffect(resetSignal) {
        if (resetSignal > 0) {
            query = ""
            menuTarget = null
            openFolder = null
            folderPickerFor = null
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxHeight < 480.dp
        val columns = settings.columns.takeIf { it > 0 }
            ?: (maxWidth / MIN_TILE_WIDTH).toInt().coerceIn(3, 8)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .padding(horizontal = 8.dp),
        ) {
            TopWidgets(
                compact = compact,
                onLongPress = {
                    sfx.play(SfxEvent.CLICK)
                    onOpenSettings()
                },
            )

            if (settings.showSearch) {
                SearchBar(
                    query = query,
                    onQueryChange = { query = it },
                    onClear = {
                        sfx.play(SfxEvent.BACK)
                        query = ""
                        keyboard?.hide()
                    },
                )
                Spacer(Modifier.height(6.dp))
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    !loaded -> CenterNotice("SCANNING APPS...")
                    tiles.isEmpty() && query.isNotEmpty() -> CenterNotice("NO MATCH")
                    tiles.isEmpty() -> CenterNotice("NO APPS FOUND")
                    else -> AppDrawer(
                        tiles = tiles,
                        columns = columns,
                        spec = spec,
                        compact = compact,
                        resetSignal = resetSignal,
                        searching = query.isNotEmpty(),
                        onOpenSettings = {
                            sfx.play(SfxEvent.CLICK)
                            onOpenSettings()
                        },
                        onOpenApp = { entry, bounds -> open(entry, bounds) },
                        onOpenFolder = { folder ->
                            sfx.play(SfxEvent.CLICK)
                            openFolder = folder
                        },
                        onLongPressApp = { entry ->
                            sfx.play(SfxEvent.MENU)
                            menuTarget = entry
                        },
                        onLongPressFolder = { folder ->
                            sfx.play(SfxEvent.MENU)
                            openFolder = folder
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
                inFolder = app.prefs.folderOf(entry.key) != null,
                onDismiss = { menuTarget = null },
                onPin = {
                    app.prefs.togglePin(entry.key)
                    sfx.play(SfxEvent.PIN)
                    menuTarget = null
                },
                onFolder = {
                    sfx.play(SfxEvent.CLICK)
                    folderPickerFor = entry
                    menuTarget = null
                },
                onRemoveFromFolder = {
                    app.prefs.removeFromFolder(entry.key)
                    sfx.play(SfxEvent.TOGGLE)
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

        openFolder?.let { folder ->
            val live = folders.firstOrNull { it.id == folder.id }
            if (live == null) {
                openFolder = null
            } else {
                FolderPanel(
                    folder = live,
                    entries = live.appKeys.mapNotNull { byKey[it] },
                    spec = spec,
                    onDismiss = { openFolder = null },
                    onOpenApp = { entry, bounds ->
                        openFolder = null
                        open(entry, bounds)
                    },
                    onRemove = { entry ->
                        app.prefs.removeFromFolder(entry.key)
                        sfx.play(SfxEvent.TOGGLE)
                    },
                    onRename = { name ->
                        app.prefs.renameFolder(live.id, name)
                        sfx.play(SfxEvent.TOGGLE)
                    },
                    onDelete = {
                        app.prefs.deleteFolder(live.id)
                        sfx.play(SfxEvent.BACK)
                        openFolder = null
                    },
                )
            }
        }

        folderPickerFor?.let { entry ->
            FolderPicker(
                entry = entry,
                folders = folders,
                onDismiss = { folderPickerFor = null },
                onPick = { folder ->
                    app.prefs.addToFolder(folder.id, entry.key)
                    sfx.play(SfxEvent.PIN)
                    folderPickerFor = null
                },
                onCreate = { name ->
                    val folder = app.prefs.createFolder(name)
                    app.prefs.addToFolder(folder.id, entry.key)
                    sfx.play(SfxEvent.PIN)
                    folderPickerFor = null
                },
            )
        }
    }
}

// -------------------------------------------------------------------- search

@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit, onClear: () -> Unit) {
    val retro = LocalRetro.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        DisplayText(text = ">", size = 12.sp, color = retro.accent)
        PixelTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = "SEARCH APPS",
            modifier = Modifier.weight(1f),
            fontSize = 17.sp,
        )
        if (query.isNotEmpty()) {
            PixelButton(text = "X", onClick = onClear, fontSize = 15.sp)
        }
    }
}

// -------------------------------------------------------------------- drawer

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppDrawer(
    tiles: List<Tile>,
    columns: Int,
    spec: IconLoader.Spec,
    compact: Boolean,
    resetSignal: Int,
    searching: Boolean,
    onOpenSettings: () -> Unit,
    onOpenApp: (AppEntry, android.graphics.Rect?) -> Unit,
    onOpenFolder: (Folder) -> Unit,
    onLongPressApp: (AppEntry) -> Unit,
    onLongPressFolder: (Folder) -> Unit,
) {
    val settings = LocalSettings.current
    val sfx = LocalSfx.current

    val perPage = columns * ROWS_PER_PAGE
    val pageCount = max(1, ceil(tiles.size / perPage.toFloat()).toInt())
    val pagerState = rememberPagerState(pageCount = { pageCount })

    LaunchedEffect(resetSignal, searching) {
        if (pagerState.currentPage != 0) pagerState.animateScrollToPage(0)
    }

    // A tick every time the drawer settles on a new block of four rows.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .drop(1)
            .collect { sfx.play(SfxEvent.PAGE) }
    }

    val letters = remember(tiles) {
        tiles.mapNotNull { tile ->
            when (tile) {
                is Tile.App -> tile.entry.label.firstOrNull()
                is Tile.Group -> tile.folder.name.firstOrNull()
                Tile.Settings -> null
            }?.uppercaseChar()
        }.distinct()
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
                val scale = (settings.iconScale / 100f).coerceIn(0.5f, 1.6f)
                val iconSize = (
                    minOf(
                        cellWidth * 0.72f,
                        if (showLabels) cellHeight * 0.58f else cellHeight * 0.82f,
                    ).coerceAtMost(if (compact) 48.dp else 72.dp)
                    ) * scale

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
                                            onOpenFolder = onOpenFolder,
                                            onLongPressApp = onLongPressApp,
                                            onLongPressFolder = onLongPressFolder,
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
            if (settings.showAlphabetBar && letters.size > 2 && !searching) {
                AlphabetBar(
                    letters = letters,
                    modifier = Modifier.fillMaxHeight().width(16.dp),
                    onPick = { letter ->
                        val index = tiles.indexOfFirst { tile ->
                            val first = when (tile) {
                                is Tile.App -> tile.entry.label.firstOrNull()
                                is Tile.Group -> tile.folder.name.firstOrNull()
                                Tile.Settings -> null
                            }?.uppercaseChar()
                            first == letter
                        }
                        if (index >= 0) {
                            sfx.play(SfxEvent.PAGE)
                            index / perPage
                        } else {
                            null
                        }
                    },
                    pagerState = pagerState,
                )
            } else {
                PageIndicator(
                    pageCount = pageCount,
                    current = pagerState.currentPage,
                    modifier = Modifier.fillMaxHeight().width(10.dp),
                )
            }
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
    onOpenFolder: (Folder) -> Unit,
    onLongPressApp: (AppEntry) -> Unit,
    onLongPressFolder: (Folder) -> Unit,
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

        is Tile.Group -> AppTile(
            tile = tile,
            spec = spec,
            iconSize = iconSize,
            label = if (showLabel) tile.folder.name else null,
            labelSize = 15.sp,
            highlight = true,
            onClick = { onOpenFolder(tile.folder) },
            onLongClick = { onLongPressFolder(tile.folder) },
        )

        is Tile.App -> AppTile(
            tile = tile,
            spec = spec,
            iconSize = iconSize,
            label = if (showLabel) tile.entry.label else null,
            labelSize = 15.sp,
            onClick = { bounds -> onOpenApp(tile.entry, bounds) },
            onLongClick = { onLongPressApp(tile.entry) },
        )
    }
}

/**
 * The A-Z strip. Dragging along it scrubs through the drawer, which is the
 * fastest way through a few hundred apps without opening the keyboard.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlphabetBar(
    letters: List<Char>,
    pagerState: androidx.compose.foundation.pager.PagerState,
    onPick: (Char) -> Int?,
    modifier: Modifier = Modifier,
) {
    val retro = LocalRetro.current
    var target by remember { mutableStateOf<Int?>(null) }
    var activeLetter by remember { mutableStateOf<Char?>(null) }

    LaunchedEffect(target) {
        target?.let { page ->
            pagerState.scrollToPage(page)
            target = null
        }
    }

    fun pick(y: Float, height: Float) {
        if (height <= 0f || letters.isEmpty()) return
        val index = ((y / height) * letters.size).toInt().coerceIn(0, letters.lastIndex)
        val letter = letters[index]
        if (letter == activeLetter) return
        activeLetter = letter
        target = onPick(letter)
    }

    Column(
        modifier = modifier
            .pointerInput(letters) {
                detectVerticalDragGestures(
                    onDragEnd = { activeLetter = null },
                    onDragCancel = { activeLetter = null },
                ) { change, _ ->
                    pick(change.position.y, size.height.toFloat())
                }
            }
            .pointerInput(letters) {
                detectTapGestures { offset ->
                    activeLetter = null
                    pick(offset.y, size.height.toFloat())
                }
            },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEach { letter ->
            BodyText(
                text = letter.toString(),
                size = 13.sp,
                color = if (letter == activeLetter) retro.accent else retro.textDim,
            )
        }
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
    val iconSize = if (compact) 28.dp else 38.dp

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

// -------------------------------------------------------------------- panels

@Composable
private fun CenterNotice(text: String) {
    val retro = LocalRetro.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        DisplayText(text = text, size = 12.sp, color = retro.textDim, align = TextAlign.Center)
    }
}

@Composable
private fun Scrim(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val retro = LocalRetro.current
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(retro.background.copy(alpha = 0.9f))
            .clickable(interactionSource = interaction, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        val panelInteraction = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier.clickable(
                interactionSource = panelInteraction,
                indication = null,
                onClick = {},
            ),
        ) {
            content()
        }
    }
}

@Composable
private fun AppMenu(
    entry: AppEntry,
    pinned: Boolean,
    inFolder: Boolean,
    onDismiss: () -> Unit,
    onPin: () -> Unit,
    onFolder: () -> Unit,
    onRemoveFromFolder: () -> Unit,
    onHide: () -> Unit,
    onInfo: () -> Unit,
    onUninstall: () -> Unit,
) {
    val retro = LocalRetro.current

    Scrim(onDismiss = onDismiss) {
        PixelPanel(
            modifier = Modifier.fillMaxWidth(0.86f).padding(16.dp),
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
                    text = if (pinned) "UNPIN FROM DOCK" else "PIN TO DOCK",
                    onClick = onPin,
                    selected = pinned,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                PixelButton(
                    text = "ADD TO FOLDER",
                    onClick = onFolder,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (inFolder) {
                    Spacer(Modifier.height(6.dp))
                    PixelButton(
                        text = "REMOVE FROM FOLDER",
                        onClick = onRemoveFromFolder,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
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

@Composable
private fun FolderPanel(
    folder: Folder,
    entries: List<AppEntry>,
    spec: IconLoader.Spec,
    onDismiss: () -> Unit,
    onOpenApp: (AppEntry, android.graphics.Rect?) -> Unit,
    onRemove: (AppEntry) -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val retro = LocalRetro.current
    var renaming by remember { mutableStateOf(false) }
    var name by remember(folder.id) { mutableStateOf(folder.name) }

    Scrim(onDismiss = onDismiss) {
        PixelPanel(
            modifier = Modifier.fillMaxWidth(0.92f).padding(12.dp),
            contentPadding = 12.dp,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (renaming) {
                    PixelTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = "FOLDER NAME",
                        onSubmit = {
                            onRename(name.ifBlank { folder.name })
                            renaming = false
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PixelButton(
                            text = "SAVE",
                            onClick = {
                                onRename(name.ifBlank { folder.name })
                                renaming = false
                            },
                        )
                        PixelButton(text = "CANCEL", onClick = { renaming = false })
                    }
                } else {
                    DisplayText(
                        text = folder.name.uppercase(Locale.getDefault()),
                        size = 12.sp,
                        color = retro.accent,
                        align = TextAlign.Center,
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(Modifier.height(10.dp))
                PixelDivider()
                Spacer(Modifier.height(10.dp))

                if (entries.isEmpty()) {
                    BodyText("EMPTY FOLDER", 17.sp, retro.textDim)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().height(280.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(entries, key = { it.key }) { entry ->
                            FolderRow(
                                entry = entry,
                                spec = spec,
                                onOpen = { bounds -> onOpenApp(entry, bounds) },
                                onRemove = { onRemove(entry) },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PixelButton(text = "RENAME", onClick = { renaming = true })
                    PixelButton(text = "DELETE", onClick = onDelete)
                    PixelButton(text = "CLOSE", onClick = onDismiss)
                }
            }
        }
    }
}

@Composable
private fun FolderRow(
    entry: AppEntry,
    spec: IconLoader.Spec,
    onOpen: (android.graphics.Rect?) -> Unit,
    onRemove: () -> Unit,
) {
    val retro = LocalRetro.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppTile(
            tile = Tile.App(entry),
            spec = spec,
            iconSize = 34.dp,
            onClick = onOpen,
        )
        BodyText(
            text = entry.label,
            size = 17.sp,
            color = retro.text,
            modifier = Modifier.weight(1f),
        )
        PixelButton(text = "-", onClick = onRemove, fontSize = 15.sp)
    }
}

@Composable
private fun FolderPicker(
    entry: AppEntry,
    folders: List<Folder>,
    onDismiss: () -> Unit,
    onPick: (Folder) -> Unit,
    onCreate: (String) -> Unit,
) {
    val retro = LocalRetro.current
    var newName by remember { mutableStateOf("") }

    Scrim(onDismiss = onDismiss) {
        PixelPanel(
            modifier = Modifier.fillMaxWidth(0.9f).padding(14.dp),
            contentPadding = 12.dp,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                DisplayText(
                    text = "ADD TO FOLDER",
                    size = 11.sp,
                    color = retro.accent,
                    align = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                BodyText(entry.label, 16.sp, retro.textDim, maxLines = 1)
                Spacer(Modifier.height(10.dp))
                PixelDivider()
                Spacer(Modifier.height(10.dp))

                if (folders.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().height(180.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(folders, key = { it.id }) { folder ->
                            PixelButton(
                                text = "${folder.name}  (${folder.appKeys.size})",
                                onClick = { onPick(folder) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                PixelTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "NEW FOLDER NAME",
                    onSubmit = { if (newName.isNotBlank()) onCreate(newName) },
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PixelButton(
                        text = "CREATE",
                        onClick = { onCreate(newName.ifBlank { "FOLDER" }) },
                    )
                    PixelButton(text = "CANCEL", onClick = onDismiss)
                }
            }
        }
    }
}
