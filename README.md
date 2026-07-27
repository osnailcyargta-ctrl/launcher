# Pixel Launcher

A real Android home-screen replacement with a retro CRT look. It lists **every
app installed on the device**, not just the pre-installed ones, and is built to
stay light on both CPU and battery.

## Layout

```
              14:32                <- clock (pixel font)
       SEN 27 JUL 2026 | BAT 82%   <- date + battery

  [SETTINGS] [ App ] [ App ] [App]
  [ App ]    [ App ] [ App ] [App]   <- app grid, 4 rows per screen,
  [ App ]    [ App ] [ App ] [App]      scrolls one whole block at a time
  [ App ]    [ App ] [ App ] [App]

     FREQUENT           PINNED
  [ A ][ B ][ C ]  |  [ D ][ E ]     <- 3 most-opened + up to 2 pinned
```

* The drawer is a vertical pager: every swipe snaps to the next block of four
  rows, so the grid never stops half way between items.
* **Launcher Settings** is the first tile in the drawer — a built-in "app" with
  its own icon. Long-pressing the clock opens it too.
* The dock shows the three apps you open most (counted locally, no usage-stats
  permission), next to up to two apps you pinned yourself.

## Features

**Appearance**
* Icon shape: rounded, rounded square, square, or the app's original artwork.
* Pixel-art icon mode with selectable detail (24/32/48/64 px), drawn with
  nearest-neighbour scaling so it stays crisp instead of blurry.
* Five CRT palettes: green, amber, cyan, magenta, ice.
* Optional scanline overlay, app labels, column count, and wallpaper mode with
  adjustable dim.
* Portrait and landscape both supported; the grid re-flows and the header
  collapses automatically.

**Sound**
* Eight chiptune effects (tap, open app, back, toggle, page snap, menu, pin,
  error) generated as 8-bit square waves.
* Every one of them can be replaced with your own audio file, or reset back to
  the default individually.
* Master mute and volume. Stays silent while the phone is on silent/vibrate.

**Apps**
* Long-press any app: pin to dock, hide from drawer, app info, uninstall.
* Work-profile apps are listed and badged.
* Installs and removals update the drawer instantly via `LauncherApps`
  callbacks — no polling, no re-scanning.

## Battery and performance notes

The launcher is deliberately boring about resources:

* **No background work at all.** No services, no alarms, no jobs, no timers.
* The clock updates from the system's own `ACTION_TIME_TICK` broadcast, and the
  receiver is registered only while the launcher is on screen.
* Icons are decoded lazily, on at most two threads, and cached. In pixel mode a
  cached icon is ~9 KB instead of ~147 KB, so hundreds of apps stay in memory
  and are never decoded twice.
* The cache is halved automatically when the launcher goes off screen.
* **Low power mode** (in settings) drops scanlines, open animations and icon
  detail in one switch.
* Release builds are minified and resource-shrunk; the APK ships no permissions.

## Building

CI builds the APK on every push — see the **Actions** tab, or grab the newest
build from the [`latest` release](../../releases/tag/latest).

Locally:

```bash
./gradlew assembleRelease     # app/build/outputs/apk/release/app-release.apk
```

Requires JDK 17 and the Android SDK (compileSdk 35, minSdk 24).

### Signing

Builds are signed with the key in `keystore/` so that every CI build installs
over the previous one. That key is public and only good for that purpose — to
sign with your own, set `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and
`KEY_PASSWORD` in the environment.

## Installing

1. Download the APK and install it (allow "install unknown apps" if asked).
2. Press HOME and choose Pixel Launcher, or open the app and use
   **Settings → Set as default launcher**.

## Credits

* [Press Start 2P](https://fonts.google.com/specimen/Press+Start+2P) and
  [VT323](https://fonts.google.com/specimen/VT323) — SIL Open Font License 1.1.
* Sound effects generated with `tools/gen_sfx.py`.
