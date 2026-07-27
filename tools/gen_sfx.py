import wave, struct, math, os
SR = 22050
OUT = "/home/user/launcher/app/src/main/res/raw"

def square(t, f, duty=0.5):
    p = (t * f) % 1.0
    return 1.0 if p < duty else -1.0

def triangle(t, f):
    p = (t * f) % 1.0
    return 4.0 * abs(p - 0.5) - 1.0

def noise(seed=[1]):
    # 15-bit LFSR, NES-ish
    s = seed[0]
    bit = ((s ^ (s >> 1)) & 1)
    s = (s >> 1) | (bit << 14)
    seed[0] = s
    return 1.0 if (s & 1) else -1.0

def render(notes, wave_type="square", duty=0.5, decay=6.0, vol=0.55, crush=6):
    """notes: list of (freq_hz, dur_ms). freq 0 = rest."""
    frames = []
    for f, dur in notes:
        n = int(SR * dur / 1000.0)
        for i in range(n):
            t = i / SR
            env = math.exp(-decay * (i / max(n, 1)))
            # tiny attack to avoid clicks
            atk = min(1.0, i / max(1, int(SR * 0.002)))
            if f <= 0:
                s = 0.0
            elif wave_type == "noise":
                s = noise()
            elif wave_type == "triangle":
                s = triangle(t, f)
            else:
                s = square(t, f, duty)
            v = s * env * atk * vol
            # bit-crush for retro grit
            levels = 2 ** crush
            v = round(v * levels) / levels
            frames.append(max(-1.0, min(1.0, v)))
    # short fade-out tail
    tail = int(SR * 0.004)
    for i in range(min(tail, len(frames))):
        frames[len(frames) - 1 - i] *= i / tail
    return frames

def write(name, frames):
    path = os.path.join(OUT, name)
    with wave.open(path, "w") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes(b"".join(struct.pack("<h", int(f * 32767)) for f in frames))
    return os.path.getsize(path)

N = {"C5":523.25,"D5":587.33,"E5":659.25,"G5":783.99,"A5":880.0,"C6":1046.5,"E6":1318.5,"G6":1568.0,
     "G4":392.0,"C4":261.63,"E4":329.63,"A4":440.0,"F5":698.46,"B5":987.77,"D6":1174.66}

banks = {
    # navigation / selection blip
    "sfx_click.wav":  render([(N["A5"], 32), (N["E6"], 40)], duty=0.25, decay=7.0, vol=0.5),
    # opening an app: rising arpeggio
    "sfx_open.wav":   render([(N["C5"], 45), (N["E5"], 45), (N["G5"], 45), (N["C6"], 110)], duty=0.5, decay=3.2, vol=0.55),
    # back / close: descending
    "sfx_back.wav":   render([(N["G5"], 45), (N["D5"], 45), (N["G4"], 90)], duty=0.5, decay=4.0, vol=0.5),
    # toggle on/off
    "sfx_toggle.wav": render([(N["E5"], 28), (N["B5"], 55)], duty=0.125, decay=6.0, vol=0.45),
    # page snap tick
    "sfx_page.wav":   render([(N["C6"], 18), (0, 6), (N["G5"], 22)], duty=0.125, decay=9.0, vol=0.32),
    # long press / menu open
    "sfx_menu.wav":   render([(N["C5"], 30), (N["G5"], 30), (N["E6"], 55)], duty=0.25, decay=5.0, vol=0.45),
    # pin / confirm
    "sfx_pin.wav":    render([(N["G5"], 35), (N["C6"], 35), (N["E6"], 35), (N["G6"], 80)], duty=0.5, decay=3.5, vol=0.5),
    # error / blocked
    "sfx_error.wav":  render([(N["C4"], 70), (0, 25), (N["C4"], 110)], duty=0.5, decay=2.5, vol=0.5),
}
total = 0
for k, v in banks.items():
    sz = write(k, v); total += sz
    print(f"{k:16s} {sz/1024:6.1f} KB  {len(v)/SR*1000:6.0f} ms")
print(f"total {total/1024:.1f} KB")
