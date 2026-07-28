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

/**
 * The launcher's own icon pack.
 *
 * Only widely installed apps get bespoke artwork; everything else falls back to
 * the app's real icon re-coloured into the active palette, so a drawer never
 * ends up half themed and half not.
 *
 * The artwork itself lives in the generated [PixelIconArt]; edit the grids in
 * `tools/gen_iconpack.py` and re-run it, which updates both this and the
 * standalone icon-pack APK.
 */
object PixelIconPack {

    /**
     * Matched on package-name fragments, first rule wins, so the specific
     * entries are listed before the catch-all category ones.
     */
    private val rules: List<Pair<List<String>, PixelIcon>> = listOf(
        listOf("whatsapp") to PixelIconArt.whatsapp,
        listOf("telegram") to PixelIconArt.telegram,
        listOf("instagram") to PixelIconArt.instagram,
        listOf("facebook", "katana") to PixelIconArt.facebook,
        listOf("twitter", "com.x.") to PixelIconArt.twitter,
        listOf("tiktok", "musically", "ugc.trill") to PixelIconArt.tiktok,
        listOf("youtube.music") to PixelIconArt.music,
        listOf("youtube") to PixelIconArt.youtube,
        listOf("spotify") to PixelIconArt.spotify,
        listOf("discord") to PixelIconArt.discord,
        listOf("netflix") to PixelIconArt.netflix,
        listOf("chrome") to PixelIconArt.chrome,
        listOf("firefox", "mozilla") to PixelIconArt.firefox,
        listOf("browser", "opera", "brave", "emmx") to PixelIconArt.globe,
        listOf("android.gm", "gmail", "outlook", ".mail") to PixelIconArt.gmail,
        listOf("maps", "waze") to PixelIconArt.maps,
        listOf("camera", "gcam") to PixelIconArt.camera,
        listOf("gallery", "photos", "album") to PixelIconArt.photos,
        listOf("dialer", "incallui", ".phone", "contacts") to PixelIconArt.contacts,
        listOf("messaging", "messages", ".mms", ".sms") to PixelIconArt.sms,
        listOf("calculator") to PixelIconArt.calculator,
        listOf("calendar") to PixelIconArt.calendar,
        listOf("deskclock", "clock", "alarm") to PixelIconArt.clock,
        listOf("documentsui", "filemanager", "fileexplorer", "files") to PixelIconArt.files,
        listOf("music", "audio", "podcast") to PixelIconArt.music,
        listOf("vending", "playstore", "fdroid", "aurora") to PixelIconArt.playstore,
        listOf("videolan", "vlc", "mxtech", "player") to PixelIconArt.player,
        listOf("game", "unity", "roblox", "minecraft", "genshin", "mihoyo") to PixelIconArt.games,
        listOf("shop", "tokopedia", "shopee", "lazada", "amazon", "bukalapak", "tkpd") to
            PixelIconArt.shop,
        listOf("bank", "wallet", "pay", "dana", "ovo", "gojek", "bca", "bri", "mandiri") to
            PixelIconArt.bank,
        listOf("mail") to PixelIconArt.mail,
        listOf("note", "keep", "memo") to PixelIconArt.note,
        listOf("chat", "messenger", "signal", "line", "wechat") to PixelIconArt.chat,
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
