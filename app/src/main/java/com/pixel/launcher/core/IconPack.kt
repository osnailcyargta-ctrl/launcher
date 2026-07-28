package com.pixel.launcher.core

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.Log
import org.xmlpull.v1.XmlPullParser

/**
 * A hand-drawn icon: a solid tile plus a glyph mask.
 *
 * Rows are read as pixels - '#' uses [fg], '+' uses [accent], and anything else
 * leaves the tile colour showing through. Keeping the artwork as text means the
 * whole icon pack lives in Kotlin with no drawables to ship or decode.
 */
class PixelIcon(
    val bg: Int,
    val fg: Int,
    val accent: Int = fg,
    val rows: List<String>,
)

private const val WHITE = 0xFFF4F4F4.toInt()
private const val BLACK = 0xFF12141A.toInt()

/**
 * The launcher's own icon pack.
 *
 * Only widely installed apps get bespoke artwork; everything else falls back to
 * the app's real icon re-coloured into the active palette, so a drawer never
 * ends up half themed and half not.
 */
object PixelIconPack {

    private val phone = listOf(
        "            ",
        "   #####    ",
        "  ##   ##   ",
        "  ##   ##   ",
        "   #####    ",
        "    ###     ",
        "     ###    ",
        "      ###   ",
        "      ##  ##",
        "       ## ##",
        "        ####",
        "            ",
    )

    private val message = listOf(
        "            ",
        "  ########  ",
        " ########## ",
        " ########## ",
        " ## ## ## # ",
        " ########## ",
        " ########## ",
        " ########## ",
        "  ######### ",
        "  ###       ",
        "  #         ",
        "            ",
    )

    private val camera = listOf(
        "            ",
        "     ###    ",
        "  ########  ",
        " ########## ",
        " ###    ### ",
        " ##  ##  ## ",
        " ##  ##  ## ",
        " ###    ### ",
        " ########## ",
        "  ########  ",
        "            ",
        "            ",
    )

    private val gallery = listOf(
        "            ",
        " ########## ",
        " #        # ",
        " #  ++    # ",
        " #        # ",
        " #    ##  # ",
        " #   #### # ",
        " #  ##### # ",
        " #########  ",
        " ########## ",
        "            ",
        "            ",
    )

    private val globe = listOf(
        "            ",
        "   ######   ",
        "  ##    ##  ",
        " ##  ##  ## ",
        " ## #  # ## ",
        " ########## ",
        " ## #  # ## ",
        " ##  ##  ## ",
        "  ##    ##  ",
        "   ######   ",
        "            ",
        "            ",
    )

    private val play = listOf(
        "            ",
        "  ########  ",
        " ########## ",
        " ###    ### ",
        " ##  #   ## ",
        " ##  ###  # ",
        " ##  #   ## ",
        " ###    ### ",
        " ########## ",
        "  ########  ",
        "            ",
        "            ",
    )

    private val chat = listOf(
        "            ",
        "   ######   ",
        "  ########  ",
        " ##      ## ",
        " ##  ##  ## ",
        " ##  ##  ## ",
        " ##      ## ",
        "  ########  ",
        "   #######  ",
        "  ###       ",
        "            ",
        "            ",
    )

    private val square = listOf(
        "            ",
        " ########## ",
        " ##      ## ",
        " #   ##   # ",
        " #  ####  # ",
        " #  ####  # ",
        " #   ##   # ",
        " ##      ## ",
        " ####   ### ",
        " ########## ",
        "            ",
        "            ",
    )

    private val wave = listOf(
        "            ",
        "   ######   ",
        "  ########  ",
        " ###    ### ",
        " ## #### ## ",
        " ##      ## ",
        " ## #### ## ",
        " ###    ### ",
        "  ########  ",
        "   ######   ",
        "            ",
        "            ",
    )

    private val mail = listOf(
        "            ",
        "            ",
        " ########## ",
        " ##      ## ",
        " # ##  ## # ",
        " #   ##   # ",
        " #  #  #  # ",
        " ## #  # ## ",
        " ########## ",
        "            ",
        "            ",
        "            ",
    )

    private val pin = listOf(
        "            ",
        "   ######   ",
        "  ########  ",
        " ###    ### ",
        " ##  ##  ## ",
        " ##  ##  ## ",
        " ###    ### ",
        "  ########  ",
        "   ######   ",
        "    ####    ",
        "     ##     ",
        "            ",
    )

    private val calculator = listOf(
        "            ",
        " ########## ",
        " #        # ",
        " #  ####  # ",
        " #        # ",
        " # ## ##  # ",
        " #        # ",
        " # ## ##  # ",
        " #        # ",
        " ########## ",
        "            ",
        "            ",
    )

    private val calendar = listOf(
        "            ",
        "  ##    ##  ",
        " ########## ",
        " ########## ",
        " #        # ",
        " # ## ##  # ",
        " #        # ",
        " # ## ##  # ",
        " #        # ",
        " ########## ",
        "            ",
        "            ",
    )

    private val clock = listOf(
        "            ",
        "   ######   ",
        "  ##    ##  ",
        " ##  #   ## ",
        " ##  #   ## ",
        " ##  ###  # ",
        " ##      ## ",
        " ##      ## ",
        "  ##    ##  ",
        "   ######   ",
        "            ",
        "            ",
    )

    private val folder = listOf(
        "            ",
        "            ",
        " ####       ",
        " ########## ",
        " ##      ## ",
        " ##      ## ",
        " ##      ## ",
        " ##      ## ",
        " ########## ",
        "            ",
        "            ",
        "            ",
    )

    private val note = listOf(
        "            ",
        "       #### ",
        "       #### ",
        "       ##   ",
        "       ##   ",
        "       ##   ",
        "  ###  ##   ",
        " ##### ##   ",
        " #####      ",
        "  ###       ",
        "            ",
        "            ",
    )

    private val store = listOf(
        "            ",
        "   ##       ",
        "   ###      ",
        "   ####     ",
        "   #####    ",
        "   ######   ",
        "   #####    ",
        "   ####     ",
        "   ###      ",
        "   ##       ",
        "            ",
        "            ",
    )

    private val plane = listOf(
        "            ",
        "         ## ",
        "       #### ",
        "     ###### ",
        "   ######   ",
        " ######     ",
        "   ####     ",
        "    ###     ",
        "     ##     ",
        "      #     ",
        "            ",
        "            ",
    )

    private val gamepad = listOf(
        "            ",
        "            ",
        "  ########  ",
        " ########## ",
        " # ##   # # ",
        " ####  ## # ",
        " # ##   # # ",
        " ########## ",
        "  ##    ##  ",
        "            ",
        "            ",
        "            ",
    )

    private val cart = listOf(
        "            ",
        " ##         ",
        " ######     ",
        " #    ##    ",
        " #     ##   ",
        " #    ###   ",
        " ######     ",
        "   ##  ##   ",
        "            ",
        "  ##    ##  ",
        "            ",
        "            ",
    )

    private val wallet = listOf(
        "            ",
        "            ",
        " ########## ",
        " ##      ## ",
        " ##      ## ",
        " ##    #### ",
        " ##    # ## ",
        " ##    #### ",
        " ########## ",
        "            ",
        "            ",
        "            ",
    )

    private val video = listOf(
        "            ",
        "            ",
        " #######    ",
        " ##   ##  # ",
        " ## #  ####",
        " ##  #  ### ",
        " ## #  ####",
        " ##   ##  # ",
        " #######    ",
        "            ",
        "            ",
        "            ",
    )

    /**
     * Matched on package-name fragments, longest first, so `com.google.android
     * .apps.messaging` picks the message glyph rather than a generic Google one.
     */
    private val rules: List<Pair<List<String>, PixelIcon>> = listOf(
        listOf("whatsapp") to PixelIcon(0xFF075E54.toInt(), WHITE, rows = chat),
        listOf("telegram") to PixelIcon(0xFF229ED9.toInt(), WHITE, rows = plane),
        listOf("instagram") to PixelIcon(0xFFC13584.toInt(), WHITE, rows = square),
        listOf("facebook", "katana") to PixelIcon(0xFF1877F2.toInt(), WHITE, rows = square),
        listOf("twitter", "com.x.") to PixelIcon(BLACK, WHITE, rows = square),
        listOf("tiktok", "musically") to PixelIcon(BLACK, 0xFF25F4EE.toInt(), rows = note),
        listOf("youtube") to PixelIcon(0xFFCC0000.toInt(), WHITE, rows = play),
        listOf("spotify") to PixelIcon(0xFF1DB954.toInt(), BLACK, rows = wave),
        listOf("discord") to PixelIcon(0xFF5865F2.toInt(), WHITE, rows = gamepad),
        listOf("netflix") to PixelIcon(BLACK, 0xFFE50914.toInt(), rows = play),
        listOf("chrome", "browser", "firefox", "opera") to
            PixelIcon(0xFF1A73E8.toInt(), WHITE, rows = globe),
        listOf("gm", "gmail", "email", "outlook", ".mail") to
            PixelIcon(0xFFD93025.toInt(), WHITE, rows = mail),
        listOf("maps", "waze") to PixelIcon(0xFF34A853.toInt(), WHITE, rows = pin),
        listOf("camera", "gcam") to PixelIcon(0xFF3C4043.toInt(), WHITE, rows = camera),
        listOf("gallery", "photos", "album") to
            PixelIcon(0xFF4285F4.toInt(), WHITE, 0xFFFBBC05.toInt(), gallery),
        listOf("dialer", "incallui", ".phone", "contacts") to
            PixelIcon(0xFF1E8E3E.toInt(), WHITE, rows = phone),
        listOf("messaging", "messages", ".mms", ".sms") to
            PixelIcon(0xFF1A73E8.toInt(), WHITE, rows = message),
        listOf("calculator") to PixelIcon(0xFF3C4043.toInt(), WHITE, rows = calculator),
        listOf("calendar") to PixelIcon(0xFF1A73E8.toInt(), WHITE, rows = calendar),
        listOf("deskclock", "clock", "alarm") to PixelIcon(0xFF202124.toInt(), WHITE, rows = clock),
        listOf("documentsui", "filemanager", "files", "explorer") to
            PixelIcon(0xFFFBBC05.toInt(), BLACK, rows = folder),
        listOf("music", "audio", "podcast") to PixelIcon(0xFFEA4335.toInt(), WHITE, rows = note),
        listOf("vending", "playstore", "appstore", "fdroid", "aurora") to
            PixelIcon(0xFF34A853.toInt(), WHITE, rows = store),
        listOf("videos", "player", "vlc", "mx") to PixelIcon(0xFF9334E6.toInt(), WHITE, rows = video),
        listOf("game", "unity", "roblox", "minecraft", "genshin") to
            PixelIcon(0xFF7B1FA2.toInt(), WHITE, rows = gamepad),
        listOf("shop", "tokopedia", "shopee", "lazada", "amazon", "bukalapak") to
            PixelIcon(0xFFF4511E.toInt(), WHITE, rows = cart),
        listOf("bank", "wallet", "pay", "dana", "ovo", "gopay", "bca", "bri", "mandiri") to
            PixelIcon(0xFF00897B.toInt(), WHITE, rows = wallet),
    )

    /** Returns hand-drawn artwork for [packageName], or null to fall back. */
    fun match(packageName: String): PixelIcon? {
        val name = packageName.lowercase()
        return rules.firstOrNull { (fragments, _) ->
            fragments.any { it in name }
        }?.second
    }
}

/**
 * A third-party icon pack (the ADW/Nova `appfilter.xml` format that nearly every
 * pack on the Play Store ships).
 */
class ExternalIconPack private constructor(
    private val packageName: String,
    private val resources: Resources,
    private val componentToDrawable: Map<String, String>,
) {

    fun drawableFor(component: ComponentName): Drawable? {
        val key = "ComponentInfo{${component.packageName}/${component.className}}"
        val name = componentToDrawable[key]
            ?: componentToDrawable[component.packageName]
            ?: return null
        val id = runCatching {
            resources.getIdentifier(name, "drawable", packageName)
        }.getOrDefault(0)
        if (id == 0) return null
        @Suppress("DEPRECATION")
        return runCatching { resources.getDrawable(id, null) }.getOrNull()
    }

    val size: Int get() = componentToDrawable.size

    companion object {
        private const val TAG = "IconPack"

        /** Actions declared by icon packs so they can be discovered. */
        private val PACK_ACTIONS = listOf(
            "org.adw.launcher.THEMES",
            "com.novalauncher.THEME",
        )

        /** Every installed icon pack, as (packageName, label). */
        fun installed(context: Context): List<Pair<String, String>> {
            val pm = context.packageManager
            val found = LinkedHashMap<String, String>()
            for (action in PACK_ACTIONS) {
                val activities = runCatching {
                    @Suppress("DEPRECATION")
                    pm.queryIntentActivities(Intent(action), PackageManager.GET_META_DATA)
                }.getOrNull() ?: continue
                for (info in activities) {
                    val pkg = info.activityInfo.packageName
                    if (pkg in found) continue
                    val label = runCatching {
                        info.loadLabel(pm).toString()
                    }.getOrDefault(pkg)
                    found[pkg] = label
                }
            }
            return found.map { it.key to it.value }
        }

        fun load(context: Context, packageName: String): ExternalIconPack? {
            val pm = context.packageManager
            val resources = runCatching {
                pm.getResourcesForApplication(packageName)
            }.getOrNull() ?: return null

            val map = runCatching { parseAppFilter(resources, packageName) }
                .onFailure { Log.w(TAG, "appfilter parse failed for $packageName", it) }
                .getOrDefault(emptyMap())

            if (map.isEmpty()) return null
            return ExternalIconPack(packageName, resources, map)
        }

        private fun parseAppFilter(
            resources: Resources,
            packageName: String,
        ): Map<String, String> {
            val parser: XmlPullParser = run {
                val xmlId = resources.getIdentifier("appfilter", "xml", packageName)
                if (xmlId != 0) {
                    resources.getXml(xmlId)
                } else {
                    val factory = org.xmlpull.v1.XmlPullParserFactory.newInstance()
                    factory.newPullParser().apply {
                        setInput(resources.assets.open("appfilter.xml"), null)
                    }
                }
            }

            val result = HashMap<String, String>(256)
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "item") {
                    val component = parser.getAttributeValue(null, "component")
                    val drawable = parser.getAttributeValue(null, "drawable")
                    if (!component.isNullOrBlank() && !drawable.isNullOrBlank()) {
                        result[component] = drawable
                    }
                }
                event = parser.next()
            }
            return result
        }
    }
}
