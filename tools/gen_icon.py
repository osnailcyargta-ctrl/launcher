import zlib, struct, os

RES = "/home/user/launcher/app/src/main/res"
S = 24  # base pixel-art canvas

PAL = {
    ".": None,             # transparent
    "b": (0x14, 0x1E, 0x18),  # bezel dark
    "h": (0x2C, 0x3F, 0x33),  # bezel highlight
    "s": (0x07, 0x14, 0x0B),  # screen background
    "g": (0x7C, 0xFF, 0x6B),  # bright green
    "d": (0x2F, 0x8C, 0x3C),  # dim green
    "a": (0xFF, 0xC9, 0x4A),  # amber accent
}

grid = [["." for _ in range(S)] for _ in range(S)]

def rect(x0, y0, x1, y1, c):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if 0 <= x < S and 0 <= y < S:
                grid[y][x] = c

# --- bezel: rounded square with 2px corner cut ---
rect(0, 0, S - 1, S - 1, "b")
for (cx, cy) in ((0, 0), (S - 2, 0), (0, S - 2), (S - 2, S - 2)):
    rect(cx, cy, cx + 1, cy + 1, ".")
for (cx, cy) in ((1, 1), (S - 2, 1), (1, S - 2), (S - 2, S - 2)):
    grid[cy][cx] = "b"
# top highlight edge
rect(2, 1, S - 3, 1, "h")
rect(1, 2, 1, S - 3, "h")

# --- screen ---
rect(3, 3, S - 4, S - 7, "s")

# --- 2x2 app tiles inside the screen ---
rect(5, 5, 9, 9, "g")
rect(12, 5, 16, 9, "d")
rect(5, 11, 9, 15, "d")
rect(12, 11, 16, 15, "g")
# tile "screens" punched out so they read as icons, not blobs
rect(6, 6, 8, 8, "s")
rect(13, 12, 15, 14, "s")

# --- dock strip under the screen ---
rect(4, S - 5, 7, S - 4, "a")
rect(9, S - 5, 12, S - 4, "d")
rect(14, S - 5, 17, S - 4, "d")

# ---------------- PNG writer ----------------
def png(path, scale, round_mask=False):
    w = h = S * scale
    raw = bytearray()
    for y in range(h):
        raw.append(0)  # filter type 0
        row = grid[y // scale]
        for x in range(w):
            c = PAL[row[x // scale]]
            if round_mask:
                cx = cy = (S * scale - 1) / 2.0
                if ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5 > (S * scale) / 2.0 - 0.5:
                    c = None
            raw += bytes(c) + b"\xff" if c else b"\x00\x00\x00\x00"
    def chunk(tag, data):
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))
    out = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
           + chunk(b"IEND", b""))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(out)
    return len(out)

# ---------------- Vector writer (adaptive foreground) ----------------
def vector(path, inset_units=18.0, span=72.0):
    """Emit a 108x108 VectorDrawable; art occupies the central safe zone."""
    u = span / S
    paths = []
    for key, rgb in PAL.items():
        if rgb is None:
            continue
        runs = []
        for y in range(S):
            x = 0
            while x < S:
                if grid[y][x] == key:
                    x0 = x
                    while x < S and grid[y][x] == key:
                        x += 1
                    runs.append((x0, y, x - x0))
                else:
                    x += 1
        if not runs:
            continue
        d = []
        for (x, y, n) in runs:
            px, py = inset_units + x * u, inset_units + y * u
            d.append(f"M{px:.3f},{py:.3f}h{n * u:.3f}v{u:.3f}h-{n * u:.3f}z")
        color = "#FF%02X%02X%02X" % rgb
        paths.append(f'    <path\n        android:fillColor="{color}"\n'
                     f'        android:pathData="{"".join(d)}" />')
    xml = ('<?xml version="1.0" encoding="utf-8"?>\n'
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
           '    android:width="108dp"\n    android:height="108dp"\n'
           '    android:viewportWidth="108"\n    android:viewportHeight="108">\n'
           + "\n".join(paths) + "\n</vector>\n")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        f.write(xml)
    return len(xml)

for dpi, scale in (("mdpi", 2), ("hdpi", 3), ("xhdpi", 4), ("xxhdpi", 6), ("xxxhdpi", 8)):
    for name, rnd in (("ic_launcher", False), ("ic_launcher_round", True)):
        n = png(f"{RES}/mipmap-{dpi}/{name}.png", scale, rnd)
    print(f"mipmap-{dpi}: {S*scale}px  {n} B")

print("vector:", vector(f"{RES}/drawable/ic_launcher_foreground.xml"), "B")
