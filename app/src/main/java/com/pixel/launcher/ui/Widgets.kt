package com.pixel.launcher.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.pixel.launcher.core.ClockMode
import com.pixel.launcher.core.TopWidget
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The stack of widgets above the drawer. Order is fixed so the layout does not
 * jump around when a widget is switched on or off.
 */
@Composable
fun TopWidgets(compact: Boolean, onLongPress: () -> Unit) {
    val settings = LocalSettings.current
    val enabled = settings.widgets

    if (enabled.isEmpty()) {
        Spacer(Modifier.height(if (compact) 4.dp else 10.dp))
        return
    }

    val openSettings by rememberUpdatedState(onLongPress)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) { detectTapGestures(onLongPress = { openSettings() }) }
            .padding(top = if (compact) 2.dp else 8.dp, bottom = if (compact) 2.dp else 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 6.dp),
    ) {
        if (TopWidget.CLOCK in enabled) {
            ClockWidget(compact = compact)
        }
        if (TopWidget.TEXT in enabled) {
            TextWidget(compact = compact)
        }
        if (TopWidget.DINO in enabled) {
            DinoWidget(height = if (compact) 34.dp else 52.dp)
        }
    }
}

// --------------------------------------------------------------------- clock

@Composable
private fun ClockWidget(compact: Boolean) {
    val retro = LocalRetro.current
    val settings = LocalSettings.current
    val clock = rememberClockSnapshot(settings.clockMode, settings.showBattery)

    Column(
        modifier = Modifier.fillMaxWidth(),
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

// ---------------------------------------------------------------------- text

@Composable
private fun TextWidget(compact: Boolean) {
    val retro = LocalRetro.current
    val settings = LocalSettings.current
    if (settings.customText.isBlank()) return

    DisplayText(
        text = settings.customText,
        size = if (compact) 10.sp else 13.sp,
        color = retro.text,
        align = TextAlign.Center,
        maxLines = 2,
        letterSpacing = 1.sp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
    )
}

// ---------------------------------------------------------------------- dino

private val DINO_BODY = listOf(
    "         ####",
    "        #.###",
    "        #####",
    "        ###  ",
    "#       #####",
    "##     ######",
    "###   #######",
    "#############",
    " ############",
    "  ##########",
)

private val DINO_LEGS_A = listOf(
    "   ###   ##  ",
    "   ##     #  ",
)

private val DINO_LEGS_B = listOf(
    "   ###   ##  ",
    "    #    ##  ",
)

private val CACTUS = listOf(
    "  #  ",
    "  #  ",
    "# # #",
    "# # #",
    "#####",
    "  #  ",
    "  #  ",
    "  #  ",
)

/** Height of the jump arc, in cells, for each tick of a jump. */
private val JUMP_ARC = intArrayOf(0, 3, 5, 6, 7, 7, 7, 6, 5, 3, 1, 0)

private const val WORLD_COLUMNS = 46
private const val GROUND_ROW = 12
private const val DINO_COLUMN = 4
private const val TICK_MS = 90L

private data class DinoFrame(
    val worldX: Int,
    val jumpHeight: Int,
    val legFrame: Int,
    val cacti: List<Int>,
    val score: Int,
)

/**
 * The Chrome offline dinosaur, running forever.
 *
 * It ticks about eleven times a second - fast enough to read as animation, slow
 * enough to be nearly free - and the loop is bound to the RESUMED state, so it
 * stops completely the moment another app is in front.
 */
@Composable
private fun DinoWidget(height: Dp) {
    val retro = LocalRetro.current
    val settings = LocalSettings.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val interaction = remember { MutableInteractionSource() }

    var frame by remember {
        mutableStateOf(DinoFrame(0, 0, 0, listOf(WORLD_COLUMNS + 8, WORLD_COLUMNS + 30), 0))
    }
    var jumpTick by remember { mutableStateOf(-1) }
    var manualJump by remember { mutableStateOf(false) }

    val running = settings.dinoEnabled

    LaunchedEffect(lifecycleOwner, running) {
        if (!running) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(TICK_MS)
                val current = frame
                val worldX = current.worldX + 1

                // Move cacti left; recycle the ones that leave the screen.
                val moved = current.cacti.map { it - 1 }
                val alive = moved.filter { it > -6 }
                val spawned = if (alive.size < 2) {
                    val furthest = alive.maxOrNull() ?: DINO_COLUMN
                    alive + (furthest + 18 + (worldX % 13))
                } else {
                    alive
                }

                // Start a jump when the next cactus is about to arrive.
                val incoming = spawned.filter { it > DINO_COLUMN }.minOrNull()
                if (jumpTick < 0 && (manualJump || (incoming != null && incoming - DINO_COLUMN <= 7))) {
                    jumpTick = 0
                    manualJump = false
                }

                val arcHeight = if (jumpTick in JUMP_ARC.indices) JUMP_ARC[jumpTick] else 0
                jumpTick = when {
                    jumpTick < 0 -> -1
                    jumpTick + 1 >= JUMP_ARC.size -> -1
                    else -> jumpTick + 1
                }

                frame = DinoFrame(
                    worldX = worldX,
                    jumpHeight = arcHeight,
                    legFrame = if (arcHeight > 0) 0 else (worldX / 2) % 2,
                    cacti = spawned,
                    score = worldX / 4,
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = { manualJump = true },
            ),
    ) {
        DinoCanvas(frame = frame, accent = retro.accent, dim = retro.textDim, edge = retro.edge)
    }
}

@Composable
private fun DinoCanvas(frame: DinoFrame, accent: Color, dim: Color, edge: Color) {
    Canvas(Modifier.fillMaxSize()) {
        val cell = minOf(size.width / WORLD_COLUMNS, size.height / (GROUND_ROW + 2))
        if (cell <= 0f) return@Canvas
        val originX = (size.width - cell * WORLD_COLUMNS) / 2f
        val originY = 0f
        val block = Size(cell + 0.5f, cell + 0.5f)

        fun put(column: Int, row: Int, color: Color) {
            if (column < 0 || column >= WORLD_COLUMNS) return
            drawRect(
                color = color,
                topLeft = Offset(originX + column * cell, originY + row * cell),
                size = block,
            )
        }

        fun sprite(rows: List<String>, column: Int, topRow: Int, color: Color, eye: Color) {
            for (y in rows.indices) {
                val line = rows[y]
                for (x in line.indices) {
                    when (line[x]) {
                        '#' -> put(column + x, topRow + y, color)
                        '.' -> put(column + x, topRow + y, eye)
                        else -> Unit
                    }
                }
            }
        }

        // Ground: a dashed line that scrolls with the world.
        for (column in 0 until WORLD_COLUMNS) {
            val worldColumn = column + frame.worldX
            val color = if (worldColumn % 7 == 0) dim else edge
            put(column, GROUND_ROW, color)
        }

        // Cacti sit on the ground line.
        for (cactusX in frame.cacti) {
            sprite(CACTUS, cactusX, GROUND_ROW - CACTUS.size, dim, dim)
        }

        // Dino, lifted by the jump arc.
        val legs = if (frame.legFrame == 0) DINO_LEGS_A else DINO_LEGS_B
        val bodyTop = GROUND_ROW - DINO_BODY.size - legs.size - frame.jumpHeight
        sprite(DINO_BODY, DINO_COLUMN, bodyTop, accent, edge)
        sprite(legs, DINO_COLUMN, bodyTop + DINO_BODY.size, accent, accent)
    }
}
