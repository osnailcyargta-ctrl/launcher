#!/usr/bin/env python3
"""Generates the Pixel Icon Pack.

This script is the single source of truth for the hand-drawn artwork. It emits:

  * PNG drawables + appfilter/drawable XML for the standalone icon pack APK
  * mipmaps for the icon pack's own launcher icon
  * PixelIconArt.kt, so the launcher draws the very same art without shipping
    a second copy of it

Run it after editing any grid:  python3 tools/gen_iconpack.py
"""

import os
import struct
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PACK_RES = os.path.join(ROOT, "iconpack/src/main/res")
LAUNCHER_SRC = os.path.join(ROOT, "app/src/main/java/com/pixel/launcher/core")

ICON_PX = 192          # drawable size shipped in the pack
GRID = 12              # every glyph is drawn on a 12x12 grid
CORNER = 0.22          # rounded-square radius, as a fraction of the icon

WHITE = 0xFFF4F4F4
BLACK = 0xFF12141A

# --------------------------------------------------------------------- artwork
# '#' = foreground, '+' = accent, anything else = the tile colour.

GLYPHS = {
    "phone": [
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
    ],
    "message": [
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
    ],
    "camera": [
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
    ],
    "gallery": [
        "            ",
        " ########## ",
        " #        # ",
        " #  ++    # ",
        " #        # ",
        " #    ##  # ",
        " #   #### # ",
        " #  ##### # ",
        " ######### #",
        " ########## ",
        "            ",
        "            ",
    ],
    "globe": [
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
    ],
    "play": [
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
    ],
    "chat": [
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
    ],
    "square": [
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
    ],
    "wave": [
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
    ],
    "mail": [
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
    ],
    "pin": [
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
    ],
    "calculator": [
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
    ],
    "calendar": [
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
    ],
    "clock": [
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
    ],
    "folder": [
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
    ],
    "note": [
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
    ],
    "store": [
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
    ],
    "plane": [
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
    ],
    "gamepad": [
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
    ],
    "cart": [
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
    ],
    "wallet": [
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
    ],
    "video": [
        "            ",
        "            ",
        " #######    ",
        " ##   ##  # ",
        " ##  # #### ",
        " ##   # ### ",
        " ##  # #### ",
        " ##   ##  # ",
        " #######    ",
        "            ",
        "            ",
        "            ",
    ],
    "pack": [
        "            ",
        "  ###  ###  ",
        "  ###  ###  ",
        "  ###  ###  ",
        "            ",
        "  +++  +++  ",
        "  +++  +++  ",
        "  +++  +++  ",
        "            ",
        "            ",
        "            ",
        "            ",
    ],
}

# name -> (background, foreground, accent)
COLOURS = {
    "phone": (0xFF1E8E3E, WHITE, WHITE),
    "message": (0xFF1A73E8, WHITE, WHITE),
    "camera": (0xFF3C4043, WHITE, WHITE),
    "gallery": (0xFF4285F4, WHITE, 0xFFFBBC05),
    "globe": (0xFF1A73E8, WHITE, WHITE),
    "play": (0xFFCC0000, WHITE, WHITE),
    "chat": (0xFF075E54, WHITE, WHITE),
    "square": (0xFFC13584, WHITE, WHITE),
    "wave": (0xFF1DB954, BLACK, BLACK),
    "mail": (0xFFD93025, WHITE, WHITE),
    "pin": (0xFF34A853, WHITE, WHITE),
    "calculator": (0xFF3C4043, WHITE, WHITE),
    "calendar": (0xFF1A73E8, WHITE, WHITE),
    "clock": (0xFF202124, WHITE, WHITE),
    "folder": (0xFFFBBC05, BLACK, BLACK),
    "note": (0xFFEA4335, WHITE, WHITE),
    "store": (0xFF34A853, WHITE, WHITE),
    "plane": (0xFF229ED9, WHITE, WHITE),
    "gamepad": (0xFF5865F2, WHITE, WHITE),
    "cart": (0xFFF4511E, WHITE, WHITE),
    "wallet": (0xFF00897B, WHITE, WHITE),
    "video": (0xFF9334E6, WHITE, WHITE),
    "pack": (0xFF070B08, 0xFF7CFF6B, 0xFF2F8C3C),
}

# Extra colour variants so well-known apps keep their own brand colour while
# reusing a glyph.
VARIANTS = {
    "telegram": ("plane", 0xFF229ED9, WHITE, WHITE),
    "instagram": ("square", 0xFFC13584, WHITE, WHITE),
    "facebook": ("square", 0xFF1877F2, WHITE, WHITE),
    "twitter": ("square", BLACK, WHITE, WHITE),
    "tiktok": ("note", BLACK, 0xFF25F4EE, 0xFFFE2C55),
    "youtube": ("play", 0xFFCC0000, WHITE, WHITE),
    "netflix": ("play", BLACK, 0xFFE50914, 0xFFE50914),
    "spotify": ("wave", 0xFF1DB954, BLACK, BLACK),
    "discord": ("gamepad", 0xFF5865F2, WHITE, WHITE),
    "whatsapp": ("chat", 0xFF075E54, 0xFF25D366, 0xFF25D366),
    "chrome": ("globe", 0xFF1A73E8, WHITE, 0xFFFBBC05),
    "firefox": ("globe", 0xFFFF7139, WHITE, WHITE),
    "maps": ("pin", 0xFF34A853, WHITE, WHITE),
    "gmail": ("mail", 0xFFD93025, WHITE, WHITE),
    "photos": ("gallery", 0xFF4285F4, WHITE, 0xFFFBBC05),
    "playstore": ("store", 0xFF34A853, WHITE, WHITE),
    "files": ("folder", 0xFFFBBC05, BLACK, BLACK),
    "music": ("note", 0xFFEA4335, WHITE, WHITE),
    "shop": ("cart", 0xFFF4511E, WHITE, WHITE),
    "bank": ("wallet", 0xFF00897B, WHITE, WHITE),
    "games": ("gamepad", 0xFF7B1FA2, WHITE, WHITE),
    "player": ("video", 0xFF9334E6, WHITE, WHITE),
    "contacts": ("phone", 0xFF1E8E3E, WHITE, WHITE),
    "sms": ("message", 0xFF1A73E8, WHITE, WHITE),
}

# drawable name -> components claimed by it. Entries are (package, class) pairs;
# a class of None also emits a package-only entry, which is what the Pixel
# Launcher itself matches on.
COMPONENTS = {
    "whatsapp": [("com.whatsapp", "com.whatsapp.Main"), ("com.whatsapp", None),
                 ("com.whatsapp.w4b", None)],
    "telegram": [("org.telegram.messenger", "org.telegram.ui.LaunchActivity"),
                 ("org.telegram.messenger", None), ("org.telegram.messenger.web", None)],
    "instagram": [("com.instagram.android", "com.instagram.mainactivity.MainActivity"),
                  ("com.instagram.android", None)],
    "facebook": [("com.facebook.katana", "com.facebook.katana.LoginActivity"),
                 ("com.facebook.katana", None), ("com.facebook.lite", None)],
    "twitter": [("com.twitter.android", "com.twitter.android.StartActivity"),
                ("com.twitter.android", None)],
    "tiktok": [("com.zhiliaoapp.musically", None), ("com.ss.android.ugc.trill", None)],
    "youtube": [("com.google.android.youtube", "com.google.android.apps.youtube.app.WatchWhileActivity"),
                ("com.google.android.youtube", None)],
    "netflix": [("com.netflix.mediaclient", None)],
    "spotify": [("com.spotify.music", "com.spotify.music.MainActivity"),
                ("com.spotify.music", None)],
    "discord": [("com.discord", None)],
    "chrome": [("com.android.chrome", "com.google.android.apps.chrome.Main"),
               ("com.android.chrome", None)],
    "firefox": [("org.mozilla.firefox", None)],
    "gmail": [("com.google.android.gm", "com.google.android.gm.ConversationListActivityGmail"),
              ("com.google.android.gm", None)],
    "maps": [("com.google.android.apps.maps", "com.google.android.maps.MapsActivity"),
             ("com.google.android.apps.maps", None), ("com.waze", None)],
    "camera": [("com.google.android.GoogleCamera", None), ("com.android.camera2", None),
               ("com.android.camera", None), ("com.sec.android.app.camera", None)],
    "photos": [("com.google.android.apps.photos", None), ("com.android.gallery3d", None),
               ("com.sec.android.gallery3d", None), ("com.miui.gallery", None)],
    "contacts": [("com.google.android.dialer", None), ("com.android.dialer", None),
                 ("com.samsung.android.dialer", None), ("com.android.contacts", None)],
    "sms": [("com.google.android.apps.messaging", None), ("com.android.mms", None),
            ("com.samsung.android.messaging", None)],
    "calculator": [("com.google.android.calculator", None), ("com.android.calculator2", None),
                   ("com.sec.android.app.popupcalculator", None)],
    "calendar": [("com.google.android.calendar", None), ("com.android.calendar", None)],
    "clock": [("com.google.android.deskclock", None), ("com.android.deskclock", None),
              ("com.sec.android.app.clockpackage", None)],
    "files": [("com.google.android.documentsui", None), ("com.android.documentsui", None),
              ("com.mi.android.globalFileexplorer", None)],
    "music": [("com.google.android.apps.youtube.music", None), ("com.android.music", None)],
    "playstore": [("com.android.vending", "com.google.android.finsky.activities.MainActivity"),
                  ("com.android.vending", None), ("org.fdroid.fdroid", None)],
    "player": [("org.videolan.vlc", None), ("com.mxtech.videoplayer.ad", None)],
    "shop": [("com.tokopedia.tkpd", None), ("com.shopee.id", None), ("com.lazada.android", None),
             ("com.amazon.mShop.android.shopping", None), ("com.bukalapak.android", None)],
    "bank": [("id.dana", None), ("ovo.id", None), ("com.gojek.app", None),
             ("com.bca", None), ("id.co.bri.brimo", None),
             ("com.paypal.android.p2pmobile", None)],
    "games": [("com.roblox.client", None), ("com.mojang.minecraftpe", None),
              ("com.miHoYo.GenshinImpact", None)],
    "globe": [("com.opera.browser", None), ("com.brave.browser", None),
              ("com.microsoft.emmx", None), ("com.android.browser", None)],
}

# --------------------------------------------------------------------- drawing


def rounded_alpha(x, y, size, radius):
    """1 inside the rounded square, 0 outside, with a hard pixel edge."""
    left, top = radius, radius
    right, bottom = size - radius, size - radius
    dx = 0.0
    dy = 0.0
    if x < left:
        dx = left - x
    elif x > right:
        dx = x - right
    if y < top:
        dy = top - y
    elif y > bottom:
        dy = y - bottom
    if dx == 0.0 and dy == 0.0:
        return 1
    return 1 if (dx * dx + dy * dy) <= radius * radius else 0


def render(name, rows, bg, fg, accent, size=ICON_PX, rounded=True):
    cell = size / GRID
    radius = size * CORNER
    pixels = []
    for y in range(size):
        row = []
        for x in range(size):
            inside = rounded_alpha(x + 0.5, y + 0.5, size, radius) if rounded else 1
            if not inside:
                row.append(0)
                continue
            gx = int(x / cell)
            gy = int(y / cell)
            colour = bg
            if 0 <= gy < len(rows):
                line = rows[gy]
                if 0 <= gx < len(line):
                    if line[gx] == "#":
                        colour = fg
                    elif line[gx] == "+":
                        colour = accent
            row.append(colour)
        pixels.append(row)
    return pixels


def write_png(path, pixels):
    height = len(pixels)
    width = len(pixels[0])
    raw = bytearray()
    for row in pixels:
        raw.append(0)
        for argb in row:
            a = (argb >> 24) & 0xFF
            r = (argb >> 16) & 0xFF
            g = (argb >> 8) & 0xFF
            b = argb & 0xFF
            raw += bytes((r, g, b, a))

    def chunk(tag, data):
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))

    blob = (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
            + chunk(b"IEND", b""))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as handle:
        handle.write(blob)
    return len(blob)


# ------------------------------------------------------------------- emitters


def all_icons():
    """drawable name -> (rows, bg, fg, accent)."""
    icons = {}
    for name, rows in GLYPHS.items():
        bg, fg, accent = COLOURS[name]
        icons[name] = (rows, bg, fg, accent)
    for name, (glyph, bg, fg, accent) in VARIANTS.items():
        icons[name] = (GLYPHS[glyph], bg, fg, accent)
    return icons


def emit_pack(icons):
    total = 0
    for name, (rows, bg, fg, accent) in sorted(icons.items()):
        if name == "pack":
            continue
        total += write_png(
            os.path.join(PACK_RES, "drawable-nodpi", f"{name}.png"),
            render(name, rows, bg, fg, accent),
        )

    # Frame used by other launchers to theme apps this pack does not know.
    back = render("iconback", [], 0xFF0C1710, 0xFF0C1710, 0xFF0C1710)
    total += write_png(os.path.join(PACK_RES, "drawable-nodpi", "iconback.png"), back)

    themed = sorted(name for name in icons if name != "pack")

    lines = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>"]
    lines.append('    <iconback img1="iconback" />')
    lines.append('    <scale factor="0.78" />')
    for name in themed:
        for package, cls in COMPONENTS.get(name, []):
            if cls:
                lines.append(
                    f'    <item component="ComponentInfo{{{package}/{cls}}}" '
                    f'drawable="{name}" />'
                )
            else:
                # Package-only entries are what Pixel Launcher matches on, so a
                # pack keeps working when an app renames its launcher activity.
                lines.append(f'    <item component="{package}" drawable="{name}" />')
    lines.append("</resources>")
    write_text(os.path.join(PACK_RES, "xml", "appfilter.xml"), "\n".join(lines) + "\n")

    grid = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>",
            '    <category title="Pixel Icons" />']
    grid += [f'    <item drawable="{name}" />' for name in themed]
    grid.append("</resources>")
    write_text(os.path.join(PACK_RES, "xml", "drawable.xml"), "\n".join(grid) + "\n")

    # The pack's own launcher icon.
    rows, bg, fg, accent = icons["pack"]
    for folder, size in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96),
                         ("xxhdpi", 144), ("xxxhdpi", 192)):
        write_png(
            os.path.join(PACK_RES, f"mipmap-{folder}", "ic_launcher.png"),
            render("pack", rows, bg, fg, accent, size=size),
        )
    return len(themed), total


def write_text(path, content):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as handle:
        handle.write(content)


def emit_kotlin(icons):
    """Regenerates the launcher-side copy of the artwork."""
    out = [
        "package com.pixel.launcher.core",
        "",
        "// GENERATED by tools/gen_iconpack.py - do not edit by hand.",
        "// Edit the grids in that script and re-run it instead.",
        "",
        "internal object PixelIconArt {",
        "",
    ]
    for name, (rows, bg, fg, accent) in sorted(icons.items()):
        if name == "pack":
            continue
        out.append(f"    val {name} = PixelIcon(")
        out.append(f"        bg = 0x{bg:08X}.toInt(),")
        out.append(f"        fg = 0x{fg:08X}.toInt(),")
        out.append(f"        accent = 0x{accent:08X}.toInt(),")
        out.append("        rows = listOf(")
        for line in rows:
            out.append(f'            "{line}",')
        out.append("        ),")
        out.append("    )")
        out.append("")
    out.append("}")
    write_text(os.path.join(LAUNCHER_SRC, "PixelIconArt.kt"), "\n".join(out) + "\n")


def main():
    icons = all_icons()
    count, size = emit_pack(icons)
    emit_kotlin(icons)
    print(f"icon pack: {count} drawables, {size / 1024:.1f} KB of PNG")
    print(f"launcher art: {len(icons) - 1} icons written to PixelIconArt.kt")


if __name__ == "__main__":
    main()
