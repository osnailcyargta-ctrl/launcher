package com.pixel.launcher.iconpack

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * The pack has nothing to configure, so this screen only explains how to apply
 * it and shows a strip of the artwork. The whole UI is built in code to keep the
 * APK free of layouts, themes and support libraries.
 */
class IconPackActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BACKGROUND)
            setPadding(dp(24), dp(40), dp(24), dp(40))
        }

        root.addView(
            heading("PIXEL ICON PACK", 22f, ACCENT),
            LinearLayout.LayoutParams(MATCH, WRAP),
        )
        root.addView(spacer(dp(8)))
        root.addView(
            heading("$ICON_COUNT hand-drawn pixel icons", 15f, TEXT_DIM),
            LinearLayout.LayoutParams(MATCH, WRAP),
        )

        root.addView(spacer(dp(24)))
        root.addView(previewStrip(dp(52), dp(8)))
        root.addView(spacer(dp(24)))

        root.addView(
            body(
                """
                HOW TO APPLY

                Pixel Launcher
                Settings > Icons > Icon artwork > ICON PACK,
                then pick Pixel Icon Pack from the list.

                Nova / Lawnchair / Apex / ADW
                Look for "Icon style" or "Look and feel",
                then choose Pixel Icon Pack.

                There is nothing to set up here. Apps the pack
                does not know keep their own icon, framed to
                match the rest.
                """.trimIndent(),
            ),
            LinearLayout.LayoutParams(MATCH, WRAP),
        )

        setContentView(
            ScrollView(this).apply {
                setBackgroundColor(BACKGROUND)
                addView(root, ViewGroup.LayoutParams(MATCH, WRAP))
            },
        )
    }

    private fun heading(text: String, size: Float, colour: Int) = TextView(this).apply {
        this.text = text
        setTextColor(colour)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        gravity = Gravity.CENTER_HORIZONTAL
        typeface = android.graphics.Typeface.MONOSPACE
    }

    private fun body(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(TEXT)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setLineSpacing(0f, 1.25f)
        typeface = android.graphics.Typeface.MONOSPACE
    }

    private fun spacer(height: Int) = TextView(this).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH, height)
    }

    /** A row of sample icons, looked up by name the same way a launcher does. */
    private fun previewStrip(size: Int, gap: Int): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        for (name in PREVIEW) {
            val id = resources.getIdentifier(name, "drawable", packageName)
            if (id == 0) continue
            row.addView(
                ImageView(this).apply {
                    @Suppress("DEPRECATION")
                    setImageDrawable(runCatching { resources.getDrawable(id, null) }.getOrNull())
                },
                LinearLayout.LayoutParams(size, size).apply {
                    marginStart = gap / 2
                    marginEnd = gap / 2
                },
            )
        }
        return row
    }

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

        val BACKGROUND = Color.parseColor("#050A06")
        val ACCENT = Color.parseColor("#7CFF6B")
        val TEXT = Color.parseColor("#CFF7C4")
        val TEXT_DIM = Color.parseColor("#6E9A73")

        val PREVIEW = listOf("whatsapp", "camera", "spotify", "maps", "playstore")
        const val ICON_COUNT = 46
    }
}
