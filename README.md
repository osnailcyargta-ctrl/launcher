# Pixel Launcher

A real Android home-screen replacement with a retro CRT look. It lists **every
app installed on the device**, not just the pre-installed ones, and is built to
stay light on both CPU and battery.

## Layout

```
              14:32                <- widget stack: clock, dino runner,
       SEN 27 JUL 2026 | BAT 82%      custom text - any combination

  > [ SEARCH APPS              ]    <- filters as you type

  [SETTINGS] [Folder] [App] [App] A
  [ App ]    [ App ]  [App] [App] C   <- 4 rows per screen, snaps one
  [ App ]    [ App ]  [App] [App] G      whole block at a time;
  [ App ]    [ App ]  [App] [App] M      drag the A-Z strip to scrub

     FREQUENT              PINNED
  [ A ][ B ][ C ]  |  [ D ][ E ][ F ][ G ]   <- 3 most-opened + up to 4 pinned
```

* The drawer is a vertical pager: every swipe snaps to the next block of four
  rows, so the grid never stops half way between items.
* **Launcher Settings** is the first tile in the drawer — a built-in "app" with
  its own icon. Long-pressing the widget area opens it too.
* The dock shows the three apps you open most (counted locally, no usage-stats
  permission), next to up to four apps you pinned yourself.
* Hold any app to pin it, hide it, or drop it into a **folder**. Folders show up
  as a single tile and open as a panel you can rename or delete.

## Features

**Widgets**

The strip above the drawer is a stack you assemble yourself:

* **Clock bundle** — big pixel clock, date, and battery readout.
* **Dino runner** — the Chrome offline dinosaur, jumping cacti forever. Tap it to
  make it jump early. It ticks at about 11 fps and freezes the moment you leave
  the launcher.
* **Custom text** — whatever you want written across the top.

**Appearance**
* Icon shape: rounded, rounded square, square, or the app's original artwork.
* Three artwork sources: the app's own icon, the launcher's **built-in pixel
  pack**, or any installed **third-party icon pack** (Nova/ADW format).
* Pixel-art icon mode with selectable detail (24/32/48/64 px), drawn with
  nearest-neighbour scaling so it stays crisp instead of blurry.
* Independent icon size and text size scales.
* Four pixel typefaces plus system monospace, picked separately for headings and
  body text.
* Five CRT palettes: green, amber, cyan, magenta, ice.
* Optional scanline overlay, app labels, column count, and wallpaper mode with
  adjustable dim.
* Portrait and landscape both supported; the grid re-flows and the header
  collapses automatically.

**Icon packs**

Two of them ship from this repo:

* The **built-in** pack, compiled into the launcher and selected with one tap.
* A **standalone icon pack APK** (`pixel-icon-pack-*.apk`) in the ADW/Nova
  format, so the same artwork also works in Nova, Lawnchair, Apex and ADW.

Both are generated from the same grids by `tools/gen_iconpack.py` - edit a glyph
there, re-run it, and the launcher and the pack update together.

**How the built-in pack works**

Popular apps get hand-drawn 12x12 artwork defined as character grids in Kotlin —
no drawables to ship. Everything else keeps its real icon but is snapped to the
four shades of the active palette, so the drawer themes as a whole instead of
looking half-converted.

**Sound**
* Eight chiptune effects (tap, open app, back, toggle, page snap, menu, pin,
  error) generated as 8-bit square waves.
* Every one of them can be replaced with your own audio file, or reset back to
  the default individually.
* Master mute and volume. Playback goes through the media stream, so the side
  keys control it while the launcher is open.
* Optional "mute when the phone is silent" — off by default.

**Apps**
* Search filters the whole list as you type; the A-Z strip scrubs through it
  without opening the keyboard.
* Long-press any app: pin to dock, add to folder, hide from drawer, app info,
  uninstall.
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
* The wallpaper flag is only set on the window while wallpaper mode is on, so the
  system does not draw one behind an opaque screen.

## Where the customisation lives

There is no styles or strings resource to dig through. Colours, typography,
window setup, icon artwork, and every layout constant are plain Kotlin:

| What | Where |
| --- | --- |
| Palettes, fonts, type scale | `ui/Theme.kt` |
| Retro widgets (panel, button, glyphs) | `ui/Pixel.kt` |
| Clock / dino / text widgets | `ui/Widgets.kt` |
| Built-in icon pack artwork | `core/IconPack.kt` |
| Icon rendering and caching | `core/IconLoader.kt` |
| Every setting and its default | `core/Model.kt` |

The only XML left is the adaptive launcher icon, because Android requires that
particular format.

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
