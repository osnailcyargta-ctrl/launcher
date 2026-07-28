package com.pixel.launcher.core

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext

/**
 * Renders and caches app icons.
 *
 * Icons are the only expensive thing a launcher does, so the work is shaped
 * around three rules:
 *
 *  1. Nothing is decoded until a tile is actually on screen.
 *  2. Decoding runs on at most two threads, so scrolling never spins up the
 *     whole CPU (which is what drains the battery on a big app list).
 *  3. With pixel mode on, the cached bitmap is the *small* one - a 48x48 icon
 *     costs 9 KB instead of 147 KB, so hundreds of apps stay in memory and are
 *     never re-decoded.
 */
class IconLoader(context: Context) {

    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager
    private val densityDpi = appContext.resources.displayMetrics.densityDpi

    @OptIn(ExperimentalCoroutinesApi::class)
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(2)

    private val cache = object : LruCache<String, ImageBitmap>(cacheSizeKb()) {
        override fun sizeOf(key: String, value: ImageBitmap): Int =
            (value.width * value.height * 4 / 1024).coerceAtLeast(1)
    }

    @Volatile
    private var signature: String = ""

    @Volatile
    private var externalPack: ExternalIconPack? = null

    @Volatile
    private var externalPackName: String = ""

    /** Drops every cached bitmap when the icon appearance settings change. */
    fun applySignature(next: String) {
        if (next == signature) return
        signature = next
        cache.evictAll()
    }

    /** Loads (or clears) the third-party icon pack. Safe to call repeatedly. */
    suspend fun useExternalPack(packageName: String) {
        if (packageName == externalPackName) return
        externalPackName = packageName
        externalPack = if (packageName.isBlank()) {
            null
        } else {
            withContext(dispatcher) { ExternalIconPack.load(appContext, packageName) }
        }
        cache.evictAll()
    }

    fun cached(key: String): ImageBitmap? = cache.get(key)

    /** Halves the cache when the launcher goes off screen. */
    fun trim() {
        cache.trimToSize(cache.size() / 2)
    }

    suspend fun load(entry: AppEntry, spec: Spec): ImageBitmap? {
        cache.get(entry.key)?.let { return it }
        return withContext(dispatcher) {
            val bitmap = runCatching { renderFor(entry, spec) }.getOrNull()
                ?: return@withContext null
            bitmap.asImageBitmap().also { cache.put(entry.key, it) }
        }
    }

    private fun renderFor(entry: AppEntry, spec: Spec): Bitmap? {
        // The launcher's own pack draws straight from its character grids, so
        // there is no drawable to decode at all for a matched app.
        if (spec.source == IconSource.BUILT_IN) {
            PixelIconPack.match(entry.packageName)?.let { art ->
                return downscale(maskShape(drawPixelIcon(art, spec.renderPx), spec), spec)
            }
        }

        val drawable = resolveDrawable(entry, spec) ?: return null
        var bitmap = render(drawable, spec)
        if (spec.source == IconSource.BUILT_IN && spec.shades.size >= 2) {
            bitmap = quantize(bitmap, spec.shades)
        }
        return bitmap
    }

    private fun resolveDrawable(entry: AppEntry, spec: Spec): Drawable? {
        if (spec.source == IconSource.EXTERNAL) {
            externalPack
                ?.drawableFor(ComponentName(entry.packageName, entry.className))
                ?.let { return it }
        }
        entry.info?.let { info ->
            // getBadgedIcon() also stamps the work-profile badge for free.
            runCatching { info.getBadgedIcon(densityDpi) }.getOrNull()?.let { return it }
            runCatching { info.getIcon(densityDpi) }.getOrNull()?.let { return it }
        }
        return runCatching { packageManager.getApplicationIcon(entry.packageName) }.getOrNull()
    }

    // ------------------------------------------------------------- built-in art

    private fun drawPixelIcon(art: PixelIcon, size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(art.bg)

        val columns = art.rows.maxOfOrNull { it.length } ?: return bitmap
        if (columns == 0 || art.rows.isEmpty()) return bitmap
        val cell = size.toFloat() / maxOf(columns, art.rows.size)
        val originX = (size - cell * columns) / 2f
        val originY = (size - cell * art.rows.size) / 2f

        val paint = Paint()
        for (y in art.rows.indices) {
            val row = art.rows[y]
            for (x in row.indices) {
                paint.color = when (row[x]) {
                    '#' -> art.fg
                    '+' -> art.accent
                    else -> continue
                }
                val left = originX + x * cell
                val top = originY + y * cell
                canvas.drawRect(left, top, left + cell + 0.5f, top + cell + 0.5f, paint)
            }
        }
        return bitmap
    }

    /**
     * Snaps every pixel to the nearest palette shade by brightness, which is what
     * turns an ordinary app icon into something that belongs on a green CRT.
     */
    private fun quantize(bitmap: Bitmap, shades: List<Int>): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val luminance = IntArray(shades.size) { index ->
            val color = shades[index]
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            (r * 299 + g * 587 + b * 114) / 1000
        }

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val alpha = pixel ushr 24
            if (alpha == 0) continue
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val lum = (r * 299 + g * 587 + b * 114) / 1000

            var best = 0
            var bestDistance = Int.MAX_VALUE
            for (index in shades.indices) {
                val distance = kotlin.math.abs(luminance[index] - lum)
                if (distance < bestDistance) {
                    bestDistance = distance
                    best = index
                }
            }
            pixels[i] = (alpha shl 24) or (shades[best] and 0x00FFFFFF)
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    // ---------------------------------------------------------------- pipeline

    private fun render(drawable: Drawable, spec: Spec): Bitmap {
        val size = spec.renderPx
        val src = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val srcCanvas = Canvas(src)

        val adaptive = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            drawable is AdaptiveIconDrawable
        if (adaptive && spec.shape != IconShape.ORIGINAL) {
            // Adaptive layers are authored on a 108dp canvas of which only the
            // central 72dp is guaranteed visible. Scale up and centre-crop so our
            // own mask lands on the same content the platform would show.
            val overdraw = ((size * (108f / 72f - 1f)) / 2f).toInt()
            drawable.setBounds(-overdraw, -overdraw, size + overdraw, size + overdraw)
        } else {
            drawable.setBounds(0, 0, size, size)
        }
        drawable.draw(srcCanvas)

        return downscale(maskShape(src, spec), spec)
    }

    private fun maskShape(source: Bitmap, spec: Spec): Bitmap {
        if (spec.shape == IconShape.ORIGINAL) return source

        val size = source.width
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        val bounds = RectF(0f, 0f, size.toFloat(), size.toFloat())
        when (spec.shape) {
            IconShape.CIRCLE -> canvas.drawOval(bounds, paint)
            IconShape.ROUNDED_SQUARE -> {
                val radius = size * 0.22f
                canvas.drawRoundRect(bounds, radius, radius, paint)
            }
            else -> canvas.drawRect(bounds, paint)
        }
        // The shader has already been consumed by the draw call above.
        source.recycle()
        return out
    }

    /**
     * Shrinks the icon to the chosen pixel resolution. Filtering is left *on*
     * here: a clean box-filtered downsample is what gives pixel art its colour.
     * The chunky look comes from drawing it back up with nearest-neighbour.
     */
    private fun downscale(bitmap: Bitmap, spec: Spec): Bitmap {
        if (!spec.pixelate || spec.pixelPx >= bitmap.width) return bitmap
        val small = Bitmap.createScaledBitmap(bitmap, spec.pixelPx, spec.pixelPx, true)
        if (small !== bitmap) bitmap.recycle()
        return small
    }

    private fun cacheSizeKb(): Int {
        val heapKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        return (heapKb / 8).coerceIn(4 * 1024, 16 * 1024)
    }

    /** Everything that affects the rendered pixels of an icon. */
    data class Spec(
        val shape: IconShape,
        val pixelate: Boolean,
        val pixelPx: Int,
        val renderPx: Int,
        val source: IconSource,
        val shades: List<Int>,
    )

    companion object {
        fun specOf(settings: Settings, shades: List<Int>): Spec = Spec(
            shape = settings.iconShape,
            pixelate = settings.pixelIcons,
            pixelPx = settings.pixelLevel.coerceIn(16, 128),
            renderPx = if (settings.lowPower) 96 else 192,
            source = settings.iconSource,
            shades = shades,
        )
    }
}
